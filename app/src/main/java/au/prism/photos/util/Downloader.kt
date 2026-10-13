package au.prism.photos.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** Small standalone OkHttp downloader used by the share sheet and media actions. */
object Downloader {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Downloads [url] into [destination], calling [onProgress] with (bytesSoFar, totalBytes).
     * [totalBytes] is -1 when the server did not send a content length.
     *
     * The bytes go to "<destination>.part" first and are renamed to [destination] only once the
     * whole body (and its Content-Length, when sent) has arrived, so a file at [destination] is
     * always complete. The partial file is deleted on any failure. Blocking; stops early if the
     * calling thread is interrupted. From a coroutine prefer [downloadAwait], which also stops
     * when the coroutine is cancelled.
     */
    @Throws(IOException::class)
    fun download(url: String, destination: File, onProgress: ((Long, Long) -> Unit)? = null) {
        downloadInternal(url, destination, onProgress) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Download cancelled")
        }
    }

    /** Coroutine version of [download]: runs on the IO dispatcher and stops promptly when cancelled. */
    suspend fun downloadAwait(url: String, destination: File, onProgress: ((Long, Long) -> Unit)? = null) {
        withContext(Dispatchers.IO) {
            downloadInternal(url, destination, onProgress) { ensureActive() }
        }
    }

    private fun downloadInternal(url: String, destination: File, onProgress: ((Long, Long) -> Unit)?, checkCancelled: () -> Unit) {
        val request = Request.Builder().url(url).build()
        destination.parentFile?.mkdirs()
        val part = File(destination.parentFile, destination.name + ".part")
        var completed = false
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response body")
                val total = body.contentLength()
                var readSoFar = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            checkCancelled()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            readSoFar += read
                            onProgress?.invoke(readSoFar, total)
                        }
                    }
                }
                if (total > 0 && readSoFar != total) throw IOException("Download incomplete ($readSoFar of $total bytes)")
            }
            if (destination.exists() && !destination.delete()) throw IOException("Couldn't replace ${destination.name}")
            if (!part.renameTo(destination)) throw IOException("Couldn't save ${destination.name}")
            completed = true
        } finally {
            if (!completed) part.delete()
        }
    }

    private const val DEFAULT_BUFFER_SIZE = 8 * 1024
}
