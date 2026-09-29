package au.prism.photos.ui.lock

import androidx.compose.runtime.Composable

/**
 * STUB. Replaced by the viewer agent. Shows a locked screen and prompts BiometricPrompt
 * (fingerprint, face or device credential). Once unlocked for this app session, renders [content].
 */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    content()
}

/** App session unlock state shared by LockGate and anything else that needs it. */
object LockSession {
    @Volatile var unlocked: Boolean = false
}
