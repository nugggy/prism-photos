package au.prism.photos

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import au.prism.photos.domain.MediaItem
import au.prism.photos.ui.components.PipState
import au.prism.photos.ui.nav.PrismNavHost
import au.prism.photos.ui.theme.PrismTheme
import kotlinx.coroutines.launch

/**
 * Single activity host. Handles sign in / library setup / the bottom nav shell through
 * PrismNavHost, plus intents from other apps: viewing shared or camera media, and acting
 * as a photo picker for other apps (ACTION_PICK / GET_CONTENT).
 */
class MainActivity : ComponentActivity() {

    private var pendingViewUris by mutableStateOf<List<String>?>(null)
    private var pickMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent, isInitial = true)

        setContent {
            val graph = PrismApp.graph
            val settings by graph.settings.settings.collectAsStateWithLifecycle()
            val navController = rememberNavController()
            val scope = rememberCoroutineScopeCompat()

            PrismTheme(themeMode = settings.theme, dynamicColour = settings.dynamicColour) {
                PrismNavHost(
                    navController = navController,
                    pendingViewUris = pendingViewUris,
                    onViewUrisConsumed = { pendingViewUris = null },
                    pickHandler = if (pickMode) { item: MediaItem -> handlePick(item, scope) } else null,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, isInitial = false)
    }

    private fun handleIntent(intent: Intent?, isInitial: Boolean) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW, "com.android.camera.action.REVIEW" -> {
                intent.data?.let { uri -> pendingViewUris = listOf(uri.toString()) }
            }
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtraCompat(Intent.EXTRA_STREAM)
                if (uri != null) pendingViewUris = listOf(uri.toString())
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtraCompat(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) pendingViewUris = uris.map { it.toString() }
            }
            Intent.ACTION_PICK, Intent.ACTION_GET_CONTENT -> {
                pickMode = true
            }
        }
    }

    private fun handlePick(item: MediaItem, scope: kotlinx.coroutines.CoroutineScope) {
        if (item.isLocal && item.localUri != null) {
            finishPick(Uri.parse(item.localUri))
        } else {
            Toast.makeText(this, "Downloading ${item.title}", Toast.LENGTH_SHORT).show()
            scope.launch {
                val result = au.prism.photos.ui.viewer.MediaActions.download(this@MainActivity, listOf(item))
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, "Saved to Downloads. Choose it again from the Device tab.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Could not download this item", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun finishPick(uri: Uri) {
        val result = Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(RESULT_OK, result)
        finish()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipState.videoPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
            PipState.aspect?.let { builder.setAspectRatio(it) }
            try {
                enterPictureInPictureMode(builder.build())
            } catch (_: IllegalStateException) {
                // Device does not support PiP right now; ignore.
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

private fun Intent.getParcelableExtraCompat(name: String): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }

private fun Intent.getParcelableArrayListExtraCompat(name: String): ArrayList<Uri>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(name, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(name)
    }
