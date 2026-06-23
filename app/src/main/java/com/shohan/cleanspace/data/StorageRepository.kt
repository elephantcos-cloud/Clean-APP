package com.shohan.cleanspace.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
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

    suspend fun getInstalledApps(): List<AppStorageInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val results = mutableListOf<AppStorageInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PermissionHelper.hasUsageAccess(context)) {
            try {
                val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val sm  = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val uuid = sm.getUuidForPath(context.dataDir)
                pm.getInstalledApplications(0).forEach { app ->
                    try {
                        val stats = ssm.queryStatsForUid(uuid, app.uid)
                        results.add(AppStorageInfo(
                            packageName = app.packageName,
                            appName = pm.getApplicationLabel(app).toString(),
                            appBytes = stats.appBytes,
                            cacheBytes = stats.cacheBytes,
                            dataBytes = stats.dataBytes,
                            isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        ))
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        results.sortedByDescending { it.cacheBytes }
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

    suspend fun forceStopApp(service: ICacheService, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                service.runCommand("am force-stop ${shellQuote(packageName)}")
                true
            } catch (_: Exception) { false }
        }
}
