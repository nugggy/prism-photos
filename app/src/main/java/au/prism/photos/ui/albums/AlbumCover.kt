package au.prism.photos.ui.albums

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.PrismApp
import au.prism.photos.domain.Album

/** Resolves an album's cover, honouring a device-only custom cover item if one was set. */
@Composable
fun rememberAlbumCoverUrl(album: Album, size: Int): String {
    val graph = PrismApp.graph
    val covers by graph.local.albumCovers.collectAsStateWithLifecycle()
    val customItemId = covers[album.id]
    var url by remember(album.id, customItemId, size) { mutableStateOf(graph.media.albumCoverUrl(album, size)) }
    LaunchedEffect(customItemId, size) {
        if (customItemId != null) {
            graph.media.item(customItemId)?.let { url = graph.media.thumbUrl(it, size) }
        }
    }
    return url
}
