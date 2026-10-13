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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import au.prism.photos.R

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_rename)) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) else onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun DeleteConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_delete_confirm_title)) },
        text = { Text(stringResource(R.string.delete_permanent_single)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
        title = { Text(stringResource(R.string.viewer_synced_delete_title)) },
        text = { Text(stringResource(R.string.viewer_synced_delete_body)) },
        confirmButton = { TextButton(onClick = onDeleteFromBoth) { Text(stringResource(R.string.viewer_synced_delete_both)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onPhoneOnly) { Text(stringResource(R.string.viewer_synced_delete_phone_only)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
                headlineContent = { Text(stringResource(R.string.wallpaper_home)) },
                modifier = Modifier.clickableRow { onPick(true, false) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.wallpaper_lock)) },
                modifier = Modifier.clickableRow { onPick(false, true) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.wallpaper_both)) },
                modifier = Modifier.clickableRow { onPick(true, true) },
            )
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
