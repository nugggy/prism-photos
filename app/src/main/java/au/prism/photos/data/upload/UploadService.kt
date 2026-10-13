package au.prism.photos.data.upload

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import au.prism.photos.data.Diagnostics
import au.prism.photos.data.plex.PlexServerApi
import au.prism.photos.domain.MediaRepository
import au.prism.photos.domain.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

data class UploadReport(
    val uploaded: Int,
    val skipped: Int,
    val failed: Int,
    val errors: List<String>,
)

/**
 * Writes device items to the configured SMB or WebDAV destination, records each one in the
 * [SyncLedger], then asks Plex to rescan the library folder. See docs/plex-api.md "Uploading to
 * the server": Plex has no upload endpoint, this is the filesystem-then-scan workaround.
 */
class UploadService(
    private val settingsStore: UploadSettingsStore,
    private val ledger: SyncLedger,
    private val media: MediaRepository,
    private val session: SessionStore,
    private val resolver: ContentResolver,
    private val webDavClient: OkHttpClient,
    private val serverApi: PlexServerApi,
) {
    /**
     * Uploads [uris] (device content:// URIs), skipping anything already in the ledger.
     * [folder] is a destination sub folder relative to the library's server folder path; when
     * null the sub folder pattern from settings is used. [onProgress] reports (items done,
     * total items, current file name, 0..1 fraction of the current file).
     */
    suspend fun uploadItems(
        uris: List<String>,
        folder: String?,
        onProgress: (done: Int, total: Int, currentName: String, fraction: Float) -> Unit,
    ): UploadReport = withContext(Dispatchers.IO) {
        val settings = settingsStore.settings.value
        val subFolder = (folder?.trim('/')?.takeIf { it.isNotBlank() })
            ?: SubFolderPattern.resolve(settings.subFolderPattern, deviceDisplayName(), System.currentTimeMillis())

        val total = uris.size
        var uploaded = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        if (uris.isEmpty()) return@withContext UploadReport(0, 0, 0, emptyList())

        RemoteStoreFactory.refusalReason(settings.destination)?.let { reason ->
            Diagnostics.log("Upload refused: $reason")
            return@withContext UploadReport(0, 0, uris.size, listOf(reason))
        }
        val store = RemoteStoreFactory.create(settings.destination, resolver, webDavClient)

        try {
            if (subFolder.isNotBlank()) store.ensureFolder(subFolder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diagnostics.log("Upload: couldn't create folder \"$subFolder\": ${e.message}")
            return@withContext UploadReport(0, 0, uris.size, listOf("Couldn't create the destination folder: ${e.message}"))
        }

        try {
            uris.forEachIndexed { index, uriStr ->
                ensureActive()
                val info = DeviceFileInspector.inspect(resolver, Uri.parse(uriStr))
                onProgress(index, total, info.displayName, 0f)

                // Same matching as the background sync: content URI first, then name + size
                // (which also covers files that were renamed on upload).
                if (ledger.isUploaded(uriStr, info.displayName, info.size)) {
                    skipped++
                    onProgress(index + 1, total, info.displayName, 1f)
                    return@forEachIndexed
                }

                try {
                    val resolution = ClashNames.resolve(subFolder, info.displayName, info.size) { path -> store.remoteState(path) }
                    when (resolution) {
                        is ClashResolution.CannotCheck -> {
                            // Never guess: writing without knowing what is there could overwrite a
                            // different file. Counted as failed so the next run retries it.
                            errors += "${info.displayName}: couldn't check the destination (${resolution.reason})"
                            Diagnostics.log("Upload skipped for ${info.displayName}: destination check failed: ${resolution.reason}")
                        }
                        is ClashResolution.Use -> {
                            val remoteName = resolution.name
                            val remotePath = if (subFolder.isBlank()) remoteName else "$subFolder/$remoteName"
                            store.upload(remotePath, uriStr, info.size, replace = resolution.replaces) { sent ->
                                val fraction = if (info.size > 0) (sent.toFloat() / info.size).coerceIn(0f, 1f) else 0f
                                onProgress(index, total, info.displayName, fraction)
                            }
                            ledger.record(
                                contentUri = uriStr,
                                displayName = remoteName,
                                size = info.size,
                                takenAt = info.takenAt,
                                remotePath = remotePath,
                                originalName = info.displayName,
                            )
                            uploaded++
                            Diagnostics.log("Uploaded ${info.displayName} -> $remotePath")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors += "${info.displayName}: ${e.message ?: "upload failed"}"
                    Diagnostics.log("Upload failed for ${info.displayName}: ${e.message}")
                }
                onProgress(index + 1, total, info.displayName, 1f)
            }
        } finally {
            withContext(NonCancellable) { ledger.flush() }
        }

        if (uploaded > 0) {
            requestScanAndResolve(settings)
        }

        Diagnostics.log("Upload batch complete: $uploaded uploaded, $skipped already synced, ${errors.size} failed")
        UploadReport(uploaded = uploaded, skipped = skipped, failed = errors.size, errors = errors)
    }

    private suspend fun requestScanAndResolve(settings: SyncSettings) {
        val sess = session.session.value
        val libKey = settings.librarySectionKey.ifBlank { sess.libraryKey }
        val token = sess.serverToken
        if (libKey != null && token != null) {
            try {
                serverApi.refreshSection(libKey, token, settings.serverFolderPath.ifBlank { null })
                Diagnostics.log("Requested Plex library refresh for section $libKey")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.log("Library refresh request failed: ${e.message}")
            }
        }
        // Give Plex a moment to walk the new files before asking the timeline to reflect them.
        delay(10_000)
        try {
            ledger.resolvePlexIds(media)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diagnostics.log("Couldn't match uploads to Plex items: ${e.message}")
        }
    }

    private fun deviceDisplayName(): String = (Build.MODEL ?: Build.DEVICE ?: "device").trim().ifBlank { "device" }
}
