package au.prism.photos.ui.share

import androidx.compose.runtime.Composable
import au.prism.photos.domain.MediaItem

/**
 * STUB. Replaced by the viewer agent. Bottom sheet with direct share targets (WhatsApp,
 * Messages, Gmail, Instagram, Messenger, Telegram) and a More button for the system chooser.
 * Downloads the files from Plex to cache first and shares through the FileProvider.
 */
@Composable
fun ShareSheet(
    items: List<MediaItem>,
    onDismiss: () -> Unit,
) {
    onDismiss()
}
