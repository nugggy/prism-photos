package au.prism.photos.ui.albums

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.prism.photos.data.AppGraph
import au.prism.photos.domain.Album
import kotlinx.coroutines.launch

class AlbumsViewModel(private val graph: AppGraph) : ViewModel() {
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var myAlbums by mutableStateOf<List<Album>>(emptyList())
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
            graph.media.rootAlbums()
                .onSuccess { albums = it.albums }
                .onFailure { error = it.message ?: "Could not load albums" }
            graph.media.myAlbums()
                .onSuccess { myAlbums = it }
                .onFailure { if (error == null) error = it.message ?: "Could not load albums" }
            loading = false
        }
    }

    fun createMyAlbum(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            graph.media.createMyAlbum(title, emptyList())
                .onSuccess { load() }
                .onFailure { error = it.message ?: "Couldn't create album" }
        }
    }
}
