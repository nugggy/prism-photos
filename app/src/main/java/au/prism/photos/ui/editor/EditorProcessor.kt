package au.prism.photos.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/**
 * The single image processing pipeline shared by the live (downscaled) preview and the full
 * resolution export: transform (rotate/flip/straighten/crop) -> colour matrix -> pixel effects ->
 * markup -> frame. Keeping one pipeline for both means what you see while editing is exactly what
 * gets saved, just at a different resolution.
 */
object EditorProcessor {

    private const val PREVIEW_REFERENCE_EDGE = 1280f

    /** Rotate90 + flip + straighten, without cropping. Used to size the crop overlay. */
    fun transformBase(src: Bitmap, state: EditState): Bitmap {
        var bmp = src
        val matrix = Matrix()
        if (state.rotationDegrees != 0) matrix.postRotate(state.rotationDegrees.toFloat())
        val sx = if (state.flipHorizontal) -1f else 1f
        val sy = if (state.flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) matrix.postScale(sx, sy)
        if (!matrix.isIdentity) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        if (state.straightenDegrees != 0f) {
            val rot = Matrix().apply { setRotate(state.straightenDegrees, bmp.width / 2f, bmp.height / 2f) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, rot, true)
        }
        return bmp
    }

    /** The centred, no-empty-corner crop rect for the given base (pre-straighten) size and angle,
     * expressed normalised to the resulting (expanded, straightened) bitmap. */
    fun straightenAutoCropRect(baseWidth: Int, baseHeight: Int, angleDegrees: Float, straightenedWidth: Int, straightenedHeight: Int): CropRect {
        if (angleDegrees == 0f) return CropRect()
        val (wr, hr) = EditorEffects.inscribedSize(baseWidth, baseHeight, angleDegrees)
        val left = (straightenedWidth - wr) / 2f / straightenedWidth
        val top = (straightenedHeight - hr) / 2f / straightenedHeight
        return CropRect(left.coerceIn(0f, 0.49f), top.coerceIn(0f, 0.49f), 1f - left.coerceIn(0f, 0.49f), 1f - top.coerceIn(0f, 0.49f))
    }

    fun applyCrop(base: Bitmap, crop: CropRect): Bitmap {
        if (crop == CropRect()) return base
        val cx = (base.width * crop.left).toInt().coerceIn(0, base.width - 1)
        val cy = (base.height * crop.top).toInt().coerceIn(0, base.height - 1)
        val cw = (base.width * crop.width).toInt().coerceAtLeast(1).coerceAtMost(base.width - cx)
        val ch = (base.height * crop.height).toInt().coerceAtLeast(1).coerceAtMost(base.height - cy)
        return Bitmap.createBitmap(base, cx, cy, cw, ch)
    }

    private fun applyColourMatrix(src: Bitmap, state: EditState): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(EditorColour.buildMatrix(state)))
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Full pipeline: transform -> crop -> colour -> pixel effects -> markup -> frame. */
    fun render(src: Bitmap, state: EditState, takenAtMillis: Long?): Bitmap {
        val base = transformBase(src, state)
        val cropped = applyCrop(base, state.crop)
        val imageScale = max(cropped.width, cropped.height) / PREVIEW_REFERENCE_EDGE
        var out = applyColourMatrix(cropped, state)
        out = EditorEffects.applyPixelPasses(out, state.adjustments, imageScale)
        if (state.markup.isNotEmpty()) {
            if (!out.isMutable) out = out.copy(Bitmap.Config.ARGB_8888, true)
            EditorMarkupRenderer.bake(out, state.markup)
        }
        out = EditorFrameRenderer.apply(out, state.frame, takenAtMillis)
        return out
    }

    /** Renders just the transform+crop, no colour/effects/markup/frame, for the "before" compare view. */
    fun renderOriginalForCompare(src: Bitmap, state: EditState): Bitmap {
        val base = transformBase(src, state)
        return applyCrop(base, state.crop)
    }

    /** Histogram-stretch based auto enhance: analyses [sample] (a downscaled bitmap) and returns
     * adjustment overrides. Callers merge these onto the current [Adjustments]. */
    fun autoEnhance(sample: Bitmap): Adjustments {
        val w = sample.width
        val h = sample.height
        val strideX = max(1, w / 200)
        val strideY = max(1, h / 200)
        val hist = IntArray(256)
        var count = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = sample.getPixel(x, y)
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                val lum = ((r * 54 + g * 183 + b * 19) shr 8).coerceIn(0, 255)
                hist[lum]++
                count++
                x += strideX
            }
            y += strideY
        }
        if (count == 0) return Adjustments()
        var cumulative = 0
        var low = 0
        var high = 255
        val lowTarget = count * 0.01f
        val highTarget = count * 0.99f
        for (i in 0..255) {
            cumulative += hist[i]
            if (cumulative >= lowTarget) { low = i; break }
        }
        cumulative = 0
        for (i in 255 downTo 0) {
            cumulative += hist[i]
            if (cumulative >= (count - highTarget)) { high = i; break }
        }
        val range = (high - low).coerceAtLeast(1)
        val contrastDelta = (((255f / range) - 1f) * 55f).coerceIn(0f, 45f)
        val midpoint = (low + high) / 2f
        val brightnessDelta = ((127f - midpoint) / 255f * 35f).coerceIn(-25f, 25f)
        val blacksDelta = (-low / 255f * 30f).coerceIn(-30f, 0f)
        val whitesDelta = ((255f - high) / 255f * 30f).coerceIn(0f, 30f)
        return Adjustments(
            brightness = brightnessDelta,
            contrast = contrastDelta,
            blacks = blacksDelta,
            whites = whitesDelta,
            saturation = 14f,
            vibrance = 10f,
            clarity = 12f,
        )
    }
}
