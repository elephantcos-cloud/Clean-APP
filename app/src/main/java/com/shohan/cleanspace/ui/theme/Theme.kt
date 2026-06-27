package com.shohan.cleanspace.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
fun CleanSpaceTheme(
    themeMode: ThemeMode,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Fix 3: SYSTEM mode now correctly follows the device dark/light setting
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Material You: only available on Android 12+ (API 31). On older devices,
    // or when the user has turned it off in Settings, this falls back to the
    // app's own branded green palette below — useDynamicColor being true on
    // an older device simply has no effect.
    val context = LocalContext.current
    val colors = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColors else LightColors
    }

    // Fix: status bar icon contrast. A static XML windowLightStatusBar value can
    // only be correct for ONE theme — since this app lets the user switch
    // light/dark/system at runtime, the icon color must be updated to match
    // whichever theme is actually active, or it goes invisible in the other mode.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = CleanSpaceTypography,
        shapes = CleanSpaceShapes,
        content = content
    )
}
