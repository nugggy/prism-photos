package au.prism.photos.ui.viewer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.util.Format

private val ScrimColor = Color.Black.copy(alpha = 0.45f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerTopBar(
    item: MediaItem,
    isLocked: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onEditDescription: () -> Unit,
    onTags: () -> Unit,
    onSetAlbumCover: () -> Unit,
    onAddToAlbum: () -> Unit,
    onWallpaper: () -> Unit,
    onLockToggle: () -> Unit,
    onSlideshow: () -> Unit,
    onOpenInPlex: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                Text(
                    Format.dateTime(item.takenAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
            }
        },
        actions = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Info") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = { menuOpen = false; onInfo() })
                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(text = { Text("Edit description") }, onClick = { menuOpen = false; onEditDescription() })
                DropdownMenuItem(text = { Text("Tags") }, onClick = { menuOpen = false; onTags() })
                if (item.albumId != null) {
                    DropdownMenuItem(text = { Text("Set as album cover") }, onClick = { menuOpen = false; onSetAlbumCover() })
                }
                DropdownMenuItem(
                    text = { Text("Add to album") },
                    leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) },
                    onClick = { menuOpen = false; onAddToAlbum() },
                )
                DropdownMenuItem(
                    text = { Text("Set as wallpaper") },
                    leadingIcon = { Icon(Icons.Filled.Wallpaper, null) },
                    onClick = { menuOpen = false; onWallpaper() },
                )
                DropdownMenuItem(
                    text = { Text(if (isLocked) "Unlock" else "Lock") },
                    leadingIcon = { Icon(if (isLocked) Icons.Filled.LockOpen else Icons.Filled.Lock, null) },
                    onClick = { menuOpen = false; onLockToggle() },
                )
                DropdownMenuItem(
                    text = { Text("Slideshow") },
                    leadingIcon = { Icon(Icons.Filled.PlayCircle, null) },
                    onClick = { menuOpen = false; onSlideshow() },
                )
                if (onOpenInPlex != null) {
                    DropdownMenuItem(
                        text = { Text("Open in Plex") },
                        leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                        onClick = { menuOpen = false; onOpenInPlex() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ScrimColor,
            titleContentColor = Color.White,
        ),
    )
}

@Composable
fun ViewerBottomBar(
    item: MediaItem,
    onShare: () -> Unit,
    onFavourite: () -> Unit,
    onEdit: (() -> Unit)?,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ScrimColor)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
        }
        IconButton(onClick = onFavourite) {
            Icon(
                imageVector = if (item.favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favourite",
                tint = if (item.favourite) PlexGold else Color.White,
                modifier = Modifier.animateContentSize(),
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White)
            }
        }
        IconButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White)
        }
        IconButton(onClick = onInfo) {
            Icon(Icons.Filled.Info, contentDescription = "Info", tint = Color.White)
        }
    }
}
