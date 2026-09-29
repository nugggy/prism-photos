package au.prism.photos.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

/** Draws the border, rounded corners, polaroid mount, date stamp and watermark around the final
 * (already colour/effect/markup processed) image. Returns a new, larger bitmap. */
object EditorFrameRenderer {

    fun apply(src: Bitmap, frame: FrameSettings, takenAtMillis: Long?): Bitmap {
        val hasBorder = frame.borderPercent > 0f || frame.polaroid
        val hasStamp = frame.dateStampEnabled && takenAtMillis != null
        val hasWatermark = frame.watermarkText.isNotBlank()
        if (!hasBorder && !frame.roundedCorners && !hasStamp && !hasWatermark) return src

        val shortEdge = min(src.width, src.height).toFloat()
        val border = if (frame.polaroid) {
            (0.05f * shortEdge).coerceAtLeast(24f)
        } else {
            (frame.borderPercent / 100f) * shortEdge
        }
        val bottomExtra = if (frame.polaroid) border * 2.2f else 0f
        val cornerRadius = if (frame.roundedCorners) (frame.cornerRadiusPercent / 100f) * shortEdge else 0f

        val outW = (src.width + border * 2).toInt().coerceAtLeast(1)
        val outH = (src.height + border * 2 + bottomExtra).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(frame.borderColorArgb)

        val imageLeft = border
        val imageTop = border
        val imageRect = RectF(imageLeft, imageTop, imageLeft + src.width, imageTop + src.height)

        if (cornerRadius > 0f && !frame.polaroid) {
            val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = android.graphics.Matrix()
            matrix.setTranslate(imageLeft, imageTop)
            shader.setLocalMatrix(matrix)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            canvas.drawRoundRect(imageRect, cornerRadius, cornerRadius, paint)
        } else {
            canvas.drawBitmap(src, imageLeft, imageTop, Paint(Paint.ANTI_ALIAS_FLAG))
        }

        if (hasStamp) {
            drawDateStamp(canvas, imageRect, takenAtMillis!!, frame.dateStampFormat, shortEdge)
        }
        if (hasWatermark) {
            drawWatermark(canvas, RectF(0f, 0f, outW.toFloat(), outH.toFloat()), frame.watermarkText, shortEdge, frame.polaroid)
        }
        return out
    }

    private fun drawDateStamp(canvas: Canvas, imageRect: RectF, takenAtMillis: Long, format: DateStampFormat, shortEdge: Float) {
        val text = try {
            SimpleDateFormat(format.pattern, Locale.getDefault()).format(java.util.Date(takenAtMillis))
        } catch (e: Exception) {
            return
        }
        val textSize = shortEdge * 0.032f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD24C.toInt()
            this.textSize = textSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            setShadowLayer(textSize * 0.15f, 0f, 0f, 0xCC000000.toInt())
        }
        val margin = textSize * 0.9f
        canvas.drawText(text, imageRect.right - margin, imageRect.bottom - margin, paint)
    }

    private fun drawWatermark(canvas: Canvas, fullRect: RectF, text: String, shortEdge: Float, polaroid: Boolean) {
        val textSize = shortEdge * 0.03f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (polaroid) 0xFF3A3A3A.toInt() else 0xB3FFFFFF.toInt()
            this.textSize = textSize
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
            if (!polaroid) setShadowLayer(textSize * 0.2f, 0f, 0f, 0x99000000.toInt())
        }
        val margin = textSize * 1.1f
        canvas.drawText(text, fullRect.centerX(), fullRect.bottom - margin, paint)
    }
}
