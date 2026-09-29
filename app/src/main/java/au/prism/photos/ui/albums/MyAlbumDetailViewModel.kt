package au.prism.photos.ui.albums

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.prism.photos.data.AppGraph
import au.prism.photos.domain.Album
import au.prism.photos.domain.MediaItem
import kotlinx.coroutines.launch

/** Backs [MyAlbumDetailScreen]. My albums are Plex photo playlists: see docs/plex-api.md. */
class MyAlbumDetailViewModel(private val graph: AppGraph, private val albumId: String) : ViewModel() {
    var album by mutableStateOf<Album?>(null)
        private set
    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            graph.media.myAlbums().onSuccess { list -> album = list.find { it.id == albumId } }
            graph.media.myAlbumItems(albumId)
                .onSuccess { items = it; loading = false }
                .onFailure { error = it.message ?: "Could not load this album"; loading = false }
        }
    }

    fun rename(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            graph.media.renameMyAlbum(albumId, title)
                .onSuccess { album = album?.copy(title = title) }
                .onFailure { error = it.message ?: "Couldn't rename album" }
        }
    }

    suspend fun removeFromAlbum(ids: Collection<String>): Result<Unit> {
        val result = graph.media.removeFromMyAlbum(albumId, ids.toList())
        if (result.isSuccess) load()
        return result
    }

    suspend fun deleteAlbum(): Result<Unit> = graph.media.deleteMyAlbum(albumId)
}
