package au.prism.photos.ui.components

import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared picture in picture state. The video player sets [videoPlaying] and [aspect] so
 * MainActivity knows whether onUserLeaveHint should trigger PiP, and MainActivity sets
 * [inPip] so the player can hide its controls while the window is tiny.
 */
object PipState {
    @Volatile var videoPlaying: Boolean = false
    @Volatile var aspect: Rational? = null

    /** Observable from Compose. True while the activity is in picture in picture mode. */
    var inPip: Boolean by mutableStateOf(false)
}
