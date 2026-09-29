package au.prism.photos.util

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
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
     */
    @Throws(IOException::class)
    fun download(url: String, destination: File, onProgress: ((Long, Long) -> Unit)? = null) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response body")
            val total = body.contentLength()
            destination.parentFile?.mkdirs()
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readSoFar = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readSoFar += read
                        onProgress?.invoke(readSoFar, total)
                    }
                }
            }
        }
    }

    private const val DEFAULT_BUFFER_SIZE = 8 * 1024
}
