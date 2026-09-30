package au.prism.photos.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
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
import au.prism.photos.data.upload.ConnectionTester
import au.prism.photos.data.upload.DestinationType
import au.prism.photos.data.upload.SyncScheduler
import au.prism.photos.domain.ConnectionMode
import au.prism.photos.domain.DeviceAlbum
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
            item { ListItem(headlineContent = { Text("Server") }, supportingContent = { Text(session.server?.name ?: "Not connected") }) }
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
            item { ClickableRow("Theme (${settings.theme.name.lowercase().replaceFirstChar { it.uppercase() }})", { themeDialog = true }) }
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
            item { ClickableRow("Thumbnail quality (${settings.thumbQuality.name.lowercase().replaceFirstChar { it.uppercase() }})", { qualityDialog = true }) }

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
            item { SectionHeader("Sync to Plex") }
            item { SyncToPlexSection() }

            item { HorizontalDivider() }
            item { SectionHeader("Diagnostics") }
            item { DiagnosticsPanel(onReload = { scope.launch { graph.media.refreshTimeline(force = true) } }) }
            item { SectionHeader("About") }
            item {
                ListItem(
                    headlineContent = { Text("Plex Gallery") },
                    supportingContent = { Text("A clean, professional photo library client for Plex Media Server. Plex Gallery is not affiliated with Plex Inc.") },
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

@Composable
private fun DiagnosticsPanel(onReload: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lines by au.prism.photos.data.Diagnostics.lines.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "What the app last did when loading your library. Copy this if something is not showing up.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(12.dp)) {
                if (lines.isEmpty()) {
                    Text("Nothing logged yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    lines.takeLast(12).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
        val lastCrash = remember { au.prism.photos.data.CrashLog.read(context) }
        if (lastCrash != null) {
            Spacer(Modifier.height(8.dp))
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(12.dp)) {
                    Text("The app crashed last time. Copy this and send it along.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(lastCrash.lineSequence().take(12).joinToString(separator = System.lineSeparator()), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Crash", lastCrash))
                        }) { Text("Copy crash report") }
                        OutlinedButton(onClick = { au.prism.photos.data.CrashLog.clear(context) }) { Text("Dismiss") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onReload) { Text("Reload library") }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostics", au.prism.photos.data.Diagnostics.asText()))
            }) { Text("Copy diagnostics") }
        }
    }
}

/**
 * Configures where device photos and videos are backed up (SMB or WebDAV), the destination
 * folder on the Plex server, and automatic background sync. See docs/plex-api.md "Uploading to
 * the server" - Plex has no upload API, this writes into the library folder over the network.
 */
@Composable
private fun SyncToPlexSection() {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncSettings by graph.uploadSettings.settings.collectAsStateWithLifecycle()
    val destination = syncSettings.destination

    var smbHost by remember(destination.smbHost) { mutableStateOf(destination.smbHost) }
    var smbShare by remember(destination.smbShare) { mutableStateOf(destination.smbShare) }
    var smbPath by remember(destination.smbPath) { mutableStateOf(destination.smbPath) }
    var smbUser by remember(destination.smbUsername) { mutableStateOf(destination.smbUsername) }
    var smbPass by remember(destination.smbPassword) { mutableStateOf(destination.smbPassword) }
    var smbDomain by remember(destination.smbDomain) { mutableStateOf(destination.smbDomain) }
    var webDavUrl by remember(destination.webDavBaseUrl) { mutableStateOf(destination.webDavBaseUrl) }
    var webDavUser by remember(destination.webDavUsername) { mutableStateOf(destination.webDavUsername) }
    var webDavPass by remember(destination.webDavPassword) { mutableStateOf(destination.webDavPassword) }
    var serverFolderPath by remember(syncSettings.serverFolderPath) { mutableStateOf(syncSettings.serverFolderPath) }
    var subFolderPattern by remember(syncSettings.subFolderPattern) { mutableStateOf(syncSettings.subFolderPattern) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var deviceAlbums by remember { mutableStateOf<List<DeviceAlbum>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (graph.device.hasPermission()) deviceAlbums = graph.device.albums()
        if (syncSettings.serverFolderPath.isBlank()) {
            graph.libraryLocationResolver.currentLibraryPath()?.let { path ->
                serverFolderPath = path
                graph.uploadSettings.update { it.copy(serverFolderPath = path) }
            }
        }
    }

    fun updateDestination(transform: (au.prism.photos.data.upload.UploadDestination) -> au.prism.photos.data.upload.UploadDestination) {
        scope.launch { graph.uploadSettings.update { it.copy(destination = transform(it.destination)) } }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    fun setAutoSync(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch { graph.uploadSettings.update { it.copy(autoSyncEnabled = enabled) } }
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Destination type", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(
                selected = destination.type == DestinationType.SMB,
                onClick = { updateDestination { it.copy(type = DestinationType.SMB) } },
            )
            Text("SMB", modifier = Modifier.padding(end = 16.dp))
            RadioButton(
                selected = destination.type == DestinationType.WEBDAV,
                onClick = { updateDestination { it.copy(type = DestinationType.WEBDAV) } },
            )
            Text("WebDAV")
        }

        if (destination.type == DestinationType.SMB) {
            OutlinedTextField(
                value = smbHost,
                onValueChange = { smbHost = it; updateDestination { d -> d.copy(smbHost = smbHost) } },
                label = { Text("Host or IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbShare,
                onValueChange = { smbShare = it; updateDestination { d -> d.copy(smbShare = smbShare) } },
                label = { Text("Share name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbPath,
                onValueChange = { smbPath = it; updateDestination { d -> d.copy(smbPath = smbPath) } },
                label = { Text("Path inside the share (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbUser,
                onValueChange = { smbUser = it; updateDestination { d -> d.copy(smbUsername = smbUser) } },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbPass,
                onValueChange = { smbPass = it; updateDestination { d -> d.copy(smbPassword = smbPass) } },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbDomain,
                onValueChange = { smbDomain = it; updateDestination { d -> d.copy(smbDomain = smbDomain) } },
                label = { Text("Domain (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        } else {
            OutlinedTextField(
                value = webDavUrl,
                onValueChange = { webDavUrl = it; updateDestination { d -> d.copy(webDavBaseUrl = webDavUrl) } },
                label = { Text("WebDAV address") },
                placeholder = { Text("https://nas.local:5006/photos") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = webDavUser,
                onValueChange = { webDavUser = it; updateDestination { d -> d.copy(webDavUsername = webDavUser) } },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = webDavPass,
                onValueChange = { webDavPass = it; updateDestination { d -> d.copy(webDavPassword = webDavPass) } },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !testing,
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        val result = ConnectionTester.test(destination, context.contentResolver, graph.uploadHttpClient)
                        testResult = result.fold({ it }, { "Failed: ${it.message ?: "unknown error"}" })
                        testing = false
                    }
                },
            ) { Text(if (testing) "Testing…" else "Test connection") }
        }
        testResult?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = serverFolderPath,
            onValueChange = { serverFolderPath = it; scope.launch { graph.uploadSettings.update { s -> s.copy(serverFolderPath = serverFolderPath) } } },
            label = { Text("Library folder path on the server") },
            supportingText = { Text("From Plex: Directory > Location > path. Narrows the rescan to this folder.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = subFolderPattern,
            onValueChange = { subFolderPattern = it; scope.launch { graph.uploadSettings.update { s -> s.copy(subFolderPattern = subFolderPattern) } } },
            label = { Text("Upload sub folder pattern") },
            supportingText = { Text("Tokens: {device} {yyyy} {MM} {dd}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Spacer(Modifier.height(16.dp))
        ListItem(
            headlineContent = { Text("Automatically sync new photos") },
            supportingContent = { Text("Back up new items from the albums below in the background") },
            trailingContent = { Switch(checked = syncSettings.autoSyncEnabled, onCheckedChange = { setAutoSync(it) }) },
        )
        if (deviceAlbums.isNotEmpty()) {
            Text("Albums to back up", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(deviceAlbums, key = { it.bucketId }) { album ->
                    val selected = album.bucketId in syncSettings.selectedBuckets
                    FilterChip(
                        selected = selected,
                        onClick = {
                            scope.launch {
                                graph.uploadSettings.update { s ->
                                    s.copy(selectedBuckets = if (selected) s.selectedBuckets - album.bucketId else s.selectedBuckets + album.bucketId)
                                }
                            }
                        },
                        label = { Text("${album.name} (${album.count})") },
                    )
                }
            }
        }
        ListItem(
            headlineContent = { Text("Wi-Fi only") },
            trailingContent = { Switch(checked = syncSettings.wifiOnly, onCheckedChange = { v -> scope.launch { graph.uploadSettings.update { it.copy(wifiOnly = v) } } }) },
        )
        ListItem(
            headlineContent = { Text("Charging only") },
            trailingContent = { Switch(checked = syncSettings.chargingOnly, onCheckedChange = { v -> scope.launch { graph.uploadSettings.update { it.copy(chargingOnly = v) } } }) },
        )

        Spacer(Modifier.height(8.dp))
        Button(onClick = { SyncScheduler.syncNow(context, syncSettings.wifiOnly, syncSettings.chargingOnly) }) { Text("Sync now") }
        val lastSync = syncSettings.lastSyncAt
        Text(
            if (lastSync > 0) {
                val when_ = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSync))
                "Last sync: $when_  ·  ${syncSettings.lastSyncUploaded} uploaded, ${syncSettings.lastSyncFailed} failed"
            } else {
                "Never synced yet"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
