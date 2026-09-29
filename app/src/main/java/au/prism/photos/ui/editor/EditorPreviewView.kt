package au.prism.photos.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.ui.theme.PlexGold
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The main image area. Shows the fully processed preview by default, the un-cropped transformed
 * base plus a crop overlay while on the Crop tab, and an interactive markup overlay while on the
 * Markup tab. Press and hold anywhere (outside those two tools) to flash the original.
 */
@Composable
fun EditorPreviewView(vm: EditorViewModel, tool: EditorTool, modifier: Modifier = Modifier) {
    val processed by vm.previewProcessed.collectAsStateWithLifecycle()
    val base by vm.previewBase.collectAsStateWithLifecycle()
    val source by vm.sourceBitmap.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val compareOriginal by vm.compareOriginal.collectAsStateWithLifecycle()
    val markupTool by vm.markupTool.collectAsStateWithLifecycle()
    val brushColor by vm.brushColor.collectAsStateWithLifecycle()
    val brushSize by vm.brushSize.collectAsStateWithLifecycle()
    val brushAlpha by vm.brushAlpha.collectAsStateWithLifecycle()
    val pixelateBrush by vm.pixelateBrush.collectAsStateWithLifecycle()

    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier.fillMaxSize().onSizeChanged { boxSize = it },
        contentAlignment = Alignment.Center,
    ) {
        val displayBitmap = when {
            compareOriginal -> source
            tool == EditorTool.CROP -> base
            else -> processed ?: base ?: source
        }
        if (displayBitmap != null) {
            val imageBitmap = remember(displayBitmap) { displayBitmap.asImageBitmap() }
            val aspect = displayBitmap.width.toFloat() / displayBitmap.height.toFloat()
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (tool != EditorTool.CROP && tool != EditorTool.MARKUP) {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    vm.setCompareOriginal(true)
                                    waitForUpOrCancellation()
                                    vm.setCompareOriginal(false)
                                }
                            }
                        } else Modifier,
                    ),
            )

            if (!compareOriginal && tool == EditorTool.CROP && boxSize.width > 0 && boxSize.height > 0) {
                val imageRect = fitRect(boxSize, aspect)
                CropOverlay(vm = vm, imageRect = imageRect, crop = state.crop, cropRatio = state.cropRatio)
            }

            if (!compareOriginal && tool == EditorTool.MARKUP && boxSize.width > 0 && boxSize.height > 0) {
                val imageRect = fitRect(boxSize, aspect)
                MarkupOverlay(
                    vm = vm, imageRect = imageRect, elements = state.markup, tool = markupTool,
                    brushColor = brushColor, brushSize = brushSize, brushAlpha = brushAlpha, pixelateBrush = pixelateBrush,
                )
            }
        }
    }
}

fun fitRect(box: IntSize, aspect: Float): Rect {
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

private enum class Corner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

@Composable
private fun CropOverlay(vm: EditorViewModel, imageRect: Rect, crop: CropRect, cropRatio: Float?) {
    var dragging by remember { mutableStateOf(false) }
    val cropPx = Rect(
        imageRect.left + crop.left * imageRect.width,
        imageRect.top + crop.top * imageRect.height,
        imageRect.left + crop.right * imageRect.width,
        imageRect.top + crop.bottom * imageRect.height,
    )
    val minSizePx = 48f

    fun toNormalised(rect: Rect): CropRect = CropRect(
        ((rect.left - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
        ((rect.top - imageRect.top) / imageRect.height).coerceIn(0f, 1f),
        ((rect.right - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
        ((rect.bottom - imageRect.top) / imageRect.height).coerceIn(0f, 1f),
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = Color.Black.copy(alpha = 0.55f))
            drawRect(color = Color.Transparent, topLeft = cropPx.topLeft, size = cropPx.size, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
            drawRect(color = PlexGold, topLeft = cropPx.topLeft, size = cropPx.size, style = Stroke(width = 2.dp.toPx()))
            if (dragging) {
                val strokeWidth = 1.dp.toPx()
                val thirdW = cropPx.width / 3f
                val thirdH = cropPx.height / 3f
                for (i in 1..2) {
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(cropPx.left + thirdW * i, cropPx.top), Offset(cropPx.left + thirdW * i, cropPx.bottom), strokeWidth = strokeWidth)
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(cropPx.left, cropPx.top + thirdH * i), Offset(cropPx.right, cropPx.top + thirdH * i), strokeWidth = strokeWidth)
                }
            }
        }

        val density = LocalDensity.current
        Box(
            Modifier
                .offset { IntOffset(cropPx.left.roundToInt(), cropPx.top.roundToInt()) }
                .size(with(density) { cropPx.width.toDp() }, with(density) { cropPx.height.toDp() })
                .pointerInput(imageRect, crop) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false; vm.commitCrop(vm.state.value.crop) },
                        onDragCancel = { dragging = false },
                    ) { change, drag ->
                        change.consume()
                        val newLeft = (cropPx.left + drag.x).coerceIn(imageRect.left, imageRect.right - cropPx.width)
                        val newTop = (cropPx.top + drag.y).coerceIn(imageRect.top, imageRect.bottom - cropPx.height)
                        vm.setCropLive(toNormalised(Rect(Offset(newLeft, newTop), cropPx.size)))
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
            val handlePx = with(density) { 28.dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((point.x - handlePx / 2).roundToInt(), (point.y - handlePx / 2).roundToInt()) }
                    .size(28.dp)
                    .pointerInput(imageRect, crop, corner, cropRatio) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false; vm.commitCrop(vm.state.value.crop) },
                            onDragCancel = { dragging = false },
                        ) { change, drag ->
                            change.consume()
                            var left = cropPx.left; var top = cropPx.top; var right = cropPx.right; var bottom = cropPx.bottom
                            if (cropRatio != null) {
                                val anchor = when (corner) {
                                    Corner.TOP_START -> Offset(right, bottom)
                                    Corner.TOP_END -> Offset(left, bottom)
                                    Corner.BOTTOM_START -> Offset(right, top)
                                    Corner.BOTTOM_END -> Offset(left, top)
                                }
                                val moving = when (corner) {
                                    Corner.TOP_START -> Offset(left + drag.x, top + drag.y)
                                    Corner.TOP_END -> Offset(right + drag.x, top + drag.y)
                                    Corner.BOTTOM_START -> Offset(left + drag.x, bottom + drag.y)
                                    Corner.BOTTOM_END -> Offset(right + drag.x, bottom + drag.y)
                                }
                                var w = abs(moving.x - anchor.x).coerceAtLeast(minSizePx)
                                var h = abs(moving.y - anchor.y).coerceAtLeast(minSizePx)
                                if (w / h > cropRatio) w = h * cropRatio else h = w / cropRatio
                                val maxW = if (moving.x >= anchor.x) imageRect.right - anchor.x else anchor.x - imageRect.left
                                val maxH = if (moving.y >= anchor.y) imageRect.bottom - anchor.y else anchor.y - imageRect.top
                                if (w > maxW) { w = maxW; h = w / cropRatio }
                                if (h > maxH) { h = maxH; w = h * cropRatio }
                                val signX = if (moving.x >= anchor.x) 1f else -1f
                                val signY = if (moving.y >= anchor.y) 1f else -1f
                                val newX = anchor.x + signX * w
                                val newY = anchor.y + signY * h
                                left = min(anchor.x, newX); right = max(anchor.x, newX)
                                top = min(anchor.y, newY); bottom = max(anchor.y, newY)
                            } else {
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
                            }
                            vm.setCropLive(toNormalised(Rect(left, top, right, bottom)))
                        }
                    },
            ) {
                Box(Modifier.align(Alignment.Center).size(14.dp).border(3.dp, PlexGold, RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
private fun MarkupOverlay(
    vm: EditorViewModel,
    imageRect: Rect,
    elements: List<MarkupElement>,
    tool: MarkupTool,
    brushColor: Color,
    brushSize: Float,
    brushAlpha: Float,
    pixelateBrush: Boolean,
) {
    val shortEdgePx = min(imageRect.width, imageRect.height)
    var inProgressPoints by remember { mutableStateOf(listOf<Offset>()) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember { mutableStateOf<Offset?>(null) }

    fun toNorm(p: Offset): Offset = Offset(((p.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f), ((p.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f))
    fun toPx(p: Offset): Offset = Offset(imageRect.left + p.x * imageRect.width, imageRect.top + p.y * imageRect.height)

    val isStrokeTool = tool == MarkupTool.PEN || tool == MarkupTool.HIGHLIGHTER || tool == MarkupTool.BLUR_BRUSH
    val isShapeTool = tool == MarkupTool.ARROW || tool == MarkupTool.RECTANGLE || tool == MarkupTool.ELLIPSE
    val isEraser = tool == MarkupTool.ERASER

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(tool, brushColor, brushSize, pixelateBrush) {
                    if (!isStrokeTool && !isShapeTool && !isEraser) return@pointerInput
                    detectDragGestures(
                        onDragStart = { start ->
                            when {
                                isStrokeTool -> inProgressPoints = listOf(toNorm(start))
                                isShapeTool -> { shapeStart = toNorm(start); shapeEnd = toNorm(start) }
                                isEraser -> eraseAt(vm, toNorm(start), 0.05f)
                            }
                        },
                        onDragEnd = {
                            if (isStrokeTool && inProgressPoints.size >= 2) {
                                val id = vm.nextMarkupId()
                                if (tool == MarkupTool.BLUR_BRUSH) {
                                    vm.addMarkupElement(BlurBrushElement(id, inProgressPoints, brushSize / 500f + 0.03f, pixelate = pixelateBrush))
                                } else {
                                    vm.addMarkupElement(
                                        StrokeElement(id, inProgressPoints, brushColor.toArgb(), widthFraction = brushSize / 1000f, alpha = if (tool == MarkupTool.HIGHLIGHTER) brushAlpha else 1f),
                                    )
                                }
                            } else if (isShapeTool && shapeStart != null && shapeEnd != null) {
                                val shape = when (tool) {
                                    MarkupTool.ARROW -> MarkupShape.ARROW
                                    MarkupTool.RECTANGLE -> MarkupShape.RECTANGLE
                                    else -> MarkupShape.ELLIPSE
                                }
                                vm.addMarkupElement(ShapeElement(vm.nextMarkupId(), shape, shapeStart!!, shapeEnd!!, brushColor.toArgb(), brushSize / 1000f))
                            }
                            inProgressPoints = emptyList(); shapeStart = null; shapeEnd = null
                        },
                        onDragCancel = { inProgressPoints = emptyList(); shapeStart = null; shapeEnd = null },
                    ) { change, _ ->
                        change.consume()
                        val p = change.position
                        when {
                            isStrokeTool -> inProgressPoints = inProgressPoints + toNorm(p)
                            isShapeTool -> shapeEnd = toNorm(p)
                            isEraser -> eraseAt(vm, toNorm(p), 0.05f)
                        }
                    }
                },
        ) {
            if (inProgressPoints.size >= 2) {
                val path = androidx.compose.ui.graphics.Path()
                val pts = inProgressPoints.map { toPx(it) }
                path.moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { path.lineTo(it.x, it.y) }
                drawPath(
                    path,
                    color = if (tool == MarkupTool.BLUR_BRUSH) Color.White.copy(alpha = 0.5f) else brushColor.copy(alpha = if (tool == MarkupTool.HIGHLIGHTER) brushAlpha else 1f),
                    style = Stroke(width = brushSize / 1000f * shortEdgePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            val ss = shapeStart; val se = shapeEnd
            if (ss != null && se != null) {
                val a = toPx(ss); val b = toPx(se)
                val stroke = Stroke(width = brushSize / 1000f * shortEdgePx)
                when (tool) {
                    MarkupTool.RECTANGLE -> drawRect(brushColor, topLeft = Offset(min(a.x, b.x), min(a.y, b.y)), size = Size(abs(b.x - a.x), abs(b.y - a.y)), style = stroke)
                    MarkupTool.ELLIPSE -> drawOval(brushColor, topLeft = Offset(min(a.x, b.x), min(a.y, b.y)), size = Size(abs(b.x - a.x), abs(b.y - a.y)), style = stroke)
                    MarkupTool.ARROW -> drawLine(brushColor, a, b, strokeWidth = stroke.width)
                    else -> {}
                }
            }
        }

        elements.forEach { el ->
            when (el) {
                is TextElement -> androidx.compose.runtime.key(el.id) {
                    DraggableMarkupHandle(imageRect, el.center) { center, scale, rotation ->
                        vm.commitMarkupElement(el.copy(center = center, scale = (el.scale * scale).coerceIn(0.3f, 4f), rotationDegrees = el.rotationDegrees + rotation))
                    }
                }
                is StickerElement -> androidx.compose.runtime.key(el.id) {
                    DraggableMarkupHandle(imageRect, el.center) { center, scale, rotation ->
                        vm.commitMarkupElement(el.copy(center = center, scale = (el.scale * scale).coerceIn(0.3f, 4f), rotationDegrees = el.rotationDegrees + rotation))
                    }
                }
                else -> {}
            }
        }
    }
}

private fun eraseAt(vm: EditorViewModel, point: Offset, radius: Float) {
    val hit = vm.state.value.markup.filter { el ->
        when (el) {
            is TextElement -> (el.center - point).getDistance() < radius * 2.5f
            is StickerElement -> (el.center - point).getDistance() < radius * 2.5f
            is StrokeElement -> el.points.any { (it - point).getDistance() < radius }
            is ShapeElement -> (el.start - point).getDistance() < radius || (el.end - point).getDistance() < radius
            is BlurBrushElement -> el.points.any { (it - point).getDistance() < radius }
        }
    }.map { it.id }.toSet()
    if (hit.isNotEmpty()) vm.removeMarkupElements(hit)
}

@Composable
private fun DraggableMarkupHandle(
    imageRect: Rect,
    center: Offset,
    onTransform: (center: Offset, scale: Float, rotationDegrees: Float) -> Unit,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { 64.dp.toPx() }
    val px = Offset(imageRect.left + center.x * imageRect.width, imageRect.top + center.y * imageRect.height)
    Box(
        Modifier
            .offset { IntOffset((px.x - sizePx / 2).roundToInt(), (px.y - sizePx / 2).roundToInt()) }
            .size(64.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    val newCenterPx = Offset(px.x + pan.x, px.y + pan.y)
                    val newCenterNorm = Offset(
                        ((newCenterPx.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f),
                        ((newCenterPx.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f),
                    )
                    onTransform(newCenterNorm, zoom, rotation)
                }
            },
    )
}
