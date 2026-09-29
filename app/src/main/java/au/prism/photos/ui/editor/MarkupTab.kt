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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.prism.photos.ui.theme.PlexGold
import kotlin.math.roundToInt

private val markupColours = listOf(PlexGold, Color.White, Color.Black, Color.Red, Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFE91E63))
private val emojiChoices = listOf("😀", "😂", "😍", "😎", "🥳", "👍", "❤️", "🔥", "⭐", "🎉", "😢", "😮", "👏", "🙌", "💯", "✨")

@Composable
fun MarkupTab(vm: EditorViewModel, markup: List<MarkupElement>, modifier: Modifier = Modifier) {
    val tool by vm.markupTool.collectAsStateWithLifecycle()
    val brushColor by vm.brushColor.collectAsStateWithLifecycle()
    val brushSize by vm.brushSize.collectAsStateWithLifecycle()
    val brushAlpha by vm.brushAlpha.collectAsStateWithLifecycle()
    val pixelateBrush by vm.pixelateBrush.collectAsStateWithLifecycle()

    var showTextDialog by remember { mutableStateOf(false) }
    var showEmojiDialog by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(MarkupTool.entries.filter { it != MarkupTool.NONE }) { t ->
                FilterChip(
                    selected = tool == t,
                    onClick = {
                        vm.setMarkupTool(if (tool == t) MarkupTool.NONE else t)
                        if (t == MarkupTool.TEXT) showTextDialog = true
                        if (t == MarkupTool.EMOJI) showEmojiDialog = true
                    },
                    label = { Text(toolLabel(t)) },
                )
            }
        }

        if (tool == MarkupTool.PEN || tool == MarkupTool.ARROW || tool == MarkupTool.RECTANGLE || tool == MarkupTool.ELLIPSE || tool == MarkupTool.HIGHLIGHTER) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                markupColours.forEach { colour ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(colour, CircleShape)
                            .border(if (brushColor.toArgb() == colour.toArgb()) 2.dp else 1.dp, if (brushColor.toArgb() == colour.toArgb()) PlexGold else Color.Gray, CircleShape)
                            .clickable { vm.brushColor.value = colour },
                    )
                }
            }
        }

        if (tool != MarkupTool.NONE && tool != MarkupTool.TEXT && tool != MarkupTool.EMOJI) {
            var size by remember(tool) { mutableStateOf(brushSize) }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (tool == MarkupTool.BLUR_BRUSH) "Brush size" else "Size", style = MaterialTheme.typography.labelMedium)
                    Text(size.roundToInt().toString(), style = MaterialTheme.typography.labelMedium, color = PlexGold)
                }
                Slider(
                    value = size,
                    onValueChange = { size = it; vm.brushSize.value = it },
                    valueRange = 4f..80f,
                    colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
                )
            }
        }

        if (tool == MarkupTool.HIGHLIGHTER) {
            var alpha by remember { mutableStateOf(brushAlpha) }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Highlighter opacity", style = MaterialTheme.typography.labelMedium)
                    Text("${(alpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = PlexGold)
                }
                Slider(
                    value = alpha,
                    onValueChange = { alpha = it; vm.brushAlpha.value = it },
                    valueRange = 0.1f..0.9f,
                    colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
                )
            }
        }

        if (tool == MarkupTool.BLUR_BRUSH) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = pixelateBrush, onCheckedChange = { vm.pixelateBrush.value = it })
                Text("Pixelate (unticked = blur)", style = MaterialTheme.typography.labelMedium)
            }
        }

        TextButton(onClick = { vm.clearMarkup() }, enabled = markup.isNotEmpty()) {
            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text("Clear all markup")
        }
    }

    if (showTextDialog) {
        AddTextDialog(
            onDismiss = { showTextDialog = false; vm.setMarkupTool(MarkupTool.NONE) },
            onConfirm = { text, colour, size, pill ->
                vm.addMarkupElement(
                    TextElement(
                        id = vm.nextMarkupId(), text = text, center = Offset(0.5f, 0.5f),
                        fontSizeFraction = size, colorArgb = colour.toArgb(), backgroundPill = pill,
                    ),
                )
                showTextDialog = false
                vm.setMarkupTool(MarkupTool.NONE)
            },
        )
    }

    if (showEmojiDialog) {
        AlertDialog(
            onDismissRequest = { showEmojiDialog = false; vm.setMarkupTool(MarkupTool.NONE) },
            title = { Text("Add a sticker") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(6)) {
                    items(emojiChoices) { emoji ->
                        Text(
                            emoji,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(6.dp).clickable {
                                vm.addMarkupElement(StickerElement(id = vm.nextMarkupId(), emoji = emoji, center = Offset(0.5f, 0.5f), sizeFraction = 0.14f))
                                showEmojiDialog = false
                                vm.setMarkupTool(MarkupTool.NONE)
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEmojiDialog = false; vm.setMarkupTool(MarkupTool.NONE) }) { Text("Close") } },
        )
    }
}

private fun toolLabel(tool: MarkupTool): String = when (tool) {
    MarkupTool.PEN -> "Pen"
    MarkupTool.HIGHLIGHTER -> "Highlighter"
    MarkupTool.ARROW -> "Arrow"
    MarkupTool.RECTANGLE -> "Rectangle"
    MarkupTool.ELLIPSE -> "Ellipse"
    MarkupTool.TEXT -> "Text"
    MarkupTool.EMOJI -> "Sticker"
    MarkupTool.BLUR_BRUSH -> "Blur/pixelate"
    MarkupTool.ERASER -> "Eraser"
    MarkupTool.NONE -> "None"
}

@Composable
private fun AddTextDialog(onDismiss: () -> Unit, onConfirm: (String, Color, Float, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var colour by remember { mutableStateOf(Color.White) }
    var size by remember { mutableStateOf(0.06f) }
    var pill by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add text") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Your caption") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    markupColours.forEach { c ->
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(c, CircleShape)
                                .border(if (colour.toArgb() == c.toArgb()) 2.dp else 1.dp, if (colour.toArgb() == c.toArgb()) PlexGold else Color.Gray, CircleShape)
                                .clickable { colour = c },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Font size", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = size, onValueChange = { size = it }, valueRange = 0.03f..0.14f,
                        colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pill, onCheckedChange = { pill = it })
                    Text("Background pill", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text, colour, size, pill) else onDismiss() }) { Text("Add", color = PlexGold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
