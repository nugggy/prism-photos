package au.prism.photos.ui.timeline

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.PrismApp
import au.prism.photos.domain.LoadState
import au.prism.photos.ui.albums.AddToAlbumSheet
import au.prism.photos.ui.components.ConfirmDeleteDialog
import au.prism.photos.ui.components.EmptyState
import au.prism.photos.ui.components.ErrorState
import au.prism.photos.ui.components.LoadingState
import au.prism.photos.ui.components.LocalPickHandler
import au.prism.photos.ui.components.MediaCell
import au.prism.photos.ui.components.MediaSelectionOps
import au.prism.photos.ui.components.SelectionState
import au.prism.photos.ui.components.SelectionTopBar
import au.prism.photos.ui.share.ShareSheet
import au.prism.photos.ui.viewer.MediaActions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    gridState: LazyGridState = rememberLazyGridState(),
    onOpenViewer: (index: Int) -> Unit,
) {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val items by graph.media.timeline.collectAsStateWithLifecycle()
    val loadState by graph.media.timelineState.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val selection = remember { SelectionState() }
    val snackbarHostState = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddToAlbum by remember { mutableStateOf(false) }
    var shareItems by remember { mutableStateOf<List<au.prism.photos.domain.MediaItem>?>(null) }
    val pick = LocalPickHandler.current

    val columns = settings.gridColumns.coerceIn(2, 6)
    val grouped = remember(items) { groupByMonth(items) }
    val flatRows = remember(grouped) { flattenRows(grouped) }

    LaunchedEffect(Unit) {
        if (items.isEmpty()) graph.media.refreshTimeline()
    }

    Scaffold(
        topBar = {
            if (selection.active) {
                val selectedItems = items.filter { it.id in selection.selected }
                SelectionTopBar(
                    count = selection.selected.size,
                    allFavourite = selectedItems.isNotEmpty() && selectedItems.all { it.favourite },
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
                        scope.launch { MediaSelectionOps.toggleFavourite(graph, selectedItems); selection.clear() }
                    },
                    onLock = {
                        scope.launch { MediaSelectionOps.lock(graph, selection.selected); selection.clear() }
                    },
                    onAddToAlbum = { showAddToAlbum = true },
                    onDelete = { showDeleteConfirm = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loadState is LoadState.Error && items.isEmpty() -> ErrorState((loadState as LoadState.Error).message, onRetry = { scope.launch { graph.media.refreshTimeline(force = true) } })
                loadState is LoadState.Loading && items.isEmpty() -> LoadingState()
                items.isEmpty() -> EmptyState("No photos yet", "Photos and videos from this library will show up here.")
                else -> {
                    var scaleAccum by remember { mutableFloatStateOf(1f) }
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = {
                            scope.launch {
                                refreshing = true
                                graph.media.refreshTimeline(force = true)
                                refreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(columns) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        scaleAccum *= zoom
                                        if (scaleAccum > 1.15f) {
                                            val newCols = (columns - 1).coerceIn(2, 6)
                                            if (newCols != columns) scope.launch { graph.settings.update { it.copy(gridColumns = newCols) } }
                                            scaleAccum = 1f
                                        } else if (scaleAccum < 0.87f) {
                                            val newCols = (columns + 1).coerceIn(2, 6)
                                            if (newCols != columns) scope.launch { graph.settings.update { it.copy(gridColumns = newCols) } }
                                            scaleAccum = 1f
                                        }
                                    }
                                },
                            contentPadding = PaddingValues(2.dp),
                        ) {
                            grouped.forEach { (month, monthItems) ->
                                item(span = { GridItemSpan(maxLineSpan) }, key = "header-$month") {
                                    Text(
                                        month,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    )
                                }
                                val startIndexInFlat = items.indexOf(monthItems.first())
                                itemsIndexed(monthItems, key = { _, it -> it.id }) { idx, item ->
                                    val globalIndex = startIndexInFlat + idx
                                    MediaCell(
                                        item = item,
                                        thumbUrl = graph.media.thumbUrl(item, 400 + (6 - columns) * 100),
                                        selected = item.id in selection.selected,
                                        selectionMode = selection.active,
                                        onClick = {
                                            when {
                                                pick != null -> pick(item)
                                                selection.active -> selection.toggle(item.id)
                                                else -> onOpenViewer(globalIndex)
                                            }
                                        },
                                        onLongClick = { if (pick == null) selection.toggle(item.id) },
                                    )
                                }
                            }
                        }
                    }
                    FastScroller(
                        gridState = gridState,
                        rowCount = flatRows.size,
                        monthAt = { i -> flatRows.getOrNull(i)?.month ?: "" },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
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
                    if (errors.isNotEmpty()) snackbarHostState.showSnackbar(errors.first())
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    shareItems?.let { toShare ->
        ShareSheet(items = toShare, onDismiss = { shareItems = null; selection.clear() })
    }

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
