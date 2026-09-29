package au.prism.photos.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.PrismApp
import au.prism.photos.domain.ViewerSource
import au.prism.photos.ui.albums.AlbumsScreen
import au.prism.photos.ui.device.DeviceScreen
import au.prism.photos.ui.favourites.FavouritesScreen
import au.prism.photos.ui.locked.LockedScreen
import au.prism.photos.ui.timeline.TimelineScreen
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.ui.update.UpdateDialog
import au.prism.photos.ui.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
    onOpenViewer: (source: ViewerSource, index: Int) -> Unit,
) {
    val graph = PrismApp.graph
    val session by graph.session.session.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(HomeTab.PHOTOS) }
    var menuOpen by remember { mutableStateOf(false) }

    val updateVm: UpdateViewModel = viewModel(factory = viewModelFactory { initializer { UpdateViewModel(graph) } })

    // Grid states created once here (not conditionally) so each tab keeps its own scroll
    // position when switching tabs, since only the active tab's composable is emitted below.
    val photosState = rememberLazyGridState()
    val albumsState = rememberLazyGridState()
    val favouritesState = rememberLazyGridState()
    val deviceState = rememberLazyGridState()
    val lockedState = rememberLazyGridState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(session.libraryTitle ?: "Prism") },
                    actions = {
                        IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                        if (updateVm.available != null) {
                            IconButton(onClick = { updateVm.openDialog() }) {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = "Update available", tint = PlexGold)
                            }
                        }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.AccountCircle, contentDescription = "Account") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(session.user?.title ?: "Account") }, onClick = {}, enabled = false)
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpenSettings() })
                        }
                    },
                )
                if (updateVm.available != null) {
                    Surface(color = PlexGold.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth().clickable { updateVm.openDialog() }) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "Prism ${updateVm.available!!.versionName} is available. Tap to update.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clip(MaterialTheme.shapes.small),
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.filled else t.outlined, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Surface(Modifier.padding(padding), color = MaterialTheme.colorScheme.background) {
            when (tab) {
                HomeTab.PHOTOS -> TimelineScreen(gridState = photosState, onOpenViewer = { index -> onOpenViewer(ViewerSource.Timeline, index) })
                HomeTab.ALBUMS -> AlbumsScreen(gridState = albumsState, onOpenAlbum = onOpenAlbum)
                HomeTab.FAVOURITES -> FavouritesScreen(gridState = favouritesState, onOpenViewer = { index -> onOpenViewer(ViewerSource.Favourites, index) })
                HomeTab.DEVICE -> DeviceScreen(gridState = deviceState, onOpenViewer = { bucket, index -> onOpenViewer(ViewerSource.Device(bucket), index) })
                HomeTab.LOCKED -> LockedScreen(gridState = lockedState, onOpenViewer = { index -> onOpenViewer(ViewerSource.Locked, index) }, onOpenAlbum = onOpenAlbum)
            }
        }
    }

    if (updateVm.dialogOpen) {
        UpdateDialog(updateVm, onDismiss = { updateVm.dismissDialog() })
    }
}
