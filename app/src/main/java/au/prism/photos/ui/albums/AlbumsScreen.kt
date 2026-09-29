package au.prism.photos.ui.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
) {
    val graph = PrismApp.graph
    val vm: AlbumsViewModel = viewModel(factory = viewModelFactory { initializer { AlbumsViewModel(graph) } })
    val lockedAlbumIds by graph.local.lockedAlbumIds.collectAsStateWithLifecycle()
    val visibleAlbums = vm.albums.filter { it.id !in lockedAlbumIds }

    when {
        vm.loading -> LoadingState()
        vm.error != null -> ErrorState(vm.error!!, onRetry = { vm.load() })
        visibleAlbums.isEmpty() -> EmptyState("No albums yet", "Albums you create in Plex will show up here.")
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(visibleAlbums, key = { it.id }) { album ->
                AlbumCard(album, onClick = { onOpenAlbum(album.id) })
            }
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit) {
    val coverUrl = rememberAlbumCoverUrl(album, 500)
    Column(Modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(album.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text("${album.itemCount} items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
