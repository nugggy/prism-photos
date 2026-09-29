package au.prism.photos.ui.viewer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import au.prism.photos.domain.ViewerSource

/**
 * STUB. Replaced by the viewer agent. Full screen pager over the items of [source],
 * starting at [startIndex]. Handles photos (zoom) and videos (player), info sheet,
 * share, download, favourite, lock, delete, edit (navigates via [onEdit]) and slideshow.
 */
@Composable
fun ViewerScreen(
    source: ViewerSource,
    startIndex: Int,
    onClose: () -> Unit,
    onEdit: (itemId: String) -> Unit,
) {
    Text("Viewer stub")
}
