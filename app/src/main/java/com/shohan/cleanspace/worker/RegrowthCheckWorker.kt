package com.shohan.cleanspace.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shohan.cleanspace.data.AutoCleanRunHistoryPreference
import com.shohan.cleanspace.data.StorageRepository
import kotlinx.coroutines.flow.first

/**
 * Runs once, a short while after AutoCleanWorker clears one app's cache.
 *
 * Why this exists: the main worker only re-examines an app's regrowth at the
 * NEXT scheduled run — which could be a day or a week away depending on the
 * configured frequency. For an app whose cache regenerates almost instantly
 * (e.g. a browser actively prefetching pages), that means it would take 2+
 * full scheduled cycles (potentially 2+ weeks) before the "keeps regrowing
 * fast" pattern was even noticed. This check closes that gap: if most of the
 * cache is already back within ~30 minutes, that's unambiguous, immediate
 * proof the cache isn't worth clearing, so the skip is applied right away
 * instead of waiting to confirm the pattern twice.
 */
class RegrowthCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val packageName = inputData.getString(KEY_PACKAGE) ?: return Result.success()
        val cacheBytesAtClean = inputData.getLong(KEY_CACHE_AT_CLEAN, 0L)
        if (cacheBytesAtClean <= 0L) return Result.success()

        val repository = StorageRepository(applicationContext)
        val currentCache = repository.getCacheBytesFor(packageName) ?: return Result.success()

        val regrowthRatio = currentCache.toDouble() / cacheBytesAtClean.toDouble()
        if (regrowthRatio >= INSTANT_REGROWTH_RATIO) {
            val runHistoryPreference = AutoCleanRunHistoryPreference(applicationContext)
            val existing = runHistoryPreference.records.first()[packageName]
            // Only escalate if this is still the same clean we were sent to
            // check on (guards against a newer, unrelated clean having
            // happened in the meantime, which would have its own follow-up
            // check scheduled separately).
            if (existing != null && existing.cacheBytesAtClean == cacheBytesAtClean) {
                runHistoryPreference.escalateStreak(packageName, STREAK_SKIP_THRESHOLD)
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME_PREFIX = "cleanspace_regrowth_check_"
        const val KEY_PACKAGE = "package_name"
        const val KEY_CACHE_AT_CLEAN = "cache_at_clean"
        const val CHECK_DELAY_MINUTES = 30L
        private const val INSTANT_REGROWTH_RATIO = 0.5
        private const val STREAK_SKIP_THRESHOLD = 2
    }
}
