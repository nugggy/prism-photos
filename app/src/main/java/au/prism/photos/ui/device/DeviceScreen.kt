package au.prism.photos.ui.device

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.PrismApp
import au.prism.photos.domain.DeviceAlbum
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.components.EmptyState
import au.prism.photos.ui.components.LoadingState
import au.prism.photos.ui.components.MediaSelectionOps
import au.prism.photos.ui.components.SelectionState
import au.prism.photos.ui.components.SelectionTopBar
import au.prism.photos.ui.share.ShareSheet
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.ui.upload.UploadScreen
import au.prism.photos.ui.viewer.DeleteConfirmDialog
import au.prism.photos.ui.viewer.MediaActions
import au.prism.photos.ui.viewer.SyncedDeleteConfirmDialog
import coil3.compose.AsyncImage
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
    val ledgerEntries by graph.syncLedger.entries.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(false) }
    var albums by remember { mutableStateOf<List<DeviceAlbum>>(emptyList()) }
    var selectedBucket by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var shareItems by remember { mutableStateOf<List<MediaItem>?>(null) }

    // Set when the selection bar's or the toolbar's upload action is used; while non-null the
    // upload screen is shown in place of the grid (there is no NavController reachable from this
    // leaf screen without changes to ui/home/HomeScreen.kt, which is out of scope here).
    var uploadUris by remember { mutableStateOf<List<String>?>(null) }

    // Delete confirmation state: items pending delete, and (once resolved) which of them are
    // already synced to Plex, keyed by MediaItem.id -> Plex rating key.
    var pendingDelete by remember { mutableStateOf<List<MediaItem>?>(null) }
    var pendingDeleteSynced by remember { mutableStateOf<Map<String, String>?>(null) }

    fun isSynced(item: MediaItem): Boolean =
        item.localUri != null && ledgerEntries.any { it.contentUri == item.localUri }

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

    fun requestDelete(toDelete: List<MediaItem>) {
        pendingDelete = toDelete
        pendingDeleteSynced = null
        scope.launch {
            val synced = MediaSelectionOps.syncedPlexIds(graph, toDelete)
            pendingDeleteSynced = synced
        }
    }

    fun runDelete(items: List<MediaItem>, plexRatingKeys: Map<String, String>) {
        scope.launch {
            val errors = MediaSelectionOps.deleteDeviceItems(graph, items, plexRatingKeys)
            selection.clear()
            pendingDelete = null
            pendingDeleteSynced = null
            scope.launch { refresh() }
            if (errors.isNotEmpty()) {
                snackbarHostState.showSnackbar(errors.first())
            }
        }
    }

    if (uploadUris != null) {
        UploadScreen(
            uris = uploadUris.orEmpty(),
            onDone = { uploadUris = null; scope.launch { refresh() } },
            onCancel = { uploadUris = null },
        )
        return
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
                    onUpload = { uploadUris = selectedItems.mapNotNull { it.localUri } },
                    onDelete = { requestDelete(selectedItems) },
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
                    val notSynced = items.count { !isSynced(it) }
                    if (!selection.active && notSynced > 0) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$notSynced not backed up to Plex",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                uploadUris = items.filterNot { isSynced(it) }.mapNotNull { it.localUri }
                            }) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = "Upload all not yet synced", tint = PlexGold)
                            }
                        }
                    }
                    DefaultGalleryCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    if (items.isEmpty()) {
                        EmptyState("No photos on this device", "Photos and videos from this phone show up here.")
                    } else {
                        DeviceMediaGrid(
                            items = items,
                            selection = selection,
                            isSynced = ::isSynced,
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

    val toDelete = pendingDelete
    if (toDelete != null) {
        val syncedMap = pendingDeleteSynced
        if (syncedMap == null) {
            // Still resolving whether any of these are synced; nothing shown yet to avoid
            // flashing the plain confirm dialog first.
        } else if (syncedMap.isNotEmpty()) {
            SyncedDeleteConfirmDialog(
                onDismiss = { pendingDelete = null; pendingDeleteSynced = null },
                onDeleteFromBoth = { runDelete(toDelete, syncedMap) },
                onPhoneOnly = { runDelete(toDelete, emptyMap()) },
            )
        } else {
            DeleteConfirmDialog(
                onDismiss = { pendingDelete = null; pendingDeleteSynced = null },
                onConfirm = { runDelete(toDelete, emptyMap()) },
            )
        }
    }
}

/**
 * A selectable grid like ui/components/SelectableMediaGrid.kt with the addition of a small cloud
 * tick badge on items already backed up to Plex (see data/upload/SyncLedger.kt). Kept local to
 * this screen since the shared grid and cell composables are outside this change's file list.
 */
@Composable
private fun DeviceMediaGrid(
    items: List<MediaItem>,
    selection: SelectionState,
    isSynced: (MediaItem) -> Boolean,
    onOpenViewer: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            DeviceMediaCell(
                item = item,
                selected = item.id in selection.selected,
                selectionMode = selection.active,
                synced = isSynced(item),
                onClick = { if (selection.active) selection.toggle(item.id) else onOpenViewer(index) },
                onLongClick = { selection.toggle(item.id) },
            )
        }
    }
}

@Composable
private fun DeviceMediaCell(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    synced: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = item.localUri,
            contentDescription = item.title,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (item.isVideo) {
            Row(Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text(formatDuration(item.durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (item.favourite && !selectionMode) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Favourite",
                tint = PlexGold,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
            )
        }
        if (synced && !selectionMode) {
            Icon(
                Icons.Filled.CloudDone,
                contentDescription = "Synced to Plex",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(2.dp)
                    .size(14.dp),
            )
        }
        if (selectionMode) {
            Box(Modifier.fillMaxSize().background(if (selected) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.05f)))
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) PlexGold else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(20.dp),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
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
