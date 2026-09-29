package au.prism.photos.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Bakes committed [MarkupElement]s onto a bitmap that already contains the colour/effect
 * processed image. All element geometry is normalised (0..1) relative to the bitmap, so the same
 * elements render correctly on both the downscaled preview and the full resolution export.
 */
object EditorMarkupRenderer {

    fun bake(bitmap: Bitmap, elements: List<MarkupElement>) {
        if (elements.isEmpty()) return
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val shortEdge = min(w, h)
        for (el in elements) {
            when (el) {
                is StrokeElement -> drawStroke(canvas, el, w, h, shortEdge)
                is ShapeElement -> drawShape(canvas, el, w, h, shortEdge)
                is TextElement -> drawText(canvas, el, w, h, shortEdge)
                is StickerElement -> drawSticker(canvas, el, w, h, shortEdge)
                is BlurBrushElement -> drawBlurBrush(bitmap, canvas, el, w, h, shortEdge)
            }
        }
    }

    private fun drawStroke(canvas: Canvas, el: StrokeElement, w: Float, h: Float, shortEdge: Float) {
        if (el.points.size < 2) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = el.colorArgb
            alpha = (el.alpha * 255).toInt().coerceIn(0, 255)
            style = Paint.Style.STROKE
            strokeWidth = el.widthFraction * shortEdge
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        path.moveTo(el.points[0].x * w, el.points[0].y * h)
        for (i in 1 until el.points.size) path.lineTo(el.points[i].x * w, el.points[i].y * h)
        canvas.drawPath(path, paint)
    }

    private fun drawShape(canvas: Canvas, el: ShapeElement, w: Float, h: Float, shortEdge: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = el.colorArgb
            style = Paint.Style.STROKE
            strokeWidth = el.widthFraction * shortEdge
            strokeCap = Paint.Cap.ROUND
        }
        val sx = el.start.x * w; val sy = el.start.y * h
        val ex = el.end.x * w; val ey = el.end.y * h
        when (el.shape) {
            MarkupShape.RECTANGLE -> canvas.drawRect(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey), paint)
            MarkupShape.ELLIPSE -> canvas.drawOval(RectF(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey)), paint)
            MarkupShape.ARROW -> {
                canvas.drawLine(sx, sy, ex, ey, paint)
                val angle = kotlin.math.atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                val headLen = paint.strokeWidth * 4f + 12f
                val a1 = angle - Math.PI / 7
                val a2 = angle + Math.PI / 7
                val headPaint = Paint(paint).apply { style = Paint.Style.FILL }
                val head = Path().apply {
                    moveTo(ex, ey)
                    lineTo((ex - headLen * kotlin.math.cos(a1)).toFloat(), (ey - headLen * kotlin.math.sin(a1)).toFloat())
                    lineTo((ex - headLen * kotlin.math.cos(a2)).toFloat(), (ey - headLen * kotlin.math.sin(a2)).toFloat())
                    close()
                }
                canvas.drawPath(head, headPaint)
            }
        }
    }

    private fun drawText(canvas: Canvas, el: TextElement, w: Float, h: Float, shortEdge: Float) {
        val textSize = el.fontSizeFraction * shortEdge * el.scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = el.colorArgb
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val cx = el.center.x * w
        val cy = el.center.y * h
        canvas.save()
        canvas.rotate(el.rotationDegrees, cx, cy)
        if (el.backgroundPill) {
            val padH = textSize * 0.35f
            val padV = textSize * 0.25f
            val textWidth = paint.measureText(el.text)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
            val fm = paint.fontMetrics
            val top = cy + fm.ascent - padV
            val bottom = cy + fm.descent + padV
            canvas.drawRoundRect(
                RectF(cx - textWidth / 2 - padH, top, cx + textWidth / 2 + padH, bottom),
                (bottom - top) / 2, (bottom - top) / 2, bgPaint,
            )
        }
        canvas.drawText(el.text, cx, cy - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2, paint)
        canvas.restore()
    }

    private fun drawSticker(canvas: Canvas, el: StickerElement, w: Float, h: Float, shortEdge: Float) {
        val size = el.sizeFraction * shortEdge * el.scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            textAlign = Paint.Align.CENTER
        }
        val cx = el.center.x * w
        val cy = el.center.y * h
        canvas.save()
        canvas.rotate(el.rotationDegrees, cx, cy)
        canvas.drawText(el.emoji, cx, cy - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2, paint)
        canvas.restore()
    }

    private fun drawBlurBrush(bitmap: Bitmap, canvas: Canvas, el: BlurBrushElement, w: Float, h: Float, shortEdge: Float) {
        val radiusPx = (el.radiusFraction * shortEdge).toInt().coerceAtLeast(4)
        val blockSize = (radiusPx / 4).coerceAtLeast(4)
        var last: Pair<Int, Int>? = null
        for (p in el.points) {
            val px = (p.x * w).toInt()
            val py = (p.y * h).toInt()
            if (el.pixelate) {
                EditorEffects.pixelateRegion(bitmap, px, py, radiusPx, blockSize)
            } else {
                // Stamp local box blur by blurring a padded crop and drawing it back.
                val left = (px - radiusPx).coerceIn(0, bitmap.width - 1)
                val top = (py - radiusPx).coerceIn(0, bitmap.height - 1)
                val right = (px + radiusPx).coerceIn(left + 1, bitmap.width)
                val bottom = (py + radiusPx).coerceIn(top + 1, bitmap.height)
                val region = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                EditorEffects.stackBlur(region, (radiusPx / 3).coerceAtLeast(3), mutate = true)
                canvas.drawBitmap(region, left.toFloat(), top.toFloat(), null)
            }
            last?.let { (lx, ly) -> if (hypot((px - lx).toDouble(), (py - ly).toDouble()) > radiusPx) { /* dense enough already via UI sampling */ } }
            last = px to py
        }
    }
}
