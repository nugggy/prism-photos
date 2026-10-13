package au.prism.photos.ui.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import au.prism.photos.PrismApp
import au.prism.photos.R
import au.prism.photos.domain.LoadState
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.SortOrder
import au.prism.photos.domain.ViewerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    /** A failed action (favourite, rename, tags). Shown once by the screen, then cleared with [clearError]. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** The initial load failed and there is nothing to show. The screen offers [retry]. */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /** Remembered playback position per item id, so swiping away and back resumes. */
    private val positions = mutableMapOf<String, Long>()

    private var settleJob: Job? = null

    init {
        when (source) {
            ViewerSource.Timeline -> follow(graph.media.timeline)
            ViewerSource.Locked -> follow(graph.media.lockedItems)
            else -> viewModelScope.launch { load() }
        }
    }

    /**
     * Timeline and Locked follow the repository. Those flows start empty (for example straight after
     * process death restores the viewer), so an empty list only counts as final once the repository
     * reports the timeline finished loading, plus a short grace period for the derived lists to catch
     * up, or after [LOAD_TIMEOUT_MS]. Until then the screen keeps showing its spinner.
     */
    private fun follow(flow: StateFlow<List<MediaItem>>) {
        viewModelScope.launch {
            flow.collect { list ->
                _items.value = list
                if (list.isNotEmpty()) {
                    _loading.value = false
                    _loadError.value = null
                }
            }
        }
        settleEmpty()
    }

    private fun settleEmpty() {
        settleJob?.cancel()
        settleJob = viewModelScope.launch {
            withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                graph.media.timelineState.first { it is LoadState.Loaded || it is LoadState.Error }
                if (source == ViewerSource.Locked) {
                    // Locked items missing from the timeline are fetched one by one after it loads.
                    combine(graph.media.lockedItems, graph.local.lockedItemIds) { list, ids -> list.isNotEmpty() || ids.isEmpty() }
                        .first { it }
                }
                delay(EMPTY_GRACE_MS)
            }
            if (_items.value.isEmpty()) {
                val state = graph.media.timelineState.value
                if (state is LoadState.Error) _loadError.value = state.message
                _loading.value = false
            }
        }
    }

    /** Tries the initial load again after [loadError]. */
    fun retry() {
        _loadError.value = null
        _loading.value = true
        when (source) {
            ViewerSource.Timeline, ViewerSource.Locked -> {
                viewModelScope.launch { graph.media.refreshTimeline(force = true) }
                settleEmpty()
            }
            else -> viewModelScope.launch { load() }
        }
    }

    fun clearError() { _error.value = null }

    private suspend fun load() {
        _loading.value = true
        var failure: Throwable? = null
        val result: List<MediaItem> = when (val s = source) {
            ViewerSource.Timeline, ViewerSource.Locked -> emptyList()
            ViewerSource.Favourites -> graph.media.favourites().getOrElse { failure = it; emptyList() }
            is ViewerSource.Album -> {
                val contents = graph.media.albumContents(s.albumId).getOrElse { failure = it; null }
                val sort = graph.local.albumSort.value[s.albumId]
                applySort(contents?.items ?: emptyList(), sort)
            }
            is ViewerSource.MyAlbum -> graph.media.myAlbumItems(s.albumId).getOrElse { failure = it; emptyList() }
            is ViewerSource.Search -> graph.media.search(s.query).getOrElse { failure = it; emptyList() }
            is ViewerSource.Device -> graph.device.items(s.bucketId)
            is ViewerSource.Items -> graph.media.items(s.ids)
            is ViewerSource.ExternalUris -> graph.device.itemsForUris(s.uris)
        }
        failure?.let { _loadError.value = it.message ?: PrismApp.instance.getString(R.string.viewer_load_failed) }
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

    /**
     * Non-null when [item] is a device item that has already been synced to the Plex library
     * (see data/upload/SyncLedger.kt), giving its Plex rating key. Resolves the rating key first
     * if the ledger recorded the upload but has not matched it to a Plex item yet. Used to decide
     * whether to show the "Delete from Plex as well?" prompt before a device delete.
     */
    suspend fun syncedPlexRatingKey(item: MediaItem): String? {
        if (!item.isLocal) return null
        val ledger = graph.syncLedger
        if (!ledger.isUploaded(item)) return null
        var entry = ledger.entryFor(item)
        if (entry?.plexRatingKey == null) {
            runCatching { ledger.resolvePlexIds(graph.media) }
            entry = ledger.entryFor(item)
        }
        return entry?.plexRatingKey
    }

    /** Deletes the Plex copy first; only deletes the device copy if that succeeds. */
    suspend fun deleteFromBoth(item: MediaItem, plexRatingKey: String): Result<Unit> {
        val plexResult = graph.media.delete(plexRatingKey)
        if (plexResult.isFailure) return plexResult
        return delete(item)
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

    companion object {
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val EMPTY_GRACE_MS = 1_500L
    }

    class Factory(private val source: ViewerSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ViewerViewModel::class.java))
            return ViewerViewModel(source) as T
        }
    }
}
