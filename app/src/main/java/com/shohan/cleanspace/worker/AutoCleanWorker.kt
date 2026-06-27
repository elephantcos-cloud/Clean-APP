package com.shohan.cleanspace.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shohan.cleanspace.data.AutoCleanPreference
import com.shohan.cleanspace.data.HistoryPreference
import com.shohan.cleanspace.data.IgnoreListPreference
import com.shohan.cleanspace.data.StorageRepository
import com.shohan.cleanspace.data.models.AutoCleanAggressiveness
import com.shohan.cleanspace.data.models.HistoryActionType
import com.shohan.cleanspace.data.models.HistoryEntry
import com.shohan.cleanspace.shizuku.ShizukuHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Rule-based ("smart", not a real ML/LLM model — see README) background
 * cache cleaner. Runs on a schedule via WorkManager; looks at each app's
 * cache size and how long it's been since it was last used, plus the
 * device's current free space, and clears cache for whatever crosses the
 * configured thresholds. Never touches ignored apps. Records one summary
 * entry in History per run, success or not, so the user always has a trail
 * to check if anything seems off.
 */
class AutoCleanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private data class Thresholds(val minCacheBytes: Long, val minIdleMillis: Long)

    private fun thresholdsFor(level: AutoCleanAggressiveness): Thresholds = when (level) {
        AutoCleanAggressiveness.CONSERVATIVE -> Thresholds(100L * 1024 * 1024, 14L * DAY_MILLIS)
        AutoCleanAggressiveness.BALANCED -> Thresholds(50L * 1024 * 1024, 7L * DAY_MILLIS)
        AutoCleanAggressiveness.AGGRESSIVE -> Thresholds(20L * 1024 * 1024, 3L * DAY_MILLIS)
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
        val lowStorage = overview.freeBytes < 1024L * 1024 * 1024 // < 1GB free

        val thresholds = if (lowStorage) thresholdsFor(AutoCleanAggressiveness.AGGRESSIVE)
                          else thresholdsFor(settings.aggressiveness)

        val now = System.currentTimeMillis()
        val candidates = repository.getInstalledApps().filter { app ->
            app.packageName !in ignored &&
                app.cacheBytes >= thresholds.minCacheBytes &&
                (app.lastUsedTime == 0L || (now - app.lastUsedTime) >= thresholds.minIdleMillis)
        }

        var cleanedCount = 0
        var freedBytes = 0L
        candidates.forEach { app ->
            if (repository.clearAppCache(service, app.packageName)) {
                cleanedCount++
                freedBytes += app.cacheBytes
            }
        }

        historyPreference.record(
            HistoryEntry(
                timestamp = System.currentTimeMillis(),
                action = HistoryActionType.AUTO_CLEAN_RUN,
                appName = "$cleanedCount app${if (cleanedCount == 1) "" else "s"}",
                bytesFreed = freedBytes
            )
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "cleanspace_auto_clean"
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
