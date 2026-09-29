package au.prism.photos.data.upload

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns

data class DeviceFileInfo(
    val uri: String,
    val displayName: String,
    val size: Long,
    val takenAt: Long,
)

/**
 * Looks up display name, size and date taken for a bare content:// URI without depending on
 * DeviceMediaSourceImpl (owned by another area), since the upload screen and worker only ever
 * carry the URI string across [au.prism.photos.ui.nav.Routes.upload].
 */
object DeviceFileInspector {
    fun inspect(resolver: ContentResolver, uri: Uri): DeviceFileInfo {
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        var takenAt = System.currentTimeMillis()
        val projection = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        runCatching {
            resolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }
                        ?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }
                        ?.let { size = c.getLong(it) }
                    val takenIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                    val taken = if (takenIdx >= 0 && !c.isNull(takenIdx)) c.getLong(takenIdx) else 0L
                    if (taken > 0) {
                        takenAt = taken
                    } else {
                        c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED).takeIf { it >= 0 && !c.isNull(it) }
                            ?.let { val added = c.getLong(it); if (added > 0) takenAt = added * 1000L }
                    }
                }
            }
        }
        if (size <= 0L) {
            runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { size = it.length } }
        }
        return DeviceFileInfo(uri.toString(), name, size, takenAt)
    }
}
