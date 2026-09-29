package au.prism.photos.data.upload

import kotlinx.serialization.Serializable

/** Where uploaded photos and videos are written before Plex is asked to scan them. */
@Serializable
enum class DestinationType { SMB, WEBDAV }

/**
 * Connection details for the upload destination. Both kinds are supported at once so the
 * user can fill in either without losing the other's fields when switching [type].
 */
@Serializable
data class UploadDestination(
    val type: DestinationType = DestinationType.SMB,
    // SMB (jcifs-ng)
    val smbHost: String = "",
    val smbShare: String = "",
    /** Path inside the share, no leading or trailing slash, e.g. "PLEX Library/Photos". */
    val smbPath: String = "",
    val smbUsername: String = "",
    val smbPassword: String = "",
    val smbDomain: String = "",
    // WebDAV
    val webDavBaseUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
)

/** Everything Settings needs for the "Sync to Plex" section, and what the worker reads. */
@Serializable
data class SyncSettings(
    val destination: UploadDestination = UploadDestination(),
    /** Plex library section key uploads should be scanned into. */
    val librarySectionKey: String = "",
    /** The library's folder path on the server, from Directory[].Location[].path. */
    val serverFolderPath: String = "",
    /** Sub folder pattern under the library folder. Tokens: {device} {yyyy} {MM} {dd}. */
    val subFolderPattern: String = "Uploads/{device}/{yyyy}/{MM}",
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val autoSyncEnabled: Boolean = false,
    /** Device album (DeviceAlbum.bucketId) names selected for automatic backup. */
    val selectedBuckets: Set<String> = emptySet(),
    val lastSyncAt: Long = 0,
    val lastSyncUploaded: Int = 0,
    val lastSyncFailed: Int = 0,
)

/**
 * Resolves the `{device}/{yyyy}/{MM}/{dd}` tokens in a sub folder pattern. Kept free of Android
 * dependencies (java.util.Calendar only) so it can be unit tested on the plain JVM.
 */
object SubFolderPattern {
    fun resolve(pattern: String, deviceName: String, whenMillis: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = whenMillis
        val yyyy = cal.get(java.util.Calendar.YEAR).toString()
        val mm = (cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
        val dd = cal.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return pattern
            .replace("{device}", sanitiseSegment(deviceName))
            .replace("{yyyy}", yyyy)
            .replace("{MM}", mm)
            .replace("{dd}", dd)
            .trim('/')
    }

    /** Strips characters that are not valid in an SMB or WebDAV path segment. */
    private fun sanitiseSegment(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "device" }
}
