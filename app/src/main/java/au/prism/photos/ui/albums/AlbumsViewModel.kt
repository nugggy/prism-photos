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
                .onSuccess { albums = it.albums; loading = false }
                .onFailure { error = it.message ?: "Could not load albums"; loading = false }
        }
    }
}
