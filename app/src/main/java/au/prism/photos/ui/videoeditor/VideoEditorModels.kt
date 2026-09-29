package au.prism.photos.ui.videoeditor

import android.graphics.Bitmap
import android.net.Uri

/** Output resolution choice for export. [KEEP] leaves the source resolution untouched. */
enum class VideoResolution(val label: String) {
    KEEP("Original"),
    R1080("1080p"),
    R720("720p"),
}

/** Every adjustable property of the clip. Compared against [initial] to detect unsaved changes. */
data class VideoEditState(
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    val muted: Boolean = false,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val speed: Float = 1f,
    val resolution: VideoResolution = VideoResolution.KEEP,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val hue: Float = 0f,
    val warmth: Float = 0f,
    val loopPreview: Boolean = true,
)

enum class VideoEditorTab(val label: String) {
    TRIM("Trim"),
    AUDIO("Audio"),
    TRANSFORM("Transform"),
    SPEED("Speed"),
    COLOUR("Colour"),
    EXPORT("Export"),
}

/** Loading the source clip: for Plex items this covers the download to cache first. */
sealed class SourcePhase {
    data object Preparing : SourcePhase()
    data class Downloading(val fraction: Float) : SourcePhase()
    data class Ready(
        val uri: Uri,
        val durationMs: Long,
        val videoWidth: Int,
        val videoHeight: Int,
    ) : SourcePhase()
    data class Error(val message: String) : SourcePhase()
}

sealed class ExportPhase {
    data object Idle : ExportPhase()
    data class Exporting(val fraction: Float) : ExportPhase()
    data class Done(val uri: Uri, val shareFile: java.io.File) : ExportPhase()
    data class Failed(val message: String) : ExportPhase()
}

data class FilmstripFrame(val timeMs: Long, val bitmap: Bitmap)
