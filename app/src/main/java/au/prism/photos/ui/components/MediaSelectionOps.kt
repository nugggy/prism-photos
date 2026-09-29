package au.prism.photos.ui.components

import au.prism.photos.data.AppGraph
import au.prism.photos.domain.MediaItem

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
}
