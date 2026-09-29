package au.prism.photos.data.upload

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import au.prism.photos.data.Diagnostics
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.MediaRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One device item that has been written to the Plex library folder. */
@Serializable
data class LedgerEntry(
    /** The device item's content:// URI, the stable key MediaItem.localUri always carries. */
    val contentUri: String,
    val displayName: String,
    val size: Long,
    val takenAt: Long,
    /** Path relative to the destination root, including the (possibly renamed) file name. */
    val remotePath: String,
    /** Set once resolvePlexIds matches this upload to the Plex item it became. */
    val plexRatingKey: String? = null,
    val uploadedAt: Long = 0,
)

@Serializable
private data class LedgerData(val entries: List<LedgerEntry> = emptyList())

private val Context.syncLedgerDataStore by preferencesDataStore(name = "prism_sync_ledger")
private val LEDGER_KEY = stringPreferencesKey("ledger_json")

/**
 * Records what has already been uploaded so a sync never sends the same file twice, and links
 * each upload to the Plex item it eventually becomes once the library has been scanned.
 */
class SyncLedger(private val app: Application) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val state: MutableStateFlow<List<LedgerEntry>>

    init {
        val loaded = runBlocking {
            runCatching {
                val prefs = app.syncLedgerDataStore.data.first()
                prefs[LEDGER_KEY]?.let { json.decodeFromString<LedgerData>(it).entries }
            }.getOrNull()
        }
        state = MutableStateFlow(loaded ?: emptyList())
    }

    val entries: StateFlow<List<LedgerEntry>> = state.asStateFlow()

    /** True if [item] (matched by content URI, or by name+size as a fallback) is already synced. */
    fun isUploaded(item: MediaItem): Boolean = LedgerMatching.isUploaded(state.value, item)

    fun entryFor(item: MediaItem): LedgerEntry? = LedgerMatching.entryFor(state.value, item)

    suspend fun record(
        contentUri: String,
        displayName: String,
        size: Long,
        takenAt: Long,
        remotePath: String,
        plexRatingKey: String? = null,
    ) {
        val entry = LedgerEntry(
            contentUri = contentUri,
            displayName = displayName,
            size = size,
            takenAt = takenAt,
            remotePath = remotePath,
            plexRatingKey = plexRatingKey,
            uploadedAt = System.currentTimeMillis(),
        )
        state.value = state.value.filterNot { it.contentUri == contentUri } + entry
        persist()
    }

    suspend fun record(item: MediaItem, remotePath: String, plexRatingKey: String? = null) {
        record(
            contentUri = item.localUri ?: item.id,
            displayName = item.title,
            size = item.fileSize,
            takenAt = item.takenAt,
            remotePath = remotePath,
            plexRatingKey = plexRatingKey,
        )
    }

    suspend fun setPlexRatingKey(contentUri: String, ratingKey: String) {
        state.value = state.value.map { if (it.contentUri == contentUri) it.copy(plexRatingKey = ratingKey) else it }
        persist()
    }

    /**
     * Matches ledger entries that do not yet have a Plex id to items in [media]'s timeline by
     * comparing file name and size against [MediaItem.filePath]/[MediaItem.fileSize], and records
     * the rating key of every match. Forces a timeline refresh first when there is anything to
     * resolve, since the scan that made the uploads visible may not have been picked up yet.
     */
    suspend fun resolvePlexIds(media: MediaRepository) {
        val unresolved = state.value.filter { it.plexRatingKey == null }
        if (unresolved.isEmpty()) return
        runCatching { media.refreshTimeline(force = true) }
        delay(300)
        val timeline = media.timeline.value
        var changed = false
        val updated = state.value.map { entry ->
            if (entry.plexRatingKey != null) return@map entry
            val match = LedgerMatching.matchPlexItem(entry, timeline)
            if (match != null) {
                changed = true
                entry.copy(plexRatingKey = match.id)
            } else {
                entry
            }
        }
        if (changed) {
            state.value = updated
            persist()
            Diagnostics.log("Sync ledger: resolved ${updated.count { it.plexRatingKey != null }} of ${updated.size} uploads to Plex items")
        }
    }

    private suspend fun persist() {
        runCatching { app.syncLedgerDataStore.edit { it[LEDGER_KEY] = json.encodeToString(LedgerData(state.value)) } }
    }
}
