package com.shohan.cleanspace.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shohan.cleanspace.data.models.HistoryActionType
import com.shohan.cleanspace.data.models.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Keeps the last [MAX_ENTRIES] actions (cache cleared / force stopped / data
 * cleared / disabled / enabled) so the user can see what happened and when —
 * helpful for piecing together what went wrong if something like an app hang
 * happens again. No JSON library needed: each entry is one delimited line,
 * newest first, joined with "\n" in a single Preferences string.
 */
class HistoryPreference(private val context: Context) {

    private val historyKey = stringPreferencesKey("action_history")

    val entries: Flow<List<HistoryEntry>> = context.cleanSpaceDataStore.data.map { prefs ->
        (prefs[historyKey] ?: "").lines()
            .filter { it.isNotBlank() }
            .mapNotNull { decode(it) }
    }

    suspend fun record(entry: HistoryEntry) {
        context.cleanSpaceDataStore.edit { prefs ->
            val existingLines = (prefs[historyKey] ?: "").lines().filter { it.isNotBlank() }
            val updated = (listOf(encode(entry)) + existingLines).take(MAX_ENTRIES)
            prefs[historyKey] = updated.joinToString("\n")
        }
    }

    suspend fun clear() {
        context.cleanSpaceDataStore.edit { prefs -> prefs[historyKey] = "" }
    }

    // Format: timestamp|ACTION|bytesFreed|appName  (appName last since it's the
    // only field that could itself contain "|" in a pathological case, and it
    // being last means split-with-limit still captures it correctly).
    private fun encode(entry: HistoryEntry): String =
        "${entry.timestamp}|${entry.action.name}|${entry.bytesFreed}|${entry.appName}"

    private fun decode(line: String): HistoryEntry? {
        val parts = line.split("|", limit = 4)
        if (parts.size != 4) return null
        return try {
            HistoryEntry(
                timestamp = parts[0].toLong(),
                action = HistoryActionType.valueOf(parts[1]),
                bytesFreed = parts[2].toLong(),
                appName = parts[3]
            )
        } catch (_: Exception) { null }
    }

    companion object {
        private const val MAX_ENTRIES = 100
    }
}
