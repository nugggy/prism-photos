package au.prism.photos.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.launch

/** A draggable thumb on the right edge that jumps the grid and shows the month while dragging. */
@Composable
fun FastScroller(
    gridState: LazyGridState,
    rowCount: Int,
    monthAt: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    if (rowCount < 40) return // not worth it for short lists
    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragYPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier
            .fillMaxHeight()
            .width(28.dp)
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
            .pointerInput(rowCount) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragYPx = offset.y.coerceIn(0f, trackHeightPx)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        dragYPx = change.position.y.coerceIn(0f, trackHeightPx)
                        val fraction = if (trackHeightPx > 0) dragYPx / trackHeightPx else 0f
                        val index = (fraction * (rowCount - 1)).toInt().coerceIn(0, rowCount - 1)
                        scope.launch { gridState.scrollToItem(index) }
                    },
                )
            },
    ) {
        if (dragging) {
            val fraction = if (trackHeightPx > 0) dragYPx / trackHeightPx else 0f
            val index = (fraction * (rowCount - 1)).toInt().coerceIn(0, rowCount - 1)
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(x = (-140).dp.roundToPx(), y = dragYPx.toInt() - 24) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    monthAt(index),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, dragYPx.toInt() - 16) }
                .size(width = 6.dp, height = 32.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.9f else 0.35f)),
        )
    }
}
