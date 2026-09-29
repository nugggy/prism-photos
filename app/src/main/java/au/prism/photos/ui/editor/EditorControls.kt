package au.prism.photos.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import au.prism.photos.ui.theme.PlexGold
import kotlin.math.roundToInt

enum class EditorTool(val label: String) { CROP("Crop"), ROTATE("Rotate"), ADJUST("Adjust"), FILTERS("Filters") }

private val cropRatios: List<Pair<String, Float?>> = listOf(
    "Free" to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:2" to 3f / 2f, "16:9" to 16f / 9f, "9:16" to 9f / 16f,
)

/** The image preview with a live ColorMatrix filter and, in crop mode, a draggable crop overlay. */
@Composable
fun EditorPreview(
    bitmap: Bitmap,
    state: EditState,
    tool: EditorTool,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val matrix = remember(state.adjustments, state.filter) {
        androidx.compose.ui.graphics.ColorMatrix(EditorColour.buildMatrix(state))
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.colorMatrix(matrix),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = state.rotationDegrees.toFloat()
                    scaleX = if (state.flipHorizontal) -1f else 1f
                    scaleY = if (state.flipVertical) -1f else 1f
                },
        )

        if (tool == EditorTool.CROP && boxSize.width > 0 && boxSize.height > 0) {
            val imageRect = fitRect(boxSize, bitmapAspect)
            CropOverlay(imageRect = imageRect, crop = state.crop, onCropChange = onCropChange)
        }
    }
}

private fun fitRect(box: IntSize, aspect: Float): Rect {
    val boxAspect = box.width.toFloat() / box.height.toFloat()
    return if (boxAspect > aspect) {
        val w = box.height * aspect
        val left = (box.width - w) / 2f
        Rect(left, 0f, left + w, box.height.toFloat())
    } else {
        val h = box.width / aspect
        val top = (box.height - h) / 2f
        Rect(0f, top, box.width.toFloat(), top + h)
    }
}

@Composable
private fun CropOverlay(imageRect: Rect, crop: CropRect, onCropChange: (CropRect) -> Unit) {
    val cropPx = Rect(
        imageRect.left + crop.left * imageRect.width,
        imageRect.top + crop.top * imageRect.height,
        imageRect.left + crop.right * imageRect.width,
        imageRect.top + crop.bottom * imageRect.height,
    )
    val minSizePx = 40f

    fun toNormalised(rect: Rect): CropRect = CropRect(
        ((rect.left - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
        ((rect.top - imageRect.top) / imageRect.height).coerceIn(0f, 1f),
        ((rect.right - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
        ((rect.bottom - imageRect.top) / imageRect.height).coerceIn(0f, 1f),
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = Color.Black.copy(alpha = 0.55f))
            drawRect(
                color = Color.Transparent,
                topLeft = cropPx.topLeft,
                size = cropPx.size,
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
            )
        }

        // Move the whole rectangle.
        Box(
            Modifier
                .offset { IntOffset(cropPx.left.roundToInt(), cropPx.top.roundToInt()) }
                .size(
                    with(androidx.compose.ui.platform.LocalDensity.current) { cropPx.width.toDp() },
                    with(androidx.compose.ui.platform.LocalDensity.current) { cropPx.height.toDp() },
                )
                .border(2.dp, PlexGold)
                .pointerInput(imageRect, crop) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val newLeft = (cropPx.left + drag.x).coerceIn(imageRect.left, imageRect.right - cropPx.width)
                        val newTop = (cropPx.top + drag.y).coerceIn(imageRect.top, imageRect.bottom - cropPx.height)
                        onCropChange(toNormalised(Rect(Offset(newLeft, newTop), cropPx.size)))
                    }
                },
        )

        val handles = listOf(
            Corner.TOP_START to Offset(cropPx.left, cropPx.top),
            Corner.TOP_END to Offset(cropPx.right, cropPx.top),
            Corner.BOTTOM_START to Offset(cropPx.left, cropPx.bottom),
            Corner.BOTTOM_END to Offset(cropPx.right, cropPx.bottom),
        )
        handles.forEach { (corner, point) ->
            val density = androidx.compose.ui.platform.LocalDensity.current
            val handlePx = with(density) { 28.dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((point.x - handlePx / 2).roundToInt(), (point.y - handlePx / 2).roundToInt()) }
                    .size(28.dp)
                    .pointerInput(imageRect, crop, corner) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            var left = cropPx.left
                            var top = cropPx.top
                            var right = cropPx.right
                            var bottom = cropPx.bottom
                            when (corner) {
                                Corner.TOP_START -> {
                                    left = (left + drag.x).coerceIn(imageRect.left, right - minSizePx)
                                    top = (top + drag.y).coerceIn(imageRect.top, bottom - minSizePx)
                                }
                                Corner.TOP_END -> {
                                    right = (right + drag.x).coerceIn(left + minSizePx, imageRect.right)
                                    top = (top + drag.y).coerceIn(imageRect.top, bottom - minSizePx)
                                }
                                Corner.BOTTOM_START -> {
                                    left = (left + drag.x).coerceIn(imageRect.left, right - minSizePx)
                                    bottom = (bottom + drag.y).coerceIn(top + minSizePx, imageRect.bottom)
                                }
                                Corner.BOTTOM_END -> {
                                    right = (right + drag.x).coerceIn(left + minSizePx, imageRect.right)
                                    bottom = (bottom + drag.y).coerceIn(top + minSizePx, imageRect.bottom)
                                }
                            }
                            onCropChange(toNormalised(Rect(left, top, right, bottom)))
                        }
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(14.dp)
                        .border(3.dp, PlexGold, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

private enum class Corner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

@Composable
fun CropControls(state: EditState, bitmapAspect: Float, onRatioSelected: (Float?, CropRect) -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cropRatios) { (label, ratio) ->
                FilterChip(
                    selected = state.cropRatio == ratio,
                    onClick = { onRatioSelected(ratio, centeredRectFor(bitmapAspect, ratio)) },
                    label = { Text(label) },
                )
            }
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" Reset crop")
        }
    }
}

private fun centeredRectFor(bitmapAspect: Float, ratio: Float?): CropRect {
    if (ratio == null) return CropRect()
    return if (ratio > bitmapAspect) {
        // Target wider than the image: full width, centred height.
        val h = bitmapAspect / ratio
        val top = (1f - h) / 2f
        CropRect(0f, top, 1f, top + h)
    } else {
        val w = ratio / bitmapAspect
        val left = (1f - w) / 2f
        CropRect(left, 0f, left + w, 1f)
    }
}

@Composable
fun RotateControls(onRotate: () -> Unit, onFlipHorizontal: () -> Unit, onFlipVertical: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LabelledAction(Icons.Filled.Rotate90DegreesCcw, "Rotate", onRotate)
        LabelledAction(Icons.Filled.Flip, "Flip horizontal", onFlipHorizontal)
        LabelledAction(Icons.Filled.Flip, "Flip vertical", onFlipVertical)
    }
}

@Composable
private fun LabelledAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AdjustControls(adjustments: Adjustments, onChange: (Adjustments) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        AdjustSlider("Brightness", adjustments.brightness) { onChange(adjustments.copy(brightness = it)) }
        AdjustSlider("Contrast", adjustments.contrast) { onChange(adjustments.copy(contrast = it)) }
        AdjustSlider("Saturation", adjustments.saturation) { onChange(adjustments.copy(saturation = it)) }
        AdjustSlider("Warmth", adjustments.warmth) { onChange(adjustments.copy(warmth = it)) }
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, onValueChangeFinished: (Float) -> Unit) {
    var current by remember(value) { mutableStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(current.roundToInt().toString(), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onValueChangeFinished(current) },
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
        )
    }
}

@Composable
fun FilterControls(bitmap: Bitmap, selected: FilterPreset, onSelect: (FilterPreset) -> Unit) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(FilterPreset.entries) { preset ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(64.dp)
                        .border(
                            width = if (preset == selected) 2.dp else 0.dp,
                            color = if (preset == selected) PlexGold else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(preset) },
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = preset.label,
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(
                                EditorColour.buildMatrix(EditState(filter = preset)),
                            ),
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(preset.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
