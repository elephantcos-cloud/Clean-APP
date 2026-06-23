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
    val isIgnored: Boolean = false
) {
    val totalBytes: Long get() = appBytes + cacheBytes + dataBytes
}

enum class AppFilter { ALL, USER, SYSTEM }

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
