package com.shohan.cleanspace.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shohan.cleanspace.data.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ThemePreference(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorKey = booleanPreferencesKey("use_dynamic_color")

    val themeMode: Flow<ThemeMode> = context.cleanSpaceDataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK"  -> ThemeMode.DARK
            else    -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.cleanSpaceDataStore.edit { prefs -> prefs[themeKey] = mode.name }
    }

    // Defaults to on — Material You wallpaper-based color is the modern
    // Android 12+ expectation. Only ever takes effect on API 31+; the Theme
    // composable ignores this entirely on older devices.
    val useDynamicColor: Flow<Boolean> = context.cleanSpaceDataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: true
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.cleanSpaceDataStore.edit { prefs -> prefs[dynamicColorKey] = enabled }
    }
}
