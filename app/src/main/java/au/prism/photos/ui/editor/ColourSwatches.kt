package au.prism.photos.ui.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import au.prism.photos.R
import au.prism.photos.ui.theme.PlexGold

/** A preset colour and the name TalkBack reads for it. */
data class NamedColour(val colour: Color, @StringRes val name: Int)

/**
 * A row of colour swatches. Each swatch draws at [swatchSize] but has a 48 dp touch target, reads
 * out its colour name and selected state, and the row scrolls sideways if it does not fit.
 */
@Composable
fun ColourSwatchRow(
    colours: List<NamedColour>,
    selectedArgb: Int?,
    onPick: (Color) -> Unit,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 28.dp,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        colours.forEach { named ->
            val selected = selectedArgb == named.colour.toArgb()
            val name = stringResource(named.name)
            Box(
                Modifier
                    .size(48.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(named.colour) })
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(swatchSize)
                        .background(named.colour, CircleShape)
                        .border(if (selected) 2.dp else 1.dp, if (selected) PlexGold else Color.Gray, CircleShape),
                )
            }
        }
    }
}
