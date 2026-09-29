package au.prism.photos.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Pixel and convolution level effects: sharpen/clarity, blur, vignette, grain, pixelate/blur brush
 * and the straighten auto-crop maths. Operates in place on ARGB_8888 bitmaps. */
object EditorEffects {

    /** Applies blur, then sharpen/clarity, then vignette, then grain, in that order, returning a
     * new bitmap (or the same one if every amount is zero). [imageScale] is the ratio of this
     * bitmap's size to the full working image, used to keep radii visually consistent between the
     * downscaled preview and the full resolution export. */
    fun applyPixelPasses(src: Bitmap, a: Adjustments, imageScale: Float): Bitmap {
        var bmp = src
        if (a.blur > 0f) {
            val radius = ((a.blur / 100f) * 18f * imageScale).roundToInt().coerceIn(1, 40)
            bmp = stackBlur(bmp, radius, mutate = bmp !== src)
        }
        if (a.sharpness != 0f) {
            bmp = unsharpMask(bmp, radius = max(1, (2 * imageScale).roundToInt()), amount = a.sharpness / 100f, mutate = bmp !== src)
        }
        if (a.clarity != 0f) {
            bmp = unsharpMask(bmp, radius = max(2, (8 * imageScale).roundToInt()), amount = a.clarity / 100f * 0.6f, mutate = bmp !== src)
        }
        if (a.vignetteStrength > 0f) {
            bmp = applyVignette(bmp, a.vignetteStrength / 100f, a.vignetteSoftness / 100f, mutate = bmp !== src)
        }
        if (a.grain > 0f) {
            bmp = applyGrain(bmp, a.grain / 100f, mutate = bmp !== src)
        }
        return bmp
    }

    private fun ensureMutable(bmp: Bitmap, mutate: Boolean): Bitmap =
        if (mutate && bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true)

    /** Classic Mario Klingemann stack blur: linear-time, radius independent per pixel. */
    fun stackBlur(src: Bitmap, radius: Int, mutate: Boolean = false): Bitmap {
        if (radius < 1) return src
        val bmp = ensureMutable(src, mutate)
        val w = bmp.width
        val h = bmp.height
        val pix = IntArray(w * h)
        bmp.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val div = radius + radius + 1
        val r = IntArray(w * h)
        val g = IntArray(w * h)
        val b = IntArray(w * h)
        var rSum: Int
        var gSum: Int
        var bSum: Int
        val vMin = IntArray(max(w, h))
        var divSum = (div + 1) shr 1
        divSum *= divSum
        val dv = IntArray(256 * divSum)
        for (i in dv.indices) dv[i] = i / divSum

        var yw = 0
        var yi = 0
        val stackR = IntArray(div)
        val stackG = IntArray(div)
        val stackB = IntArray(div)

        for (y in 0 until h) {
            rSum = 0; gSum = 0; bSum = 0
            var rOutSum = 0; var gOutSum = 0; var bOutSum = 0
            var rInSum = 0; var gInSum = 0; var bInSum = 0
            for (i in -radius..radius) {
                val p = pix[yi + min(wm, max(i, 0))]
                val sir = (p shr 16) and 0xff
                val sig = (p shr 8) and 0xff
                val sib = p and 0xff
                val rbs = radius + 1 - abs(i)
                stackR[i + radius] = sir; stackG[i + radius] = sig; stackB[i + radius] = sib
                rSum += sir * rbs; gSum += sig * rbs; bSum += sib * rbs
                if (i > 0) { rInSum += sir; gInSum += sig; bInSum += sib } else { rOutSum += sir; gOutSum += sig; bOutSum += sib }
            }
            var stackPointer = radius
            for (x in 0 until w) {
                r[yi] = dv[rSum]; g[yi] = dv[gSum]; b[yi] = dv[bSum]
                rSum -= rOutSum; gSum -= gOutSum; bSum -= bOutSum
                var stackStart = stackPointer - radius + div
                var sp = stackStart % div
                rOutSum -= stackR[sp]; gOutSum -= stackG[sp]; bOutSum -= stackB[sp]
                if (y == 0) vMin[x] = min(x + radius + 1, wm)
                val p2 = pix[yw + vMin[x]]
                stackR[sp] = (p2 shr 16) and 0xff
                stackG[sp] = (p2 shr 8) and 0xff
                stackB[sp] = p2 and 0xff
                rInSum += stackR[sp]; gInSum += stackG[sp]; bInSum += stackB[sp]
                rSum += rInSum; gSum += gInSum; bSum += bInSum
                stackPointer = (stackPointer + 1) % div
                val sp2 = stackPointer % div
                rOutSum += stackR[sp2]; gOutSum += stackG[sp2]; bOutSum += stackB[sp2]
                rInSum -= stackR[sp2]; gInSum -= stackG[sp2]; bInSum -= stackB[sp2]
                yi++
            }
            yw += w
        }

        for (x in 0 until w) {
            rSum = 0; gSum = 0; bSum = 0
            var rOutSum = 0; var gOutSum = 0; var bOutSum = 0
            var rInSum = 0; var gInSum = 0; var bInSum = 0
            var yp = -radius * w
            for (i in -radius..radius) {
                val yi2 = max(0, yp) + x
                stackR[i + radius] = r[yi2]; stackG[i + radius] = g[yi2]; stackB[i + radius] = b[yi2]
                val rbs = radius + 1 - abs(i)
                rSum += r[yi2] * rbs; gSum += g[yi2] * rbs; bSum += b[yi2] * rbs
                if (i > 0) { rInSum += r[yi2]; gInSum += g[yi2]; bInSum += b[yi2] } else { rOutSum += r[yi2]; gOutSum += g[yi2]; bOutSum += b[yi2] }
                if (i < hm) yp += w
            }
            yi = x
            var stackPointer = radius
            for (y in 0 until h) {
                val a = (pix[yi] ushr 24) and 0xff
                pix[yi] = (a shl 24) or (dv[rSum] shl 16) or (dv[gSum] shl 8) or dv[bSum]
                rSum -= rOutSum; gSum -= gOutSum; bSum -= bOutSum
                var stackStart = stackPointer - radius + div
                var sp = stackStart % div
                rOutSum -= stackR[sp]; gOutSum -= stackG[sp]; bOutSum -= stackB[sp]
                val yi2 = x + min(y + radius + 1, hm) * w
                stackR[sp] = r[yi2]; stackG[sp] = g[yi2]; stackB[sp] = b[yi2]
                rInSum += stackR[sp]; gInSum += stackG[sp]; bInSum += stackB[sp]
                rSum += rInSum; gSum += gInSum; bSum += bInSum
                stackPointer = (stackPointer + 1) % div
                val sp2 = stackPointer % div
                rOutSum += stackR[sp2]; gOutSum += stackG[sp2]; bOutSum += stackB[sp2]
                rInSum -= stackR[sp2]; gInSum -= stackG[sp2]; bInSum -= stackB[sp2]
                yi += w
            }
        }

        bmp.setPixels(pix, 0, w, 0, 0, w, h)
        return bmp
    }

    /** Sharpen/clarity via unsharp mask: result = src + (src - blur(src)) * amount. */
    private fun unsharpMask(src: Bitmap, radius: Int, amount: Float, mutate: Boolean): Bitmap {
        if (amount == 0f) return src
        val bmp = ensureMutable(src, mutate)
        val w = bmp.width
        val h = bmp.height
        val original = IntArray(w * h)
        bmp.getPixels(original, 0, w, 0, 0, w, h)
        val blurred = bmp.copy(Bitmap.Config.ARGB_8888, true)
        stackBlur(blurred, radius, mutate = true)
        val blurPix = IntArray(w * h)
        blurred.getPixels(blurPix, 0, w, 0, 0, w, h)

        val out = IntArray(w * h)
        for (i in 0 until w * h) {
            val o = original[i]
            val bl = blurPix[i]
            val a = (o ushr 24) and 0xff
            val or_ = (o shr 16) and 0xff; val og = (o shr 8) and 0xff; val ob = o and 0xff
            val br = (bl shr 16) and 0xff; val bg = (bl shr 8) and 0xff; val bb = bl and 0xff
            val nr = (or_ + (or_ - br) * amount).roundToInt().coerceIn(0, 255)
            val ng = (og + (og - bg) * amount).roundToInt().coerceIn(0, 255)
            val nb = (ob + (ob - bb) * amount).roundToInt().coerceIn(0, 255)
            out[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        blurred.recycle()
        return bmp
    }

    private fun applyVignette(src: Bitmap, strength: Float, softness: Float, mutate: Boolean): Bitmap {
        val bmp = ensureMutable(src, mutate)
        val w = bmp.width
        val h = bmp.height
        val canvas = Canvas(bmp)
        val cx = w / 2f
        val cy = h / 2f
        val radius = max(w, h) * (0.75f + (1f - softness) * 0.15f)
        val innerStop = (0.35f + softness * 0.5f).coerceIn(0.1f, 0.95f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(0x00000000, 0x00000000, (((strength * 200).roundToInt().coerceIn(0, 255)) shl 24)),
                floatArrayOf(0f, innerStop, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    private fun applyGrain(src: Bitmap, amount: Float, mutate: Boolean): Bitmap {
        val bmp = ensureMutable(src, mutate)
        val w = bmp.width
        val h = bmp.height
        val pix = IntArray(w * h)
        bmp.getPixels(pix, 0, w, 0, 0, w, h)
        val strength = (amount * 40f).roundToInt()
        val random = Random(0)
        for (i in pix.indices) {
            val p = pix[i]
            val noise = random.nextInt(-strength, strength + 1)
            val a = (p ushr 24) and 0xff
            val r = ((p shr 16) and 0xff) + noise
            val g = ((p shr 8) and 0xff) + noise
            val b = (p and 0xff) + noise
            pix[i] = (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h)
        return bmp
    }

    /** Pixelates a square region of [bmp] centred at ([cx],[cy]) with the given pixel [blockSize]. */
    fun pixelateRegion(bmp: Bitmap, cx: Int, cy: Int, radius: Int, blockSize: Int) {
        val left = (cx - radius).coerceIn(0, bmp.width - 1)
        val top = (cy - radius).coerceIn(0, bmp.height - 1)
        val right = (cx + radius).coerceIn(0, bmp.width - 1)
        val bottom = (cy + radius).coerceIn(0, bmp.height - 1)
        var by = top
        while (by <= bottom) {
            var bx = left
            while (bx <= right) {
                val blockRight = min(bx + blockSize - 1, right)
                val blockBottom = min(by + blockSize - 1, bottom)
                var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0L
                var yy = by
                while (yy <= blockBottom) {
                    var xx = bx
                    while (xx <= blockRight) {
                        val p = bmp.getPixel(xx, yy)
                        rSum += (p shr 16) and 0xff; gSum += (p shr 8) and 0xff; bSum += p and 0xff
                        count++
                        xx++
                    }
                    yy++
                }
                if (count > 0) {
                    val avg = (0xff shl 24) or (((rSum / count).toInt()) shl 16) or (((gSum / count).toInt()) shl 8) or (bSum / count).toInt()
                    var yy2 = by
                    while (yy2 <= blockBottom) {
                        var xx2 = bx
                        while (xx2 <= blockRight) {
                            bmp.setPixel(xx2, yy2, avg)
                            xx2++
                        }
                        yy2++
                    }
                }
                bx += blockSize
            }
            by += blockSize
        }
    }

    /**
     * Size (in pixels, within the original w x h axis) of the largest axis-aligned rectangle that
     * fits entirely inside a w x h image once it has been rotated by [angleDegrees] about its own
     * centre, leaving no empty corners. Classic "rotate then crop" formula.
     */
    fun inscribedSize(w: Int, h: Int, angleDegrees: Float): Pair<Float, Float> {
        if (angleDegrees == 0f) return w.toFloat() to h.toFloat()
        val angle = abs(angleDegrees) * kotlin.math.PI.toFloat() / 180f
        val widthIsLonger = w >= h
        val sideLong = if (widthIsLonger) w.toFloat() else h.toFloat()
        val sideShort = if (widthIsLonger) h.toFloat() else w.toFloat()
        val sinA = abs(sin(angle))
        val cosA = abs(cos(angle))
        var wr: Float
        var hr: Float
        if (sideShort <= 2f * sinA * cosA * sideLong || abs(sinA - cosA) < 1e-6f) {
            val x = 0.5f * sideShort
            if (widthIsLonger) {
                wr = x / sinA; hr = x / cosA
            } else {
                wr = x / cosA; hr = x / sinA
            }
        } else {
            val cos2a = cosA * cosA - sinA * sinA
            wr = (w * cosA - h * sinA) / cos2a
            hr = (h * cosA - w * sinA) / cos2a
        }
        wr = wr.coerceIn(1f, w.toFloat())
        hr = hr.coerceIn(1f, h.toFloat())
        return wr to hr
    }

    /** Bounding box size of a w x h rectangle after rotating it by [angleDegrees] about its centre. */
    fun expandedSize(w: Int, h: Int, angleDegrees: Float): Pair<Float, Float> {
        if (angleDegrees == 0f) return w.toFloat() to h.toFloat()
        val angle = angleDegrees * kotlin.math.PI.toFloat() / 180f
        val sinA = abs(sin(angle))
        val cosA = abs(cos(angle))
        val ew = w * cosA + h * sinA
        val eh = w * sinA + h * cosA
        return ew to eh
    }
}
