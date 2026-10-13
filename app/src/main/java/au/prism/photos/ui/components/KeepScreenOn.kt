package au.prism.photos.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * Keeps the screen on while composed with [enabled] true. Reference counted per View, because the
 * viewer's slideshow and the video player both want the same root view's keepScreenOn flag and
 * one of them finishing must not switch it off under the other.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (enabled) ScreenOnCounter.acquire(view)
        onDispose { if (enabled) ScreenOnCounter.release(view) }
    }
}

private object ScreenOnCounter {
    private val counts = WeakHashMap<View, Int>()

    fun acquire(view: View) {
        val n = (counts[view] ?: 0) + 1
        counts[view] = n
        if (n == 1) view.keepScreenOn = true
    }

    fun release(view: View) {
        val n = ((counts[view] ?: 0) - 1).coerceAtLeast(0)
        if (n == 0) {
            counts.remove(view)
            view.keepScreenOn = false
        } else {
            counts[view] = n
        }
    }
}
