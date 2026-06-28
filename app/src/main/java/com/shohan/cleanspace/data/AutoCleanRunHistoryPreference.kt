package com.shohan.cleanspace.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AutoCleanRecord(val timestamp: Long, val cacheBytesAtClean: Long)

/**
 * Remembers, per package, how much cache it had and when it was last cleared
 * by Auto-Clean. This is what lets the engine tell the difference between an
 * app whose cache is genuinely stale dead weight versus one that gets
 * re-filled almost immediately after every clear (which usually means the
 * cache is actively serving the app, e.g. a streaming or maps app — clearing
 * it repeatedly mostly just wastes battery re-downloading the same data).
 * Only the single most recent record per package is kept.
 */
class AutoCleanRunHistoryPreference(private val context: Context) {

    private val key = stringPreferencesKey("auto_clean_run_history")

    val records: Flow<Map<String, AutoCleanRecord>> = context.cleanSpaceDataStore.data.map { prefs ->
        (prefs[key] ?: "").lines().filter { it.isNotBlank() }.mapNotNull { decode(it) }.toMap()
    }

    suspend fun recordCleaned(packageName: String, cacheBytesAtClean: Long) {
        context.cleanSpaceDataStore.edit { prefs ->
            val current = (prefs[key] ?: "").lines().filter { it.isNotBlank() }
                .mapNotNull { decode(it) }.toMap().toMutableMap()
            current[packageName] = AutoCleanRecord(System.currentTimeMillis(), cacheBytesAtClean)
            prefs[key] = current.entries.joinToString("\n") { (pkg, rec) -> encode(pkg, rec) }
        }
    }

    private fun encode(packageName: String, record: AutoCleanRecord): String =
        "${record.timestamp}|${record.cacheBytesAtClean}|$packageName"

    private fun decode(line: String): Pair<String, AutoCleanRecord>? {
        val parts = line.split("|", limit = 3)
        if (parts.size != 3) return null
        return try {
            parts[2] to AutoCleanRecord(parts[0].toLong(), parts[1].toLong())
        } catch (_: Exception) { null }
    }
}
