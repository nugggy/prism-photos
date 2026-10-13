package au.prism.photos.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.ui.res.stringResource
import au.prism.photos.R

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
    onUpload: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selection_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.selection_clear)) }
        },
        actions = {
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share)) }
            IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.selection_download)) }
            if (onToggleFavourite != null) {
                IconButton(onClick = onToggleFavourite) {
                    Icon(if (allFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = stringResource(R.string.selection_favourite))
                }
            }
            if (onAddToAlbum != null) {
                IconButton(onClick = onAddToAlbum) { Icon(Icons.Filled.PlaylistAdd, contentDescription = stringResource(R.string.selection_add_to_album)) }
            }
            if (onRemoveFromAlbum != null) {
                IconButton(onClick = onRemoveFromAlbum) { Icon(Icons.Filled.PlaylistRemove, contentDescription = stringResource(R.string.selection_remove_from_album)) }
            }
            if (onLock != null) {
                IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.selection_lock)) }
            }
            if (onUpload != null) {
                IconButton(onClick = onUpload) { Icon(Icons.Filled.CloudUpload, contentDescription = stringResource(R.string.selection_upload)) }
            }
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all)) }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}
