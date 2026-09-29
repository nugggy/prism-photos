package au.prism.photos.ui.viewer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * A pannable, pinch to zoom image. Swiping to the next viewer page is only allowed at 1x, so
 * this reports its zoom state through [onSwipeEnabled] rather than consuming pager gestures.
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderModel: Any? = null,
    onTap: () -> Unit = {},
    onSwipeEnabled: (Boolean) -> Unit = {},
) {
    var scale by remember(model) { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current

    fun clamp(newScale: Float, candidate: Offset): Offset {
        val maxX = (containerSize.width * (newScale - 1)) / 2f
        val maxY = (containerSize.height * (newScale - 1)) / 2f
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY),
        )
    }

    val placeholderPainter = if (placeholderModel != null) {
        rememberAsyncImagePainter(
            model = ImageRequest.Builder(context).data(placeholderModel).crossfade(false).build(),
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(model) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapPoint ->
                        if (scale > MIN_SCALE) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                        } else {
                            val target = Offset(
                                (containerSize.width / 2f - tapPoint.x) * (DOUBLE_TAP_SCALE - 1f),
                                (containerSize.height / 2f - tapPoint.y) * (DOUBLE_TAP_SCALE - 1f),
                            )
                            scale = DOUBLE_TAP_SCALE
                            offset = clamp(DOUBLE_TAP_SCALE, target)
                        }
                        onSwipeEnabled(scale <= MIN_SCALE)
                    },
                )
            }
            .pointerInput(model) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val newOffset = if (newScale > MIN_SCALE) {
                        clamp(newScale, offset + pan * scale)
                    } else {
                        Offset.Zero
                    }
                    scale = newScale
                    offset = newOffset
                    onSwipeEnabled(scale <= MIN_SCALE + 0.01f)
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
            placeholder = placeholderPainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
