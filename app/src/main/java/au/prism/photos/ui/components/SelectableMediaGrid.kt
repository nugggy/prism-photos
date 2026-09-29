package au.prism.photos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import au.prism.photos.domain.MediaItem

/**
 * A plain (non month-grouped) selectable grid used by favourites, album detail, device,
 * locked and search screens. The timeline has its own grid because of month headers,
 * pinch to zoom and the fast scroller.
 */
@Composable
fun SelectableMediaGrid(
    items: List<MediaItem>,
    columns: Int,
    thumbUrl: (MediaItem) -> String,
    selection: SelectionState,
    onOpenViewer: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(2.dp),
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val pick = LocalPickHandler.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        header?.invoke(this)
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            MediaCell(
                item = item,
                thumbUrl = thumbUrl(item),
                selected = item.id in selection.selected,
                selectionMode = selection.active,
                onClick = {
                    when {
                        pick != null -> pick(item)
                        selection.active -> selection.toggle(item.id)
                        else -> onOpenViewer(index)
                    }
                },
                onLongClick = { if (pick == null) selection.toggle(item.id) },
            )
        }
    }
}
