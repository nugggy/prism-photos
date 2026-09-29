package au.prism.photos.ui.videoeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import au.prism.photos.ui.theme.PlexGold
import au.prism.photos.util.Format

@Composable
fun VideoEditorTabBar(selected: VideoEditorTab, onSelect: (VideoEditorTab) -> Unit) {
    NavigationBar(containerColor = Color.Black) {
        VideoEditorTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        when (tab) {
                            VideoEditorTab.TRIM -> Icons.Filled.ContentCut
                            VideoEditorTab.AUDIO -> Icons.Filled.VolumeUp
                            VideoEditorTab.TRANSFORM -> Icons.Filled.RotateRight
                            VideoEditorTab.SPEED -> Icons.Filled.Speed
                            VideoEditorTab.COLOUR -> Icons.Filled.Tune
                            VideoEditorTab.EXPORT -> Icons.Filled.Done
                        },
                        contentDescription = null,
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PlexGold,
                    selectedTextColor = PlexGold,
                    indicatorColor = Color(0xFF2A2A2A),
                    unselectedIconColor = Color.White.copy(alpha = 0.7f),
                    unselectedTextColor = Color.White.copy(alpha = 0.7f),
                ),
            )
        }
    }
}

@Composable
fun TrimPanel(trimStartMs: Long, trimEndMs: Long, durationMs: Long, onCaptureFrame: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Format.duration(trimStartMs), color = Color.White, style = MaterialTheme.typography.labelLarge)
            Text(
                "Selected: ${Format.duration((trimEndMs - trimStartMs).coerceAtLeast(0))}",
                color = PlexGold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(Format.duration(trimEndMs), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onCaptureFrame) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = PlexGold)
                Text(" Save frame as photo", color = PlexGold)
            }
        }
    }
}

@Composable
fun AudioPanel(muted: Boolean, onMutedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White)
            Text(if (muted) "  Audio muted" else "  Audio on", color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
        Switch(
            checked = !muted,
            onCheckedChange = { onMutedChange(!it) },
            colors = SwitchDefaults.colors(checkedThumbColor = PlexGold, checkedTrackColor = PlexGold.copy(alpha = 0.5f)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransformPanel(
    state: VideoEditState,
    onRotate: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onResolution: (VideoResolution) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButtonWithLabel(icon = Icons.Filled.RotateRight, label = "Rotate", active = state.rotationDegrees != 0, onClick = onRotate)
            IconButtonWithLabel(icon = Icons.Filled.Flip, label = "Flip H", active = state.flipHorizontal, onClick = onFlipHorizontal)
            IconButtonWithLabel(icon = Icons.Filled.Flip, label = "Flip V", active = state.flipVertical, onClick = onFlipVertical, rotateIcon = true)
        }
        Text("Resolution", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoResolution.entries.forEach { res ->
                FilterChip(
                    selected = state.resolution == res,
                    onClick = { onResolution(res) },
                    label = { Text(res.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PlexGold,
                        selectedLabelColor = Color.Black,
                        labelColor = Color.White,
                    ),
                )
            }
        }
    }
}

@Composable
private fun IconButtonWithLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    rotateIcon: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) PlexGold else Color.White,
                modifier = if (rotateIcon) Modifier.rotate(90f) else Modifier,
            )
        }
        Text(label, color = if (active) PlexGold else Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SpeedPanel(speed: Float, onSpeedChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Speed: ${"%.2f".format(speed)}x", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = speed,
            onValueChange = onSpeedChange,
            valueRange = 0.25f..4f,
            colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f).forEach { preset ->
                TextButton(onClick = { onSpeedChange(preset) }) {
                    Text("${preset}x", color = if (speed == preset) PlexGold else Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun ColourPanel(
    state: VideoEditState,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onHue: (Float) -> Unit,
    onWarmth: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        ColourSlider("Brightness", state.brightness, -1f..1f, onBrightness)
        ColourSlider("Contrast", state.contrast, -1f..1f, onContrast)
        ColourSlider("Saturation", state.saturation, -100f..100f, onSaturation)
        ColourSlider("Hue", state.hue, -180f..180f, onHue)
        ColourSlider("Warmth", state.warmth, -1f..1f, onWarmth)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onReset) { Text("Reset colour", color = PlexGold) }
        }
    }
}

@Composable
private fun ColourSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
    )
}

@Composable
fun LoopToggle(loop: Boolean, onLoopChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = loop,
            onCheckedChange = onLoopChange,
            colors = CheckboxDefaults.colors(checkedColor = PlexGold),
        )
        Text("Loop preview", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
