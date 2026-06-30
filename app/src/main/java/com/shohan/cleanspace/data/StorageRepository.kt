package com.shohan.cleanspace.data

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import com.shohan.cleanspace.data.models.AppStorageInfo
import com.shohan.cleanspace.data.models.StorageOverview
import com.shohan.cleanspace.shizuku.ICacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageRepository(private val context: Context) {

    // ── Storage Overview ──────────────────────────────────────────────────────
    // Simple, accurate Total/Used/Free straight from StatFs. The old per-category
    // (Apps/Images/Videos/Audio) breakdown was removed because it double-counted:
    // StorageStatsManager's per-app dataBytes already includes an app's files
    // under Android/media/<pkg> and Android/data/<pkg> on external storage, and
    // those SAME files are also indexed (and summed again) by MediaStore as
    // Images/Video/Audio — so Apps + Images + Videos + Audio could add up to far
    // more than the actual total used space. A single accurate number beats a
    // detailed but wrong one.

    suspend fun getStorageOverview(): StorageOverview = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        StorageOverview(totalBytes = total, usedBytes = total - free, freeBytes = free)
    }

    // ── Installed Apps (cache + force-stop) ─────────────────────────────────────

    // Last-used timestamp per package via UsageStatsManager (same Usage Access
    // permission already required for StorageStatsManager — no extra permission
    // needed). INTERVAL_BEST can return several overlapping buckets per package,
    // so the largest lastTimeUsed across all of them is kept.
    private fun lastUsedTimes(): Map<String, Long> {
        val usageMap = HashMap<String, Long>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = 0L
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)?.forEach { stat ->
                val existing = usageMap[stat.packageName] ?: 0L
                if (stat.lastTimeUsed > existing) usageMap[stat.packageName] = stat.lastTimeUsed
            }
        } catch (_: Exception) {}
        return usageMap
    }

    suspend fun getInstalledApps(): List<AppStorageInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val results = mutableListOf<AppStorageInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PermissionHelper.hasUsageAccess(context)) {
            try {
                val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val sm  = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val uuid = sm.getUuidForPath(context.dataDir)
                val usageMap = lastUsedTimes()
                pm.getInstalledApplications(0).forEach { app ->
                    try {
                        val stats = ssm.queryStatsForUid(uuid, app.uid)
                        val enabledSetting = try { pm.getApplicationEnabledSetting(app.packageName) } catch (_: Exception) { PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
                        val isDisabled = enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
                        results.add(AppStorageInfo(
                            packageName = app.packageName,
                            appName = pm.getApplicationLabel(app).toString(),
                            appBytes = stats.appBytes,
                            cacheBytes = stats.cacheBytes,
                            dataBytes = stats.dataBytes,
                            isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            isStopped = (app.flags and ApplicationInfo.FLAG_STOPPED) != 0,
                            isPersistent = (app.flags and ApplicationInfo.FLAG_PERSISTENT) != 0,
                            hasLaunchIntent = pm.getLaunchIntentForPackage(app.packageName) != null,
                            lastUsedTime = usageMap[app.packageName] ?: 0L,
                            isDisabled = isDisabled
                        ))
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        results.sortedByDescending { it.cacheBytes }
    }

    // Single-package cache size lookup — used for the quick post-clean
    // regrowth check (RegrowthCheckWorker), so that check doesn't have to
    // re-scan every installed app just to look at one of them.
    suspend fun getCacheBytesFor(packageName: String): Long? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !PermissionHelper.hasUsageAccess(context)) return@withContext null
        try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val uuid = sm.getUuidForPath(context.dataDir)
            ssm.queryStatsForUid(uuid, appInfo.uid).cacheBytes
        } catch (_: Exception) { null }
    }

    // App icons, decoded to small fixed-size bitmaps up front so Compose never
    // has to touch a Drawable directly. Loaded once per app-list refresh; a
    // missing/failed icon for one package just leaves it out of the map (the
    // UI falls back to a placeholder), it never fails the whole load.
    suspend fun loadAppIcons(packageNames: List<String>): Map<String, Bitmap> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val result = HashMap<String, Bitmap>()
        val sizePx = 96
        packageNames.forEach { pkg ->
            try {
                result[pkg] = drawableToBitmap(pm.getApplicationIcon(pkg), sizePx)
            } catch (_: Exception) {}
        }
        result
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    // Well-known critical packages + whatever the device currently has set as
    // default launcher/dialer/SMS app — used to seed the ignore list once on
    // first run so a new user can't accidentally force-stop something that
    // can hang the phone (this is exactly what happened before this existed).
    fun detectDefaultProtectedPackages(): Set<String> {
        val pm = context.packageManager
        val result = mutableSetOf<String>()
        listOf(
            "com.android.systemui",
            "android",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom"
        ).forEach { pkg ->
            try { pm.getApplicationInfo(pkg, 0); result.add(pkg) } catch (_: Exception) {}
        }
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.resolveActivity(homeIntent, 0)?.activityInfo?.packageName?.let { result.add(it) }
        } catch (_: Exception) {}
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecom?.defaultDialerPackage?.let { result.add(it) }
        } catch (_: Exception) {}
        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let { result.add(it) }
        } catch (_: Exception) {}
        result.add(context.packageName) // never let CleanSpace force-stop/disable itself
        return result
    }

    // Single-quote shell escaping: wrapping a value in single quotes and escaping
    // any embedded single quote as '\'' is the only quoting style that fully
    // neutralizes shell metacharacters ($(...), `...`, ;, &&, etc). Package names
    // are validated by Android (only [a-zA-Z0-9_.] is allowed) so this can't
    // actually be exploited here, but it costs nothing to do it properly.
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    suspend fun clearAppCache(service: ICacheService, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    service.runCommand("pm clear --cache-only ${shellQuote(packageName)}")
                } else {
                    service.runCommand("pm trim-caches 999999999999")
                }
                true
            } catch (_: Exception) { false }
        }

    // Full data wipe — equivalent to Settings' "Clear Storage" button. Resets
    // the app to a fresh-install state: logs the user out, erases local
    // files/settings. Far more destructive than clearAppCache above.
    suspend fun clearAppData(service: ICacheService, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                service.runCommand("pm clear ${shellQuote(packageName)}")
                true
            } catch (_: Exception) { false }
        }

    suspend fun forceStopApp(service: ICacheService, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                service.runCommand("am force-stop ${shellQuote(packageName)}")
                true
            } catch (_: Exception) { false }
        }

    suspend fun disableApp(service: ICacheService, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                service.runCommand("pm disable-user --user 0 ${shellQuote(packageName)}")
                true
            } catch (_: Exception) { false }
        }

    suspend fun enableApp(service: ICacheService, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                service.runCommand("pm enable ${shellQuote(packageName)}")
                true
            } catch (_: Exception) { false }
        }
}
