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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class UpdateCheckerImpl(
    private val app: Application,
    private val httpClient: OkHttpClient,
    private val json: Json,
) : UpdateChecker {

    override val installedVersion: String = BuildConfig.VERSION_NAME.substringBefore("-")

    override suspend fun check(repo: String): Result<UpdateInfo?> = try {
        val slug = repo.ifBlank { BuildConfig.UPDATE_REPO }
        val info = withContext(Dispatchers.IO) {
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
                    apkSha256 = parseSha256Digest(asset.digest),
                )
            }
        }
        Result.success(info)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Downloads to a .part file, checks the byte count against the advertised size and the SHA-256
     * against GitHub's digest (when there is one), and only then renames it to the final name, so
     * a cut off or tampered download is never offered to the installer. Failures are emitted as
     * [DownloadProgress.Failed] by the catch operator, which leaves downstream exceptions and
     * cancellation alone (Flow exception transparency).
     */
    override fun download(info: UpdateInfo): Flow<DownloadProgress> = flow {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "prism-${info.tagName}.apk")
        val part = File(dir, "${target.name}.part")
        var completed = false
        try {
            val request = Request.Builder().url(info.apkUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed with HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response body")
                val contentLength = body.contentLength()
                val total = contentLength.takeIf { it > 0 } ?: info.apkSize
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            emit(DownloadProgress.InProgress(read, total))
                        }
                    }
                }
                if (contentLength > 0 && read != contentLength) throw IOException("Download incomplete ($read of $contentLength bytes)")
                if (info.apkSize > 0 && read != info.apkSize) throw IOException("Download size mismatch ($read bytes, expected ${info.apkSize})")
                info.apkSha256?.let { expected ->
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actual.equals(expected, ignoreCase = true)) throw IOException("Download failed its integrity check, please try again")
                }
            }
            target.delete()
            if (!part.renameTo(target)) throw IOException("Couldn't save the downloaded update")
            completed = true
        } finally {
            if (!completed) part.delete()
        }
        emit(DownloadProgress.Done(target.absolutePath))
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(DownloadProgress.Failed(e.message ?: "Download failed"))
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

/** The hex part of a GitHub asset digest ("sha256:<64 hex chars>"), or null if absent or another algorithm. */
fun parseSha256Digest(digest: String?): String? {
    val value = digest?.trim() ?: return null
    if (!value.startsWith("sha256:", ignoreCase = true)) return null
    val hex = value.substringAfter(':').lowercase()
    return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
}

/**
 * Semantic-ish version compare: "1.2.3" > "1.2.0". Each component's leading digits are its
 * value ("10-rc" is 10), so "1.2.10-rc" is not mistaken for 1.2.0. A pre-release ("-rc", "-beta")
 * is older than the same version without one. Pure, unit tested.
 */
fun isNewerVersion(remote: String, installed: String): Boolean {
    val (r, rPre) = parseVersion(remote)
    val (i, iPre) = parseVersion(installed)
    for (idx in 0 until maxOf(r.size, i.size)) {
        val rv = r.getOrElse(idx) { 0 }
        val iv = i.getOrElse(idx) { 0 }
        if (rv != iv) return rv > iv
    }
    return when {
        rPre == null -> iPre != null
        iPre == null -> false
        else -> rPre > iPre
    }
}

/** Numeric components plus the pre-release label (text after the first '-'), if any. */
private fun parseVersion(version: String): Pair<List<Int>, String?> {
    val clean = version.trim().removePrefix("v").removePrefix("V").substringBefore('+')
    val core = clean.substringBefore('-')
    val pre = clean.substringAfter('-', "").takeIf { it.isNotEmpty() }
    val parts = core.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    // "1.2.3rc1" style: letters glued to a number also mark a pre-release.
    val glued = core.split('.').firstOrNull { p -> p.isNotEmpty() && p.first().isDigit() && p.any { !it.isDigit() } }
    return parts to (pre ?: glued?.dropWhile { it.isDigit() })
}
