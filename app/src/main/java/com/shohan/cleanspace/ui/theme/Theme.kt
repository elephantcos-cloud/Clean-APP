package com.shohan.cleanspace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.shohan.cleanspace.data.models.ThemeMode

private val LightColors = lightColorScheme(
    primary = Emerald600,
    onPrimary = LightSurface,
    primaryContainer = Emerald50,
    onPrimaryContainer = Emerald700,
    secondary = Sky,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVar,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
    error = Rose
)

private val DarkColors = darkColorScheme(
    primary = Emerald400,
    onPrimary = DarkBackground,
    primaryContainer = Emerald700,
    onPrimaryContainer = Emerald100,
    secondary = Sky,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVar,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    error = Rose
)

@Composable
fun CleanSpaceTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> false  // Default light; only Dark when user explicitly chooses
    }
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = CleanSpaceTypography,
        shapes = CleanSpaceShapes,
        content = content
    )
}
