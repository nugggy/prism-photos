package au.prism.photos.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records uncaught exceptions to a file so the next launch can show them under Settings,
 * Diagnostics. Without this a crash on a real phone leaves no trace the user can send.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                File(context.filesDir, FILE).writeText(
                    "Crash at $stamp on thread ${thread.name}\n${throwable::class.java.name}: ${throwable.message}\n\n$sw",
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
        // Surface the previous crash, if any, in the diagnostics log.
        read(context)?.let { Diagnostics.log("Previous run crashed: " + it.lineSequence().take(2).joinToString(" | ")) }
    }

    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
