package au.prism.photos.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) else onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this item?") },
        text = { Text("This can't be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Shown instead of [DeleteConfirmDialog] when the item being deleted is a device item that has
 * already been synced to the Plex library (see ViewerViewModel.syncedPlexRatingKey). Lets the
 * user choose whether the Plex copy should go too.
 */
@Composable
fun SyncedDeleteConfirmDialog(
    onDismiss: () -> Unit,
    onDeleteFromBoth: () -> Unit,
    onPhoneOnly: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete from Plex as well?") },
        text = { Text("This photo was synced to your Plex library. Deleting it here can also remove it from Plex.") },
        confirmButton = { TextButton(onClick = onDeleteFromBoth) { Text("Delete from both") } },
        dismissButton = {
            Row {
                TextButton(onClick = onPhoneOnly) { Text("Phone only") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSheet(onDismiss: () -> Unit, onPick: (home: Boolean, lock: Boolean) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text("Home screen") },
                modifier = Modifier.clickableRow { onPick(true, false) },
            )
            ListItem(
                headlineContent = { Text("Lock screen") },
                modifier = Modifier.clickableRow { onPick(false, true) },
            )
            ListItem(
                headlineContent = { Text("Home and lock screen") },
                modifier = Modifier.clickableRow { onPick(true, true) },
            )
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
