package au.prism.photos.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import au.prism.photos.ui.theme.PlexGold
import kotlin.math.roundToInt

private val framePresetColours = listOf(Color.White, Color.Black, PlexGold, Color(0xFFB0BEC5), Color(0xFFEF9A9A), Color(0xFF90CAF9), Color(0xFFA5D6A7), Color(0xFFCE93D8))

@Composable
fun FramesTab(vm: EditorViewModel, frame: FrameSettings, hasTakenAt: Boolean, modifier: Modifier = Modifier) {
    var borderPercent by remember(frame.borderPercent) { mutableStateOf(frame.borderPercent) }
    var cornerRadius by remember(frame.cornerRadiusPercent) { mutableStateOf(frame.cornerRadiusPercent) }
    var watermark by remember(frame.watermarkText) { mutableStateOf(frame.watermarkText) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Border thickness", style = MaterialTheme.typography.labelMedium)
            Text("${borderPercent.roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = PlexGold)
        }
        Slider(
            value = borderPercent,
            onValueChange = { borderPercent = it; vm.setFrameLive(frame.copy(borderPercent = it)) },
            onValueChangeFinished = { vm.commitFrame(frame.copy(borderPercent = borderPercent)) },
            valueRange = 0f..10f,
            colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
        )

        Text("Border colour", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            framePresetColours.forEach { colour ->
                Box(
                    Modifier
                        .size(30.dp)
                        .background(colour, CircleShape)
                        .border(if (frame.borderColorArgb == colour.toArgb()) 2.dp else 1.dp, if (frame.borderColorArgb == colour.toArgb()) PlexGold else Color.Gray, CircleShape)
                        .clickable { vm.commitFrame(frame.copy(borderColorArgb = colour.toArgb())) },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Rounded corners", style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = frame.roundedCorners,
                onCheckedChange = { vm.commitFrame(frame.copy(roundedCorners = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = PlexGold, checkedTrackColor = PlexGold.copy(alpha = 0.5f)),
            )
        }
        if (frame.roundedCorners) {
            Slider(
                value = cornerRadius,
                onValueChange = { cornerRadius = it; vm.setFrameLive(frame.copy(cornerRadiusPercent = it)) },
                onValueChangeFinished = { vm.commitFrame(frame.copy(cornerRadiusPercent = cornerRadius)) },
                valueRange = 1f..25f,
                colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Polaroid style", style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = frame.polaroid,
                onCheckedChange = { vm.commitFrame(frame.copy(polaroid = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = PlexGold, checkedTrackColor = PlexGold.copy(alpha = 0.5f)),
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (hasTakenAt) "Date stamp" else "Date stamp (unavailable)", style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = frame.dateStampEnabled,
                enabled = hasTakenAt,
                onCheckedChange = { vm.commitFrame(frame.copy(dateStampEnabled = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = PlexGold, checkedTrackColor = PlexGold.copy(alpha = 0.5f)),
            )
        }
        if (frame.dateStampEnabled && hasTakenAt) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text(frame.dateStampFormat.label, color = PlexGold)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DateStampFormat.entries.forEach { fmt ->
                        DropdownMenuItem(text = { Text(fmt.label) }, onClick = { vm.commitFrame(frame.copy(dateStampFormat = fmt)); menuOpen = false })
                    }
                }
            }
        }

        Text("Watermark text", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = watermark,
            onValueChange = { watermark = it },
            placeholder = { Text("e.g. Plex Gallery") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        TextButton(onClick = { vm.commitFrame(frame.copy(watermarkText = watermark)) }) {
            Text("Apply watermark", color = PlexGold)
        }
    }
}
