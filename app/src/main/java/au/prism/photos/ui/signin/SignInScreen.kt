package au.prism.photos.ui.signin

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.prism.photos.PrismApp
import au.prism.photos.ui.theme.PlexGold

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onManualConnected: () -> Unit,
) {
    val graph = PrismApp.graph
    val vm: SignInViewModel = viewModel(factory = viewModelFactory { initializer { SignInViewModel(graph) } })
    val context = LocalContext.current
    var manualExpanded by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf(graph.settings.settings.value.manualServerUrl) }
    var manualToken by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = PlexGold.copy(alpha = 0.15f),
                modifier = Modifier.size(88.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = PlexGold, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Prism", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your Plex photos, done properly",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))

            when (val state = vm.state) {
                is SignInUiState.Waiting -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Waiting for you to finish signing in", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(state.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = PlexGold)
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { vm.cancelWaiting() }) { Text("Cancel") }
                        }
                    }
                }
                is SignInUiState.Signing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Signing in…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is SignInUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    SignInButton {
                        vm.startPlexSignIn(
                            onUrlReady = { url -> openCustomTab(context, url) },
                            onSignedIn = onSignedIn,
                        )
                    }
                }
                SignInUiState.Idle -> {
                    SignInButton {
                        vm.startPlexSignIn(
                            onUrlReady = { url -> openCustomTab(context, url) },
                            onSignedIn = onSignedIn,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            TextButton(onClick = { manualExpanded = !manualExpanded }) {
                Text("Connect manually")
                Icon(if (manualExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
            }
            AnimatedVisibility(visible = manualExpanded) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = { manualUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://192.168.1.20:32400") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = { manualToken = it },
                        label = { Text("Plex token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (vm.manualError != null) {
                        Text(vm.manualError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            enabled = manualUrl.isNotBlank() && manualToken.isNotBlank() && !vm.manualLoading,
                            onClick = { vm.verifyManual(manualUrl, manualToken, onManualConnected) },
                        ) {
                            if (vm.manualLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SignInButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Sign in with Plex", fontWeight = FontWeight.SemiBold)
    }
}

private fun openCustomTab(context: android.content.Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}
