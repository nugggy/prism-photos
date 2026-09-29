package au.prism.photos.ui.viewer

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
import au.prism.photos.util.Downloader
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/**
 * Shared item actions used by the viewer and by the multi-select bar in the grids. Each is a
 * suspend function that reports success or failure.
 */
object MediaActions {

    /** Saves the original file to Downloads/Prism (Plex items) or copies device items there. */
    suspend fun download(context: Context, items: List<MediaItem>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var saved = 0
            for (item in items) {
                val ok = if (item.isLocal) downloadDeviceItem(context, item) else downloadPlexItem(context, item)
                if (ok) saved++
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadPlexItem(context: Context, item: MediaItem): Boolean {
        val resolver = context.contentResolver
        val values = downloadValues(item)
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val request = Request.Builder().url(PrismApp.graph.media.downloadUrl(item)).build()
            Downloader.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response body")
                resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) }
                    ?: throw IOException("Couldn't open destination")
            }
            markDone(resolver, uri)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun downloadDeviceItem(context: Context, item: MediaItem): Boolean {
        val sourceUri = item.localUri?.toUri() ?: return false
        val resolver = context.contentResolver
        val values = downloadValues(item)
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                    ?: throw IOException("Couldn't open destination")
            } ?: throw IOException("Couldn't open source")
            markDone(resolver, uri)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun downloadValues(item: MediaItem) = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileNameFor(item))
        put(MediaStore.Downloads.MIME_TYPE, mimeFor(item))
        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Plex Gallery")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }

    private fun markDone(resolver: android.content.ContentResolver, uri: android.net.Uri) {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        resolver.update(uri, values, null, null)
    }

    /** Sets the item as the home or lock screen wallpaper (or both when [lockScreen] is true). */
    suspend fun setWallpaper(context: Context, item: MediaItem, lockScreen: Boolean): Result<Unit> =
        setWallpaperFlags(context, item, home = true, lock = lockScreen)

    /** Finer grained variant used by the viewer's wallpaper picker (home / lock / both). */
    suspend fun setWallpaperFlags(context: Context, item: MediaItem, home: Boolean, lock: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = loadBitmap(context, item) ?: return@withContext Result.failure(Exception("Couldn't load the image"))
                val manager = WallpaperManager.getInstance(context)
                var flags = 0
                if (home) flags = flags or WallpaperManager.FLAG_SYSTEM
                if (lock) flags = flags or WallpaperManager.FLAG_LOCK
                if (flags == 0) flags = WallpaperManager.FLAG_SYSTEM
                manager.setBitmap(bitmap, null, true, flags)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun loadBitmap(context: Context, item: MediaItem): Bitmap? = if (item.isLocal) {
        val uri = item.localUri?.toUri()
        if (uri == null) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = false }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } else {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(PrismApp.graph.media.originalUrl(item))
            .allowHardware(false)
            .build()
        val result = loader.execute(request)
        if (result is SuccessResult) {
            val image = result.image
            (image as? BitmapImage)?.bitmap ?: image.toBitmap(image.width, image.height)
        } else {
            null
        }
    }

    private fun fileNameFor(item: MediaItem): String {
        val existing = item.filePath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val ext = if (item.isVideo) "mp4" else "jpg"
        val base = item.title.ifBlank { item.id }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (base.contains('.')) base else "$base.$ext"
    }

    private fun mimeFor(item: MediaItem): String = item.mimeType ?: if (item.isVideo) "video/mp4" else "image/jpeg"
}
