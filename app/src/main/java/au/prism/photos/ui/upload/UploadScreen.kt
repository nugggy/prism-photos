package au.prism.photos.ui.upload

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import au.prism.photos.PrismApp
import au.prism.photos.data.upload.DestinationType
import au.prism.photos.data.upload.DeviceFileInfo
import au.prism.photos.data.upload.DeviceFileInspector
import au.prism.photos.data.upload.SubFolderPattern
import au.prism.photos.data.upload.SyncSettings
import au.prism.photos.data.upload.UploadReport
import au.prism.photos.domain.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Uploads the device items (content:// URIs) in [uris] to the Plex library folder over SMB or
 * WebDAV (see docs/plex-api.md "Uploading to the server"), lets the user pick or create the
 * destination folder, shows progress, then asks Plex to scan. Calls [onDone] when finished.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    uris: List<String>,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val graph = PrismApp.graph
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf<SyncSettings>(graph.uploadSettings.settings.value) }
    LaunchedEffect(Unit) { graph.uploadSettings.settings.collect { settings = it } }

    var fileInfos by remember { mutableStateOf<List<DeviceFileInfo>>(emptyList()) }
    LaunchedEffect(uris) {
        fileInfos = withContext(Dispatchers.IO) {
            uris.map { DeviceFileInspector.inspect(context.contentResolver, Uri.parse(it)) }
        }
    }

    var folderAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    LaunchedEffect(Unit) { graph.media.rootAlbums().onSuccess { folderAlbums = it.albums } }

    val destinationConfigured = when (settings.destination.type) {
        DestinationType.SMB -> settings.destination.smbHost.isNotBlank() && settings.destination.smbShare.isNotBlank()
        DestinationType.WEBDAV -> settings.destination.webDavBaseUrl.isNotBlank()
    }

    var selectedAlbumTitle by remember { mutableStateOf<String?>(null) }
    var useNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val defaultPattern = remember(settings.subFolderPattern) {
        SubFolderPattern.resolve(settings.subFolderPattern, android.os.Build.MODEL ?: "device", System.currentTimeMillis())
    }

    var uploading by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(uris.size) }
    var currentName by remember { mutableStateOf("") }
    var fraction by remember { mutableFloatStateOf(0f) }
    var report by remember { mutableStateOf<UploadReport?>(null) }

    fun startUpload() {
        val folder = when {
            useNewFolder && newFolderName.isNotBlank() -> newFolderName.trim()
            selectedAlbumTitle != null -> selectedAlbumTitle
            else -> null // falls back to the sub folder pattern in UploadService
        }
        uploading = true
        report = null
        done = 0
        total = uris.size
        scope.launch {
            val result = graph.uploadService.uploadItems(uris, folder) { d, t, name, f ->
                done = d; total = t; currentName = name; fraction = f
            }
            report = result
            uploading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload to Plex") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            val totalBytes = fileInfos.sumOf { it.size }
            Text(
                if (fileInfos.isEmpty()) "${uris.size} item(s)" else "${uris.size} item(s) · ${formatBytes(totalBytes)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))

            if (!destinationConfigured) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No sync destination set up", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Open Settings and fill in \"Sync to Plex\" with your server's SMB share or WebDAV address before uploading.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        Button(onClick = { (onOpenSettings ?: onCancel)() }) { Text("Go to Settings") }
                    }
                }
                return@Column
            }

            if (report == null) {
                Text("Destination folder", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !useNewFolder && selectedAlbumTitle == null,
                            onClick = { useNewFolder = false; selectedAlbumTitle = null },
                        )
                        Text("Default: $defaultPattern", style = MaterialTheme.typography.bodyMedium)
                    }
                    folderAlbums.forEach { album ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = !useNewFolder && selectedAlbumTitle == album.title,
                                onClick = { useNewFolder = false; selectedAlbumTitle = album.title },
                            )
                            Text(album.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = useNewFolder, onClick = { useNewFolder = true })
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("New folder") },
                            singleLine = true,
                            enabled = !uploading,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))
            }

            if (uploading) {
                Text("Uploading $done of $total  ·  $currentName", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                val overall = if (total > 0) (done + fraction) / total else 0f
                Text("${(overall * 100).toInt()}% overall", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val r = report
                if (r != null) {
                    Text("Uploaded ${r.uploaded}, already synced ${r.skipped}, failed ${r.failed}", style = MaterialTheme.typography.titleSmall)
                    if (r.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(12.dp)) {
                                r.errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                } else {
                    Button(
                        onClick = { startUpload() },
                        enabled = uris.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Upload") }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.2f GB", gb)
}
