package au.prism.photos.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/** Normalised (0..1) crop rectangle relative to the working bitmap. */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * All slider based colour and effect adjustments. The first block (brightness through dehaze)
 * is applied as a single combined ColorMatrix (cheap, GPU friendly for live preview). The second
 * block (sharpness through grain) is applied as pixel/convolution passes over the bitmap.
 */
@Serializable
data class Adjustments(
    val brightness: Float = 0f,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val hueShift: Float = 0f,
    val fade: Float = 0f,
    val dehaze: Float = 0f,
    val sharpness: Float = 0f,
    val clarity: Float = 0f,
    val blur: Float = 0f,
    val vignetteStrength: Float = 0f,
    val vignetteSoftness: Float = 50f,
    val grain: Float = 0f,
) {
    /** True when every field is at its default, i.e. no adjustment is applied. */
    fun isIdentity(): Boolean = this == Adjustments()
}

@Serializable
enum class FilterPreset(val label: String) {
    ORIGINAL("Original"), VIVID("Vivid"), WARM("Warm"), COOL("Cool"),
    MONO("Mono"), SILVER("Silver"), SEPIA("Sepia"), FADE("Fade"),
    NOIR("Noir"), MATTE("Matte"), FILM("Film"), GOLDEN("Golden"),
    TEAL_ORANGE("Teal and orange"), CINEMATIC("Cinematic"), PASTEL("Pastel"),
    HIGH_KEY("High key"), LOW_KEY("Low key"), VINTAGE("Vintage"),
}

enum class MarkupTool { NONE, PEN, HIGHLIGHTER, ARROW, RECTANGLE, ELLIPSE, TEXT, EMOJI, BLUR_BRUSH, ERASER }

enum class MarkupShape { ARROW, RECTANGLE, ELLIPSE }

sealed class MarkupElement {
    abstract val id: Long
}

/** Freehand pen/highlighter stroke. Points and width are normalised to the image's short edge. */
data class StrokeElement(
    override val id: Long,
    val points: List<Offset>,
    val colorArgb: Int,
    val widthFraction: Float,
    val alpha: Float = 1f,
) : MarkupElement()

data class ShapeElement(
    override val id: Long,
    val shape: MarkupShape,
    val start: Offset,
    val end: Offset,
    val colorArgb: Int,
    val widthFraction: Float,
) : MarkupElement()

data class TextElement(
    override val id: Long,
    val text: String,
    val center: Offset,
    val fontSizeFraction: Float,
    val colorArgb: Int,
    val backgroundPill: Boolean,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
) : MarkupElement()

data class StickerElement(
    override val id: Long,
    val emoji: String,
    val center: Offset,
    val sizeFraction: Float,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
) : MarkupElement()

/** Blur or pixelate brush stroke used to hide faces, plates, etc. */
data class BlurBrushElement(
    override val id: Long,
    val points: List<Offset>,
    val radiusFraction: Float,
    val pixelate: Boolean,
) : MarkupElement()

@Serializable
enum class DateStampFormat(val label: String, val pattern: String) {
    DMY("31/08/2026", "dd/MM/yyyy"),
    DMY_SHORT("31/08/26", "dd/MM/yy"),
    D_MONTH_Y("31 August 2026", "d MMMM yyyy"),
    ISO("2026-08-31", "yyyy-MM-dd"),
}

@Serializable
data class FrameSettings(
    val borderPercent: Float = 0f,
    val borderColorArgb: Int = Color.White.toArgbInt(),
    val roundedCorners: Boolean = false,
    val cornerRadiusPercent: Float = 8f,
    val polaroid: Boolean = false,
    val dateStampEnabled: Boolean = false,
    val dateStampFormat: DateStampFormat = DateStampFormat.DMY,
    val watermarkText: String = "",
)

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp"),
}

enum class ExportSizeChoice(val label: String, val longestEdge: Int?) {
    ORIGINAL("Original", null),
    SIZE_2048("2048px", 2048),
    SIZE_1080("1080px", 1080),
}

data class ExportSettings(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    val size: ExportSizeChoice = ExportSizeChoice.ORIGINAL,
)

/** The full edit description for a photo. Every field is part of the linear undo/redo history. */
data class EditState(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val straightenDegrees: Float = 0f,
    val crop: CropRect = CropRect(),
    val cropRatio: Float? = null,
    val adjustments: Adjustments = Adjustments(),
    val filter: FilterPreset = FilterPreset.ORIGINAL,
    val filterStrength: Float = 100f,
    val markup: List<MarkupElement> = emptyList(),
    val frame: FrameSettings = FrameSettings(),
)

/** The subset of an [EditState] that represents a reusable "look", for presets and copy/paste. */
@Serializable
data class EditLook(
    val adjustments: Adjustments = Adjustments(),
    val filter: FilterPreset = FilterPreset.ORIGINAL,
    val filterStrength: Float = 100f,
    val frame: FrameSettings = FrameSettings(),
)

fun EditState.toLook(): EditLook = EditLook(adjustments, filter, filterStrength, frame)

fun EditState.applyLook(look: EditLook): EditState = copy(
    adjustments = look.adjustments,
    filter = look.filter,
    filterStrength = look.filterStrength,
    frame = look.frame,
)

@Serializable
data class EditorPreset(val id: String, val name: String, val look: EditLook)

enum class EditorTool(val label: String) {
    ADJUST("Adjust"), FILTERS("Filters"), CROP("Crop"), MARKUP("Markup"), FRAMES("Frames"), EXPORT("Export"),
}
