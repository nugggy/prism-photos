package au.prism.photos.ui.videoeditor

import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import au.prism.photos.PrismApp
import au.prism.photos.util.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FILMSTRIP_FRAME_COUNT = 16
private const val FILMSTRIP_THUMB_WIDTH = 240

/**
 * Video editor for the Plex clip or device video [itemId] (device items pass their content://
 * URI as the id, matching the photo editor's convention). Downloads Plex clips to a local cache
 * file first so scrubbing and export are reliable, then edits with Media3 Transformer.
 */
@OptIn(UnstableApi::class)
class VideoEditorViewModel(private val itemId: String) : ViewModel() {
    private val graph = PrismApp.graph
    private val context = PrismApp.instance
    private val isLocalUri = itemId.startsWith("content://") || itemId.startsWith("file://")

    private val _sourcePhase = MutableStateFlow<SourcePhase>(SourcePhase.Preparing)
    val sourcePhase: StateFlow<SourcePhase> = _sourcePhase.asStateFlow()

    private val _editState = MutableStateFlow(VideoEditState())
    val editState: StateFlow<VideoEditState> = _editState.asStateFlow()

    private var initialState = VideoEditState()

    private val _filmstrip = MutableStateFlow<List<FilmstripFrame>>(emptyList())
    val filmstrip: StateFlow<List<FilmstripFrame>> = _filmstrip.asStateFlow()

    private val _exportPhase = MutableStateFlow<ExportPhase>(ExportPhase.Idle)
    val exportPhase: StateFlow<ExportPhase> = _exportPhase.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var transformer: Transformer? = null

    val hasChanges: Boolean get() = _editState.value != initialState

    init {
        viewModelScope.launch { loadSource() }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private suspend fun loadSource() {
        _sourcePhase.value = SourcePhase.Preparing
        try {
            val uri = withContext(Dispatchers.IO) { resolveSourceUri() }
            val (durationMs, width, height) = withContext(Dispatchers.IO) { readMetadata(uri) }
            initialState = VideoEditState(trimStartMs = 0, trimEndMs = durationMs)
            _editState.value = initialState
            _sourcePhase.value = SourcePhase.Ready(uri, durationMs, width, height)
            loadFilmstrip(uri, durationMs)
        } catch (e: Exception) {
            _sourcePhase.value = SourcePhase.Error(e.message ?: "Couldn't load this video")
        }
    }

    private suspend fun resolveSourceUri(): Uri {
        if (isLocalUri) return itemId.toUri()
        val item = graph.media.item(itemId) ?: throw IOException("Item not found")
        val dir = File(context.cacheDir, "videoedit").apply { mkdirs() }
        val dest = File(dir, "plex_${itemId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.mp4")
        if (!dest.exists() || dest.length() == 0L) {
            _sourcePhase.value = SourcePhase.Downloading(0f)
            Downloader.download(graph.media.downloadUrl(item), dest) { bytes, total ->
                if (total > 0) _sourcePhase.value = SourcePhase.Downloading((bytes.toFloat() / total).coerceIn(0f, 1f))
            }
        }
        return Uri.fromFile(dest)
    }

    private fun readMetadata(uri: Uri): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Triple(duration, width, height)
        } finally {
            retriever.release()
        }
    }

    private fun loadFilmstrip(uri: Uri, durationMs: Long) {
        viewModelScope.launch {
            val frames = withContext(Dispatchers.IO) { extractFilmstrip(uri, durationMs) }
            _filmstrip.value = frames
        }
    }

    private fun extractFilmstrip(uri: Uri, durationMs: Long): List<FilmstripFrame> {
        if (durationMs <= 0) return emptyList()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val step = durationMs / FILMSTRIP_FRAME_COUNT
            (0 until FILMSTRIP_FRAME_COUNT).mapNotNull { i ->
                val timeMs = (step * i).coerceIn(0, durationMs - 1)
                val frame = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                frame?.let { FilmstripFrame(timeMs, downscale(it)) }
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            retriever.release()
        }
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= FILMSTRIP_THUMB_WIDTH) return bitmap
        val ratio = FILMSTRIP_THUMB_WIDTH.toFloat() / bitmap.width
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, FILMSTRIP_THUMB_WIDTH, h, true)
    }

    // --- Tool edits -----------------------------------------------------

    fun setTrim(startMs: Long, endMs: Long) {
        _editState.value = _editState.value.copy(trimStartMs = startMs, trimEndMs = endMs)
    }

    fun setMuted(muted: Boolean) {
        _editState.value = _editState.value.copy(muted = muted)
    }

    fun rotate90() {
        val current = _editState.value
        _editState.value = current.copy(rotationDegrees = (current.rotationDegrees + 90) % 360)
    }

    fun flipHorizontal() {
        val current = _editState.value
        _editState.value = current.copy(flipHorizontal = !current.flipHorizontal)
    }

    fun flipVertical() {
        val current = _editState.value
        _editState.value = current.copy(flipVertical = !current.flipVertical)
    }

    fun setResolution(resolution: VideoResolution) {
        _editState.value = _editState.value.copy(resolution = resolution)
    }

    fun setSpeed(speed: Float) {
        _editState.value = _editState.value.copy(speed = speed)
    }

    fun setLoopPreview(loop: Boolean) {
        _editState.value = _editState.value.copy(loopPreview = loop)
    }

    fun setBrightness(v: Float) {
        _editState.value = _editState.value.copy(brightness = v)
    }

    fun setContrast(v: Float) {
        _editState.value = _editState.value.copy(contrast = v)
    }

    fun setSaturation(v: Float) {
        _editState.value = _editState.value.copy(saturation = v)
    }

    fun setHue(v: Float) {
        _editState.value = _editState.value.copy(hue = v)
    }

    fun setWarmth(v: Float) {
        _editState.value = _editState.value.copy(warmth = v)
    }

    fun resetColour() {
        val current = _editState.value
        _editState.value = current.copy(brightness = 0f, contrast = 0f, saturation = 0f, hue = 0f, warmth = 0f)
    }

    // --- Frame capture ----------------------------------------------------

    fun captureFrame(atMs: Long) {
        val ready = _sourcePhase.value as? SourcePhase.Ready ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { saveFrameAsPhoto(ready.uri, atMs) }
                _message.value = "Frame saved to Pictures"
            } catch (e: Exception) {
                _message.value = e.message ?: "Couldn't save the frame"
            }
        }
    }

    private fun saveFrameAsPhoto(uri: Uri, atMs: Long): Uri {
        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw IOException("Couldn't read that frame")
        } finally {
            retriever.release()
        }
        val name = "PlexGallery_${timestamp()}.jpg"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Plex Gallery")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create the file")
        try {
            resolver.openOutputStream(outUri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                ?: throw IOException("Couldn't open the destination")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(outUri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(outUri, null, null)
            throw e
        }
        return outUri
    }

    // --- Export -------------------------------------------------------

    fun export() {
        val ready = _sourcePhase.value as? SourcePhase.Ready ?: return
        if (_exportPhase.value is ExportPhase.Exporting) return
        val state = _editState.value
        viewModelScope.launch {
            _exportPhase.value = ExportPhase.Exporting(0f)
            try {
                val outFile = File(context.cacheDir, "edits").apply { mkdirs() }
                    .let { File(it, "PlexGallery_${timestamp()}.mp4") }
                val editedItem = buildEditedMediaItem(ready.uri, state)

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            viewModelScope.launch { finishExport(outFile) }
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            outFile.delete()
                            _exportPhase.value = ExportPhase.Failed(readableError(exportException))
                        }
                    })
                    .build()
                this@VideoEditorViewModel.transformer = transformer
                transformer.start(editedItem, outFile.absolutePath)
                pollProgress(transformer)
            } catch (e: Exception) {
                _exportPhase.value = ExportPhase.Failed(e.message ?: "Export failed")
            }
        }
    }

    private fun buildEditedMediaItem(sourceUri: Uri, state: VideoEditState): EditedMediaItem {
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(state.trimStartMs.coerceAtLeast(0))
            .setEndPositionMs(state.trimEndMs.coerceAtLeast(state.trimStartMs + 1))
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(clip)
            .build()

        val videoEffects = mutableListOf<Effect>()
        if (state.rotationDegrees != 0 || state.flipHorizontal || state.flipVertical) {
            val sx = if (state.flipHorizontal) -1f else 1f
            val sy = if (state.flipVertical) -1f else 1f
            videoEffects += ScaleAndRotateTransformation.Builder()
                .setScale(sx, sy)
                .setRotationDegrees(state.rotationDegrees.toFloat())
                .build()
        }
        if (state.brightness != 0f) videoEffects += Brightness(state.brightness)
        if (state.contrast != 0f) videoEffects += Contrast(state.contrast)
        if (state.saturation != 0f || state.hue != 0f) {
            videoEffects += HslAdjustment.Builder()
                .adjustHue(state.hue)
                .adjustSaturation(state.saturation)
                .build()
        }
        if (state.warmth != 0f) {
            videoEffects += RgbAdjustment.Builder()
                .setRedScale((1f + state.warmth * 0.3f).coerceAtLeast(0f))
                .setBlueScale((1f - state.warmth * 0.3f).coerceAtLeast(0f))
                .build()
        }
        when (state.resolution) {
            VideoResolution.R1080 -> videoEffects += Presentation.createForHeight(1080)
            VideoResolution.R720 -> videoEffects += Presentation.createForHeight(720)
            VideoResolution.KEEP -> {}
        }
        if (state.speed != 1f) videoEffects += SpeedChangeEffect(state.speed)

        val audioProcessors = mutableListOf<AudioProcessor>()
        if (!state.muted && state.speed != 1f) {
            audioProcessors += SonicAudioProcessor().apply { setSpeed(state.speed) }
        }

        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(state.muted)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()
    }

    private suspend fun pollProgress(transformer: Transformer) {
        val holder = ProgressHolder()
        while (_exportPhase.value is ExportPhase.Exporting) {
            val progressState = transformer.getProgress(holder)
            if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                _exportPhase.value = ExportPhase.Exporting(holder.progress / 100f)
            }
            delay(500)
        }
    }

    private suspend fun finishExport(outFile: File) {
        try {
            val uri = withContext(Dispatchers.IO) { copyToMediaStore(outFile) }
            _exportPhase.value = ExportPhase.Done(uri, outFile)
        } catch (e: Exception) {
            _exportPhase.value = ExportPhase.Failed(e.message ?: "Couldn't save the video")
        }
    }

    private fun copyToMediaStore(outFile: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Plex Gallery")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create the file")
        try {
            resolver.openOutputStream(uri)?.use { out -> outFile.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Couldn't open the destination")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    fun cancelExport() {
        transformer?.cancel()
        transformer = null
        _exportPhase.value = ExportPhase.Idle
    }

    private fun readableError(exception: ExportException): String = when (exception.errorCode) {
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED -> "This device's codec doesn't support that setting"
        ExportException.ERROR_CODE_DECODER_INIT_FAILED,
        ExportException.ERROR_CODE_ENCODER_INIT_FAILED -> "Couldn't start the video codec"
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The source file couldn't be found"
        ExportException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        ExportException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Lost connection while exporting"
        else -> exception.message ?: "Export failed"
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    override fun onCleared() {
        transformer?.cancel()
        transformer = null
    }

    class Factory(private val itemId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VideoEditorViewModel::class.java))
            return VideoEditorViewModel(itemId) as T
        }
    }
}
