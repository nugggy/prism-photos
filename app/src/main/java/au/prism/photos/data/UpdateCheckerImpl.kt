package au.prism.photos.data

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import au.prism.photos.BuildConfig
import au.prism.photos.data.plex.GithubReleaseDto
import au.prism.photos.domain.DownloadProgress
import au.prism.photos.domain.UpdateChecker
import au.prism.photos.domain.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class UpdateCheckerImpl(
    private val app: Application,
    private val httpClient: OkHttpClient,
    private val json: Json,
) : UpdateChecker {

    override val installedVersion: String = BuildConfig.VERSION_NAME.substringBefore("-")

    override suspend fun check(repo: String): Result<UpdateInfo?> = runCatching {
        val slug = repo.ifBlank { BuildConfig.UPDATE_REPO }
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$slug/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("GitHub responded with HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val release = json.decodeFromString<GithubReleaseDto>(body)
                val remoteVersion = release.tagName.removePrefix("v").removePrefix("V")
                if (remoteVersion.isBlank() || !isNewer(remoteVersion, installedVersion)) return@use null
                val asset = release.assets
                    .filter { it.name.endsWith(".apk", ignoreCase = true) }
                    .sortedByDescending { it.name.contains("release", ignoreCase = true) }
                    .firstOrNull() ?: return@use null
                UpdateInfo(
                    versionName = remoteVersion,
                    tagName = release.tagName,
                    notes = release.body,
                    apkUrl = asset.browserDownloadUrl,
                    apkSize = asset.size,
                    publishedAt = release.publishedAt,
                    htmlUrl = release.htmlUrl,
                )
            }
        }
    }

    override fun download(info: UpdateInfo): Flow<DownloadProgress> = flow {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "prism-${info.tagName}.apk")
        try {
            val request = Request.Builder().url(info.apkUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadProgress.Failed("Download failed with HTTP ${response.code}"))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(DownloadProgress.Failed("Empty response body"))
                    return@flow
                }
                val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
                var read = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            emit(DownloadProgress.InProgress(read, total))
                        }
                    }
                }
                emit(DownloadProgress.Done(target.absolutePath))
            }
        } catch (e: Exception) {
            emit(DownloadProgress.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    override fun install(filePath: String) {
        val file = File(filePath)
        val uri: Uri = FileProvider.getUriForFile(app, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(intent)
    }

    private fun isNewer(remote: String, installed: String): Boolean = isNewerVersion(remote, installed)
}

/** Semantic-ish version compare: "1.2.3" > "1.2.0". Non numeric segments compare as 0. Pure, unit tested. */
fun isNewerVersion(remote: String, installed: String): Boolean {
    val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val i = installed.split(".").map { it.toIntOrNull() ?: 0 }
    for (idx in 0 until maxOf(r.size, i.size)) {
        val rv = r.getOrElse(idx) { 0 }
        val iv = i.getOrElse(idx) { 0 }
        if (rv != iv) return rv > iv
    }
    return false
}
