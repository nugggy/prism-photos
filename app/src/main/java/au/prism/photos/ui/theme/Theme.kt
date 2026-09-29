package au.prism.photos.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import au.prism.photos.domain.ThemeMode

val PlexGold = Color(0xFFE5A00D)
val PlexDark = Color(0xFF1F2326)
val PlexSurface = Color(0xFF282A2D)
val PlexSurfaceVariant = Color(0xFF34383C)
val PlexAmoledBackground = Color(0xFF000000)
val PlexAmoledSurface = Color(0xFF0B0B0B)

private val DarkScheme = darkColorScheme(
    primary = PlexGold,
    onPrimary = Color(0xFF241A00),
    primaryContainer = Color(0xFF5C4000),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = PlexGold,
    onSecondary = Color(0xFF241A00),
    secondaryContainer = Color(0xFF4A3A10),
    onSecondaryContainer = Color(0xFFFFE6A8),
    tertiary = PlexGold,
    background = PlexDark,
    onBackground = Color(0xFFE7E7E7),
    surface = PlexSurface,
    onSurface = Color(0xFFE7E7E7),
    surfaceVariant = PlexSurfaceVariant,
    onSurfaceVariant = Color(0xFFC9C6BE),
    surfaceContainerLowest = Color(0xFF1A1D20),
    surfaceContainerLow = PlexDark,
    surfaceContainer = PlexSurface,
    surfaceContainerHigh = Color(0xFF303336),
    surfaceContainerHighest = PlexSurfaceVariant,
    outline = Color(0xFF6E6A62),
    outlineVariant = Color(0xFF3E4145),
)

private val AmoledScheme = DarkScheme.copy(
    background = PlexAmoledBackground,
    surface = PlexAmoledSurface,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = PlexAmoledSurface,
    surfaceContainerHigh = Color(0xFF151515),
    surfaceContainerHighest = Color(0xFF1E1E1E),
)

/** Warm neutral light scheme so surfaces do not pick up Material's default lavender tint. */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF8A5D00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF2A1B00),
    secondary = Color(0xFF8A5D00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE7B8),
    onSecondaryContainer = Color(0xFF2A1B00),
    tertiary = Color(0xFF8A5D00),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFEDE9E1),
    onSurfaceVariant = Color(0xFF4D4A43),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3EF),
    surfaceContainer = Color(0xFFEFEDE8),
    surfaceContainerHigh = Color(0xFFE9E7E2),
    surfaceContainerHighest = Color(0xFFE3E1DC),
    outline = Color(0xFF7E7A72),
    outlineVariant = Color(0xFFD0CCC3),
)

/** Forces the Plex gold brand colour back onto a dynamic scheme's primary/tertiary slots. */
private fun ColorScheme.withPlexAccent(): ColorScheme = copy(primary = PlexGold, tertiary = PlexGold)

@Composable
fun PrismTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColour: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColour && canDynamic && dark -> {
            val base = dynamicDarkColorScheme(context)
            if (themeMode == ThemeMode.AMOLED) base.copy(background = PlexAmoledBackground, surface = PlexAmoledSurface) else base
        }
        dynamicColour && canDynamic && !dark -> dynamicLightColorScheme(context)
        !dynamicColour && dark && themeMode == ThemeMode.AMOLED -> AmoledScheme
        !dynamicColour && dark -> DarkScheme
        !dynamicColour -> LightScheme
        // Dynamic colour requested but unavailable (pre Android 12): still honour the Plex accent.
        dark && themeMode == ThemeMode.AMOLED -> AmoledScheme.withPlexAccent()
        dark -> DarkScheme.withPlexAccent()
        else -> LightScheme.withPlexAccent()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = PrismTypography, shapes = PrismShapes, content = content)
}
