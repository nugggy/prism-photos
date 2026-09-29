package au.prism.photos.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import au.prism.photos.ui.theme.PlexGold
import kotlin.math.roundToInt

@Composable
fun FiltersTab(vm: EditorViewModel, bitmap: Bitmap, selected: FilterPreset, strength: Float, modifier: Modifier = Modifier) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(FilterPreset.entries) { preset ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .border(
                                width = if (preset == selected) 2.dp else 0.dp,
                                color = if (preset == selected) PlexGold else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { vm.setFilter(preset) },
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = preset.label,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix(EditorColour.filterMatrixAtStrength(preset, 100f)),
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(preset.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (selected != FilterPreset.ORIGINAL) {
            var current by remember(selected, strength) { mutableStateOf(strength) }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Strength", style = MaterialTheme.typography.labelMedium)
                    Text("${current.roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = PlexGold)
                }
                Slider(
                    value = current,
                    onValueChange = { current = it; vm.setFilterStrengthLive(it) },
                    onValueChangeFinished = { vm.commitFilterStrength(current) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
                )
            }
        }
    }
}
