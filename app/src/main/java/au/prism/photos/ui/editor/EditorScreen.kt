package au.prism.photos.ui.editor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * STUB. Replaced by the viewer agent. Photo editor for the Plex item or device item [itemId]
 * (device items are content:// URIs passed through as the id). Saves a JPEG copy to
 * Pictures/Prism and calls [onDone] with the saved content URI, or [onCancel].
 */
@Composable
fun EditorScreen(
    itemId: String,
    onDone: (savedUri: String) -> Unit,
    onCancel: () -> Unit,
) {
    Text("Editor stub")
}
