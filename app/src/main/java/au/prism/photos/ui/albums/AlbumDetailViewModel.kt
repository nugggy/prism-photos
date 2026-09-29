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

class AlbumDetailViewModel(private val graph: AppGraph, private val albumId: String) : ViewModel() {
    var album by mutableStateOf<Album?>(null)
        private set
    var subAlbums by mutableStateOf<List<Album>>(emptyList())
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
            album = graph.media.album(albumId)
            graph.media.albumContents(albumId)
                .onSuccess { subAlbums = it.albums; items = it.items; loading = false }
                .onFailure { error = it.message ?: "Could not load this album"; loading = false }
        }
    }

    fun rename(title: String) {
        viewModelScope.launch {
            graph.media.renameAlbum(albumId, title)
            album = album?.copy(title = title)
        }
    }
}
