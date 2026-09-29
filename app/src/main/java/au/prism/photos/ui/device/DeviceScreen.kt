package au.prism.photos.ui.device

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import au.prism.photos.PrismApp
import au.prism.photos.domain.DeviceAlbum
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.components.EmptyState
import au.prism.photos.ui.components.LoadingState
import au.prism.photos.ui.components.MediaSelectionOps
import au.prism.photos.ui.components.SelectableMediaGrid
import au.prism.photos.ui.components.SelectionState
import au.prism.photos.ui.components.SelectionTopBar
import au.prism.photos.ui.share.ShareSheet
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.ui.viewer.MediaActions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    gridState: LazyGridState = rememberLazyGridState(),
    onOpenViewer: (bucketId: String?, index: Int) -> Unit,
) {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selection = remember { SelectionState() }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermission by remember { mutableStateOf(false) }
    var albums by remember { mutableStateOf<List<DeviceAlbum>>(emptyList()) }
    var selectedBucket by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var shareItems by remember { mutableStateOf<List<MediaItem>?>(null) }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    suspend fun refresh() {
        loading = true
        hasPermission = graph.device.hasPermission()
        if (hasPermission) {
            albums = graph.device.albums()
            items = graph.device.items(selectedBucket)
        }
        loading = false
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) scope.launch { refresh() }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(selectedBucket, hasPermission) {
        if (hasPermission) items = graph.device.items(selectedBucket)
    }

    Scaffold(
        topBar = {
            if (selection.active) {
                val selectedItems = items.filter { it.id in selection.selected }
                SelectionTopBar(
                    count = selection.selected.size,
                    allFavourite = false,
                    onClose = { selection.clear() },
                    onSelectAll = { selection.selectAll(items.map { it.id }) },
                    onShare = { shareItems = selectedItems },
                    onDownload = {
                        scope.launch {
                            val result = MediaActions.download(context, selectedItems)
                            snackbarHostState.showSnackbar(if (result.isSuccess) "Saved ${result.getOrNull()} item(s)" else "Download failed")
                        }
                    },
                    onToggleFavourite = null,
                    onLock = { scope.launch { MediaSelectionOps.lock(graph, selection.selected); selection.clear() } },
                    onDelete = null,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingState()
                !hasPermission -> PermissionRequestCard(onRequest = { launcher.launch(permissions) })
                else -> {
                    if (albums.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(selected = selectedBucket == null, onClick = { selectedBucket = null }, label = { Text("All") })
                            }
                            items(albums, key = { it.bucketId }) { album ->
                                FilterChip(selected = selectedBucket == album.bucketId, onClick = { selectedBucket = album.bucketId }, label = { Text("${album.name} (${album.count})") })
                            }
                        }
                    }
                    DefaultGalleryCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    if (items.isEmpty()) {
                        EmptyState("No photos on this device", "Photos and videos from this phone show up here.")
                    } else {
                        SelectableMediaGrid(
                            items = items,
                            columns = 3,
                            thumbUrl = { it.localUri ?: "" },
                            selection = selection,
                            onOpenViewer = { index -> onOpenViewer(selectedBucket, index) },
                            gridState = gridState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    shareItems?.let { toShare -> ShareSheet(items = toShare, onDismiss = { shareItems = null; selection.clear() }) }
}

@Composable
private fun PermissionRequestCard(onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = PlexGold)
        Text("Allow access to your photos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Plex Gallery needs permission to show the photos and videos already on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Button(onClick = onRequest) { Text("Allow access") }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            context.startActivity(intent)
        }) { Text("Manage in Settings") }
    }
}

@Composable
private fun DefaultGalleryCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp)) {
            Text("Set Plex Gallery as your default gallery", style = MaterialTheme.typography.titleSmall)
            Text(
                "Open a photo from any app, pick Plex Gallery, then tap Always.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    context.startActivity(intent)
                }) { Text("Open settings") }
            }
        }
    }
}
