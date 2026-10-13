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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.prism.photos.R
import au.prism.photos.BuildConfig
import au.prism.photos.PrismApp
import au.prism.photos.data.upload.ConnectionTester
import au.prism.photos.data.upload.DestinationType
import au.prism.photos.data.upload.SyncScheduler
import au.prism.photos.data.upload.isInsecureRemoteUrl
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
    val reconnectedMessage = stringResource(R.string.settings_reconnected)
    val reconnectFailedMessage = stringResource(R.string.settings_reconnect_failed)
    val cacheClearedMessage = stringResource(R.string.settings_cache_cleared)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader(stringResource(R.string.settings_section_account)) }
            item { ListItem(headlineContent = { Text(session.user?.title ?: stringResource(R.string.settings_not_signed_in)) }, supportingContent = { Text(session.user?.email.orEmpty()) }) }
            item { ListItem(headlineContent = { Text(stringResource(R.string.settings_server)) }, supportingContent = { Text(session.server?.name ?: stringResource(R.string.settings_not_connected)) }) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_connection)) },
                    supportingContent = { Text("${session.active?.kind ?: "–"} · ${session.active?.uri ?: ""}") },
                )
            }
            item { ClickableRow(stringResource(R.string.settings_switch_server), onSwitchServer) }
            item { ClickableRow(stringResource(R.string.settings_switch_library, session.libraryTitle ?: stringResource(R.string.settings_library_none)), onSwitchLibrary) }
            item { ClickableRow(stringResource(R.string.settings_sign_out), { signOutConfirm = true }, danger = true) }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.settings_section_connection)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    listOf(
                        ConnectionMode.AUTO to stringResource(R.string.settings_connection_auto),
                        ConnectionMode.LAN to stringResource(R.string.settings_connection_lan),
                        ConnectionMode.REMOTE to stringResource(R.string.settings_connection_remote),
                    ).forEach { (mode, label) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = settings.connectionMode == mode, onClick = { scope.launch { graph.settings.update { it.copy(connectionMode = mode) } } })
                            Text(label)
                        }
                    }
                    OutlinedTextField(
                        value = manualLan,
                        onValueChange = { manualLan = it; scope.launch { graph.settings.update { s -> s.copy(manualServerUrl = manualLan) } } },
                        label = { Text(stringResource(R.string.settings_manual_lan_address)) },
                        placeholder = { Text(stringResource(R.string.settings_manual_lan_placeholder)) },
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
                                    .onSuccess { active -> graph.session.update { it.copy(active = active) }; snackbarHostState.showSnackbar(reconnectedMessage) }
                                    .onFailure { snackbarHostState.showSnackbar(it.message ?: reconnectFailedMessage) }
                                reconnecting = false
                            }
                        },
                    ) { Text(stringResource(R.string.settings_reconnect_now)) }
                }
            }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item { ClickableRow(stringResource(R.string.settings_theme_row, themeLabel(settings.theme)), { themeDialog = true }) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_colour)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_colour_summary)) },
                    trailingContent = {
                        Switch(checked = settings.dynamicColour, onCheckedChange = { v -> scope.launch { graph.settings.update { it.copy(dynamicColour = v) } } })
                    },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.settings_grid_columns, settings.gridColumns), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.gridColumns.toFloat(),
                        onValueChange = { v -> scope.launch { graph.settings.update { it.copy(gridColumns = v.toInt().coerceIn(2, 6)) } } },
                        valueRange = 2f..6f,
                        steps = 3,
                    )
                }
            }
            item { ClickableRow(stringResource(R.string.settings_thumb_quality_row, thumbQualityLabel(settings.thumbQuality)), { qualityDialog = true }) }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.settings_section_playback)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_prefer_transcoding)) },
                    supportingContent = { Text(stringResource(R.string.settings_prefer_transcoding_summary)) },
                    trailingContent = {
                        Switch(checked = settings.preferTranscode, onCheckedChange = { v -> scope.launch { graph.settings.update { it.copy(preferTranscode = v) } } })
                    },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.settings_slideshow_interval, settings.slideshowIntervalSec), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.slideshowIntervalSec.toFloat(),
                        onValueChange = { v -> scope.launch { graph.settings.update { it.copy(slideshowIntervalSec = v.toInt().coerceIn(2, 15)) } } },
                        valueRange = 2f..15f,
                        steps = 12,
                    )
                }
            }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.settings_section_updates)) }
            item {
                OutlinedTextField(
                    value = updateRepo,
                    onValueChange = { updateRepo = it; scope.launch { graph.settings.update { s -> s.copy(updateRepo = updateRepo) } } },
                    label = { Text(stringResource(R.string.settings_update_repo)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_auto_check_updates)) },
                    trailingContent = { Switch(checked = settings.autoCheckUpdates, onCheckedChange = { v -> scope.launch { graph.settings.update { it.copy(autoCheckUpdates = v) } } }) },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(onClick = { updateVm.checkNow(manual = true) }, enabled = !updateVm.checking) { Text(stringResource(if (updateVm.checking) R.string.settings_checking else R.string.settings_check_for_updates)) }
                    updateVm.manualMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                }
            }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.settings_section_storage)) }
            item {
                ClickableRow(stringResource(R.string.settings_clear_cache), {
                    scope.launch { graph.media.clearCache(); snackbarHostState.showSnackbar(cacheClearedMessage) }
                })
            }
            item { ListItem(headlineContent = { Text(stringResource(R.string.settings_version)) }, supportingContent = { Text(BuildConfig.VERSION_NAME) }) }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.settings_section_gallery)) }
            item {
                ClickableRow(stringResource(R.string.settings_set_default_gallery), {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                })
            }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.settings_section_sync)) }
            item { SyncToPlexSection() }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.settings_section_diagnostics)) }
            item { DiagnosticsPanel(onReload = { scope.launch { graph.media.refreshTimeline(force = true) } }) }
            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_about_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_about_body)) },
                )
            }
        }
    }

    if (updateVm.dialogOpen) UpdateDialog(updateVm, onDismiss = { updateVm.dismissDialog() })

    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(onClick = {
                    signOutConfirm = false
                    scope.launch {
                        graph.auth.signOut(session.accountToken)
                        graph.session.clear()
                        onSignedOut()
                    }
                }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { signOutConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text(stringResource(R.string.settings_theme)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = settings.theme == mode, onClick = { scope.launch { graph.settings.update { it.copy(theme = mode) } }; themeDialog = false })
                            Text(themeLabel(mode))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }

    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text(stringResource(R.string.settings_thumb_quality)) },
            text = {
                Column {
                    ThumbQuality.entries.forEach { q ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = settings.thumbQuality == q, onClick = { scope.launch { graph.settings.update { it.copy(thumbQuality = q) } }; qualityDialog = false })
                            Text(thumbQualityLabel(q))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { qualityDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.AMOLED -> R.string.settings_theme_amoled
    },
)

@Composable
private fun thumbQualityLabel(quality: ThumbQuality): String = stringResource(
    when (quality) {
        ThumbQuality.LOW -> R.string.settings_thumb_quality_low
        ThumbQuality.MEDIUM -> R.string.settings_thumb_quality_medium
        ThumbQuality.HIGH -> R.string.settings_thumb_quality_high
    },
)

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
            stringResource(R.string.settings_diagnostics_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(12.dp)) {
                if (lines.isEmpty()) {
                    Text(stringResource(R.string.settings_diagnostics_empty), style = MaterialTheme.typography.bodySmall)
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
                    Text(stringResource(R.string.settings_crash_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(lastCrash.lineSequence().take(12).joinToString(separator = System.lineSeparator()), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Crash", lastCrash))
                        }) { Text(stringResource(R.string.settings_copy_crash_report)) }
                        OutlinedButton(onClick = { au.prism.photos.data.CrashLog.clear(context) }) { Text(stringResource(R.string.settings_dismiss)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onReload) { Text(stringResource(R.string.settings_reload_library)) }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostics", au.prism.photos.data.Diagnostics.asText()))
            }) { Text(stringResource(R.string.settings_copy_diagnostics)) }
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
    val unknownError = stringResource(R.string.sync_unknown_error)

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
        Text(stringResource(R.string.sync_destination_type), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(
                selected = destination.type == DestinationType.SMB,
                onClick = { updateDestination { it.copy(type = DestinationType.SMB) } },
            )
            Text(stringResource(R.string.sync_type_smb), modifier = Modifier.padding(end = 16.dp))
            RadioButton(
                selected = destination.type == DestinationType.WEBDAV,
                onClick = { updateDestination { it.copy(type = DestinationType.WEBDAV) } },
            )
            Text(stringResource(R.string.sync_type_webdav))
        }

        if (destination.type == DestinationType.SMB) {
            OutlinedTextField(
                value = smbHost,
                onValueChange = { smbHost = it; updateDestination { d -> d.copy(smbHost = smbHost) } },
                label = { Text(stringResource(R.string.sync_smb_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbShare,
                onValueChange = { smbShare = it; updateDestination { d -> d.copy(smbShare = smbShare) } },
                label = { Text(stringResource(R.string.sync_smb_share)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbPath,
                onValueChange = { smbPath = it; updateDestination { d -> d.copy(smbPath = smbPath) } },
                label = { Text(stringResource(R.string.sync_smb_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbUser,
                onValueChange = { smbUser = it; updateDestination { d -> d.copy(smbUsername = smbUser) } },
                label = { Text(stringResource(R.string.sync_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbPass,
                onValueChange = { smbPass = it; updateDestination { d -> d.copy(smbPassword = smbPass) } },
                label = { Text(stringResource(R.string.sync_password)) },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = smbDomain,
                onValueChange = { smbDomain = it; updateDestination { d -> d.copy(smbDomain = smbDomain) } },
                label = { Text(stringResource(R.string.sync_smb_domain)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        } else {
            OutlinedTextField(
                value = webDavUrl,
                onValueChange = { webDavUrl = it; updateDestination { d -> d.copy(webDavBaseUrl = webDavUrl) } },
                label = { Text(stringResource(R.string.sync_webdav_address)) },
                placeholder = { Text(stringResource(R.string.sync_webdav_placeholder)) },
                singleLine = true,
                isError = isInsecureRemoteUrl(webDavUrl),
                supportingText = if (isInsecureRemoteUrl(webDavUrl)) {
                    { Text(stringResource(R.string.webdav_insecure_warning)) }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = webDavUser,
                onValueChange = { webDavUser = it; updateDestination { d -> d.copy(webDavUsername = webDavUser) } },
                label = { Text(stringResource(R.string.sync_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = webDavPass,
                onValueChange = { webDavPass = it; updateDestination { d -> d.copy(webDavPassword = webDavPass) } },
                label = { Text(stringResource(R.string.sync_password)) },
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
                        testResult = result.fold({ it }, { context.getString(R.string.sync_test_failed, it.message ?: unknownError) })
                        testing = false
                    }
                },
            ) { Text(stringResource(if (testing) R.string.sync_testing else R.string.sync_test_connection)) }
        }
        testResult?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = serverFolderPath,
            onValueChange = { serverFolderPath = it; scope.launch { graph.uploadSettings.update { s -> s.copy(serverFolderPath = serverFolderPath) } } },
            label = { Text(stringResource(R.string.sync_server_folder_path)) },
            supportingText = { Text(stringResource(R.string.sync_server_folder_path_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = subFolderPattern,
            onValueChange = { subFolderPattern = it; scope.launch { graph.uploadSettings.update { s -> s.copy(subFolderPattern = subFolderPattern) } } },
            label = { Text(stringResource(R.string.sync_sub_folder_pattern)) },
            supportingText = { Text(stringResource(R.string.sync_sub_folder_pattern_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Spacer(Modifier.height(16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.sync_auto_sync)) },
            supportingContent = { Text(stringResource(R.string.sync_auto_sync_summary)) },
            trailingContent = { Switch(checked = syncSettings.autoSyncEnabled, onCheckedChange = { setAutoSync(it) }) },
        )
        if (deviceAlbums.isNotEmpty()) {
            Text(stringResource(R.string.sync_albums_to_back_up), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
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
                        label = { Text(stringResource(R.string.sync_album_chip, album.name, album.count)) },
                    )
                }
            }
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.sync_wifi_only)) },
            trailingContent = { Switch(checked = syncSettings.wifiOnly, onCheckedChange = { v -> scope.launch { graph.uploadSettings.update { it.copy(wifiOnly = v) } } }) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.sync_charging_only)) },
            trailingContent = { Switch(checked = syncSettings.chargingOnly, onCheckedChange = { v -> scope.launch { graph.uploadSettings.update { it.copy(chargingOnly = v) } } }) },
        )

        Spacer(Modifier.height(8.dp))
        Button(onClick = { SyncScheduler.syncNow(context, syncSettings.wifiOnly, syncSettings.chargingOnly) }) { Text(stringResource(R.string.sync_now)) }
        val lastSync = syncSettings.lastSyncAt
        Text(
            if (lastSync > 0) {
                val when_ = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSync))
                stringResource(R.string.sync_last_sync, when_, syncSettings.lastSyncUploaded, syncSettings.lastSyncFailed)
            } else {
                stringResource(R.string.sync_never_synced)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
