package com.shohan.cleanspace.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.shohan.cleanspace.R
import com.shohan.cleanspace.data.IgnoreListPreference
import com.shohan.cleanspace.data.StorageRepository
import com.shohan.cleanspace.shizuku.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: "Clear Cache" — one tap, no need to open the app.
 * Clears cache for every eligible app (same eligibility as the Cache Clean
 * tab: has cache, not on the ignore list) directly via Shizuku.
 *
 * minSdk for this whole app is already 24, the same API level TileService
 * itself requires, so no version guard is needed here.
 *
 * If Shizuku isn't connected yet (e.g. the app process was killed and only
 * this tile woke it up), a short wait-and-bind is attempted, mirroring the
 * same pattern AutoCleanWorker uses; if that still fails, the tile falls
 * back to simply opening the app instead of silently doing nothing.
 */
class CacheCleanTileService : TileService() {

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        updateTile(active = false)
    }

    override fun onClick() {
        super.onClick()
        if (job?.isActive == true) return

        updateTile(active = true)
        job = scope.launch {
            try {
                if (!waitForShizukuService()) {
                    packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                        openAndCollapse(intent)
                    }
                    return@launch
                }
                val service = ShizukuHelper.cacheService ?: return@launch
                val repository = StorageRepository(applicationContext)
                val ignoreListPreference = IgnoreListPreference(applicationContext)
                val ignored = ignoreListPreference.ignoredPackages.first()
                val targets = repository.getInstalledApps()
                    .filter { it.cacheBytes > 0 && it.packageName !in ignored }
                targets.forEach { app -> repository.clearAppCache(service, app.packageName) }
            } finally {
                updateTile(active = false)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun openAndCollapse(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private suspend fun waitForShizukuService(timeoutMs: Long = 4000): Boolean {
        if (ShizukuHelper.cacheService != null) return true
        if (!ShizukuHelper.hasPermission()) return false
        ShizukuHelper.bindService(applicationContext)
        val start = System.currentTimeMillis()
        while (ShizukuHelper.cacheService == null && System.currentTimeMillis() - start < timeoutMs) {
            delay(200)
        }
        return ShizukuHelper.cacheService != null
    }

    private fun updateTile(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
        tile.label = if (active) "Clearing…" else "Clear Cache"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_clean)
        tile.updateTile()
    }
}
