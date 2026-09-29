package au.prism.photos.ui.favourites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.albums.AddToAlbumSheet
import au.prism.photos.ui.components.ConfirmDeleteDialog
import au.prism.photos.ui.components.EmptyState
import au.prism.photos.ui.components.ErrorState
import au.prism.photos.ui.components.LoadingState
import au.prism.photos.ui.components.MediaSelectionOps
import au.prism.photos.ui.components.SelectableMediaGrid
import au.prism.photos.ui.components.SelectionState
import au.prism.photos.ui.components.SelectionTopBar
import au.prism.photos.ui.share.ShareSheet
import au.prism.photos.ui.viewer.MediaActions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(
    gridState: LazyGridState = rememberLazyGridState(),
    onOpenViewer: (index: Int) -> Unit,
) {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selection = remember { SelectionState() }
    val snackbarHostState = remember { SnackbarHostState() }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddToAlbum by remember { mutableStateOf(false) }
    var shareItems by remember { mutableStateOf<List<MediaItem>?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            graph.media.favourites()
                .onSuccess { items = it; loading = false }
                .onFailure { error = it.message ?: "Could not load favourites"; loading = false }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            if (selection.active) {
                val selectedItems = items.filter { it.id in selection.selected }
                SelectionTopBar(
                    count = selection.selected.size,
                    allFavourite = true,
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(items.map { it.id }) },
                    onShare = { shareItems = selectedItems },
                    onDownload = {
                        scope.launch {
                            val result = MediaActions.download(context, selectedItems)
                            snackbarHostState.showSnackbar(if (result.isSuccess) "Downloaded ${result.getOrNull()} item(s)" else "Download failed")
                        }
                    },
                    onToggleFavourite = {
                        scope.launch { MediaSelectionOps.toggleFavourite(graph, selectedItems); selection.clear(); load() }
                    },
                    onLock = { scope.launch { MediaSelectionOps.lock(graph, selection.selected); selection.clear() } },
                    onAddToAlbum = { showAddToAlbum = true },
                    onDelete = { showDeleteConfirm = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && items.isEmpty() -> LoadingState()
                error != null && items.isEmpty() -> ErrorState(error!!, onRetry = { load() })
                items.isEmpty() -> EmptyState("No favourites yet", "Items you favourite show up here.")
                else -> SelectableMediaGrid(
                    items = items,
                    columns = 3,
                    thumbUrl = { graph.media.thumbUrl(it, 400) },
                    selection = selection,
                    onOpenViewer = onOpenViewer,
                    gridState = gridState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        val selectedItems = items.filter { it.id in selection.selected }
        ConfirmDeleteDialog(
            count = selectedItems.size,
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    val errors = MediaSelectionOps.delete(graph, selectedItems)
                    selection.clear()
                    load()
                    if (errors.isNotEmpty()) snackbarHostState.showSnackbar(errors.first())
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    shareItems?.let { toShare -> ShareSheet(items = toShare, onDismiss = { shareItems = null; selection.clear() }) }

    if (showAddToAlbum) {
        AddToAlbumSheet(
            itemIds = selection.selected.toList(),
            onDismiss = { showAddToAlbum = false },
            onAdded = { title ->
                showAddToAlbum = false
                selection.clear()
                scope.launch { snackbarHostState.showSnackbar("Added to $title") }
            },
        )
    }
}
