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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.Properties

/** Where the app writes uploaded files, and how it checks what is already there. */
interface RemoteStore {
    /** Creates every missing segment of [path] (relative to the destination root). */
    suspend fun ensureFolder(path: String)

    /** True if a file already exists at [path] with exactly [size] bytes. */
    suspend fun exists(path: String, size: Long): Boolean

    /** The remote file's size in bytes, or null if nothing exists at [path] yet. */
    suspend fun remoteSize(path: String): Long?

    /** Sub folder names directly under [path] (used for the Settings "Test connection" check). */
    suspend fun list(path: String): List<String>

    /** Streams [contentUri] to [path], calling [onProgress] with bytes sent so far. */
    suspend fun upload(path: String, contentUri: String, size: Long, onProgress: (Long) -> Unit)
}

/** 256 KiB chunks; big enough to be efficient over Wi-Fi, small enough for smooth progress. */
private const val COPY_BUFFER_SIZE = 256 * 1024

private fun copyWithProgress(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit) {
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var total = 0L
    while (true) {
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

    override suspend fun exists(path: String, size: Long): Boolean = remoteSize(path) == size

    override suspend fun remoteSize(path: String): Long? = withContext(Dispatchers.IO) {
        try {
            val f = SmbFile(urlFor(path), ctx)
            if (f.exists() && !f.isDirectory) f.length() else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun list(path: String): List<String> = withContext(Dispatchers.IO) {
        smb {
            val f = SmbFile(urlFor(path) + "/", ctx)
            if (!f.exists()) emptyList() else f.listFiles()?.filter { it.isDirectory }?.map { it.name.trimEnd('/') } ?: emptyList()
        }
    }

    override suspend fun upload(path: String, contentUri: String, size: Long, onProgress: (Long) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            smb {
                val f = SmbFile(urlFor(path), ctx)
                val input = resolver.openInputStream(Uri.parse(contentUri)) ?: throw IOException("Couldn't open $contentUri")
                input.use { source ->
                    SmbFileOutputStream(f).use { output -> copyWithProgress(source, output, onProgress) }
                }
            }
        }
}

/** Uploads over WebDAV (MKCOL for folders, PUT for files, basic auth) using OkHttp. */
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

    override suspend fun ensureFolder(path: String): Unit = withContext(Dispatchers.IO) {
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

    override suspend fun exists(path: String, size: Long): Boolean = remoteSize(path) == size

    override suspend fun remoteSize(path: String): Long? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(urlFor(path)).method("HEAD", null).header("Authorization", authHeader()).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.header("Content-Length")?.toLongOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun list(path: String): List<String> = withContext(Dispatchers.IO) {
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

    override suspend fun upload(path: String, contentUri: String, size: Long, onProgress: (Long) -> Unit): Unit =
        withContext(Dispatchers.IO) {
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
            val request = Request.Builder().url(urlFor(path)).put(body).header("Authorization", authHeader()).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Upload failed (HTTP ${response.code})")
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
}

/** Settings screen "Test connection": makes sure the destination folder can be created and read. */
object ConnectionTester {
    suspend fun test(destination: UploadDestination, resolver: ContentResolver, webDavClient: OkHttpClient): Result<String> {
        val misconfigured = when (destination.type) {
            DestinationType.SMB -> destination.smbHost.isBlank() || destination.smbShare.isBlank()
            DestinationType.WEBDAV -> destination.webDavBaseUrl.isBlank()
        }
        if (misconfigured) return Result.failure(IllegalStateException("Fill in the destination fields first"))
        return try {
            val store = RemoteStoreFactory.create(destination, resolver, webDavClient)
            store.ensureFolder("")
            val folders = store.list("")
            Result.success(
                if (folders.isEmpty()) "Connected. The destination folder is empty or ready to use."
                else "Connected. Found ${folders.size} folder(s): ${folders.take(5).joinToString(", ")}${if (folders.size > 5) "…" else ""}",
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
