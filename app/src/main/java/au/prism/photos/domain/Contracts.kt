package au.prism.photos.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contracts between the data layer and the UI. The UI only talks to these.
 * Implementations live in au.prism.photos.data. See docs/plex-api.md.
 */

interface PlexAuth {
    suspend fun createPin(): Result<PlexPin>
    fun authUrl(pin: PlexPin): String
    /** Returns the account token once the PIN has been claimed, null while still waiting. */
    suspend fun pollPin(pinId: Long): Result<String?>
    suspend fun user(accountToken: String): Result<PlexUser>
    suspend fun servers(accountToken: String): Result<List<PlexServer>>
    /** Picks the best reachable connection according to [mode] and an optional manual URL. */
    suspend fun connect(server: PlexServer, mode: ConnectionMode, manualUrl: String?): Result<ActiveConnection>
    /** Verifies a manual server URL and token. Returns the server friendly name. */
    suspend fun verifyManual(url: String, token: String): Result<String>
    suspend fun signOut(accountToken: String?)
}

interface SessionStore {
    val session: StateFlow<Session>
    suspend fun update(transform: (Session) -> Session)
    suspend fun clear()
}

interface SettingsStore {
    val settings: StateFlow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Device only customisations. Plex has no concept of these. */
interface LocalStore {
    val lockedItemIds: StateFlow<Set<String>>
    val lockedAlbumIds: StateFlow<Set<String>>
    val albumCovers: StateFlow<Map<String, String>>       // albumId -> itemId
    val albumAccents: StateFlow<Map<String, Long>>        // albumId -> ARGB colour
    val albumSort: StateFlow<Map<String, SortOrder>>
    suspend fun setItemLocked(id: String, locked: Boolean)
    suspend fun setItemsLocked(ids: Collection<String>, locked: Boolean)
    suspend fun setAlbumLocked(id: String, locked: Boolean)
    suspend fun setAlbumCover(albumId: String, itemId: String?)
    suspend fun setAlbumAccent(albumId: String, argb: Long?)
    suspend fun setAlbumSort(albumId: String, order: SortOrder?)
}

interface MediaRepository {
    /** Every visible (not locked) item in the selected library, newest first. */
    val timeline: StateFlow<List<MediaItem>>
    val timelineState: StateFlow<LoadState>
    /** Items the user has locked. Only shown behind the biometric gate. */
    val lockedItems: StateFlow<List<MediaItem>>

    suspend fun refreshTimeline(force: Boolean = false)
    suspend fun libraries(): Result<List<PlexLibrary>>
    suspend fun rootAlbums(): Result<AlbumContents>
    suspend fun albumContents(albumId: String): Result<AlbumContents>
    suspend fun album(albumId: String): Album?
    suspend fun favourites(): Result<List<MediaItem>>
    suspend fun search(query: String): Result<List<MediaItem>>
    /** From cache, or fetched from /library/metadata/{id}. */
    suspend fun item(id: String): MediaItem?
    suspend fun items(ids: List<String>): List<MediaItem>

    suspend fun setFavourite(id: String, favourite: Boolean): Result<Unit>
    suspend fun rename(id: String, kind: MediaKind?, title: String): Result<Unit>
    suspend fun renameAlbum(albumId: String, title: String): Result<Unit>
    suspend fun setSummary(id: String, kind: MediaKind?, summary: String): Result<Unit>
    suspend fun addTag(id: String, kind: MediaKind?, tag: String): Result<Unit>
    suspend fun removeTag(id: String, kind: MediaKind?, tag: String): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
    suspend fun clearCache()

    /** Square-ish thumbnail through the Plex transcoder. [size] is the longest edge in px. */
    fun thumbUrl(item: MediaItem, size: Int): String
    fun albumCoverUrl(album: Album, size: Int): String
    /** Full resolution original with token. */
    fun originalUrl(item: MediaItem): String
    fun downloadUrl(item: MediaItem): String
    /** Direct play URL, or an HLS transcode URL when [transcode] is true. */
    fun videoUrl(item: MediaItem, transcode: Boolean): String
    suspend fun stopTranscode()
}

interface UpdateChecker {
    val installedVersion: String
    suspend fun check(repo: String): Result<UpdateInfo?>
    fun download(info: UpdateInfo): Flow<DownloadProgress>
    /** Launches the system package installer for a downloaded APK. */
    fun install(filePath: String)
}

interface DeviceMediaSource {
    suspend fun hasPermission(): Boolean
    suspend fun albums(): List<DeviceAlbum>
    /** Items in a bucket, or all device items when [bucketId] is null. Newest first. */
    suspend fun items(bucketId: String?): List<MediaItem>
    suspend fun itemsForUris(uris: List<String>): List<MediaItem>
}
