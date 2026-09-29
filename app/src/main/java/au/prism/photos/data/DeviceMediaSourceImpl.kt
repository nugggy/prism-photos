package au.prism.photos.data

import android.app.Application
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import au.prism.photos.domain.DeviceAlbum
import au.prism.photos.domain.DeviceMediaSource
import au.prism.photos.domain.ExifInfo
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Phone gallery via MediaStore, so Prism can also work as a general gallery app. */
class DeviceMediaSourceImpl(private val app: Application) : DeviceMediaSource {

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.WIDTH,
        MediaStore.Files.FileColumns.HEIGHT,
        MediaStore.Files.FileColumns.DURATION,
        MediaStore.Files.FileColumns.DATE_TAKEN,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.BUCKET_ID,
        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
    )

    private val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
    private val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
    )

    override suspend fun hasPermission(): Boolean = withContext(Dispatchers.Default) {
        val images = granted(imagesPermission())
        val video = if (Build.VERSION.SDK_INT >= 33) granted(android.Manifest.permission.READ_MEDIA_VIDEO) else images
        val partial = if (Build.VERSION.SDK_INT >= 34) granted(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else false
        images || video || partial
    }

    private fun imagesPermission(): String = when {
        Build.VERSION.SDK_INT >= 33 -> android.Manifest.permission.READ_MEDIA_IMAGES
        else -> android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun albums(): List<DeviceAlbum> = withContext(Dispatchers.IO) {
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        val covers = HashMap<String, String>()
        queryCursor(null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getStringOrNull(bucketIdCol) ?: continue
                val bucketName = cursor.getStringOrNull(bucketNameCol) ?: bucketId
                val (_, count) = counts[bucketId] ?: (bucketName to 0)
                counts[bucketId] = bucketName to (count + 1)
                if (!covers.containsKey(bucketId)) {
                    val id = cursor.getLong(idCol)
                    val type = cursor.getInt(typeCol)
                    covers[bucketId] = contentUriFor(type, id).toString()
                }
            }
        }
        counts.map { (bucketId, nameAndCount) -> DeviceAlbum(bucketId, nameAndCount.first, nameAndCount.second, covers[bucketId]) }
    }

    override suspend fun items(bucketId: String?): List<MediaItem> = withContext(Dispatchers.IO) {
        val sel = if (bucketId != null) "$selection AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?" else selection
        val args = if (bucketId != null) selectionArgs + bucketId else selectionArgs
        val out = mutableListOf<MediaItem>()
        queryCursor(sel, args)?.use { cursor -> while (cursor.moveToNext()) out += cursor.toMediaItem() }
        out.sortedByDescending { it.takenAt }
    }

    override suspend fun itemsForUris(uris: List<String>): List<MediaItem> = withContext(Dispatchers.IO) {
        uris.mapNotNull { resolveUri(Uri.parse(it)) }
    }

    private fun resolveUri(uri: Uri): MediaItem? {
        val resolver = app.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Unknown"
        val mime = resolver.getType(uri)
        val isVideo = mime?.startsWith("video/") == true
        return MediaItem(
            id = "local:$uri",
            kind = if (isVideo) MediaKind.VIDEO else MediaKind.PHOTO,
            title = displayName,
            takenAt = System.currentTimeMillis(),
            localUri = uri.toString(),
            mimeType = mime,
        )
    }

    private fun queryCursor(extraSelection: String?, extraArgs: Array<String> = selectionArgs): Cursor? {
        val sel = extraSelection ?: selection
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC, ${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        return app.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            sel,
            extraArgs,
            sortOrder,
        )
    }

    private fun contentUriFor(mediaType: Int, id: Long): Uri = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    } else {
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
    }

    private fun Cursor.toMediaItem(): MediaItem {
        val idCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val typeCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val nameCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val mimeCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val widthCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
        val heightCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
        val durationCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
        val takenCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
        val addedCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val sizeCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val dataCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

        val id = getLong(idCol)
        val mediaType = getInt(typeCol)
        val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val addedAtSec = getLongOrNull(addedCol) ?: 0L
        val takenAtMs = getLongOrNull(takenCol)?.takeIf { it > 0 } ?: (addedAtSec * 1000L)
        val uri = contentUriFor(mediaType, id)
        return MediaItem(
            id = "local:$id",
            kind = if (isVideo) MediaKind.VIDEO else MediaKind.PHOTO,
            title = getStringOrNull(nameCol) ?: "Untitled",
            takenAt = takenAtMs,
            addedAt = addedAtSec * 1000L,
            width = getIntOrNull(widthCol) ?: 0,
            height = getIntOrNull(heightCol) ?: 0,
            durationMs = getLongOrNull(durationCol) ?: 0L,
            fileSize = getLongOrNull(sizeCol) ?: 0L,
            filePath = getStringOrNull(dataCol),
            localUri = uri.toString(),
            mimeType = getStringOrNull(mimeCol),
            exif = ExifInfo(),
        )
    }

    private fun Cursor.getStringOrNull(col: Int): String? = if (isNull(col)) null else getString(col)
    private fun Cursor.getLongOrNull(col: Int): Long? = if (isNull(col)) null else getLong(col)
    private fun Cursor.getIntOrNull(col: Int): Int? = if (isNull(col)) null else getInt(col)
}
