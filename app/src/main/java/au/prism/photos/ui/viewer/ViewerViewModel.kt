package au.prism.photos.ui.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.SortOrder
import au.prism.photos.domain.ViewerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resolves a [ViewerSource] into a list of [MediaItem] for the pager and exposes the item
 * actions the viewer's top and bottom bars call. Timeline and Locked sources stay reactive by
 * following the repository's StateFlows; the other sources are loaded once and then updated
 * optimistically as actions succeed.
 */
class ViewerViewModel(private val source: ViewerSource) : ViewModel() {
    private val graph = PrismApp.graph

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Remembered playback position per item id, so swiping away and back resumes. */
    private val positions = mutableMapOf<String, Long>()

    init {
        when (source) {
            ViewerSource.Timeline -> viewModelScope.launch {
                graph.media.timeline.collectLatest {
                    _items.value = it
                    _loading.value = false
                }
            }
            ViewerSource.Locked -> viewModelScope.launch {
                graph.media.lockedItems.collectLatest {
                    _items.value = it
                    _loading.value = false
                }
            }
            else -> viewModelScope.launch { load() }
        }
    }

    private suspend fun load() {
        _loading.value = true
        val result: List<MediaItem> = when (val s = source) {
            ViewerSource.Timeline, ViewerSource.Locked -> emptyList()
            ViewerSource.Favourites -> graph.media.favourites().getOrElse { onError(it); emptyList() }
            is ViewerSource.Album -> {
                val contents = graph.media.albumContents(s.albumId).getOrElse { onError(it); null }
                val sort = graph.local.albumSort.value[s.albumId]
                applySort(contents?.items ?: emptyList(), sort)
            }
            is ViewerSource.MyAlbum -> graph.media.myAlbumItems(s.albumId).getOrElse { onError(it); emptyList() }
            is ViewerSource.Search -> graph.media.search(s.query).getOrElse { onError(it); emptyList() }
            is ViewerSource.Device -> graph.device.items(s.bucketId)
            is ViewerSource.Items -> graph.media.items(s.ids)
            is ViewerSource.ExternalUris -> graph.device.itemsForUris(s.uris)
        }
        _items.value = result
        _loading.value = false
    }

    private fun onError(t: Throwable) {
        _error.value = t.message ?: "Something went wrong"
    }

    private fun applySort(list: List<MediaItem>, sort: SortOrder?): List<MediaItem> = when (sort) {
        SortOrder.OLDEST -> list.sortedBy { it.takenAt }
        SortOrder.NAME -> list.sortedBy { it.title.lowercase() }
        SortOrder.NEWEST, null -> list.sortedByDescending { it.takenAt }
    }

    private fun updateItem(id: String, transform: (MediaItem) -> MediaItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    fun positionFor(itemId: String): Long = positions[itemId] ?: 0L
    fun setPosition(itemId: String, ms: Long) { positions[itemId] = ms }

    fun toggleFavourite(item: MediaItem) {
        val newValue = !item.favourite
        updateItem(item.id) { it.copy(favourite = newValue) }
        viewModelScope.launch {
            graph.media.setFavourite(item.id, newValue).onFailure {
                updateItem(item.id) { i -> i.copy(favourite = !newValue) }
                onError(it)
            }
        }
    }

    fun rename(item: MediaItem, title: String) {
        if (title.isBlank() || title == item.title) return
        val previous = item.title
        updateItem(item.id) { it.copy(title = title) }
        viewModelScope.launch {
            graph.media.rename(item.id, item.kind, title).onFailure {
                updateItem(item.id) { i -> i.copy(title = previous) }
                onError(it)
            }
        }
    }

    fun setSummary(item: MediaItem, summary: String) {
        val previous = item.summary
        updateItem(item.id) { it.copy(summary = summary) }
        viewModelScope.launch {
            graph.media.setSummary(item.id, item.kind, summary).onFailure {
                updateItem(item.id) { i -> i.copy(summary = previous) }
                onError(it)
            }
        }
    }

    fun addTag(item: MediaItem, tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty() || trimmed in item.tags) return
        updateItem(item.id) { it.copy(tags = (it.tags + trimmed).distinct()) }
        viewModelScope.launch {
            graph.media.addTag(item.id, item.kind, trimmed).onFailure {
                updateItem(item.id) { i -> i.copy(tags = i.tags - trimmed) }
                onError(it)
            }
        }
    }

    fun removeTag(item: MediaItem, tag: String) {
        updateItem(item.id) { it.copy(tags = it.tags - tag) }
        viewModelScope.launch {
            graph.media.removeTag(item.id, item.kind, tag).onFailure {
                updateItem(item.id) { i -> i.copy(tags = (i.tags + tag).distinct()) }
                onError(it)
            }
        }
    }

    fun lock(item: MediaItem) {
        viewModelScope.launch { graph.local.setItemLocked(item.id, true) }
        if (source != ViewerSource.Locked) {
            _items.value = _items.value.filterNot { it.id == item.id }
        }
    }

    fun unlock(item: MediaItem) {
        viewModelScope.launch { graph.local.setItemLocked(item.id, false) }
        if (source == ViewerSource.Locked) {
            _items.value = _items.value.filterNot { it.id == item.id }
        }
    }

    /** Deletes the item. Plex items go through the repository; device items through MediaStore. */
    suspend fun delete(item: MediaItem): Result<Unit> {
        val result = if (item.isLocal) deleteDeviceItem(item) else graph.media.delete(item.id)
        if (result.isSuccess) {
            _items.value = _items.value.filterNot { it.id == item.id }
        }
        return result
    }

    private suspend fun deleteDeviceItem(item: MediaItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(item.localUri)
            val rows = PrismApp.instance.contentResolver.delete(uri, null, null)
            if (rows > 0) Result.success(Unit) else Result.failure(Exception("Couldn't delete file"))
        } catch (e: Exception) {
            // On Android 10+ this may be a RecoverableSecurityException; the caller can inspect
            // it and launch the system confirmation dialog, then retry delete().
            Result.failure(e)
        }
    }

    fun setAlbumCover(item: MediaItem) {
        val albumId = item.albumId ?: return
        viewModelScope.launch { graph.local.setAlbumCover(albumId, item.id) }
    }

    class Factory(private val source: ViewerSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ViewerViewModel::class.java))
            return ViewerViewModel(source) as T
        }
    }
}
