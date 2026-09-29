package au.prism.photos.ui.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.prism.photos.PrismApp
import au.prism.photos.domain.Album
import au.prism.photos.ui.components.EmptyState
import au.prism.photos.ui.components.ErrorState
import au.prism.photos.ui.components.LoadingState
import coil3.compose.AsyncImage

@Composable
fun AlbumsScreen(
    gridState: LazyGridState = rememberLazyGridState(),
    onOpenAlbum: (albumId: String) -> Unit,
    onOpenMyAlbum: (albumId: String) -> Unit,
) {
    val graph = PrismApp.graph
    val vm: AlbumsViewModel = viewModel(factory = viewModelFactory { initializer { AlbumsViewModel(graph) } })
    val lockedAlbumIds by graph.local.lockedAlbumIds.collectAsStateWithLifecycle()
    val visibleAlbums = vm.albums.filter { it.id !in lockedAlbumIds }
    var newAlbumOpen by remember { mutableStateOf(false) }

    when {
        vm.loading -> LoadingState()
        vm.error != null && vm.myAlbums.isEmpty() && visibleAlbums.isEmpty() -> ErrorState(vm.error!!, onRetry = { vm.load() })
        vm.myAlbums.isEmpty() && visibleAlbums.isEmpty() -> EmptyState("No albums yet", "Create an album below, or albums from Plex will show up here.")
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("My albums", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { newAlbumOpen = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("New album")
                    }
                }
            }
            if (vm.myAlbums.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No albums yet. Tap New album to add photos to one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(vm.myAlbums, key = { "my-${it.id}" }) { album ->
                    AlbumCard(
                        album = album,
                        onClick = { onOpenMyAlbum(album.id) },
                        badge = if (album.readOnly) "Smart" else null,
                    )
                }
            }
            if (visibleAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Folders",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(visibleAlbums, key = { "folder-${it.id}" }) { album ->
                    AlbumCard(album, onClick = { onOpenAlbum(album.id) })
                }
            }
        }
    }

    if (newAlbumOpen) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newAlbumOpen = false },
            title = { Text("New album") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Album title") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        vm.createMyAlbum(text.trim())
                        newAlbumOpen = false
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newAlbumOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, badge: String? = null) {
    val coverUrl = rememberAlbumCoverUrl(album, 500)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Card(
                onClick = onClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text(album.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        if (album.itemCount > 0) {
            Text("${album.itemCount} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
