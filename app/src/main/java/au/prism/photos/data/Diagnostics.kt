package au.prism.photos.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small in-memory log of what the data layer did, shown in Settings under Diagnostics so
 * problems with a real server can be reported without hooking up a debugger.
 */
object Diagnostics {
    private const val MAX_LINES = 60
    private val linesFlow = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = linesFlow

    fun log(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        linesFlow.update { (it + "$stamp  $message").takeLast(MAX_LINES) }
    }

    fun clear() = linesFlow.update { emptyList() }

    fun asText(): String = linesFlow.value.joinToString("\n")
}
