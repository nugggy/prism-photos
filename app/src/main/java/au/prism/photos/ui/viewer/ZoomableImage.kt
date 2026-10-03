package au.prism.photos.ui.viewer

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val ORIGINAL_RETRIES = 2

private fun logLoadFailure(which: String, model: Any?, error: Throwable) {
    val where = model.toString().replace(Regex("X-Plex-Token=[^&]+"), "X-Plex-Token=…")
    Log.w("PrismViewer", "$which failed: $where", error)
}

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

    // The preview sits underneath the original until the original has drawn, so a slow or
    // failed original load still leaves the preview on screen instead of a blank page.
    var originalLoaded by remember(model) { mutableStateOf(false) }

    // The NAS sometimes drops the connection part way through a file. Reload the original a
    // couple of times before settling for the preview.
    var attempt by remember(model) { mutableIntStateOf(0) }
    var failedAttempt by remember(model) { mutableIntStateOf(-1) }
    LaunchedEffect(failedAttempt) {
        if (failedAttempt >= 0 && failedAttempt < ORIGINAL_RETRIES) {
            delay(1000L * (failedAttempt + 1))
            attempt = failedAttempt + 1
        }
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
                // Only claim the gesture for a pinch, or a pan while zoomed in. A one finger drag
                // at 1x is left unconsumed so the pager can swipe and the viewer can drag to close.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || scale > MIN_SCALE) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(MIN_SCALE, MAX_SCALE)
                            offset = if (newScale > MIN_SCALE) {
                                clamp(newScale, offset + event.calculatePan() * scale)
                            } else {
                                Offset.Zero
                            }
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                            onSwipeEnabled(scale <= MIN_SCALE + 0.01f)
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val transformed = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        if (placeholderModel != null && !originalLoaded) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(placeholderModel).crossfade(false).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { logLoadFailure("preview", placeholderModel, it.result.throwable) },
                modifier = transformed,
            )
        }
        key(attempt) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(model).crossfade(false).build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                onSuccess = { originalLoaded = true },
                onError = {
                    logLoadFailure("original (attempt ${attempt + 1})", model, it.result.throwable)
                    failedAttempt = attempt
                },
                modifier = transformed,
            )
        }
    }
}
