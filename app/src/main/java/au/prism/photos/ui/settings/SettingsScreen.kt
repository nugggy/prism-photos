package au.prism.photos.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.prism.photos.BuildConfig
import au.prism.photos.PrismApp
import au.prism.photos.domain.ConnectionMode
import au.prism.photos.domain.ThemeMode
import au.prism.photos.domain.ThumbQuality
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.ui.update.UpdateDialog
import au.prism.photos.ui.update.UpdateViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSwitchServer: () -> Unit,
    onSwitchLibrary: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val session by graph.session.session.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val updateVm: UpdateViewModel = viewModel(factory = viewModelFactory { initializer { UpdateViewModel(graph) } })

    var manualLan by remember(settings.manualServerUrl) { mutableStateOf(settings.manualServerUrl) }
    var updateRepo by remember(settings.updateRepo) { mutableStateOf(settings.updateRepo.ifBlank { BuildConfig.UPDATE_REPO }) }
    var reconnecting by remember { mutableStateOf(false) }
    var signOutConfirm by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var qualityDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Account") }
            item { ListItem(headlineContent = { Text(session.user?.title ?: "Not signed in") }, supportingContent = { Text(session.user?.email.orEmpty()) }) }
            item { ListItem(headlineContent = { Text("Server") }, supportingContent = { Text(session.server?.name ?: "–") }) }
            item {
                ListItem(
                    headlineContent = { Text("Connection") },
                    supportingContent = { Text("${session.active?.kind ?: "–"} · ${session.active?.uri ?: ""}") },
                )
            }
            item { ClickableRow("Switch server", onSwitchServer) }
            item { ClickableRow("Switch library (${session.libraryTitle ?: "none"})", onSwitchLibrary) }
            item { ClickableRow("Sign out", { signOutConfirm = true }, danger = true) }

            item { HorizontalDivider() }
            item { SectionHeader("Connection") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    listOf(ConnectionMode.AUTO to "Auto", ConnectionMode.LAN to "Same network as server", ConnectionMode.REMOTE to "Remote only").forEach { (mode, label) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = settings.connectionMode == mode, onClick = { scope.launch { graph.settings.update { it.copy(connectionMode = mode) } } })
                            Text(label)
                        }
                    }
                    OutlinedTextField(
                        value = manualLan,
                        onValueChange = { manualLan = it; scope.launch { graph.settings.update { s -> s.copy(manualServerUrl = manualLan) } } },
                        label = { Text("Manual LAN address") },
                        placeholder = { Text("http://192.168.1.20:32400") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    Button(
                        enabled = !reconnecting && session.server != null,
                        onClick = {
                            val server = session.server ?: return@Button
                            scope.launch {
                                reconnecting = true
                                graph.auth.connect(server, settings.connectionMode, settings.manualServerUrl.ifBlank { null })
                                    .onSuccess { active -> graph.session.update { it.copy(active = active) }; snackbarHostState.showSnackbar("Reconnected") }
                                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Could not reconnect") }
                                reconnecting = false
                            }
                        },
                    ) { Text("Reconnect now") }
                }
            }

            item { HorizontalDivider() }
            item { SectionHeader("Appearance") }
            item { ClickableRow("Theme (${settings.theme})", { themeDialog = true }) }
            item {
                ListItem(
                    headlineContent = { Text("Dynamic colour") },
                    supportingContent = { Text("Use Material You colours from your wallpaper") },
                    trailingContent = {
                        Switch(checked = settings.dynamicColour, onCheckedChange = { v -> scope.launch { graph.settings.update { it.copy(dynamicColour = v) } } })
                    },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Default grid columns: ${settings.gridColumns}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.gridColumns.toFloat(),
                        onValueChange = { v -> scope.launch { graph.settings.update { it.copy(gridColumns = v.toInt().coerceIn(2, 6)) } } },
                        valueRange = 2f..6f,
                        steps = 3,
                    )
                }
            }
            item { ClickableRow("Thumbnail quality (${settings.thumbQuality})", { qualityDialog = true }) }

            item { HorizontalDivider() }
            item { SectionHeader("Playback") }
            item {
                ListItem(
                    headlineContent = { Text("Prefer transcoding") },
                    supportingContent = { Text("Use server transcode instead of direct play") },
                    trailingContent = {
                        Switch(checked = settings.preferTranscode, onCheckedChange = { v -> scope.launch { graph.settings.update { it.copy(preferTranscode = v) } } })
                    },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Slideshow interval: ${settings.slideshowIntervalSec}s", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.slideshowIntervalSec.toFloat(),
                        onValueChange = { v -> scope.launch { graph.settings.update { it.copy(slideshowIntervalSec = v.toInt().coerceIn(2, 15)) } } },
                        valueRange = 2f..15f,
                        steps = 12,
                    )
                }
            }

            item { HorizontalDivider() }
            item { SectionHeader("Updates") }
            item {
                OutlinedTextField(
                    value = updateRepo,
                    onValueChange = { updateRepo = it; scope.launch { graph.settings.update { s -> s.copy(updateRepo = updateRepo) } } },
                    label = { Text("GitHub repository (owner/name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Automatically check for updates") },
                    trailingContent = { Switch(checked = settings.autoCheckUpdates, onCheckedChange = { v -> scope.launch { graph.settings.update { it.copy(autoCheckUpdates = v) } } }) },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(onClick = { updateVm.checkNow(manual = true) }, enabled = !updateVm.checking) { Text(if (updateVm.checking) "Checking" else "Check for updates") }
                    updateVm.manualMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                }
            }

            item { HorizontalDivider() }
            item { SectionHeader("Storage") }
            item {
                ClickableRow("Clear cache", {
                    scope.launch { graph.media.clearCache(); snackbarHostState.showSnackbar("Cache cleared") }
                })
            }
            item { ListItem(headlineContent = { Text("Version") }, supportingContent = { Text(BuildConfig.VERSION_NAME) }) }

            item { HorizontalDivider() }
            item { SectionHeader("Gallery") }
            item {
                ClickableRow("Set as default gallery", {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                })
            }

            item { HorizontalDivider() }
            item { SectionHeader("About") }
            item {
                ListItem(
                    headlineContent = { Text("Prism") },
                    supportingContent = { Text("A clean, professional photo library client for Plex Media Server. Prism is not affiliated with Plex Inc.") },
                )
            }
        }
    }

    if (updateVm.dialogOpen) UpdateDialog(updateVm, onDismiss = { updateVm.dismissDialog() })

    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            title = { Text("Sign out?") },
            text = { Text("You will need to sign in again to see your photos.") },
            confirmButton = {
                TextButton(onClick = {
                    signOutConfirm = false
                    scope.launch {
                        graph.auth.signOut(session.accountToken)
                        graph.session.clear()
                        onSignedOut()
                    }
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { signOutConfirm = false }) { Text("Cancel") } },
        )
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = settings.theme == mode, onClick = { scope.launch { graph.settings.update { it.copy(theme = mode) } }; themeDialog = false })
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Close") } },
        )
    }

    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text("Thumbnail quality") },
            text = {
                Column {
                    ThumbQuality.entries.forEach { q ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = settings.thumbQuality == q, onClick = { scope.launch { graph.settings.update { it.copy(thumbQuality = q) } }; qualityDialog = false })
                            Text(q.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { qualityDialog = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = PlexGold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ClickableRow(title: String, onClick: () -> Unit, danger: Boolean = false) {
    ListItem(
        headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
