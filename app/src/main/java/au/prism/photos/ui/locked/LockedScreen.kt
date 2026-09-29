package au.prism.photos.ui.locked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.PrismApp
import au.prism.photos.domain.Album
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.components.ConfirmDeleteDialog
import au.prism.photos.ui.components.EmptyState
import au.prism.photos.ui.components.MediaSelectionOps
import au.prism.photos.ui.components.SelectableMediaGrid
import au.prism.photos.ui.components.SelectionState
import au.prism.photos.ui.lock.LockGate
import au.prism.photos.ui.share.ShareSheet
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.ui.viewer.MediaActions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedScreen(
    gridState: LazyGridState = rememberLazyGridState(),
    onOpenViewer: (index: Int) -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
) {
    LockGate {
        val graph = PrismApp.graph
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val selection = remember { SelectionState() }
        val snackbarHostState = remember { SnackbarHostState() }
        val items by graph.media.lockedItems.collectAsStateWithLifecycle()
        val lockedAlbumIds by graph.local.lockedAlbumIds.collectAsStateWithLifecycle()
        var lockedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var shareItems by remember { mutableStateOf<List<MediaItem>?>(null) }

        LaunchedEffect(lockedAlbumIds) {
            lockedAlbums = lockedAlbumIds.mapNotNull { graph.media.album(it) }
        }

        Scaffold(
            topBar = {
                if (selection.active) {
                    val selectedItems = items.filter { it.id in selection.selected }
                    TopAppBar(
                        title = { Text("${selection.selected.size} selected") },
                        navigationIcon = { IconButton(onClick = { selection.clear() }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } },
                        actions = {
                            IconButton(onClick = { shareItems = selectedItems }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                            IconButton(onClick = {
                                scope.launch {
                                    val result = MediaActions.download(context, selectedItems)
                                    snackbarHostState.showSnackbar(if (result.isSuccess) "Downloaded ${result.getOrNull()} item(s)" else "Download failed")
                                }
                            }) { Icon(Icons.Filled.Download, contentDescription = "Download") }
                            IconButton(onClick = { scope.launch { MediaSelectionOps.unlock(graph, selection.selected); selection.clear() } }) {
                                Icon(Icons.Filled.LockOpen, contentDescription = "Unlock")
                            }
                            IconButton(onClick = { selection.selectAll(items.map { it.id }) }) { Icon(Icons.Filled.SelectAll, contentDescription = "Select all") }
                            IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                        },
                    )
                } else {
                    TopAppBar(title = { Text("Locked") })
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (items.isEmpty() && lockedAlbums.isEmpty()) {
                    EmptyState("Nothing locked", "Lock a photo, video or album from its selection menu and it will show up here.")
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (lockedAlbums.isNotEmpty()) {
                            Text("Locked albums", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(lockedAlbums, key = { it.id }) { album ->
                                    LockedAlbumChip(album, onClick = { onOpenAlbum(album.id) }, onUnlock = { scope.launch { graph.local.setAlbumLocked(album.id, false) } })
                                }
                            }
                        }
                        if (items.isNotEmpty()) {
                            Text("Locked photos and videos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                            SelectableMediaGrid(
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

        shareItems?.let { toShare -> ShareSheet(items = toShare, onDismiss = { shareItems = null; selection.clear() }) }
    }
}

@Composable
private fun LockedAlbumChip(album: Album, onClick: () -> Unit, onUnlock: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(12.dp).fillMaxWidth()) {
            Icon(Icons.Filled.PhotoAlbum, contentDescription = null, tint = PlexGold)
            Column(Modifier.padding(start = 8.dp)) {
                Text(album.title, style = MaterialTheme.typography.bodyMedium)
                Text("${album.itemCount} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUnlock) { Icon(Icons.Filled.LockOpen, contentDescription = "Unlock album") }
        }
    }
}
