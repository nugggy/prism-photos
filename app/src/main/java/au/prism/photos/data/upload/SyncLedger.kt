package au.prism.photos.data.upload

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import au.prism.photos.data.Diagnostics
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.MediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One device item that has been written to the Plex library folder. */
@Serializable
data class LedgerEntry(
    /** The device item's content:// URI, the stable key MediaItem.localUri always carries. */
    val contentUri: String,
    /** The file name as uploaded, which may carry a _1 style suffix after a name clash. */
    val displayName: String,
    val size: Long,
    val takenAt: Long,
    /** Path relative to the destination root, including the (possibly renamed) file name. */
    val remotePath: String,
    /** Set once resolvePlexIds matches this upload to the Plex item it became. */
    val plexRatingKey: String? = null,
    val uploadedAt: Long = 0,
    /** The device's own file name before any clash rename, so the name+size fallback still matches. */
    val originalName: String? = null,
)

@Serializable
private data class LedgerData(val entries: List<LedgerEntry> = emptyList())

private val Context.syncLedgerDataStore by preferencesDataStore(name = "prism_sync_ledger")
private val LEDGER_KEY = stringPreferencesKey("ledger_json")

/** Persist at least this often while a batch is recording, so a crash loses little. */
private const val PERSIST_EVERY_RECORDS = 25
private const val PERSIST_EVERY_MS = 5_000L

/**
 * Records what has already been uploaded so a sync never sends the same file twice, and links
 * each upload to the Plex item it eventually becomes once the library has been scanned.
 *
 * All changes go through [mutex] so a manual upload running alongside the background sync
 * cannot drop each other's entries. Lookups use a [LedgerIndex] rebuilt on each change.
 */
class SyncLedger(private val app: Application) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val mutex = Mutex()
    private val state: MutableStateFlow<List<LedgerEntry>>

    @Volatile
    private var index: LedgerIndex

    private var pendingWrites = 0
    private var lastPersistAt = 0L

    init {
        val loaded = runBlocking {
            runCatching {
                val prefs = app.syncLedgerDataStore.data.first()
                prefs[LEDGER_KEY]?.let { json.decodeFromString<LedgerData>(it).entries }
            }.getOrNull()
        }
        val initial = loaded ?: emptyList()
        index = LedgerIndex(initial)
        state = MutableStateFlow(initial)
    }

    val entries: StateFlow<List<LedgerEntry>> = state.asStateFlow()

    /** The current lookup index; cheap to call, rebuilt only when the ledger changes. */
    fun index(): LedgerIndex = index

    /** True if [item] (matched by content URI, or by name+size as a fallback) is already synced. */
    fun isUploaded(item: MediaItem): Boolean = index.isUploaded(item)

    fun isUploaded(contentUri: String, displayName: String, size: Long): Boolean = index.isUploaded(contentUri, displayName, size)

    fun entryFor(item: MediaItem): LedgerEntry? = index.entryFor(item)

    /** Caller must hold [mutex]. */
    private fun publish(next: List<LedgerEntry>) {
        index = LedgerIndex(next)
        state.value = next
    }

    /**
     * Adds or replaces the entry for [contentUri]. Writes to disk are batched (see
     * [PERSIST_EVERY_RECORDS]); call [flush] when a batch of uploads finishes.
     */
    suspend fun record(
        contentUri: String,
        displayName: String,
        size: Long,
        takenAt: Long,
        remotePath: String,
        plexRatingKey: String? = null,
        originalName: String? = null,
    ) {
        val entry = LedgerEntry(
            contentUri = contentUri,
            displayName = displayName,
            size = size,
            takenAt = takenAt,
            remotePath = remotePath,
            plexRatingKey = plexRatingKey,
            uploadedAt = System.currentTimeMillis(),
            originalName = originalName?.takeIf { it != displayName },
        )
        mutex.withLock {
            publish(state.value.filterNot { it.contentUri == contentUri } + entry)
            pendingWrites++
            if (pendingWrites >= PERSIST_EVERY_RECORDS || System.currentTimeMillis() - lastPersistAt >= PERSIST_EVERY_MS) {
                persistLocked()
            }
        }
    }

    suspend fun record(item: MediaItem, remotePath: String, plexRatingKey: String? = null) {
        record(
            contentUri = item.localUri ?: item.id,
            displayName = remotePath.substringAfterLast('/').ifBlank { item.title },
            size = item.fileSize,
            takenAt = item.takenAt,
            remotePath = remotePath,
            plexRatingKey = plexRatingKey,
            originalName = item.title,
        )
    }

    /** Writes any batched changes to disk. */
    suspend fun flush() {
        mutex.withLock { if (pendingWrites > 0) persistLocked() }
    }

    suspend fun setPlexRatingKey(contentUri: String, ratingKey: String) {
        mutex.withLock {
            publish(state.value.map { if (it.contentUri == contentUri) it.copy(plexRatingKey = ratingKey) else it })
            persistLocked()
        }
    }

    /**
     * Matches ledger entries that do not yet have a Plex id to items in [media]'s timeline by
     * comparing file name and size against [MediaItem.filePath]/[MediaItem.fileSize], and records
     * the rating key of every match. Forces a timeline refresh first when there is anything to
     * resolve, since the scan that made the uploads visible may not have been picked up yet.
     */
    suspend fun resolvePlexIds(media: MediaRepository) {
        if (state.value.none { it.plexRatingKey == null }) return
        try {
            media.refreshTimeline(force = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diagnostics.log("Sync ledger: timeline refresh failed: ${e.message}")
        }
        delay(300)
        val timelineByName = LedgerMatching.indexTimeline(media.timeline.value)
        mutex.withLock {
            var changed = false
            val updated = state.value.map { entry ->
                if (entry.plexRatingKey != null) return@map entry
                val match = LedgerMatching.matchPlexItem(entry, timelineByName)
                if (match != null) {
                    changed = true
                    entry.copy(plexRatingKey = match.id)
                } else {
                    entry
                }
            }
            if (changed) {
                publish(updated)
                persistLocked()
                Diagnostics.log("Sync ledger: resolved ${updated.count { it.plexRatingKey != null }} of ${updated.size} uploads to Plex items")
            }
        }
    }

    /** Caller must hold [mutex]. */
    private suspend fun persistLocked() {
        val snapshot = state.value
        try {
            app.syncLedgerDataStore.edit { it[LEDGER_KEY] = json.encodeToString(LedgerData(snapshot)) }
            pendingWrites = 0
            lastPersistAt = System.currentTimeMillis()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diagnostics.log("Sync ledger: couldn't save: ${e.message}")
        }
    }
}
