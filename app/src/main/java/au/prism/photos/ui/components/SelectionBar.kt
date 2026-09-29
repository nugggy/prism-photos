package au.prism.photos.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/** Top bar shown while a grid is in multi-select mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    allFavourite: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavourite: (() -> Unit)? = null,
    onLock: (() -> Unit)? = null,
    onAddToAlbum: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") }
        },
        actions = {
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share") }
            IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, contentDescription = "Download") }
            if (onToggleFavourite != null) {
                IconButton(onClick = onToggleFavourite) {
                    Icon(if (allFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Favourite")
                }
            }
            if (onAddToAlbum != null) {
                IconButton(onClick = onAddToAlbum) { Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to album") }
            }
            if (onRemoveFromAlbum != null) {
                IconButton(onClick = onRemoveFromAlbum) { Icon(Icons.Filled.PlaylistRemove, contentDescription = "Remove from album") }
            }
            if (onLock != null) {
                IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, contentDescription = "Lock") }
            }
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, contentDescription = "Select all") }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}
