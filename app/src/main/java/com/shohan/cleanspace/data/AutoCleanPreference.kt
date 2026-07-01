package com.shohan.cleanspace.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shohan.cleanspace.data.models.AutoCleanAggressiveness
import com.shohan.cleanspace.data.models.AutoCleanFrequency
import com.shohan.cleanspace.data.models.AutoCleanSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AutoCleanPreference(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("auto_clean_enabled")
    private val frequencyKey = stringPreferencesKey("auto_clean_frequency")
    private val aggressivenessKey = stringPreferencesKey("auto_clean_aggressiveness")
    private val notifyKey = booleanPreferencesKey("auto_clean_notify")

    val settings: Flow<AutoCleanSettings> = context.cleanSpaceDataStore.data.map { prefs ->
        AutoCleanSettings(
            enabled = prefs[enabledKey] ?: false,
            frequency = runCatching { AutoCleanFrequency.valueOf(prefs[frequencyKey] ?: "") }
                .getOrDefault(AutoCleanFrequency.DAILY),
            aggressiveness = runCatching { AutoCleanAggressiveness.valueOf(prefs[aggressivenessKey] ?: "") }
                .getOrDefault(AutoCleanAggressiveness.BALANCED),
            notifyOnClean = prefs[notifyKey] ?: false
        )
    }

    suspend fun save(settings: AutoCleanSettings) {
        context.cleanSpaceDataStore.edit { prefs ->
            prefs[enabledKey] = settings.enabled
            prefs[frequencyKey] = settings.frequency.name
            prefs[aggressivenessKey] = settings.aggressiveness.name
            prefs[notifyKey] = settings.notifyOnClean
        }
    }
}
