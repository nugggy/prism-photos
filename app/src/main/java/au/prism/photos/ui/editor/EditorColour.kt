package au.prism.photos.ui.editor

/**
 * Builds a single 4x5 colour matrix (row major, Android/Compose ColorMatrix layout) combining
 * the selected filter preset with the brightness/contrast/saturation/warmth sliders. Used for
 * both the live preview (as a Compose ColorMatrix) and the final render (as an android.graphics
 * ColorMatrix via ColorMatrixColorFilter).
 */
object EditorColour {

    fun buildMatrix(state: EditState): FloatArray {
        var m = filterMatrix(state.filter)
        m = concat(brightnessMatrix(state.adjustments.brightness), m)
        m = concat(contrastMatrix(state.adjustments.contrast), m)
        m = concat(saturationMatrix(state.adjustments.saturation), m)
        m = concat(warmthMatrix(state.adjustments.warmth), m)
        return m
    }

    private fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Combines two 4x5 colour matrices so that [m2] is applied first, then [m1]. */
    private fun concat(m1: FloatArray, m2: FloatArray): FloatArray {
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

    private fun saturationMatrix(value: Float): FloatArray {
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

    private fun grayscaleMatrix(): FloatArray = saturationMatrix(-100f)

    private fun sepiaMatrix(): FloatArray = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun filterMatrix(preset: FilterPreset): FloatArray = when (preset) {
        FilterPreset.ORIGINAL -> identity()
        FilterPreset.VIVID -> concat(contrastMatrix(15f), saturationMatrix(45f))
        FilterPreset.WARM -> warmthMatrix(28f)
        FilterPreset.COOL -> warmthMatrix(-28f)
        FilterPreset.MONO -> grayscaleMatrix()
        FilterPreset.SEPIA -> sepiaMatrix()
        FilterPreset.FADE -> concat(brightnessMatrix(10f), contrastMatrix(-25f))
        FilterPreset.NOIR -> concat(contrastMatrix(30f), grayscaleMatrix())
    }
}
