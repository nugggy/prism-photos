package au.prism.photos.ui.components

import android.util.Rational

/**
 * Shared flag the video player sets while a video is actively playing, so MainActivity
 * knows whether onUserLeaveHint should trigger picture in picture.
 */
object PipState {
    @Volatile var videoPlaying: Boolean = false
    @Volatile var aspect: Rational? = null
}
