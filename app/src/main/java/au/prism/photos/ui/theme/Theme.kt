package au.prism.photos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PlexGold = Color(0xFFE5A00D)
val PlexDark = Color(0xFF1F2326)
val PlexSurface = Color(0xFF282A2D)

private val DarkScheme = darkColorScheme(primary = PlexGold, background = PlexDark, surface = PlexSurface)
private val LightScheme = lightColorScheme(primary = Color(0xFF9A6A00))

@Composable
fun PrismTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
}
