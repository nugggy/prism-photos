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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.R
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
        Text(stringResource(R.string.editor_looks), style = MaterialTheme.typography.titleSmall, color = PlexGold)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.editor_save_preset))
            }
            OutlinedButton(onClick = { vm.copyEdits() }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.editor_copy_edits))
            }
            OutlinedButton(onClick = { vm.pasteEdits() }, enabled = hasClipboard) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.editor_paste_edits))
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
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.editor_delete_preset), modifier = Modifier.padding(2.dp))
                            }
                        },
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(stringResource(R.string.editor_format), style = MaterialTheme.typography.titleSmall, color = PlexGold)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEach { fmt ->
                FilterChip(selected = settings.format == fmt, onClick = { vm.setExportSettings(settings.copy(format = fmt)) }, label = { Text(fmt.label) })
            }
        }

        if (settings.format != ExportFormat.PNG) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.editor_quality), style = MaterialTheme.typography.labelMedium)
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

        Text(stringResource(R.string.editor_export_size), style = MaterialTheme.typography.titleSmall, color = PlexGold, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportSizeChoice.entries.forEach { size ->
                FilterChip(selected = settings.size == size, onClick = { vm.setExportSettings(settings.copy(size = size)) }, label = { Text(size.longestEdge?.let { stringResource(R.string.editor_export_size_px, it) } ?: stringResource(R.string.editor_export_size_original)) })
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSaveCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.editor_save_copy))
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.action_share))
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.editor_save_preset)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text(stringResource(R.string.editor_preset_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { vm.savePreset(name); showSaveDialog = false }) { Text(stringResource(R.string.action_save), color = PlexGold) }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
