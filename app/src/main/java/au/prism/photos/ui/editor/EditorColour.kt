package au.prism.photos.ui.editor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds a single 4x5 colour matrix (row major, Android/Compose ColorMatrix layout) combining
 * the selected filter preset (at its strength) with the adjustment sliders that are pure colour
 * operations. Used for both the live preview (as a Compose ColorMatrix) and the final render (as
 * an android.graphics ColorMatrix via ColorMatrixColorFilter). Sharpness/clarity/blur/vignette/
 * grain are handled separately in [EditorEffects] as pixel passes.
 */
object EditorColour {

    fun buildMatrix(state: EditState): FloatArray {
        val a = state.adjustments
        var m = identity()
        m = concat(brightnessMatrix(a.brightness), m)
        m = concat(exposureMatrix(a.exposure), m)
        m = concat(blacksMatrix(a.blacks), m)
        m = concat(shadowsMatrix(a.shadows), m)
        m = concat(highlightsMatrix(a.highlights), m)
        m = concat(whitesMatrix(a.whites), m)
        m = concat(contrastMatrix(a.contrast), m)
        m = concat(fadeMatrix(a.fade), m)
        m = concat(dehazeMatrix(a.dehaze), m)
        m = concat(warmthMatrix(a.warmth), m)
        m = concat(tintMatrix(a.tint), m)
        m = concat(hueRotationMatrix(a.hueShift), m)
        m = concat(saturationMatrix(a.saturation), m)
        m = concat(saturationMatrix(a.vibrance * 0.6f), m)
        m = concat(filterMatrixAtStrength(state.filter, state.filterStrength), m)
        return m
    }

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Combines two 4x5 colour matrices so that [m2] is applied first, then [m1]. */
    fun concat(m1: FloatArray, m2: FloatArray): FloatArray {
        val result = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += m1[row * 5 + k] * m2[k * 5 + col]
                }
                if (col == 4) sum += m1[row * 5 + 4]
                result[row * 5 + col] = sum
            }
        }
        return result
    }

    /** Linearly blends a matrix towards identity by [strengthPercent] (0..100). */
    private fun atStrength(m: FloatArray, strengthPercent: Float): FloatArray {
        if (strengthPercent >= 100f) return m
        val t = (strengthPercent / 100f).coerceIn(0f, 1f)
        val id = identity()
        val out = FloatArray(20)
        for (i in 0 until 20) out[i] = id[i] + (m[i] - id[i]) * t
        return out
    }

    private fun brightnessMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        val b = value * 2.55f
        return floatArrayOf(
            1f, 0f, 0f, 0f, b,
            0f, 1f, 0f, 0f, b,
            0f, 0f, 1f, 0f, b,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun exposureMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        val scale = (1f + value / 100f).coerceAtLeast(0f)
        return floatArrayOf(
            scale, 0f, 0f, 0f, 0f,
            0f, scale, 0f, 0f, 0f,
            0f, 0f, scale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun contrastMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        val c = 1f + value / 100f
        val t = (1f - c) * 128f
        return floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Approximate tonal-range sliders. A single global matrix cannot truly separate tonal ranges,
     * so each is modelled as a small brightness/contrast bias that reads naturally on a slider. */
    private fun highlightsMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        return concat(contrastMatrix(value * 0.25f), brightnessMatrix(value * 0.1f))
    }

    private fun shadowsMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        return concat(brightnessMatrix(value * 0.3f), contrastMatrix(-value * 0.2f))
    }

    private fun whitesMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        return contrastMatrix(value * 0.3f)
    }

    private fun blacksMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        return concat(brightnessMatrix(-value * 0.15f), contrastMatrix(value * 0.15f))
    }

    private fun fadeMatrix(value: Float): FloatArray {
        if (value <= 0f) return identity()
        return concat(brightnessMatrix(value * 0.25f), contrastMatrix(-value * 0.35f))
    }

    private fun dehazeMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        return concat(contrastMatrix(value * 0.35f), saturationMatrix(value * 0.25f))
    }

    fun saturationMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        val s = (1f + value / 100f).coerceAtLeast(0f)
        val rw = 0.213f
        val gw = 0.715f
        val bw = 0.072f
        val sr = (1 - s) * rw
        val sg = (1 - s) * gw
        val sb = (1 - s) * bw
        return floatArrayOf(
            sr + s, sg, sb, 0f, 0f,
            sr, sg + s, sb, 0f, 0f,
            sr, sg, sb + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun warmthMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        val w = value * 0.6f
        return floatArrayOf(
            1f, 0f, 0f, 0f, w,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, -w,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun tintMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        val t = value * 0.6f
        return floatArrayOf(
            1f, 0f, 0f, 0f, t * 0.4f,
            0f, 1f, 0f, 0f, -t,
            0f, 0f, 1f, 0f, t * 0.4f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Standard luminance-preserving hue rotation matrix. [value] is -100..100 mapped to degrees. */
    private fun hueRotationMatrix(value: Float): FloatArray {
        if (value == 0f) return identity()
        val degrees = value * 1.8f
        val radians = degrees * PI.toFloat() / 180f
        val cosA = cos(radians)
        val sinA = sin(radians)
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f
        return floatArrayOf(
            lumR + cosA * (1 - lumR) + sinA * -lumR, lumG + cosA * -lumG + sinA * -lumG, lumB + cosA * -lumB + sinA * (1 - lumB), 0f, 0f,
            lumR + cosA * -lumR + sinA * 0.143f, lumG + cosA * (1 - lumG) + sinA * 0.140f, lumB + cosA * -lumB + sinA * -0.283f, 0f, 0f,
            lumR + cosA * -lumR + sinA * -(1 - lumR), lumG + cosA * -lumG + sinA * lumG, lumB + cosA * (1 - lumB) + sinA * lumB, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun grayscaleMatrix(): FloatArray = saturationMatrix(-100f)

    private fun sepiaMatrix(): FloatArray = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun tealOrangeMatrix(): FloatArray = floatArrayOf(
        1.08f, 0f, 0.05f, 0f, 6f,
        0f, 0.98f, 0.04f, 0f, -4f,
        -0.05f, 0.06f, 1.12f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun filterMatrix(preset: FilterPreset): FloatArray = when (preset) {
        FilterPreset.ORIGINAL -> identity()
        FilterPreset.VIVID -> concat(contrastMatrix(15f), saturationMatrix(45f))
        FilterPreset.WARM -> warmthMatrix(28f)
        FilterPreset.COOL -> warmthMatrix(-28f)
        FilterPreset.MONO -> grayscaleMatrix()
        FilterPreset.SILVER -> concat(contrastMatrix(20f), grayscaleMatrix())
        FilterPreset.SEPIA -> sepiaMatrix()
        FilterPreset.FADE -> concat(brightnessMatrix(10f), contrastMatrix(-25f))
        FilterPreset.NOIR -> concat(contrastMatrix(35f), grayscaleMatrix())
        FilterPreset.MATTE -> concat(brightnessMatrix(6f), concat(contrastMatrix(-18f), saturationMatrix(-8f)))
        FilterPreset.FILM -> concat(warmthMatrix(10f), concat(contrastMatrix(-10f), saturationMatrix(12f)))
        FilterPreset.GOLDEN -> concat(warmthMatrix(35f), concat(brightnessMatrix(8f), saturationMatrix(15f)))
        FilterPreset.TEAL_ORANGE -> tealOrangeMatrix()
        FilterPreset.CINEMATIC -> concat(contrastMatrix(25f), concat(tealOrangeMatrix(), saturationMatrix(-10f)))
        FilterPreset.PASTEL -> concat(brightnessMatrix(12f), concat(contrastMatrix(-20f), saturationMatrix(-20f)))
        FilterPreset.HIGH_KEY -> concat(brightnessMatrix(28f), contrastMatrix(-15f))
        FilterPreset.LOW_KEY -> concat(brightnessMatrix(-22f), contrastMatrix(25f))
        FilterPreset.VINTAGE -> concat(sepiaMatrix(), concat(contrastMatrix(-12f), warmthMatrix(8f)))
    }

    fun filterMatrixAtStrength(preset: FilterPreset, strengthPercent: Float): FloatArray {
        if (preset == FilterPreset.ORIGINAL) return identity()
        return atStrength(filterMatrix(preset), strengthPercent)
    }
}
