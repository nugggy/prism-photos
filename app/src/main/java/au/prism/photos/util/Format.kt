package au.prism.photos.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Duration, byte size and Australian date/time formatting used across the viewer. */
object Format {
    private val auLocale = Locale("en", "AU")
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a", auLocale)
    private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", auLocale)
    private val shortDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", auLocale)
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", auLocale)

    /** "14 March 2024, 3:42 pm" */
    fun dateTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateTimeFmt).lowerAmPm()

    /** "14 March 2024" */
    fun date(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateFmt)

    /** "14/03/2024" */
    fun shortDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(shortDateFmt)

    /** "3:42 pm" */
    fun time(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFmt).lowerAmPm()

    private fun String.lowerAmPm(): String = replace("AM", "am").replace("PM", "pm")

    /** mm:ss, or h:mm:ss once past an hour. */
    fun duration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(auLocale, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(auLocale, "%d:%02d", m, s)
        }
    }

    /** Human readable file size, one decimal place. */
    fun bytes(size: Long): String {
        if (size <= 0) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            size >= gb -> String.format(auLocale, "%.1f GB", size / gb)
            size >= mb -> String.format(auLocale, "%.1f MB", size / mb)
            size >= kb -> String.format(auLocale, "%.1f KB", size / kb)
            else -> "$size B"
        }
    }

    fun megapixels(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "0 MP"
        val mp = (width.toLong() * height.toLong()) / 1_000_000.0
        return String.format(auLocale, "%.1f MP", mp)
    }
}
