package au.prism.photos.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.R
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.util.findActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shows a locked screen and prompts BiometricPrompt (fingerprint, face or device credential).
 * Once unlocked for this app session, renders [content] with a blank recents thumbnail. Screenshots
 * and downloads are allowed. If [LockSession] relocks while [content] is showing
 * (the app was in the background too long), the lock screen replaces it straight away.
 */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    val unlocked by LockSession.unlocked.collectAsStateWithLifecycle()
    if (unlocked) {
        HideFromRecents()
        content()
    } else {
        LockScreen(onUnlocked = { LockSession.unlock() })
    }
}

/**
 * App session unlock state shared by every [LockGate]. MainActivity reports when the app goes to
 * and comes back from the background (there is no lifecycle-process dependency; this is a single
 * activity app so the activity lifecycle stands in for the process). The session relocks after
 * [RELOCK_AFTER_MS] in the background, whichever screen was showing at the time. Process death
 * relocks too, as the state lives only in memory.
 */
object LockSession {
    const val RELOCK_AFTER_MS: Long = 60_000L

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Volatile private var backgroundedAt: Long = 0L

    fun unlock() { _unlocked.value = true }

    fun lock() { _unlocked.value = false }

    fun onAppBackgrounded(nowMs: Long = System.currentTimeMillis()) {
        backgroundedAt = nowMs
    }

    fun onAppForegrounded(nowMs: Long = System.currentTimeMillis()) {
        val since = backgroundedAt
        backgroundedAt = 0L
        if (shouldRelock(since, nowMs)) lock()
    }

    /** True when the app has been in the background (since [backgroundedAtMs]) for longer than the timeout. */
    fun shouldRelock(backgroundedAtMs: Long, nowMs: Long): Boolean =
        backgroundedAtMs > 0 && nowMs - backgroundedAtMs > RELOCK_AFTER_MS
}

/**
 * Blanks the recents thumbnail while composed (Android 13 and later), so locked content doesn't
 * show in the app switcher. Screenshots and screen recording are deliberately still allowed.
 * Reference counted: during a navigation transition two gated screens can be on screen at once,
 * and the setting must stay until the last one leaves.
 */
@Composable
fun HideFromRecents() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (activity != null) HideFromRecentsCounter.acquire(activity)
        onDispose { if (activity != null) HideFromRecentsCounter.release(activity) }
    }
}

private object HideFromRecentsCounter {
    private var count = 0

    fun acquire(activity: Activity) {
        count++
        if (count == 1) apply(activity, hidden = true)
    }

    fun release(activity: Activity) {
        count = (count - 1).coerceAtLeast(0)
        if (count == 0) apply(activity, hidden = false)
    }

    private fun apply(activity: Activity, hidden: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!hidden)
        }
    }
}

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** True when the device has a screen lock or enrolled biometrics we can authenticate against. */
private fun canUnlock(context: Context): Boolean {
    if (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS) return true
    return context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
}

@Composable
private fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    var available by remember { mutableStateOf(canUnlock(context)) }

    // Re-check when returning, for example after setting up a screen lock in Settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) available = canUnlock(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onUnlocked()
    }

    val promptTitle = stringResource(R.string.lock_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_prompt_subtitle)
    val credentialDescription = stringResource(R.string.lock_prompt_credential)

    fun startBiometric() {
        val canAuth = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
        val fragmentActivity = activity as? FragmentActivity
        if (fragmentActivity != null && canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                fragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlocked()
                    }
                },
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            prompt.authenticate(promptInfo)
        } else {
            val keyguardManager = context.getSystemService(KeyguardManager::class.java)
            if (keyguardManager != null && keyguardManager.isDeviceSecure) {
                val intent = keyguardManager.createConfirmDeviceCredentialIntent(promptTitle, credentialDescription)
                if (intent != null) credentialLauncher.launch(intent) else available = false
            } else {
                available = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = PlexGold, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (available) R.string.lock_subtitle else R.string.lock_no_screen_lock),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            if (available) {
                Button(onClick = { startBiometric() }, colors = ButtonDefaults.buttonColors(containerColor = PlexGold)) {
                    Text(stringResource(R.string.lock_unlock))
                }
            } else {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PlexGold),
                ) {
                    Text(stringResource(R.string.lock_open_security_settings))
                }
            }
        }
    }
}
