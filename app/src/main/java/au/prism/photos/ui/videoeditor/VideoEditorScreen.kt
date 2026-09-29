package au.prism.photos.ui.videoeditor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * STUB, replaced by the video editor agent. Edits the Plex clip or device video [itemId]
 * (device items pass their content:// URI as the id): trim, mute, rotate, flip, speed and
 * colour adjustments through Media3 Transformer. Exports to Movies/Plex Gallery and calls
 * [onDone] with the saved content URI, or [onCancel].
 */
@Composable
fun VideoEditorScreen(
    itemId: String,
    onDone: (savedUri: String) -> Unit,
    onCancel: () -> Unit,
) {
    Text("Video editor stub")
}
