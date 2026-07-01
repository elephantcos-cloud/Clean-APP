package com.shohan.cleanspace.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shohan.cleanspace.MainActivity
import com.shohan.cleanspace.R
import com.shohan.cleanspace.viewmodel.MainViewModel

/**
 * Posts a single low-priority notification summarizing what Auto-Clean did
 * in the background, so the user finds out without having to remember to
 * open History. Entirely optional: gated behind
 * AutoCleanSettings.notifyOnClean (off by default) and, on Android 13+, the
 * POST_NOTIFICATIONS runtime permission — which is only ever requested when
 * the user explicitly turns the setting on in Settings, never on app launch.
 */
object AutoCleanNotifier {

    private const val CHANNEL_ID = "auto_clean_results"
    private const val NOTIFICATION_ID = 1001

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto-Clean results",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "A summary shown after each background Auto-Clean run"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyRunComplete(context: Context, cleanedCount: Int, freedBytes: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_clean)
            .setContentTitle("Auto-Clean finished")
            .setContentText(
                "Cleared $cleanedCount app${if (cleanedCount == 1) "" else "s"} — " +
                    "${MainViewModel.formatBytes(freedBytes)} freed"
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check above and this call — ignore.
        }
    }
}
