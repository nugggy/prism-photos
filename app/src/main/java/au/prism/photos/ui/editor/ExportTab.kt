package au.prism.photos.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.ui.theme.PlexGold
import kotlin.math.roundToInt

@Composable
fun ExportTab(
    vm: EditorViewModel,
    settings: ExportSettings,
    onSaveCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets by vm.presets.collectAsStateWithLifecycle()
    val hasClipboard by vm.hasClipboard.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }
    var quality by remember(settings.quality) { mutableStateOf(settings.quality.toFloat()) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Looks", style = MaterialTheme.typography.titleSmall, color = PlexGold)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Save preset")
            }
            OutlinedButton(onClick = { vm.copyEdits() }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Copy edits")
            }
            OutlinedButton(onClick = { vm.pasteEdits() }, enabled = hasClipboard) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Paste edits")
            }
        }
        if (presets.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                items(presets) { preset ->
                    AssistChip(
                        onClick = { vm.applyPreset(preset) },
                        label = { Text(preset.name) },
                        trailingIcon = {
                            IconButton(onClick = { vm.deletePreset(preset.id) }, modifier = Modifier.padding(0.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete preset", modifier = Modifier.padding(2.dp))
                            }
                        },
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Format", style = MaterialTheme.typography.titleSmall, color = PlexGold)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEach { fmt ->
                FilterChip(selected = settings.format == fmt, onClick = { vm.setExportSettings(settings.copy(format = fmt)) }, label = { Text(fmt.label) })
            }
        }

        if (settings.format != ExportFormat.PNG) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Quality", style = MaterialTheme.typography.labelMedium)
                Text("${quality.roundToInt()}", style = MaterialTheme.typography.labelMedium, color = PlexGold)
            }
            Slider(
                value = quality,
                onValueChange = { quality = it },
                onValueChangeFinished = { vm.setExportSettings(settings.copy(quality = quality.roundToInt())) },
                valueRange = 40f..100f,
                colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
            )
        }

        Text("Size", style = MaterialTheme.typography.titleSmall, color = PlexGold, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportSizeChoice.entries.forEach { size ->
                FilterChip(selected = settings.size == size, onClick = { vm.setExportSettings(settings.copy(size = size)) }, label = { Text(size.label) })
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSaveCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Save copy")
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Share")
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save preset") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Preset name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { vm.savePreset(name); showSaveDialog = false }) { Text("Save", color = PlexGold) }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
        )
    }
}
