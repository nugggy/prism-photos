package au.prism.photos.ui.albums

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.prism.photos.data.AppGraph
import au.prism.photos.domain.Album
import au.prism.photos.domain.MediaItem
import kotlinx.coroutines.launch

/**
 * [showLocked] is true when the album itself is behind the lock (a locked album or one opened from
 * inside it). Otherwise items and sub-albums locked while this screen is open drop out straight away.
 */
class AlbumDetailViewModel(
    private val graph: AppGraph,
    private val albumId: String,
    private val showLocked: Boolean = false,
) : ViewModel() {
    var album by mutableStateOf<Album?>(null)
        private set
    private var allSubAlbums by mutableStateOf<List<Album>>(emptyList())
    private var allItems by mutableStateOf<List<MediaItem>>(emptyList())
    private var lockedItemIds by mutableStateOf(graph.local.lockedItemIds.value)
    private var lockedAlbumIds by mutableStateOf(graph.local.lockedAlbumIds.value)

    val subAlbums: List<Album> by derivedStateOf {
        if (showLocked) allSubAlbums else allSubAlbums.filter { it.id !in lockedAlbumIds }
    }
    val items: List<MediaItem> by derivedStateOf {
        if (showLocked) allItems else allItems.filter { it.id !in lockedItemIds }
    }
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        if (!showLocked) {
            viewModelScope.launch { graph.local.lockedItemIds.collect { lockedItemIds = it } }
            viewModelScope.launch { graph.local.lockedAlbumIds.collect { lockedAlbumIds = it } }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            album = graph.media.album(albumId)
            graph.media.albumContents(albumId)
                .onSuccess { allSubAlbums = it.albums; allItems = it.items; loading = false }
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
