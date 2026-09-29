package au.prism.photos.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
import au.prism.photos.domain.SortOrder
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private val accentPresets = listOf(
    0xFFE5A00D, 0xFFE53935, 0xFF8E24AA, 0xFF3949AB,
    0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFFB8C00,
).map { it or 0xFF000000 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
    onOpenViewer: (index: Int) -> Unit,
) {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val vm: AlbumDetailViewModel = viewModel(factory = viewModelFactory { initializer { AlbumDetailViewModel(graph, albumId) } })
    val scope = rememberCoroutineScope()
    val selection = remember { SelectionState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val pick = LocalPickHandler.current

    val accents by graph.local.albumAccents.collectAsStateWithLifecycle()
    val sorts by graph.local.albumSort.collectAsStateWithLifecycle()
    val accent = accents[albumId]?.let { Color(it) }
    val order = sorts[albumId] ?: SortOrder.NEWEST
    val sortedItems = remember(vm.items, order) {
        when (order) {
            SortOrder.NEWEST -> vm.items.sortedByDescending { it.takenAt }
            SortOrder.OLDEST -> vm.items.sortedBy { it.takenAt }
            SortOrder.NAME -> vm.items.sortedBy { it.title }
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var coverPickerOpen by remember { mutableStateOf(false) }
    var accentPickerOpen by remember { mutableStateOf(false) }
    var sortPickerOpen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var shareItems by remember { mutableStateOf<List<MediaItem>?>(null) }

    Scaffold(
        topBar = {
            if (selection.active) {
                val selectedItems = sortedItems.filter { it.id in selection.selected }
                SelectionTopBar(
                    count = selection.selected.size,
                    allFavourite = selectedItems.isNotEmpty() && selectedItems.all { it.favourite },
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(sortedItems.map { it.id }) },
                    onShare = { shareItems = selectedItems },
                    onDownload = {
                        scope.launch {
                            val result = MediaActions.download(context, selectedItems)
                            snackbarHostState.showSnackbar(if (result.isSuccess) "Downloaded ${result.getOrNull()} item(s)" else "Download failed")
                        }
                    },
                    onToggleFavourite = { scope.launch { MediaSelectionOps.toggleFavourite(graph, selectedItems); selection.clear() } },
                    onLock = { scope.launch { MediaSelectionOps.lock(graph, selection.selected); selection.clear() } },
                    onDelete = { showDeleteConfirm = true },
                )
            } else {
                TopAppBar(
                    title = { Text(vm.album?.title ?: "Album", color = accent ?: MaterialTheme.colorScheme.onSurface) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Album options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameOpen = true })
                            DropdownMenuItem(text = { Text("Set cover") }, onClick = { menuOpen = false; coverPickerOpen = true })
                            DropdownMenuItem(text = { Text("Accent colour") }, leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) }, onClick = { menuOpen = false; accentPickerOpen = true })
                            DropdownMenuItem(text = { Text("Sort") }, leadingIcon = { Icon(Icons.Filled.Sort, contentDescription = null) }, onClick = { menuOpen = false; sortPickerOpen = true })
                            DropdownMenuItem(
                                text = { Text("Lock album") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { graph.local.setAlbumLocked(albumId, true) }
                                    onBack()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Select all") },
                                leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                                onClick = { menuOpen = false; selection.selectAll(sortedItems.map { it.id }) },
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                vm.loading -> LoadingState()
                vm.error != null -> ErrorState(vm.error!!, onRetry = { vm.load() })
                vm.subAlbums.isEmpty() && sortedItems.isEmpty() -> EmptyState("Nothing in this album yet")
                else -> LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(2.dp)) {
                    if (vm.subAlbums.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                lazyRowItems(vm.subAlbums, key = { it.id }) { sub ->
                                    Box(Modifier.width(140.dp)) { AlbumCard(sub, onClick = { onOpenAlbum(sub.id) }) }
                                }
                            }
                        }
                    }
                    itemsIndexed(sortedItems, key = { _, it -> it.id }) { index, item ->
                        MediaCell(
                            item = item,
                            thumbUrl = graph.media.thumbUrl(item, 400),
                            selected = item.id in selection.selected,
                            selectionMode = selection.active,
                            onClick = {
                                when {
                                    pick != null -> pick(item)
                                    selection.active -> selection.toggle(item.id)
                                    else -> onOpenViewer(index)
                                }
                            },
                            onLongClick = { if (pick == null) selection.toggle(item.id) },
                        )
                    }
                }
            }
        }
    }

    if (renameOpen) {
        var text by remember { mutableStateOf(vm.album?.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename album") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(text); renameOpen = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }

    if (coverPickerOpen) {
        AlertDialog(
            onDismissRequest = { coverPickerOpen = false },
            title = { Text("Set cover") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), contentPadding = PaddingValues(4.dp)) {
                    items(sortedItems, key = { it.id }) { item ->
                        AsyncImage(
                            model = graph.media.thumbUrl(item, 200),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickableCover { scope.launch { graph.local.setAlbumCover(albumId, item.id) }; coverPickerOpen = false },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { coverPickerOpen = false }) { Text("Close") } },
        )
    }

    if (accentPickerOpen) {
        AlertDialog(
            onDismissRequest = { accentPickerOpen = false },
            title = { Text("Accent colour") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    accentPresets.forEach { argb ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickableCover {
                                    scope.launch { graph.local.setAlbumAccent(albumId, argb) }
                                    accentPickerOpen = false
                                },
                        ) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(argb)))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { graph.local.setAlbumAccent(albumId, null) }; accentPickerOpen = false }) { Text("Clear") }
            },
        )
    }

    if (sortPickerOpen) {
        AlertDialog(
            onDismissRequest = { sortPickerOpen = false },
            title = { Text("Sort") },
            text = {
                Column {
                    listOf(SortOrder.NEWEST to "Newest first", SortOrder.OLDEST to "Oldest first", SortOrder.NAME to "Name").forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickableCover { scope.launch { graph.local.setAlbumSort(albumId, value) }; sortPickerOpen = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = order == value, onClick = { scope.launch { graph.local.setAlbumSort(albumId, value) }; sortPickerOpen = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sortPickerOpen = false }) { Text("Done") } },
        )
    }

    if (showDeleteConfirm) {
        val selectedItems = sortedItems.filter { it.id in selection.selected }
        ConfirmDeleteDialog(
            count = selectedItems.size,
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    val errors = MediaSelectionOps.delete(graph, selectedItems)
                    selection.clear()
                    vm.load()
                    if (errors.isNotEmpty()) snackbarHostState.showSnackbar(errors.first())
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    shareItems?.let { toShare -> ShareSheet(items = toShare, onDismiss = { shareItems = null; selection.clear() }) }
}

private fun Modifier.clickableCover(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
