package au.prism.photos.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import au.prism.photos.PrismApp
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Normalised (0..1) crop rectangle relative to the working bitmap. */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
)

enum class FilterPreset(val label: String) {
    ORIGINAL("Original"), VIVID("Vivid"), WARM("Warm"), COOL("Cool"),
    MONO("Mono"), SEPIA("Sepia"), FADE("Fade"), NOIR("Noir"),
}

data class EditState(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropRect = CropRect(),
    val cropRatio: Float? = null,
    val adjustments: Adjustments = Adjustments(),
    val filter: FilterPreset = FilterPreset.ORIGINAL,
)

private const val MAX_EDGE = 4096

class EditorViewModel(private val itemId: String) : ViewModel() {
    private val graph = PrismApp.graph
    private val isLocalUri = itemId.startsWith("content://") || itemId.startsWith("file://")

    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<EditState>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    init {
        viewModelScope.launch { loadSource() }
    }

    private suspend fun loadSource() {
        _loading.value = true
        try {
            val bitmap = withContext(Dispatchers.IO) {
                if (isLocalUri) decodeFromContentUri(itemId.toUri()) else decodeFromPlex(itemId)
            }
            _sourceBitmap.value = bitmap
        } catch (e: Exception) {
            _error.value = e.message ?: "Couldn't load the image"
        } finally {
            _loading.value = false
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

    private fun commit(newState: EditState) {
        undoStack.addLast(_state.value)
        _canUndo.value = true
        _state.value = newState
    }

    fun rotate90() = commit(_state.value.copy(rotationDegrees = (_state.value.rotationDegrees + 90) % 360))
    fun flipHorizontal() = commit(_state.value.copy(flipHorizontal = !_state.value.flipHorizontal))
    fun flipVertical() = commit(_state.value.copy(flipVertical = !_state.value.flipVertical))
    fun setCrop(rect: CropRect) = commit(_state.value.copy(crop = rect))
    fun setCropRatio(ratio: Float?, rect: CropRect) = commit(_state.value.copy(cropRatio = ratio, crop = rect))
    fun setAdjustments(a: Adjustments) = commit(_state.value.copy(adjustments = a))
    fun setFilter(f: FilterPreset) = commit(_state.value.copy(filter = f))

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        _state.value = previous
        _canUndo.value = undoStack.isNotEmpty()
    }

    fun reset() {
        if (_state.value == EditState()) return
        undoStack.addLast(_state.value)
        _canUndo.value = true
        _state.value = EditState()
    }

    fun save(onDone: (String) -> Unit) {
        val src = _sourceBitmap.value ?: return
        val editState = _state.value
        viewModelScope.launch {
            _saving.value = true
            try {
                val rendered = withContext(Dispatchers.Default) { renderFinal(src, editState) }
                val uri = withContext(Dispatchers.IO) { saveToMediaStore(PrismApp.instance, rendered) }
                _saving.value = false
                onDone(uri.toString())
            } catch (e: Exception) {
                _saving.value = false
                _error.value = e.message ?: "Couldn't save the image"
            }
        }
    }

    private fun renderFinal(src: Bitmap, state: EditState): Bitmap {
        val matrix = Matrix()
        if (state.rotationDegrees != 0) matrix.postRotate(state.rotationDegrees.toFloat())
        val sx = if (state.flipHorizontal) -1f else 1f
        val sy = if (state.flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) matrix.postScale(sx, sy)
        val transformed = if (!matrix.isIdentity) {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } else {
            src
        }

        val crop = state.crop
        val cropped = if (crop != CropRect()) {
            val cx = (transformed.width * crop.left).toInt().coerceIn(0, transformed.width - 1)
            val cy = (transformed.height * crop.top).toInt().coerceIn(0, transformed.height - 1)
            val cw = (transformed.width * crop.width).toInt().coerceAtLeast(1).coerceAtMost(transformed.width - cx)
            val ch = (transformed.height * crop.height).toInt().coerceAtLeast(1).coerceAtMost(transformed.height - cy)
            Bitmap.createBitmap(transformed, cx, cy, cw, ch)
        } else {
            transformed
        }

        val output = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(EditorColour.buildMatrix(state)))
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        return output
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap): Uri {
        val name = "Prism_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Prism")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create the file")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: throw IOException("Couldn't open the destination")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            resetExifOrientation(resolver, uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
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
