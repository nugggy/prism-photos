package au.prism.photos.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AU = Locale("en", "AU")

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(AU, "%d:%02d:%02d", h, m, s) else String.format(AU, "%d:%02d", m, s)
}

fun formatMonthHeader(epochMillis: Long): String =
    SimpleDateFormat("MMMM yyyy", AU).format(Date(epochMillis))

fun formatDateShort(epochMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", AU).format(Date(epochMillis))

fun formatDateLong(epochMillis: Long): String =
    SimpleDateFormat("d MMMM yyyy", AU).format(Date(epochMillis))

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return String.format(AU, "%.1f %s", value, units[unit])
}
