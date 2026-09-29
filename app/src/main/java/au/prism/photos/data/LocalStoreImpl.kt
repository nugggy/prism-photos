package au.prism.photos.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import au.prism.photos.domain.LocalStore
import au.prism.photos.domain.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.localDataStore by preferencesDataStore(name = "prism_local")
private val LOCKED_ITEMS_KEY = stringPreferencesKey("locked_items_json")
private val LOCKED_ALBUMS_KEY = stringPreferencesKey("locked_albums_json")
private val ALBUM_COVERS_KEY = stringPreferencesKey("album_covers_json")
private val ALBUM_ACCENTS_KEY = stringPreferencesKey("album_accents_json")
private val ALBUM_SORT_KEY = stringPreferencesKey("album_sort_json")

/** Device only customisations (locks, covers, accents, per-album sort). Plex has no concept of these. */
class LocalStoreImpl(private val app: Application) : LocalStore {
    private val json = Json { ignoreUnknownKeys = true }

    private val locked: MutableStateFlow<Set<String>>
    private val lockedAlbums: MutableStateFlow<Set<String>>
    private val covers: MutableStateFlow<Map<String, String>>
    private val accents: MutableStateFlow<Map<String, Long>>
    private val sorts: MutableStateFlow<Map<String, SortOrder>>

    init {
        val prefs = runBlocking { runCatching { app.localDataStore.data.first() }.getOrNull() }
        locked = MutableStateFlow(prefs?.get(LOCKED_ITEMS_KEY)?.let { decodeOrNull<Set<String>>(it) } ?: emptySet())
        lockedAlbums = MutableStateFlow(prefs?.get(LOCKED_ALBUMS_KEY)?.let { decodeOrNull<Set<String>>(it) } ?: emptySet())
        covers = MutableStateFlow(prefs?.get(ALBUM_COVERS_KEY)?.let { decodeOrNull<Map<String, String>>(it) } ?: emptyMap())
        accents = MutableStateFlow(prefs?.get(ALBUM_ACCENTS_KEY)?.let { decodeOrNull<Map<String, Long>>(it) } ?: emptyMap())
        sorts = MutableStateFlow(prefs?.get(ALBUM_SORT_KEY)?.let { decodeOrNull<Map<String, SortOrder>>(it) } ?: emptyMap())
    }

    private inline fun <reified T> decodeOrNull(text: String): T? = runCatching { json.decodeFromString<T>(text) }.getOrNull()

    override val lockedItemIds: StateFlow<Set<String>> = locked.asStateFlow()
    override val lockedAlbumIds: StateFlow<Set<String>> = lockedAlbums.asStateFlow()
    override val albumCovers: StateFlow<Map<String, String>> = covers.asStateFlow()
    override val albumAccents: StateFlow<Map<String, Long>> = accents.asStateFlow()
    override val albumSort: StateFlow<Map<String, SortOrder>> = sorts.asStateFlow()

    override suspend fun setItemLocked(id: String, locked: Boolean) {
        this.locked.update { if (locked) it + id else it - id }
        persist(LOCKED_ITEMS_KEY, this.locked.value)
    }

    override suspend fun setItemsLocked(ids: Collection<String>, locked: Boolean) {
        this.locked.update { if (locked) it + ids else it - ids.toSet() }
        persist(LOCKED_ITEMS_KEY, this.locked.value)
    }

    override suspend fun setAlbumLocked(id: String, locked: Boolean) {
        lockedAlbums.update { if (locked) it + id else it - id }
        persist(LOCKED_ALBUMS_KEY, lockedAlbums.value)
    }

    override suspend fun setAlbumCover(albumId: String, itemId: String?) {
        covers.update { if (itemId == null) it - albumId else it + (albumId to itemId) }
        persist(ALBUM_COVERS_KEY, covers.value)
    }

    override suspend fun setAlbumAccent(albumId: String, argb: Long?) {
        accents.update { if (argb == null) it - albumId else it + (albumId to argb) }
        persist(ALBUM_ACCENTS_KEY, accents.value)
    }

    override suspend fun setAlbumSort(albumId: String, order: SortOrder?) {
        sorts.update { if (order == null) it - albumId else it + (albumId to order) }
        persist(ALBUM_SORT_KEY, sorts.value)
    }

    private suspend inline fun <reified T> persist(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: T) {
        runCatching { app.localDataStore.edit { it[key] = json.encodeToString(value) } }
    }
}
