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
    val lastUsedTime: Long = 0L
) {
    val totalBytes: Long get() = appBytes + cacheBytes + dataBytes

    // Apps Android itself won't let you force-stop: persistent system
    // processes, or anything with no launchable UI at all.
    val canForceStop: Boolean get() = !isPersistent && hasLaunchIntent
}

enum class AppFilter { ALL, USER, SYSTEM }

enum class AppTab { CACHE_CLEAN, FORCE_STOP }

enum class AppSortKey { SIZE, DATE }

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
