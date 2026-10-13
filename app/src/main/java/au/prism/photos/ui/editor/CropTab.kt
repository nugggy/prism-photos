package au.prism.photos.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import au.prism.photos.R
import au.prism.photos.ui.theme.PlexGold
import kotlin.math.abs

/** A crop aspect ratio chip. Named ratios use [labelRes]; numeric ones show [text] as is. */
private data class CropRatio(val ratio: Float?, @StringRes val labelRes: Int? = null, val text: String = "")

private val cropRatios: List<CropRatio> = listOf(
    CropRatio(null, R.string.editor_crop_free), CropRatio(-1f, R.string.editor_crop_original),
    CropRatio(1f, text = "1:1"), CropRatio(4f / 3f, text = "4:3"), CropRatio(3f / 2f, text = "3:2"),
    CropRatio(16f / 9f, text = "16:9"), CropRatio(9f / 16f, text = "9:16"), CropRatio(5f / 4f, text = "5:4"),
)

@Composable
fun CropTab(vm: EditorViewModel, state: EditState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cropRatios) { (ratio, labelRes, text) ->
                val actualRatio = if (ratio == -1f) vm.straightenedAspect() else ratio
                val isSelected = state.cropRatio != null && actualRatio != null && abs(state.cropRatio!! - actualRatio) < 0.01f ||
                    (ratio == null && state.cropRatio == null)
                FilterChip(
                    selected = isSelected,
                    onClick = { vm.setCropRatio(actualRatio) },
                    label = { Text(labelRes?.let { stringResource(it) } ?: text) },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabelledAction(Icons.AutoMirrored.Filled.RotateLeft, stringResource(R.string.editor_rotate_left)) { vm.rotate90(clockwise = false) }
            LabelledAction(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.editor_rotate_right)) { vm.rotate90(clockwise = true) }
            LabelledAction(Icons.Filled.Flip, stringResource(R.string.editor_flip_horizontal)) { vm.flipHorizontal() }
            LabelledAction(Icons.Filled.Flip, stringResource(R.string.editor_flip_vertical)) { vm.flipVertical() }
        }

        var straighten by remember(state.straightenDegrees) { mutableStateOf(state.straightenDegrees) }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.editor_straighten), style = MaterialTheme.typography.labelMedium)
                Text("${straighten.toInt()}°", style = MaterialTheme.typography.labelMedium, color = PlexGold)
            }
            Slider(
                value = straighten,
                onValueChange = { straighten = it; vm.setStraighten(it, final = false) },
                onValueChangeFinished = { vm.setStraighten(straighten, final = true) },
                valueRange = -45f..45f,
                colors = SliderDefaults.colors(thumbColor = PlexGold, activeTrackColor = PlexGold),
            )
        }
    }
}

@Composable
private fun LabelledAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
