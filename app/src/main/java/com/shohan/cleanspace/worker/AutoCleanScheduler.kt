package com.shohan.cleanspace.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shohan.cleanspace.data.models.AutoCleanSettings
import java.util.concurrent.TimeUnit

/**
 * Applies the current AutoCleanSettings to WorkManager. WorkManager's
 * periodic work is "best effort" — Android (Doze, battery optimization,
 * background restrictions) can and does delay the actual run time, so the
 * configured frequency is a minimum interval, not a guarantee.
 */
object AutoCleanScheduler {

    fun apply(context: Context, settings: AutoCleanSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.enabled) {
            workManager.cancelUniqueWork(AutoCleanWorker.WORK_NAME)
            return
        }
        // No network needed — this never leaves the device — but charging is
        // not required either; the whole point is it works unattended.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoCleanWorker>(
            settings.frequency.hours, TimeUnit.HOURS
        ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            AutoCleanWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }
}
