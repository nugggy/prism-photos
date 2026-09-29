package au.prism.photos.ui.videoeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import au.prism.photos.ui.theme.PlexGold
import kotlin.math.roundToLong

private const val HANDLE_WIDTH_DP = 14

/**
 * Filmstrip of extracted thumbnails with a dual-handle trim range overlay and a play head.
 * Coordinates are all in milliseconds against [durationMs].
 */
@Composable
fun FilmstripRangeSlider(
    frames: List<FilmstripFrame>,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    positionMs: Long,
    onTrimChange: (startMs: Long, endMs: Long) -> Unit,
    onScrub: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0) return
    val startState = rememberUpdatedState(trimStartMs)
    val endState = rememberUpdatedState(trimEndMs)
    val onTrimChangeState = rememberUpdatedState(onTrimChange)
    val onScrubState = rememberUpdatedState(onScrub)

    BoxWithConstraints(modifier = modifier.height(72.dp)) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        val handleWidthPx = with(density) { HANDLE_WIDTH_DP.dp.toPx() }

        fun msToX(ms: Long): Float = (ms / durationMs.toFloat()) * widthPx
        fun xToMs(x: Float): Long = ((x / widthPx) * durationMs).roundToLong().coerceIn(0, durationMs)

        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF15171A)),
        ) {
            Row(Modifier.fillMaxSize()) {
                if (frames.isEmpty()) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF222528)))
                } else {
                    frames.forEach { frame ->
                        Image(
                            bitmap = frame.bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxHeight().weight(1f),
                        )
                    }
                }
            }

            val startX = msToX(trimStartMs)
            val endX = msToX(trimEndMs)
            val playX = msToX(positionMs.coerceIn(trimStartMs, trimEndMs))

            // Scrim over the trimmed-out portions, left and right of the selected range.
            Box(
                Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .width(with(density) { startX.toDp() })
                    .background(Color.Black.copy(alpha = 0.65f)),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .width(with(density) { (widthPx - endX).coerceAtLeast(0f).toDp() })
                    .background(Color.Black.copy(alpha = 0.65f)),
            )

            // Scrub and tap-to-seek across the whole strip.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(durationMs) {
                        detectTapGestures { offset -> onScrubState.value(xToMs(offset.x)) }
                    },
            )

            // Play head.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .graphicsLayer { translationX = playX }
                    .background(Color.White),
            )

            // Start handle.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(HANDLE_WIDTH_DP.dp)
                    .graphicsLayer { translationX = startX - handleWidthPx / 2 }
                    .background(PlexGold, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                    .pointerInput(durationMs) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newStart = xToMs(msToX(startState.value) + dragAmount.x)
                                    .coerceIn(0, endState.value - 200)
                                onTrimChangeState.value(newStart, endState.value)
                            },
                        )
                    },
            )

            // End handle.
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(HANDLE_WIDTH_DP.dp)
                    .graphicsLayer { translationX = endX - handleWidthPx / 2 }
                    .background(PlexGold, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                    .pointerInput(durationMs) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newEnd = xToMs(msToX(endState.value) + dragAmount.x)
                                    .coerceIn(startState.value + 200, durationMs)
                                onTrimChangeState.value(startState.value, newEnd)
                            },
                        )
                    },
            )
        }
    }
}
