package com.shohan.cleanspace.data.models

data class StorageOverview(
    val totalBytes: Long = 0,
    val usedBytes: Long = 0,
    val freeBytes: Long = 0
)

data class AppStorageInfo(
    val packageName: String,
    val appName: String,
    val appBytes: Long,
    val cacheBytes: Long,
    val dataBytes: Long,
    val isSystemApp: Boolean,
    val selected: Boolean = false,
    val isIgnored: Boolean = false,
    // Already force-stopped (or never launched) — ApplicationInfo.FLAG_STOPPED.
    val isStopped: Boolean = false,
    // Persistent system process — Android's own Settings app does not offer
    // Force Stop for these (e.g. "Media Storage"), since stopping them can
    // destabilize the OS. ApplicationInfo.FLAG_PERSISTENT.
    val isPersistent: Boolean = false,
    // No launcher entry point — packageManager.getLaunchIntentForPackage == null.
    val hasLaunchIntent: Boolean = true,
    // Last-used timestamp from UsageStatsManager, 0 if never recorded.
    val lastUsedTime: Long = 0L,
    // Disabled via PackageManager.getApplicationEnabledSetting.
    val isDisabled: Boolean = false
) {
    val totalBytes: Long get() = appBytes + cacheBytes + dataBytes

    // Apps Android itself won't let you force-stop: persistent system
    // processes, or anything with no launchable UI at all.
    val canForceStop: Boolean get() = !isPersistent && hasLaunchIntent
}

enum class AppFilter { ALL, USER, SYSTEM }

enum class AppTab { CACHE_CLEAN, FORCE_STOP }

enum class AppSortKey { SIZE, DATE, NAME }

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class AppPermissions(
    val usageAccess: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermission: Boolean = false
)

/**
 * Live progress for a bulk action (clear cache / force stop) running across
 * multiple selected apps — drives the "live screen" progress dialog so the
 * user can see exactly which app is being processed in real time, not just
 * a number.
 */
data class BulkActionProgress(
    val actionLabel: String,
    val currentAppName: String,
    val currentIndex: Int,
    val total: Int
)

enum class HistoryActionType { CACHE_CLEARED, FORCE_STOPPED, DATA_CLEARED, DISABLED, ENABLED, AUTO_CLEAN_RUN }

data class HistoryEntry(
    val timestamp: Long,
    val action: HistoryActionType,
    val appName: String,
    val bytesFreed: Long = 0L
)

// A status message that can optionally offer a follow-up action (e.g. a
// "Relaunch" button right after force-stopping an app) — plain text alone
// can't carry that, so this replaced the old `String?` status message.
data class StatusMessage(
    val text: String,
    val relaunchPackage: String? = null
)

enum class AutoCleanFrequency(val hours: Long) { DAILY(24), WEEKLY(168) }

// Maps to concrete thresholds in AutoCleanWorker — not exposed as raw numbers
// in Settings to keep the UI simple and avoid the user picking values that
// don't actually do anything useful.
enum class AutoCleanAggressiveness { CONSERVATIVE, BALANCED, AGGRESSIVE }

data class AutoCleanSettings(
    val enabled: Boolean = false,
    val frequency: AutoCleanFrequency = AutoCleanFrequency.DAILY,
    val aggressiveness: AutoCleanAggressiveness = AutoCleanAggressiveness.BALANCED
)
