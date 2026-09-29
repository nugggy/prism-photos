package au.prism.photos.ui.upload

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * STUB, replaced by the upload agent. Uploads the device items (content:// URIs) in [uris]
 * to the Plex library folder over SMB or WebDAV, lets the user pick or create the destination
 * folder (album), shows progress, then asks Plex to scan. Calls [onDone] when finished.
 */
@Composable
fun UploadScreen(
    uris: List<String>,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    Text("Upload stub")
}
