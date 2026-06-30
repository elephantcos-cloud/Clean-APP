package com.shohan.cleanspace.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AutoCleanRecord(
    val timestamp: Long,
    val cacheBytesAtClean: Long,
    // How many auto-clean cycles IN A ROW this app's cache has regrown most
    // of the way back within ~2 days of being cleared. Resets to 0 the
    // moment a cycle passes without that happening (including simply because
    // enough real time passed since the last actual clean without the app
    // being re-evaluated) — see AutoCleanWorker for how this drives the
    // "stop bothering with this one for now" soft-skip.
    val fastRegrowthStreak: Int = 0
)

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

    suspend fun recordCleaned(packageName: String, cacheBytesAtClean: Long, fastRegrowthStreak: Int) {
        context.cleanSpaceDataStore.edit { prefs ->
            val current = (prefs[key] ?: "").lines().filter { it.isNotBlank() }
                .mapNotNull { decode(it) }.toMap().toMutableMap()
            current[packageName] = AutoCleanRecord(System.currentTimeMillis(), cacheBytesAtClean, fastRegrowthStreak)
            prefs[key] = current.entries.joinToString("\n") { (pkg, rec) -> encode(pkg, rec) }
        }
    }

    // Bumps only the streak counter for an already-existing record, leaving
    // its timestamp and cacheBytesAtClean untouched — used by the fast
    // post-clean follow-up check (RegrowthCheckWorker) to escalate the skip
    // immediately on detecting near-instant regrowth, without disturbing the
    // 12-hour cooldown window that timestamp also drives.
    suspend fun escalateStreak(packageName: String, newStreak: Int) {
        context.cleanSpaceDataStore.edit { prefs ->
            val current = (prefs[key] ?: "").lines().filter { it.isNotBlank() }
                .mapNotNull { decode(it) }.toMap().toMutableMap()
            current[packageName]?.let { existing ->
                current[packageName] = existing.copy(fastRegrowthStreak = newStreak)
                prefs[key] = current.entries.joinToString("\n") { (pkg, rec) -> encode(pkg, rec) }
            }
        }
    }

    private fun encode(packageName: String, record: AutoCleanRecord): String =
        "${record.timestamp}|${record.cacheBytesAtClean}|${record.fastRegrowthStreak}|$packageName"

    private fun decode(line: String): Pair<String, AutoCleanRecord>? {
        val parts = line.split("|", limit = 4)
        if (parts.size != 4) return null
        return try {
            parts[3] to AutoCleanRecord(parts[0].toLong(), parts[1].toLong(), parts[2].toInt())
        } catch (_: Exception) { null }
    }
}
