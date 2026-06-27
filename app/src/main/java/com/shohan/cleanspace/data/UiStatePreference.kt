package com.shohan.cleanspace.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shohan.cleanspace.data.models.AppFilter
import com.shohan.cleanspace.data.models.AppSortKey
import com.shohan.cleanspace.data.models.AppTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remembers the user's last filter/tab/sort choice so the app reopens exactly
 * where they left off, instead of always resetting to the defaults. Also
 * tracks whether the one-time default-protected-apps seeding has run yet.
 */
class UiStatePreference(private val context: Context) {

    private val filterKey = stringPreferencesKey("ui_app_filter")
    private val tabKey = stringPreferencesKey("ui_active_tab")
    private val sortKeyKey = stringPreferencesKey("ui_sort_key")
    private val sortAscKey = booleanPreferencesKey("ui_sort_ascending")
    private val showProtectedKey = booleanPreferencesKey("ui_show_protected")
    private val seededDefaultsKey = booleanPreferencesKey("seeded_default_ignore_list")

    data class SavedUiState(
        val filter: AppFilter,
        val tab: AppTab,
        val sortKey: AppSortKey,
        val sortAscending: Boolean,
        val showProtected: Boolean
    )

    val savedUiState: Flow<SavedUiState> = context.cleanSpaceDataStore.data.map { prefs ->
        SavedUiState(
            filter = runCatching { AppFilter.valueOf(prefs[filterKey] ?: "") }.getOrDefault(AppFilter.ALL),
            tab = runCatching { AppTab.valueOf(prefs[tabKey] ?: "") }.getOrDefault(AppTab.CACHE_CLEAN),
            sortKey = runCatching { AppSortKey.valueOf(prefs[sortKeyKey] ?: "") }.getOrDefault(AppSortKey.SIZE),
            sortAscending = prefs[sortAscKey] ?: false,
            showProtected = prefs[showProtectedKey] ?: false
        )
    }

    suspend fun save(state: SavedUiState) {
        context.cleanSpaceDataStore.edit { prefs ->
            prefs[filterKey] = state.filter.name
            prefs[tabKey] = state.tab.name
            prefs[sortKeyKey] = state.sortKey.name
            prefs[sortAscKey] = state.sortAscending
            prefs[showProtectedKey] = state.showProtected
        }
    }

    val hasSeededDefaults: Flow<Boolean> = context.cleanSpaceDataStore.data.map { prefs ->
        prefs[seededDefaultsKey] ?: false
    }

    suspend fun markDefaultsSeeded() {
        context.cleanSpaceDataStore.edit { prefs -> prefs[seededDefaultsKey] = true }
    }
}
