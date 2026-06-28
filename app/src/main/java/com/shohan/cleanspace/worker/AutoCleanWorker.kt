package com.shohan.cleanspace.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shohan.cleanspace.data.AutoCleanPreference
import com.shohan.cleanspace.data.AutoCleanRecord
import com.shohan.cleanspace.data.AutoCleanRunHistoryPreference
import com.shohan.cleanspace.data.HistoryPreference
import com.shohan.cleanspace.data.IgnoreListPreference
import com.shohan.cleanspace.data.StorageRepository
import com.shohan.cleanspace.data.models.AppStorageInfo
import com.shohan.cleanspace.data.models.AutoCleanAggressiveness
import com.shohan.cleanspace.data.models.HistoryActionType
import com.shohan.cleanspace.data.models.HistoryEntry
import com.shohan.cleanspace.shizuku.ShizukuHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.ln

/**
 * Rule-based ("smart", not a real ML/LLM model — see README) background
 * cache cleaner. Runs on a schedule via WorkManager.
 *
 * How it decides what to clean (in order):
 * 1. Hard safety gates, always enforced: never an ignored app, never an app
 *    used in the last hour, never below the size floor for the current tier.
 * 2. Storage tier — NORMAL / LOW (<1GB or <10% free) / CRITICAL (<300MB free).
 *    LOW and CRITICAL temporarily escalate to Aggressive thresholds
 *    regardless of the user's chosen setting, and clean until either the
 *    candidate pool runs out or enough has been freed to climb back out of
 *    that tier — not just "clean everything that qualifies".
 * 3. Ranking — eligible apps are scored (bigger cache + longer idle = higher)
 *    and only the top N are touched per run (N depends on aggressiveness),
 *    so a normal run is a small, deliberate pass rather than indiscriminate.
 * 4. Regrowth awareness — if an app's cache mostly grew back within 2 days of
 *    the last time Auto-Clean cleared it, that's a sign the cache is actively
 *    serving the app (e.g. maps/streaming), so its score is heavily
 *    discounted — repeatedly clearing it would mostly just waste battery
 *    re-downloading the same data for little lasting benefit.
 */
class AutoCleanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private enum class StorageTier { NORMAL, LOW, CRITICAL }

    private data class Thresholds(val minCacheBytes: Long, val minIdleMillis: Long, val maxAppsPerRun: Int)

    private fun thresholdsFor(level: AutoCleanAggressiveness): Thresholds = when (level) {
        AutoCleanAggressiveness.CONSERVATIVE -> Thresholds(100L * MB, 14L * DAY_MILLIS, 5)
        AutoCleanAggressiveness.BALANCED -> Thresholds(50L * MB, 7L * DAY_MILLIS, 15)
        AutoCleanAggressiveness.AGGRESSIVE -> Thresholds(20L * MB, 3L * DAY_MILLIS, 40)
    }

    private fun storageTierOf(freeBytes: Long, totalBytes: Long): StorageTier {
        val freeRatio = if (totalBytes > 0) freeBytes.toDouble() / totalBytes else 1.0
        return when {
            freeBytes < CRITICAL_STORAGE_BYTES -> StorageTier.CRITICAL
            freeBytes < LOW_STORAGE_BYTES || freeRatio < 0.10 -> StorageTier.LOW
            else -> StorageTier.NORMAL
        }
    }

    // Higher score = cleaned first. Cache size contributes on a log scale (so
    // one 2GB app doesn't completely dominate the ranking over several
    // 200MB ones) and idle time on a 0..1 scale capped at 30 days. Apps that
    // demonstrably regrew most of their cache within 2 days of the last
    // auto-clean get heavily discounted instead of excluded outright — still
    // eligible if things get desperate (Critical tier), just not first in line.
    private fun score(app: AppStorageInfo, now: Long, lastRecords: Map<String, AutoCleanRecord>): Double {
        val cacheMb = app.cacheBytes / MB.toDouble()
        val cacheScore = ln(cacheMb + 1.0)
        val idleDays = if (app.lastUsedTime == 0L) 30.0
            else ((now - app.lastUsedTime) / DAY_MILLIS.toDouble()).coerceIn(0.0, 30.0)
        val idleScore = idleDays / 30.0

        var multiplier = 1.0
        lastRecords[app.packageName]?.let { record ->
            val sinceClean = now - record.timestamp
            if (sinceClean in 1 until 2 * DAY_MILLIS && record.cacheBytesAtClean > 0) {
                val regrowthRatio = app.cacheBytes.toDouble() / record.cacheBytesAtClean.toDouble()
                if (regrowthRatio > 0.7) multiplier = 0.25
            }
        }
        return cacheScore * (1.0 + idleScore) * multiplier
    }

    private fun isHardEligible(app: AppStorageInfo, now: Long, ignored: Set<String>, minCacheBytes: Long): Boolean {
        if (app.packageName in ignored) return false
        if (app.cacheBytes < minCacheBytes) return false
        // Safety floor: never touch anything opened in the last hour, no
        // matter how desperate the storage situation is.
        if (app.lastUsedTime != 0L && now - app.lastUsedTime < HOUR_MILLIS) return false
        return true
    }

    private suspend fun waitForShizukuService(timeoutMs: Long = 6000): Boolean {
        ShizukuHelper.bindService(applicationContext)
        val start = System.currentTimeMillis()
        while (ShizukuHelper.cacheService == null && System.currentTimeMillis() - start < timeoutMs) {
            delay(250)
        }
        return ShizukuHelper.cacheService != null
    }

    override suspend fun doWork(): Result {
        val historyPreference = HistoryPreference(applicationContext)
        val autoCleanPreference = AutoCleanPreference(applicationContext)
        val ignoreListPreference = IgnoreListPreference(applicationContext)
        val runHistoryPreference = AutoCleanRunHistoryPreference(applicationContext)
        val repository = StorageRepository(applicationContext)

        val settings = autoCleanPreference.settings.first()
        if (!settings.enabled) return Result.success()

        if (!ShizukuHelper.hasPermission() || !waitForShizukuService()) {
            historyPreference.record(
                HistoryEntry(System.currentTimeMillis(), HistoryActionType.AUTO_CLEAN_RUN, "Skipped — Shizuku not available", 0L)
            )
            return Result.success()
        }
        val service = ShizukuHelper.cacheService ?: return Result.success()

        val ignored = ignoreListPreference.ignoredPackages.first()
        val overview = repository.getStorageOverview()
        val tier = storageTierOf(overview.freeBytes, overview.totalBytes)

        val effectiveAggressiveness = if (tier != StorageTier.NORMAL) AutoCleanAggressiveness.AGGRESSIVE else settings.aggressiveness
        val thresholds = thresholdsFor(effectiveAggressiveness)
        val records = runHistoryPreference.records.first()
        val now = System.currentTimeMillis()
        val allApps = repository.getInstalledApps()

        // Normal idle-time gate for the configured (or escalated) aggressiveness.
        var pool = allApps.filter {
            isHardEligible(it, now, ignored, thresholds.minCacheBytes) &&
                (it.lastUsedTime == 0L || (now - it.lastUsedTime) >= thresholds.minIdleMillis)
        }

        // Critical tier: also pull in smaller/more-recently-used caches that
        // the normal gate above would skip — still subject to every hard
        // safety check (ignore list, 1-hour floor), just a lower size bar and
        // no idle-time requirement.
        if (tier == StorageTier.CRITICAL) {
            val poolPackages = pool.map { it.packageName }.toSet()
            val emergencyExtra = allApps.filter {
                it.packageName !in poolPackages &&
                    isHardEligible(it, now, ignored, EMERGENCY_MIN_CACHE_BYTES)
            }
            pool = pool + emergencyExtra
        }

        val ranked = pool.sortedByDescending { score(it, now, records) }

        // Normal tier: a small, deliberate pass — top N by score, full stop.
        // Low/Critical tier: keep going (in score order) only until enough is
        // freed to climb back above the threshold that triggered this, plus a
        // small buffer, instead of indiscriminately clearing everything.
        val targets = if (tier == StorageTier.NORMAL) {
            ranked.take(thresholds.maxAppsPerRun)
        } else {
            val targetFreed = (LOW_STORAGE_BYTES - overview.freeBytes).coerceAtLeast(0) + 200L * MB
            val selected = mutableListOf<AppStorageInfo>()
            var runningTotal = 0L
            for (app in ranked) {
                if (runningTotal >= targetFreed) break
                selected.add(app)
                runningTotal += app.cacheBytes
            }
            selected
        }

        var cleanedCount = 0
        var freedBytes = 0L
        targets.forEach { app ->
            if (repository.clearAppCache(service, app.packageName)) {
                cleanedCount++
                freedBytes += app.cacheBytes
                runHistoryPreference.recordCleaned(app.packageName, app.cacheBytes)
            }
        }

        val tierNote = when (tier) {
            StorageTier.CRITICAL -> " (critical storage)"
            StorageTier.LOW -> " (low storage)"
            StorageTier.NORMAL -> ""
        }
        historyPreference.record(
            HistoryEntry(
                timestamp = System.currentTimeMillis(),
                action = HistoryActionType.AUTO_CLEAN_RUN,
                appName = "$cleanedCount app${if (cleanedCount == 1) "" else "s"}$tierNote",
                bytesFreed = freedBytes
            )
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "cleanspace_auto_clean"
        private const val MB = 1024L * 1024
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val HOUR_MILLIS = 60L * 60 * 1000
        private const val LOW_STORAGE_BYTES = 1024L * MB        // < 1 GB free
        private const val CRITICAL_STORAGE_BYTES = 300L * MB    // < 300 MB free
        private const val EMERGENCY_MIN_CACHE_BYTES = 5L * MB
    }
}
