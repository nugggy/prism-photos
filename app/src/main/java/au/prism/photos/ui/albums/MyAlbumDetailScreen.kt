package au.prism.photos.ui.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.prism.photos.PrismApp
import au.prism.photos.domain.MediaItem
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

/**
 * A My album is a Plex photo playlist: the user can add and remove photos, unlike folder
 * albums which Plex does not let clients modify. See docs/plex-api.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    onOpenViewer: (index: Int) -> Unit,
) {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val vm: MyAlbumDetailViewModel = viewModel(factory = viewModelFactory { initializer { MyAlbumDetailViewModel(graph, albumId) } })
    val scope = rememberCoroutineScope()
    val selection = remember { SelectionState() }
    val snackbarHostState = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var showDeleteAlbumConfirm by remember { mutableStateOf(false) }
    var shareItems by remember { mutableStateOf<List<MediaItem>?>(null) }

    val readOnly = vm.album?.readOnly == true

    Scaffold(
        topBar = {
            if (selection.active) {
                val selectedItems = vm.items.filter { it.id in selection.selected }
                SelectionTopBar(
                    count = selection.selected.size,
                    allFavourite = selectedItems.isNotEmpty() && selectedItems.all { it.favourite },
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(vm.items.map { it.id }) },
                    onShare = { shareItems = selectedItems },
                    onDownload = {
                        scope.launch {
                            val result = MediaActions.download(context, selectedItems)
                            snackbarHostState.showSnackbar(if (result.isSuccess) "Downloaded ${result.getOrNull()} item(s)" else "Download failed")
                        }
                    },
                    onToggleFavourite = { scope.launch { MediaSelectionOps.toggleFavourite(graph, selectedItems); selection.clear() } },
                    onRemoveFromAlbum = if (!readOnly) {
                        {
                            scope.launch {
                                val ids = selection.selected.toList()
                                val result = vm.removeFromAlbum(ids)
                                selection.clear()
                                if (result.isFailure) {
                                    snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Couldn't remove from album")
                                }
                            }
                        }
                    } else null,
                )
            } else {
                TopAppBar(
                    title = { Text(vm.album?.title ?: "Album") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Album options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (!readOnly) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameOpen = true })
                            }
                            DropdownMenuItem(
                                text = { Text("Select all") },
                                leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                                onClick = { menuOpen = false; selection.selectAll(vm.items.map { it.id }) },
                            )
                            if (!readOnly) {
                                DropdownMenuItem(
                                    text = { Text("Delete album") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = { menuOpen = false; showDeleteAlbumConfirm = true },
                                )
                            }
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
                vm.items.isEmpty() -> EmptyState("Nothing in this album yet", "Add photos from the viewer or a selection.")
                else -> SelectableMediaGrid(
                    items = vm.items,
                    columns = 3,
                    thumbUrl = { graph.media.thumbUrl(it, 400) },
                    selection = selection,
                    onOpenViewer = onOpenViewer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (renameOpen) {
        var text by remember { mutableStateOf(vm.album?.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename album") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(text.trim()); renameOpen = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }

    if (showDeleteAlbumConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAlbumConfirm = false },
            title = { Text("Delete album?") },
            text = { Text("This removes the album from Plex. The photos in it are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAlbumConfirm = false
                    scope.launch {
                        vm.deleteAlbum()
                            .onSuccess { onBack() }
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Couldn't delete album") }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAlbumConfirm = false }) { Text("Cancel") } },
        )
    }

    shareItems?.let { toShare -> ShareSheet(items = toShare, onDismiss = { shareItems = null; selection.clear() }) }
}
