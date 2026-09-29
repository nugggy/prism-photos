package au.prism.photos.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import au.prism.photos.BuildConfig
import au.prism.photos.PrismApp
import au.prism.photos.ui.theme.PlexGold
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

private const val MAX_EDGE = 4096
private const val PREVIEW_EDGE = 1280

/** Result of a completed save/export, so the UI can offer a share sheet after saving. */
data class ExportResult(val uri: Uri, val mimeType: String)

class EditorViewModel(private val itemId: String) : ViewModel() {
    private val graph = PrismApp.graph
    private val isLocalUri = itemId.startsWith("content://") || itemId.startsWith("file://")
    private val presetsStore = EditorPresetsStore(PrismApp.instance)

    private var fullSourceBitmap: Bitmap? = null
    private val _previewSourceBitmap = MutableStateFlow<Bitmap?>(null)
    /** Downscaled (max 1280px) source bitmap, used for the live preview pipeline. */
    val sourceBitmap: StateFlow<Bitmap?> = _previewSourceBitmap.asStateFlow()

    private var takenAtMillis: Long? = null

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    private val _previewProcessed = MutableStateFlow<Bitmap?>(null)
    val previewProcessed: StateFlow<Bitmap?> = _previewProcessed.asStateFlow()

    /** Rotate/flip/straighten applied but not cropped; used as the backdrop for the crop overlay. */
    private val _previewBase = MutableStateFlow<Bitmap?>(null)
    val previewBase: StateFlow<Bitmap?> = _previewBase.asStateFlow()

    private val _compareOriginal = MutableStateFlow(false)
    val compareOriginal: StateFlow<Boolean> = _compareOriginal.asStateFlow()

    private val history = mutableListOf(EditState())
    private var historyIndex = 0
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _exportSettings = MutableStateFlow(ExportSettings())
    val exportSettings: StateFlow<ExportSettings> = _exportSettings.asStateFlow()

    private val _presets = MutableStateFlow<List<EditorPreset>>(emptyList())
    val presets: StateFlow<List<EditorPreset>> = _presets.asStateFlow()

    private val _hasClipboard = MutableStateFlow(false)
    val hasClipboard: StateFlow<Boolean> = _hasClipboard.asStateFlow()

    private val _markupTool = MutableStateFlow(MarkupTool.NONE)
    val markupTool: StateFlow<MarkupTool> = _markupTool.asStateFlow()

    /** Tool configuration shared between the Markup tab controls and the on-canvas overlay. This
     * is deliberately not part of [EditState] history: it is a drawing-tool setting, not an edit. */
    val brushColor = MutableStateFlow(PlexGold)
    val brushSize = MutableStateFlow(24f)
    val brushAlpha = MutableStateFlow(0.4f)
    val pixelateBrush = MutableStateFlow(true)

    private val markupIdGen = AtomicLong(1)
    fun nextMarkupId(): Long = markupIdGen.getAndIncrement()

    init {
        viewModelScope.launch { loadSource() }
        viewModelScope.launch {
            _presets.value = presetsStore.loadPresets()
            _hasClipboard.value = presetsStore.pasteLook() != null
        }
        // Debounced preview recompute: cancels any in-flight render when a newer state arrives.
        viewModelScope.launch {
            _state.collectLatest { s ->
                val src = _previewSourceBitmap.value ?: return@collectLatest
                delay(30)
                val (rendered, base) = withContext(Dispatchers.Default) {
                    runCatching { EditorProcessor.render(src, s, takenAtMillis) }.getOrNull() to
                        runCatching { EditorProcessor.transformBase(src, s) }.getOrNull()
                }
                if (rendered != null) _previewProcessed.value = rendered
                if (base != null) _previewBase.value = base
            }
        }
    }

    private suspend fun loadSource() {
        _loading.value = true
        try {
            val bitmap = withContext(Dispatchers.IO) {
                if (isLocalUri) decodeFromContentUri(itemId.toUri()) else decodeFromPlex(itemId)
            }
            fullSourceBitmap = bitmap
            _previewSourceBitmap.value = downscale(bitmap, PREVIEW_EDGE)
            takenAtMillis = resolveTakenAt()
        } catch (e: Exception) {
            _error.value = e.message ?: "Couldn't load the image"
        } finally {
            _loading.value = false
        }
    }

    private suspend fun resolveTakenAt(): Long? {
        return try {
            if (isLocalUri) {
                withContext(Dispatchers.IO) {
                    val uri = itemId.toUri()
                    val resolver = PrismApp.instance.contentResolver
                    resolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use { c ->
                        if (c.moveToFirst() && c.columnCount > 0) c.getLong(0) else null
                    }
                }
            } else {
                graph.media.item(itemId)?.takenAt
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeFromContentUri(uri: Uri): Bitmap {
        val resolver = PrismApp.instance.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = downsampleSize(info.size.width, info.size.height)
                decoder.setTargetSize(w, h)
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            downsampleBitmap(MediaStore.Images.Media.getBitmap(resolver, uri))
        }
    }

    private suspend fun decodeFromPlex(id: String): Bitmap {
        val item = graph.media.item(id) ?: throw IOException("Item not found")
        val loader = ImageLoader(PrismApp.instance)
        val request = ImageRequest.Builder(PrismApp.instance)
            .data(graph.media.originalUrl(item))
            .allowHardware(false)
            .build()
        val result = loader.execute(request)
        if (result !is SuccessResult) throw IOException("Couldn't download the image")
        val image = result.image
        val bitmap = (image as? BitmapImage)?.bitmap ?: image.toBitmap(image.width, image.height)
        return downsampleBitmap(bitmap)
    }

    private fun downsampleSize(w: Int, h: Int): Pair<Int, Int> {
        val longest = maxOf(w, h)
        if (longest <= MAX_EDGE) return w to h
        val scale = MAX_EDGE.toFloat() / longest
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    private fun downsampleBitmap(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE) return bitmap
        val (w, h) = downsampleSize(bitmap.width, bitmap.height)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    // ---- History -----------------------------------------------------------------------------

    /** Updates the live editing state for immediate preview feedback, without touching history. */
    fun updateLive(newState: EditState) {
        _state.value = newState
    }

    /** Commits [newState] as a new history entry (truncating any redo branch). */
    fun commit(newState: EditState) {
        if (historyIndex < history.size - 1) {
            while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
        }
        history.add(newState)
        historyIndex++
        while (history.size > 60) { history.removeAt(0); historyIndex-- }
        _state.value = newState
        refreshUndoRedo()
    }

    private fun refreshUndoRedo() {
        _canUndo.value = historyIndex > 0
        _canRedo.value = historyIndex < history.size - 1
    }

    fun undo() {
        if (historyIndex <= 0) return
        historyIndex--
        _state.value = history[historyIndex]
        refreshUndoRedo()
    }

    fun redo() {
        if (historyIndex >= history.size - 1) return
        historyIndex++
        _state.value = history[historyIndex]
        refreshUndoRedo()
    }

    fun revertToOriginal() {
        commit(EditState())
    }

    // ---- Crop / rotate / straighten -----------------------------------------------------------

    private fun baseDims(state: EditState): Pair<Int, Int> {
        val src = _previewSourceBitmap.value ?: return 1 to 1
        return if (state.rotationDegrees % 180 != 0) src.height to src.width else src.width to src.height
    }

    fun rotate90(clockwise: Boolean = true) {
        val cur = _state.value
        val delta = if (clockwise) 90 else -90
        commit(cur.copy(rotationDegrees = ((cur.rotationDegrees + delta) % 360 + 360) % 360, crop = CropRect(), cropRatio = null, straightenDegrees = 0f))
    }

    fun flipHorizontal() = commit(_state.value.copy(flipHorizontal = !_state.value.flipHorizontal))
    fun flipVertical() = commit(_state.value.copy(flipVertical = !_state.value.flipVertical))

    fun setStraighten(degrees: Float, final: Boolean) {
        val cur = _state.value
        val (bw, bh) = baseDims(cur)
        val (ew, eh) = EditorEffects.expandedSize(bw, bh, degrees)
        val autoCrop = EditorProcessor.straightenAutoCropRect(bw, bh, degrees, ew.roundToInt().coerceAtLeast(1), eh.roundToInt().coerceAtLeast(1))
        val next = cur.copy(straightenDegrees = degrees, crop = autoCrop, cropRatio = null)
        if (final) commit(next) else updateLive(next)
    }

    fun straightenedAspect(): Float {
        val cur = _state.value
        val (bw, bh) = baseDims(cur)
        val (ew, eh) = EditorEffects.expandedSize(bw, bh, cur.straightenDegrees)
        return ew / eh
    }

    fun setCropRatio(ratio: Float?) {
        val cur = _state.value
        val aspect = straightenedAspect()
        val rect = centeredRectFor(aspect, ratio)
        commit(cur.copy(cropRatio = ratio, crop = rect))
    }

    fun setCropLive(rect: CropRect) = updateLive(_state.value.copy(crop = rect))
    fun commitCrop(rect: CropRect) = commit(_state.value.copy(crop = rect))

    // ---- Adjustments / filters -----------------------------------------------------------------

    fun setAdjustmentsLive(a: Adjustments) = updateLive(_state.value.copy(adjustments = a))
    fun commitAdjustments(a: Adjustments) = commit(_state.value.copy(adjustments = a))

    fun setFilter(f: FilterPreset) = commit(_state.value.copy(filter = f, filterStrength = if (f == FilterPreset.ORIGINAL) 100f else _state.value.filterStrength))
    fun setFilterStrengthLive(v: Float) = updateLive(_state.value.copy(filterStrength = v))
    fun commitFilterStrength(v: Float) = commit(_state.value.copy(filterStrength = v))

    fun autoEnhance() {
        val src = _previewSourceBitmap.value ?: return
        viewModelScope.launch {
            val computed = withContext(Dispatchers.Default) { EditorProcessor.autoEnhance(src) }
            commit(_state.value.copy(adjustments = computed))
        }
    }

    // ---- Markup ---------------------------------------------------------------------------------

    fun setMarkupTool(tool: MarkupTool) {
        _markupTool.value = tool
    }

    fun addMarkupElement(element: MarkupElement) = commit(_state.value.copy(markup = _state.value.markup + element))

    fun removeMarkupElements(ids: Set<Long>) {
        if (ids.isEmpty()) return
        commit(_state.value.copy(markup = _state.value.markup.filterNot { it.id in ids }))
    }

    fun updateMarkupElementLive(updated: MarkupElement) {
        val list = _state.value.markup.map { if (it.id == updated.id) updated else it }
        updateLive(_state.value.copy(markup = list))
    }

    fun commitMarkupElement(updated: MarkupElement) {
        val list = _state.value.markup.map { if (it.id == updated.id) updated else it }
        commit(_state.value.copy(markup = list))
    }

    fun clearMarkup() = commit(_state.value.copy(markup = emptyList()))

    // ---- Frames ----------------------------------------------------------------------------------

    fun setFrameLive(frame: FrameSettings) = updateLive(_state.value.copy(frame = frame))
    fun commitFrame(frame: FrameSettings) = commit(_state.value.copy(frame = frame))

    val hasTakenAt: Boolean get() = takenAtMillis != null

    // ---- Compare ---------------------------------------------------------------------------------

    fun setCompareOriginal(active: Boolean) {
        _compareOriginal.value = active
    }

    fun toggleCompare() {
        _compareOriginal.value = !_compareOriginal.value
    }

    // ---- Presets / copy & paste -------------------------------------------------------------------

    fun savePreset(name: String) {
        viewModelScope.launch {
            _presets.value = presetsStore.savePreset(name, _state.value.toLook())
        }
    }

    fun applyPreset(preset: EditorPreset) = commit(_state.value.applyLook(preset.look))

    fun deletePreset(id: String) {
        viewModelScope.launch { _presets.value = presetsStore.deletePreset(id) }
    }

    fun copyEdits() {
        viewModelScope.launch {
            presetsStore.copyLook(_state.value.toLook())
            _hasClipboard.value = true
        }
    }

    fun pasteEdits() {
        viewModelScope.launch {
            val look = presetsStore.pasteLook() ?: return@launch
            commit(_state.value.applyLook(look))
        }
    }

    // ---- Export / save -----------------------------------------------------------------------------

    fun setExportSettings(settings: ExportSettings) {
        _exportSettings.value = settings
    }

    fun reset() {
        if (_state.value == EditState()) return
        commit(EditState())
    }

    fun save(onDone: (String) -> Unit) {
        exportInternal(share = false) { result -> onDone(result.uri.toString()) }
    }

    fun exportForShare(onReady: (ExportResult) -> Unit) {
        exportInternal(share = true, onReady = onReady)
    }

    private fun exportInternal(share: Boolean, onReady: (ExportResult) -> Unit) {
        val src = fullSourceBitmap ?: return
        val editState = _state.value
        val settings = _exportSettings.value
        val takenAt = takenAtMillis
        viewModelScope.launch {
            _saving.value = true
            try {
                val rendered = withContext(Dispatchers.Default) {
                    var out = EditorProcessor.render(src, editState, takenAt)
                    val cap = settings.size.longestEdge
                    if (cap != null) {
                        val longest = maxOf(out.width, out.height)
                        if (longest > cap) {
                            val scale = cap.toFloat() / longest
                            out = Bitmap.createScaledBitmap(out, (out.width * scale).roundToInt().coerceAtLeast(1), (out.height * scale).roundToInt().coerceAtLeast(1), true)
                        }
                    } else {
                        val longest = maxOf(out.width, out.height)
                        if (longest > MAX_EDGE) {
                            val scale = MAX_EDGE.toFloat() / longest
                            out = Bitmap.createScaledBitmap(out, (out.width * scale).roundToInt().coerceAtLeast(1), (out.height * scale).roundToInt().coerceAtLeast(1), true)
                        }
                    }
                    out
                }
                val result = withContext(Dispatchers.IO) {
                    if (share) saveToShareCache(PrismApp.instance, rendered, settings) else saveToMediaStore(PrismApp.instance, rendered, settings)
                }
                _saving.value = false
                onReady(result)
            } catch (e: Exception) {
                _saving.value = false
                _error.value = e.message ?: "Couldn't save the image"
            }
        }
    }

    private fun compressInto(bitmap: Bitmap, format: ExportFormat, quality: Int, out: java.io.OutputStream) {
        val fmt = when (format) {
            ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        bitmap.compress(fmt, quality, out)
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap, settings: ExportSettings): ExportResult {
        val name = "Prism_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.${settings.format.extension}"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, settings.format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Plex Gallery")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create the file")
        try {
            resolver.openOutputStream(uri)?.use { out -> compressInto(bitmap, settings.format, settings.quality, out) }
                ?: throw IOException("Couldn't open the destination")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            if (settings.format == ExportFormat.JPEG) resetExifOrientation(resolver, uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return ExportResult(uri, settings.format.mime)
    }

    private fun saveToShareCache(context: Context, bitmap: Bitmap, settings: ExportSettings): ExportResult {
        val dir = File(context.cacheDir, "edits").apply { mkdirs() }
        val name = "Prism_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.${settings.format.extension}"
        val file = File(dir, name)
        file.outputStream().use { out -> compressInto(bitmap, settings.format, settings.quality, out) }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        return ExportResult(uri, settings.format.mime)
    }

    private fun resetExifOrientation(resolver: android.content.ContentResolver, uri: Uri) {
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            // Best effort only; the saved image is still valid without this.
        }
    }

    class Factory(private val itemId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EditorViewModel::class.java))
            return EditorViewModel(itemId) as T
        }
    }
}

/** Centred crop rect for [ratio] (width/height) inside an image of aspect [bitmapAspect]. Null ratio
 * (Free) keeps the current full frame as the starting rect. */
fun centeredRectFor(bitmapAspect: Float, ratio: Float?): CropRect {
    if (ratio == null) return CropRect()
    return if (ratio > bitmapAspect) {
        val h = bitmapAspect / ratio
        val top = (1f - h) / 2f
        CropRect(0f, top, 1f, top + h)
    } else {
        val w = ratio / bitmapAspect
        val left = (1f - w) / 2f
        CropRect(left, 0f, left + w, 1f)
    }
}
