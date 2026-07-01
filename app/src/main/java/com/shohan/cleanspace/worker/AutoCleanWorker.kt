package com.shohan.cleanspace.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
import com.shohan.cleanspace.notification.AutoCleanNotifier
import com.shohan.cleanspace.shizuku.ShizukuHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.math.ln

/**
 * Rule-based ("smart", not a real ML/LLM model — see README) background
 * cache cleaner. Runs on a schedule via WorkManager.
 *
 * How it decides what to clean (in order):
 * 1. Hard safety gates, always enforced no matter what: never an ignored
 *    app, never an app used in the last hour, never below the size floor for
 *    the current tier, and never an app already cleaned within the last
 *    [COOLDOWN_MILLIS] — this last one is an unconditional guarantee that no
 *    single app can be hammered repeatedly, even if every other rule below
 *    somehow pointed at it run after run.
 * 2. Storage tier — NORMAL / LOW (<1GB or <10% free) / CRITICAL (<300MB free).
 *    LOW and CRITICAL temporarily escalate to Aggressive thresholds
 *    regardless of the user's chosen setting, and clean until either the
 *    candidate pool runs out or enough has been freed to climb back out of
 *    that tier — not just "clean everything that qualifies".
 * 3. Ranking — eligible apps are scored (bigger cache + longer idle = higher)
 *    and only the top N are touched per run (N depends on aggressiveness),
 *    so a normal run is a small, deliberate pass rather than indiscriminate.
 * 4. Regrowth awareness — if an app's cache mostly grew back within 2 days of
 *    the last time Auto-Clean cleared it, its score is heavily discounted for
 *    that run. If that happens again on the NEXT run too (2 in a row), the
 *    app is soft-skipped entirely for a while — repeatedly clearing a cache
 *    that always refills fast mostly just wastes battery re-downloading the
 *    same data. The skip is temporary: once 2 days pass since the last actual
 *    clean without a fresh comparison point, it naturally becomes eligible
 *    again. In a Critical-storage run, the skip is overridden (still scored
 *    low, but no longer excluded) since freeing space right now matters more
 *    than long-term efficiency.
 */
class AutoCleanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private enum class StorageTier { NORMAL, LOW, CRITICAL }

    private data class Thresholds(val minCacheBytes: Long, val minIdleMillis: Long, val maxAppsPerRun: Int)

    private data class Regrowth(val isFast: Boolean, val streak: Int)

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

    // Whether this app's cache mostly regrew within 2 days of the last time
    // Auto-Clean cleared it, and how many such cycles in a row that's now
    // been true. No prior record at all (never auto-cleaned, or more than 2
    // days since the last time) simply means "not fast regrowth, streak 0".
    private fun regrowthOf(app: AppStorageInfo, now: Long, lastRecords: Map<String, AutoCleanRecord>): Regrowth {
        val record = lastRecords[app.packageName] ?: return Regrowth(false, 0)
        val sinceClean = now - record.timestamp
        val isFast = sinceClean in 1 until 2 * DAY_MILLIS && record.cacheBytesAtClean > 0 &&
            (app.cacheBytes.toDouble() / record.cacheBytesAtClean.toDouble()) > 0.7
        return Regrowth(isFast, if (isFast) record.fastRegrowthStreak + 1 else 0)
    }

    // Higher score = cleaned first. Cache size contributes on a log scale (so
    // one 2GB app doesn't completely dominate the ranking over several
    // 200MB ones) and idle time on a 0..1 scale capped at 30 days.
    private fun score(app: AppStorageInfo, now: Long, regrowth: Regrowth): Double {
        val cacheMb = app.cacheBytes / MB.toDouble()
        val cacheScore = ln(cacheMb + 1.0)
        val idleDays = if (app.lastUsedTime == 0L) 30.0
            else ((now - app.lastUsedTime) / DAY_MILLIS.toDouble()).coerceIn(0.0, 30.0)
        val idleScore = idleDays / 30.0
        val multiplier = if (regrowth.isFast) 0.25 else 1.0
        return cacheScore * (1.0 + idleScore) * multiplier
    }

    private fun isHardEligible(
        app: AppStorageInfo,
        now: Long,
        ignored: Set<String>,
        minCacheBytes: Long,
        lastRecords: Map<String, AutoCleanRecord>
    ): Boolean {
        if (app.packageName in ignored) return false
        if (app.cacheBytes < minCacheBytes) return false
        // Safety floor: never touch anything opened in the last hour, no
        // matter how desperate the storage situation is.
        if (app.lastUsedTime != 0L && now - app.lastUsedTime < HOUR_MILLIS) return false
        // Per-app cooldown: an absolute guarantee, independent of tier or
        // score, that the same app can't be re-cleaned more than once every
        // COOLDOWN_MILLIS — this is what prevents repeated-rapid-fire on one
        // app even if every other rule above somehow kept pointing at it.
        lastRecords[app.packageName]?.let { record ->
            if (now - record.timestamp < COOLDOWN_MILLIS) return false
        }
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

    private fun scheduleRegrowthCheck(packageName: String, cacheBytesAtClean: Long) {
        val request = OneTimeWorkRequestBuilder<RegrowthCheckWorker>()
            .setInitialDelay(RegrowthCheckWorker.CHECK_DELAY_MINUTES, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    RegrowthCheckWorker.KEY_PACKAGE to packageName,
                    RegrowthCheckWorker.KEY_CACHE_AT_CLEAN to cacheBytesAtClean
                )
            )
            .build()
        // Unique-by-package so a later clean of the same app (e.g. a manual
        // one from the main screen) cleanly replaces any still-pending check
        // for an earlier clean, rather than piling up duplicate checks.
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            RegrowthCheckWorker.WORK_NAME_PREFIX + packageName,
            ExistingWorkPolicy.REPLACE,
            request
        )
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

        // Normal idle-time + cooldown gate for the configured (or escalated) aggressiveness.
        var pool = allApps.filter {
            isHardEligible(it, now, ignored, thresholds.minCacheBytes, records) &&
                (it.lastUsedTime == 0L || (now - it.lastUsedTime) >= thresholds.minIdleMillis)
        }

        // Critical tier: also pull in smaller/more-recently-used caches that
        // the normal gate above would skip — still subject to every hard
        // safety check (ignore list, 1-hour floor, cooldown), just a lower
        // size bar and no idle-time requirement.
        if (tier == StorageTier.CRITICAL) {
            val poolPackages = pool.map { it.packageName }.toSet()
            val emergencyExtra = allApps.filter {
                it.packageName !in poolPackages &&
                    isHardEligible(it, now, ignored, EMERGENCY_MIN_CACHE_BYTES, records)
            }
            pool = pool + emergencyExtra
        }

        val withRegrowth = pool.map { it to regrowthOf(it, now, records) }

        // Soft-skip: 2+ consecutive fast-regrowth cycles excludes an app
        // entirely from a Normal/Low run (it's still scored low rather than
        // excluded on the first fast-regrowth cycle — this only kicks in
        // once the pattern repeats). Overridden in Critical tier.
        val eligible = if (tier == StorageTier.CRITICAL) withRegrowth
            else withRegrowth.filter { (_, regrowth) -> regrowth.streak < STREAK_SKIP_THRESHOLD }

        val ranked = eligible.sortedByDescending { (app, regrowth) -> score(app, now, regrowth) }

        // Normal tier: a small, deliberate pass — top N by score, full stop.
        // Low/Critical tier: keep going (in score order) only until enough is
        // freed to climb back above the threshold that triggered this, plus a
        // small buffer, instead of indiscriminately clearing everything.
        val targets = if (tier == StorageTier.NORMAL) {
            ranked.take(thresholds.maxAppsPerRun)
        } else {
            val targetFreed = (LOW_STORAGE_BYTES - overview.freeBytes).coerceAtLeast(0) + 200L * MB
            val selected = mutableListOf<Pair<AppStorageInfo, Regrowth>>()
            var runningTotal = 0L
            for (pair in ranked) {
                if (runningTotal >= targetFreed) break
                selected.add(pair)
                runningTotal += pair.first.cacheBytes
            }
            selected
        }

        var cleanedCount = 0
        var freedBytes = 0L
        targets.forEach { (app, regrowth) ->
            if (repository.clearAppCache(service, app.packageName)) {
                cleanedCount++
                freedBytes += app.cacheBytes
                runHistoryPreference.recordCleaned(app.packageName, app.cacheBytes, regrowth.streak)
                scheduleRegrowthCheck(app.packageName, app.cacheBytes)
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
        if (cleanedCount > 0 && settings.notifyOnClean) {
            AutoCleanNotifier.notifyRunComplete(applicationContext, cleanedCount, freedBytes)
        }
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
        // Absolute floor: never re-clean the same app within this window,
        // regardless of score, tier, or anything else.
        private const val COOLDOWN_MILLIS = 12L * 60 * 60 * 1000
        private const val STREAK_SKIP_THRESHOLD = 2
    }
}
