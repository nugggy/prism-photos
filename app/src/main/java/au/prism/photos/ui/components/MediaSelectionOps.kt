package au.prism.photos.ui.components

import android.net.Uri
import au.prism.photos.data.AppGraph
import au.prism.photos.domain.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared suspend operations used by the selection top bar across grids. */
object MediaSelectionOps {
    suspend fun toggleFavourite(graph: AppGraph, items: List<MediaItem>) {
        val makeFavourite = items.any { !it.favourite }
        items.forEach { graph.media.setFavourite(it.id, makeFavourite) }
    }

    suspend fun lock(graph: AppGraph, ids: Collection<String>) {
        graph.local.setItemsLocked(ids, true)
    }

    suspend fun unlock(graph: AppGraph, ids: Collection<String>) {
        graph.local.setItemsLocked(ids, false)
    }

    /** Deletes each item, returning a human readable error per failure (empty list = all ok). */
    suspend fun delete(graph: AppGraph, items: List<MediaItem>): List<String> {
        val errors = mutableListOf<String>()
        for (item in items) {
            val result = graph.media.delete(item.id)
            if (result.isFailure) {
                errors += "${item.title}: ${result.exceptionOrNull()?.message ?: "delete failed"}"
            }
        }
        return errors
    }

    /** Deletes one device item through MediaStore (may throw RecoverableSecurityException on Android 10+). */
    suspend fun deleteDeviceItem(graph: AppGraph, item: MediaItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(item.localUri)
            val rows = graph.app.contentResolver.delete(uri, null, null)
            if (rows > 0) Result.success(Unit) else Result.failure(Exception("Couldn't delete file"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Of [items] (device items), which ones are already synced to Plex, mapped to their Plex
     * rating key. Resolves any unknown rating keys against the ledger first. Used before showing
     * the "delete from Plex as well?" prompt for a device delete.
     */
    suspend fun syncedPlexIds(graph: AppGraph, items: List<MediaItem>): Map<String, String> {
        val ledger = graph.syncLedger
        val synced = items.filter { ledger.isUploaded(it) }
        if (synced.isEmpty()) return emptyMap()
        if (synced.any { ledger.entryFor(it)?.plexRatingKey == null }) {
            runCatching { ledger.resolvePlexIds(graph.media) }
        }
        return synced.mapNotNull { item -> ledger.entryFor(item)?.plexRatingKey?.let { item.id to it } }.toMap()
    }

    /**
     * Deletes [items] (device items) from the phone. For any item whose id is a key in
     * [plexRatingKeys], the matching Plex item is deleted first; if that Plex deletion fails the
     * item is left on the phone (its error is reported) rather than deleted locally anyway.
     */
    suspend fun deleteDeviceItems(graph: AppGraph, items: List<MediaItem>, plexRatingKeys: Map<String, String>): List<String> {
        val errors = mutableListOf<String>()
        for (item in items) {
            val ratingKey = plexRatingKeys[item.id]
            if (ratingKey != null) {
                val plexResult = graph.media.delete(ratingKey)
                if (plexResult.isFailure) {
                    errors += "${item.title}: ${plexResult.exceptionOrNull()?.message ?: "couldn't delete from Plex"}"
                    continue
                }
            }
            val result = deleteDeviceItem(graph, item)
            if (result.isFailure) errors += "${item.title}: ${result.exceptionOrNull()?.message ?: "delete failed"}"
        }
        return errors
    }
}
