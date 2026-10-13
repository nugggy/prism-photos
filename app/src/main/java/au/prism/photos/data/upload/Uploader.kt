package au.prism.photos.data.upload

import android.content.ContentResolver
import android.net.Uri
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.Properties
import kotlin.coroutines.resumeWithException

/** Where the app writes uploaded files, and how it checks what is already there. */
interface RemoteStore {
    /** Creates every missing segment of [path] (relative to the destination root). */
    suspend fun ensureFolder(path: String)

    /**
     * What exists at [path]. Never throws for an ordinary failure: a timeout, auth error or
     * unexpected reply is [RemoteState.Unknown], so callers can refuse to write rather than guess.
     */
    suspend fun remoteState(path: String): RemoteState

    /** Sub folder names directly under [path] (used for the Settings "Test connection" check). */
    suspend fun list(path: String): List<String>

    /**
     * Streams [contentUri] to a hidden temporary file next to [path], then renames it into place,
     * so an interrupted upload never leaves a truncated file under the real name. When [replace]
     * is false the rename refuses to overwrite a file that appeared meanwhile. The temporary file
     * is removed (best effort) on failure or cancellation.
     */
    suspend fun upload(path: String, contentUri: String, size: Long, replace: Boolean, onProgress: (Long) -> Unit)
}

/** 256 KiB chunks; big enough to be efficient over Wi-Fi, small enough for smooth progress. */
private const val COPY_BUFFER_SIZE = 256 * 1024

private fun copyWithProgress(input: InputStream, output: OutputStream, checkCancelled: () -> Unit, onProgress: (Long) -> Unit) {
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var total = 0L
    while (true) {
        checkCancelled()
        val read = input.read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
        total += read
        onProgress(total)
    }
    output.flush()
}

private fun joinPath(vararg segments: String): String =
    segments.map { it.trim('/') }.filter { it.isNotBlank() }.joinToString("/")

private fun describe(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName

/** Runs an OkHttp call so that cancelling the coroutine also cancels the request in flight. */
private suspend fun Call.executeCancellable(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
    })
}

/** Uploads over SMB/CIFS using jcifs-ng. */
class SmbStore(
    private val host: String,
    private val share: String,
    private val basePath: String,
    private val username: String,
    private val password: String,
    private val domain: String,
    private val resolver: ContentResolver,
) : RemoteStore {

    private val ctx: CIFSContext by lazy {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
        }
        val base: CIFSContext = BaseContext(PropertyConfiguration(props))
        val auth = NtlmPasswordAuthenticator(domain.ifBlank { null }, username, password)
        base.withCredentials(auth)
    }

    private fun urlFor(relativePath: String): String {
        val full = joinPath(basePath, relativePath)
        val cleanShare = share.trim('/')
        return if (full.isBlank()) "smb://$host/$cleanShare/" else "smb://$host/$cleanShare/$full"
    }

    private fun <T> smb(block: () -> T): T = try {
        block()
    } catch (e: SmbException) {
        // jcifs-ng's SmbException messages ("Access is denied.", "The network name cannot be
        // found.", ...) are already written for people, not developers - surface them as-is.
        throw IOException(e.message ?: "SMB error", e)
    }

    override suspend fun ensureFolder(path: String): Unit = withContext(Dispatchers.IO) {
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        var acc = ""
        for (seg in segments) {
            acc = if (acc.isEmpty()) seg else "$acc/$seg"
            smb {
                val f = SmbFile(urlFor(acc) + "/", ctx)
                if (!f.exists()) f.mkdir()
            }
        }
    }

    override suspend fun remoteState(path: String): RemoteState = withContext(Dispatchers.IO) {
        try {
            val f = SmbFile(urlFor(path), ctx)
            when {
                // jcifs-ng returns false only for "not found" statuses; auth and network
                // problems throw and land in the catch below.
                !f.exists() -> RemoteState.Absent
                f.isDirectory -> RemoteState.Present(-1)
                else -> RemoteState.Present(f.length())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RemoteState.Unknown(describe(e))
        }
    }

    override suspend fun list(path: String): List<String> = withContext(Dispatchers.IO) {
        smb {
            val f = SmbFile(urlFor(path) + "/", ctx)
            if (!f.exists()) emptyList() else f.listFiles()?.filter { it.isDirectory }?.map { it.name.trimEnd('/') } ?: emptyList()
        }
    }

    override suspend fun upload(path: String, contentUri: String, size: Long, replace: Boolean, onProgress: (Long) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val target = SmbFile(urlFor(path), ctx)
            val temp = SmbFile(urlFor(ClashNames.tempPathFor(path)), ctx)
            var finished = false
            try {
                smb {
                    // Re-check right before writing: another device may have taken the name since
                    // the clash check, and a new name must never overwrite someone else's file.
                    if (!replace && target.exists()) throw IOException("A different file named ${path.substringAfterLast('/')} appeared at the destination")
                    val input = resolver.openInputStream(Uri.parse(contentUri)) ?: throw IOException("Couldn't open $contentUri")
                    input.use { source ->
                        SmbFileOutputStream(temp).use { output -> copyWithProgress(source, output, { ensureActive() }, onProgress) }
                    }
                    ensureActive()
                    if (replace) {
                        if (target.exists()) target.delete()
                        temp.renameTo(target)
                    } else {
                        // replace = false: the server refuses if the name was taken meanwhile.
                        temp.renameTo(target, false)
                    }
                }
                finished = true
            } finally {
                if (!finished) {
                    withContext(NonCancellable) { runCatching { if (temp.exists()) temp.delete() } }
                }
            }
        }
}

/** Uploads over WebDAV (MKCOL for folders, PUT then MOVE for files, basic auth) using OkHttp. */
class WebDavStore(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val client: OkHttpClient,
    private val resolver: ContentResolver,
) : RemoteStore {

    private fun urlFor(relativePath: String): String {
        val base = baseUrl.trimEnd('/')
        val rel = relativePath.trim('/')
        if (rel.isBlank()) return "$base/"
        val encoded = rel.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "$base/$encoded"
    }

    private fun authHeader(): String = Credentials.basic(username, password)

    /** Basic auth over http:// to a public host would expose the password, so refuse outright. */
    private fun requireSecure() {
        if (InsecureUrl.isInsecureRemoteUrl(baseUrl)) throw IOException(InsecureUrl.MESSAGE)
    }

    override suspend fun ensureFolder(path: String): Unit = withContext(Dispatchers.IO) {
        requireSecure()
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        var acc = ""
        for (seg in segments) {
            acc = if (acc.isEmpty()) seg else "$acc/$seg"
            val request = Request.Builder()
                .url(urlFor(acc) + "/")
                .method("MKCOL", null)
                .header("Authorization", authHeader())
                .build()
            client.newCall(request).execute().use { response ->
                // 201 created, 405/409 already exists - anything else is a real problem.
                if (!response.isSuccessful && response.code != 405 && response.code != 409) {
                    throw IOException("Couldn't create folder \"$seg\" (HTTP ${response.code})")
                }
            }
        }
    }

    override suspend fun remoteState(path: String): RemoteState = withContext(Dispatchers.IO) {
        try {
            requireSecure()
            val request = Request.Builder().url(urlFor(path)).method("HEAD", null).header("Authorization", authHeader()).build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 || response.code == 410 -> RemoteState.Absent
                    !response.isSuccessful -> RemoteState.Unknown("HTTP ${response.code}")
                    else -> response.header("Content-Length")?.toLongOrNull()?.let { RemoteState.Present(it) }
                        // Some servers omit Content-Length on HEAD; ask for the property instead.
                        // Either way something is there, so it is never treated as free.
                        ?: RemoteState.Present(propfindSize(path) ?: -1)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RemoteState.Unknown(describe(e))
        }
    }

    private fun propfindSize(path: String): Long? = runCatching {
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/></d:prop></d:propfind>"""
        val request = Request.Builder()
            .url(urlFor(path))
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaTypeOrNull()))
            .header("Authorization", authHeader())
            .header("Depth", "0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val xml = response.body?.string().orEmpty()
            Regex("<[^:>]*:?getcontentlength[^>]*>\\s*(\\d+)\\s*<", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)?.toLongOrNull()
        }
    }.getOrNull()

    override suspend fun list(path: String): List<String> = withContext(Dispatchers.IO) {
        requireSecure()
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        val request = Request.Builder()
            .url(urlFor(path) + "/")
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaTypeOrNull()))
            .header("Authorization", authHeader())
            .header("Depth", "1")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val xml = response.body?.string().orEmpty()
                Regex("<[^:>]*:?href>([^<]+)</[^:>]*:?href>", RegexOption.IGNORE_CASE)
                    .findAll(xml)
                    .map { it.groupValues[1].trim('/').substringAfterLast('/') }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun upload(path: String, contentUri: String, size: Long, replace: Boolean, onProgress: (Long) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            requireSecure()
            val uri = Uri.parse(contentUri)
            val mediaType = resolver.getType(uri)?.toMediaTypeOrNull()
            val body = object : RequestBody() {
                override fun contentType() = mediaType
                override fun contentLength() = size
                override fun writeTo(sink: BufferedSink) {
                    val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open $contentUri")
                    input.use { source ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            total += read
                            onProgress(total)
                        }
                    }
                }
            }
            val tempUrl = urlFor(ClashNames.tempPathFor(path))
            var finished = false
            try {
                // The temporary name is ours alone, so a stale one from an earlier interrupted
                // attempt is simply overwritten. The clash guard lives on the MOVE below.
                val put = Request.Builder().url(tempUrl).put(body).header("Authorization", authHeader()).build()
                client.newCall(put).executeCancellable().use { response ->
                    if (!response.isSuccessful) throw IOException("Upload failed (HTTP ${response.code})")
                }
                ensureActive()
                // Overwrite: F makes the server answer 412 instead of replacing a file that took
                // the name after the clash check (RFC 4918 section 10.6), the MOVE equivalent of
                // If-None-Match: * on a direct PUT.
                val move = Request.Builder()
                    .url(tempUrl)
                    .method("MOVE", null)
                    .header("Authorization", authHeader())
                    .header("Destination", urlFor(path))
                    .header("Overwrite", if (replace) "T" else "F")
                    .build()
                client.newCall(move).executeCancellable().use { response ->
                    when {
                        response.isSuccessful -> Unit
                        response.code == 412 -> throw IOException("A different file named ${path.substringAfterLast('/')} appeared at the destination")
                        else -> throw IOException("Couldn't move the upload into place (HTTP ${response.code})")
                    }
                }
                finished = true
            } finally {
                if (!finished) {
                    withContext(NonCancellable) {
                        runCatching {
                            val delete = Request.Builder().url(tempUrl).delete().header("Authorization", authHeader()).build()
                            client.newCall(delete).execute().close()
                        }
                    }
                }
            }
        }
}

object RemoteStoreFactory {
    fun create(destination: UploadDestination, resolver: ContentResolver, webDavClient: OkHttpClient): RemoteStore =
        when (destination.type) {
            DestinationType.SMB -> SmbStore(
                host = destination.smbHost,
                share = destination.smbShare,
                basePath = destination.smbPath,
                username = destination.smbUsername,
                password = destination.smbPassword,
                domain = destination.smbDomain,
                resolver = resolver,
            )
            DestinationType.WEBDAV -> WebDavStore(
                baseUrl = destination.webDavBaseUrl,
                username = destination.webDavUsername,
                password = destination.webDavPassword,
                client = webDavClient,
                resolver = resolver,
            )
        }

    /** A reason the destination must not be used at all, or null when it is fine to try. */
    fun refusalReason(destination: UploadDestination): String? = when {
        destination.type == DestinationType.WEBDAV && InsecureUrl.isInsecureRemoteUrl(destination.webDavBaseUrl) -> InsecureUrl.MESSAGE
        else -> null
    }
}

/** Settings screen "Test connection": makes sure the destination folder can be created and read. */
object ConnectionTester {
    suspend fun test(destination: UploadDestination, resolver: ContentResolver, webDavClient: OkHttpClient): Result<String> {
        val misconfigured = when (destination.type) {
            DestinationType.SMB -> destination.smbHost.isBlank() || destination.smbShare.isBlank()
            DestinationType.WEBDAV -> destination.webDavBaseUrl.isBlank()
        }
        if (misconfigured) return Result.failure(IllegalStateException("Fill in the destination fields first"))
        RemoteStoreFactory.refusalReason(destination)?.let { return Result.failure(IllegalStateException(it)) }
        return try {
            val store = RemoteStoreFactory.create(destination, resolver, webDavClient)
            store.ensureFolder("")
            val folders = store.list("")
            Result.success(
                if (folders.isEmpty()) "Connected. The destination folder is empty or ready to use."
                else "Connected. Found ${folders.size} folder(s): ${folders.take(5).joinToString(", ")}${if (folders.size > 5) "…" else ""}",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
