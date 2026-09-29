package au.prism.photos.ui.editor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import au.prism.photos.ui.theme.PlexGold
import kotlin.math.roundToInt

private data class AdjustField(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val default: Float,
    val get: (Adjustments) -> Float,
    val set: (Adjustments, Float) -> Adjustments,
)

private val adjustFields = listOf(
    AdjustField("Brightness", -100f..100f, 0f, { it.brightness }, { a, v -> a.copy(brightness = v) }),
    AdjustField("Exposure", -100f..100f, 0f, { it.exposure }, { a, v -> a.copy(exposure = v) }),
    AdjustField("Contrast", -100f..100f, 0f, { it.contrast }, { a, v -> a.copy(contrast = v) }),
    AdjustField("Highlights", -100f..100f, 0f, { it.highlights }, { a, v -> a.copy(highlights = v) }),
    AdjustField("Shadows", -100f..100f, 0f, { it.shadows }, { a, v -> a.copy(shadows = v) }),
    AdjustField("Whites", -100f..100f, 0f, { it.whites }, { a, v -> a.copy(whites = v) }),
    AdjustField("Blacks", -100f..100f, 0f, { it.blacks }, { a, v -> a.copy(blacks = v) }),
    AdjustField("Saturation", -100f..100f, 0f, { it.saturation }, { a, v -> a.copy(saturation = v) }),
    AdjustField("Vibrance", -100f..100f, 0f, { it.vibrance }, { a, v -> a.copy(vibrance = v) }),
    AdjustField("Warmth", -100f..100f, 0f, { it.warmth }, { a, v -> a.copy(warmth = v) }),
    AdjustField("Tint", -100f..100f, 0f, { it.tint }, { a, v -> a.copy(tint = v) }),
    AdjustField("Hue shift", -100f..100f, 0f, { it.hueShift }, { a, v -> a.copy(hueShift = v) }),
    AdjustField("Fade", 0f..100f, 0f, { it.fade }, { a, v -> a.copy(fade = v) }),
    AdjustField("Dehaze", 0f..100f, 0f, { it.dehaze }, { a, v -> a.copy(dehaze = v) }),
    AdjustField("Sharpness", 0f..100f, 0f, { it.sharpness }, { a, v -> a.copy(sharpness = v) }),
    AdjustField("Clarity", -100f..100f, 0f, { it.clarity }, { a, v -> a.copy(clarity = v) }),
    AdjustField("Blur (soften)", 0f..100f, 0f, { it.blur }, { a, v -> a.copy(blur = v) }),
    AdjustField("Vignette strength", 0f..100f, 0f, { it.vignetteStrength }, { a, v -> a.copy(vignetteStrength = v) }),
    AdjustField("Vignette softness", 0f..100f, 50f, { it.vignetteSoftness }, { a, v -> a.copy(vignetteSoftness = v) }),
    AdjustField("Grain", 0f..100f, 0f, { it.grain }, { a, v -> a.copy(grain = v) }),
)

@Composable
fun AdjustTab(vm: EditorViewModel, adjustments: Adjustments, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth().height(260.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        item {
            TextButton(onClick = { vm.autoEnhance() }) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Auto enhance", color = PlexGold)
            }
        }
        items(adjustFields) { field ->
            AdjustSliderRow(
                field = field,
                value = field.get(adjustments),
                onLive = { v -> vm.setAdjustmentsLive(field.set(adjustments, v)) },
                onCommit = { v -> vm.commitAdjustments(field.set(adjustments, v)) },
            )
        }
    }
}

@Composable
private fun AdjustSliderRow(field: AdjustField, value: Float, onLive: (Float) -> Unit, onCommit: (Float) -> Unit) {
    var current by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(field.label, style = MaterialTheme.typography.labelMedium)
            Text(current.roundToInt().toString(), style = MaterialTheme.typography.labelMedium, color = PlexGold)
        }
        Slider(
            value = current,
            onValueChange = { current = it; onLive(it) },
            onValueChangeFinished = { onCommit(current) },
            valueRange = field.range,
            colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(field.label) {
                    detectTapGestures(onDoubleTap = {
                        current = field.default
                        onLive(field.default)
                        onCommit(field.default)
                    })
                },
        )
    }
}
