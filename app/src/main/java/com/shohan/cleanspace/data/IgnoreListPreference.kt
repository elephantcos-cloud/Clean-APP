package com.shohan.cleanspace.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the set of package names the user has long-pressed and chosen to
 * "ignore" — these apps can never be selected or force-stopped (single or
 * bulk), as a safeguard against accidentally stopping something important.
 * Cache clearing is unaffected; the ignore list only protects force-stop.
 */
class IgnoreListPreference(private val context: Context) {

    private val ignoreKey = stringSetPreferencesKey("ignored_packages")

    val ignoredPackages: Flow<Set<String>> = context.cleanSpaceDataStore.data.map { prefs ->
        prefs[ignoreKey] ?: emptySet()
    }

    suspend fun addToIgnoreList(packageName: String) {
        context.cleanSpaceDataStore.edit { prefs ->
            prefs[ignoreKey] = (prefs[ignoreKey] ?: emptySet()) + packageName
        }
    }

    suspend fun removeFromIgnoreList(packageName: String) {
        context.cleanSpaceDataStore.edit { prefs ->
            prefs[ignoreKey] = (prefs[ignoreKey] ?: emptySet()) - packageName
        }
    }
}
