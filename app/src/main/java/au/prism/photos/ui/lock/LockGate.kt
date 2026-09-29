package au.prism.photos.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.util.findActivity

/**
 * Shows a locked screen and prompts BiometricPrompt (fingerprint, face or device credential).
 * Once unlocked for this app session, renders [content]. Re-locks after the hosting activity
 * has been backgrounded for more than a minute (there is no lifecycle-process dependency in
 * this project, so the activity lifecycle stands in for the process lifecycle; fine for this
 * single-activity app).
 */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var unlocked by remember { mutableStateOf(LockSession.unlocked) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> LockSession.backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val since = LockSession.backgroundedAt
                    if (since > 0 && System.currentTimeMillis() - since > LockSession.RELOCK_AFTER_MS) {
                        LockSession.unlocked = false
                        unlocked = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (unlocked) {
        content()
    } else {
        LockScreen(onUnlocked = {
            LockSession.unlocked = true
            unlocked = true
        })
    }
}

/** App session unlock state shared by LockGate and anything else that needs it. */
object LockSession {
    @Volatile var unlocked: Boolean = false
    @Volatile var backgroundedAt: Long = 0L
    const val RELOCK_AFTER_MS: Long = 60_000L
}

@Composable
private fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onUnlocked()
    }

    fun startBiometric() {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
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
                .setTitle("Unlock Prism")
                .setSubtitle("Locked items")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build()
            prompt.authenticate(promptInfo)
        } else {
            val keyguardManager = context.getSystemService(KeyguardManager::class.java)
            if (keyguardManager != null && keyguardManager.isDeviceSecure) {
                val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                    "Unlock Prism",
                    "Confirm your PIN, pattern or password",
                )
                if (intent != null) credentialLauncher.launch(intent)
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = PlexGold, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Locked items", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Unlock with your fingerprint, face or device PIN",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { startBiometric() }, colors = ButtonDefaults.buttonColors(containerColor = PlexGold)) {
                Text("Unlock")
            }
        }
    }
}
