package com.shohan.cleanspace.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shohan.cleanspace.data.models.AutoCleanSettings
import java.util.concurrent.TimeUnit

/**
 * Applies the current AutoCleanSettings to WorkManager. WorkManager's
 * periodic work is "best effort" — Android (Doze, battery optimization,
 * background restrictions) can and does delay the actual run time, so the
 * configured frequency is a minimum interval, not a guarantee.
 *
 * Fix: there used to be only one function, called with REPLACE every time
 * the settings Flow emitted — including the very first emission on every
 * single app start (Flow.collect always re-delivers the current value right
 * away). REPLACE cancels the existing schedule and starts a brand new
 * PeriodicWorkRequest with no initial delay, so every time the app was
 * opened, Auto-Clean effectively restarted and ran again almost immediately
 * — which is exactly what the History screen showed (several runs seconds
 * apart). Now there are two distinct entry points with different intent.
 */
object AutoCleanScheduler {

    // Call on every app start. Never disturbs an already-running schedule's
    // timing — only creates one if none exists yet (KEEP = no-op if the
    // unique work is already scheduled).
    fun ensureScheduled(context: Context, settings: AutoCleanSettings) {
        apply(context, settings, ExistingPeriodicWorkPolicy.KEEP)
    }

    // Call ONLY when the user explicitly changes a setting (enabled,
    // frequency, or aggressiveness) in Settings. Intentionally replaces any
    // existing schedule with a fresh one — but the fresh one still has a
    // full-interval initial delay, so changing a setting never itself
    // triggers an immediate run.
    fun reschedule(context: Context, settings: AutoCleanSettings) {
        apply(context, settings, ExistingPeriodicWorkPolicy.REPLACE)
    }

    private fun apply(context: Context, settings: AutoCleanSettings, policy: ExistingPeriodicWorkPolicy) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.enabled) {
            workManager.cancelUniqueWork(AutoCleanWorker.WORK_NAME)
            return
        }
        workManager.enqueueUniquePeriodicWork(AutoCleanWorker.WORK_NAME, policy, buildRequest(settings))
    }

    private fun buildRequest(settings: AutoCleanSettings): PeriodicWorkRequest {
        // No network needed — this never leaves the device — but charging is
        // not required either; the whole point is it works unattended.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        return PeriodicWorkRequestBuilder<AutoCleanWorker>(settings.frequency.hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            // The fix for the rapid-refire bug: always wait at least one full
            // interval before the first run of a freshly (re)created schedule.
            .setInitialDelay(settings.frequency.hours, TimeUnit.HOURS)
            .build()
    }
}
