package au.prism.photos.domain

import kotlinx.serialization.Serializable

/** Kind of media item. Plex "photo" maps to PHOTO, Plex "clip" maps to VIDEO. */
@Serializable
enum class MediaKind { PHOTO, VIDEO }

/** Camera metadata Plex extracts from the file. All optional. */
@Serializable
data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    val aperture: String? = null,
    val exposure: String? = null,
    val iso: Int? = null,
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val videoResolution: String? = null,
)

/**
 * One photo or video. Used for Plex items and for device (MediaStore) items.
 * For device items [localUri] is set and [partKey]/[thumbPath] are null.
 */
@Serializable
data class MediaItem(
    val id: String,
    val kind: MediaKind,
    val title: String,
    val summary: String = "",
    /** Epoch millis of when the photo was taken (Plex originallyAvailableAt), falls back to addedAt. */
    val takenAt: Long,
    val addedAt: Long = takenAt,
    val width: Int = 0,
    val height: Int = 0,
    /** Plex thumb path, e.g. /library/metadata/1234/thumb/1700000000 */
    val thumbPath: String? = null,
    /** Plex part key for the full resolution file, e.g. /library/parts/5678/1700000000/file.jpg */
    val partKey: String? = null,
    val durationMs: Long = 0,
    val favourite: Boolean = false,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val exif: ExifInfo? = null,
    val fileSize: Long = 0,
    val filePath: String? = null,
    val tags: List<String> = emptyList(),
    val place: String? = null,
    val country: String? = null,
    /** Plex library section key this item belongs to. Empty for device items. */
    val sectionKey: String = "",
    /** content:// URI for device items. */
    val localUri: String? = null,
    val mimeType: String? = null,
) {
    val isVideo: Boolean get() = kind == MediaKind.VIDEO
    val isLocal: Boolean get() = localUri != null
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

/** A Plex photo album (folder). */
@Serializable
data class Album(
    val id: String,
    val title: String,
    val thumbPath: String? = null,
    /** Plex collage image path, preferred over thumbPath for covers when present. */
    val compositePath: String? = null,
    val itemCount: Int = 0,
    val addedAt: Long = 0,
    val parentId: String? = null,
    val sectionKey: String = "",
    val summary: String = "",
)

@Serializable
data class AlbumContents(
    val albums: List<Album> = emptyList(),
    val items: List<MediaItem> = emptyList(),
)

@Serializable
data class PlexLibrary(
    val key: String,
    val title: String,
    val uuid: String = "",
    val thumbPath: String? = null,
)

@Serializable
data class PlexConnection(
    val uri: String,
    val local: Boolean,
    val relay: Boolean,
    val protocol: String,
    val address: String = "",
    val port: Int = 0,
)

@Serializable
data class PlexServer(
    val name: String,
    val clientIdentifier: String,
    /** Server scoped token from plex.tv resources. Use this for all server requests. */
    val accessToken: String,
    val connections: List<PlexConnection>,
    val owned: Boolean = true,
    val version: String = "",
    val platform: String = "",
)

enum class ConnectionMode { AUTO, LAN, REMOTE }

enum class ConnectionKind { MANUAL, LOCAL, REMOTE, RELAY }

@Serializable
data class ActiveConnection(
    val uri: String,
    val kind: ConnectionKind,
)

@Serializable
data class PlexUser(
    val id: Long = 0,
    val username: String,
    val email: String = "",
    val thumb: String? = null,
    val title: String = username,
)

@Serializable
data class PlexPin(
    val id: Long,
    val code: String,
)

/** Everything needed to talk to the chosen server. Null session means signed out. */
@Serializable
data class Session(
    val accountToken: String? = null,
    val user: PlexUser? = null,
    val server: PlexServer? = null,
    /** Token to use for the server (server.accessToken, or the manual token). */
    val serverToken: String? = null,
    val active: ActiveConnection? = null,
    val libraryKey: String? = null,
    val libraryTitle: String? = null,
    val clientId: String,
) {
    val isSignedIn: Boolean get() = serverToken != null && active != null
    val hasLibrary: Boolean get() = isSignedIn && libraryKey != null
}

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

enum class ThumbQuality { LOW, MEDIUM, HIGH }

enum class SortOrder { NEWEST, OLDEST, NAME }

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColour: Boolean = false,
    val gridColumns: Int = 3,
    val connectionMode: ConnectionMode = ConnectionMode.AUTO,
    val manualServerUrl: String = "",
    val preferTranscode: Boolean = false,
    val updateRepo: String = "",
    val autoCheckUpdates: Boolean = true,
    val slideshowIntervalSec: Int = 4,
    val thumbQuality: ThumbQuality = ThumbQuality.MEDIUM,
    val showDeviceMedia: Boolean = true,
    val lastUpdateCheckAt: Long = 0,
)

@Serializable
data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: String,
    val htmlUrl: String,
)

sealed class DownloadProgress {
    data class InProgress(val bytes: Long, val total: Long) : DownloadProgress() {
        val fraction: Float get() = if (total > 0) bytes.toFloat() / total else 0f
    }
    data class Done(val filePath: String) : DownloadProgress()
    data class Failed(val message: String) : DownloadProgress()
}

@Serializable
data class DeviceAlbum(
    val bucketId: String,
    val name: String,
    val count: Int,
    val coverUri: String?,
)

sealed class LoadState {
    data object Idle : LoadState()
    data object Loading : LoadState()
    data object Loaded : LoadState()
    data class Error(val message: String) : LoadState()
}

/** What a viewer is paging through. */
sealed class ViewerSource {
    data object Timeline : ViewerSource()
    data object Favourites : ViewerSource()
    data object Locked : ViewerSource()
    data class Album(val albumId: String) : ViewerSource()
    data class Search(val query: String) : ViewerSource()
    data class Device(val bucketId: String?) : ViewerSource()
    /** Explicit list of Plex item ids (multi select, shared intents). */
    data class Items(val ids: List<String>) : ViewerSource()
    /** One or more content URIs opened from another app. */
    data class ExternalUris(val uris: List<String>) : ViewerSource()
}
