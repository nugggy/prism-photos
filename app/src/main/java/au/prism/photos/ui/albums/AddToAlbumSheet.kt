package au.prism.photos.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import au.prism.photos.PrismApp
import au.prism.photos.domain.Album
import kotlinx.coroutines.launch

/**
 * Bottom sheet for adding [itemIds] to a My album (a Plex photo playlist), or creating a new
 * one with those items. Read only (smart) albums cannot be added to, so they are left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToAlbumSheet(
    itemIds: List<String>,
    onDismiss: () -> Unit,
    onAdded: (albumTitle: String) -> Unit = {},
) {
    val graph = PrismApp.graph
    val scope = rememberCoroutineScope()
    var albums by remember { mutableStateOf<List<Album>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var newAlbumOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        graph.media.myAlbums()
            .onSuccess { albums = it.filterNot { a -> a.readOnly } }
            .onFailure { error = it.message ?: "Could not load albums" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Add to album",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("New album") },
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.clickable(enabled = !busy) { newAlbumOpen = true },
            )
            when {
                error != null -> Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                albums == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                albums!!.isEmpty() -> Text(
                    "No albums yet. Create one above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(albums!!, key = { it.id }) { album ->
                        ListItem(
                            headlineContent = { Text(album.title) },
                            supportingContent = if (album.itemCount > 0) {
                                { Text(if (album.itemCount == 1) "1 item" else "${album.itemCount} items") }
                            } else null,
                            leadingContent = { Icon(Icons.Filled.PhotoAlbum, contentDescription = null) },
                            modifier = Modifier.clickable(enabled = !busy) {
                                busy = true
                                scope.launch {
                                    graph.media.addToMyAlbum(album.id, itemIds)
                                        .onSuccess { onAdded(album.title) }
                                        .onFailure { error = it.message ?: "Couldn't add to album" }
                                    busy = false
                                }
                            },
                        )
                    }
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
                        val title = text.trim()
                        newAlbumOpen = false
                        scope.launch {
                            graph.media.createMyAlbum(title, itemIds)
                                .onSuccess { onAdded(title) }
                                .onFailure { error = it.message ?: "Couldn't create album" }
                        }
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newAlbumOpen = false }) { Text("Cancel") } },
        )
    }
}
