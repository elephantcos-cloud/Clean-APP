package com.shohan.cleanspace.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.cleanspace.data.PermissionHelper
import com.shohan.cleanspace.data.StorageRepository
import com.shohan.cleanspace.data.ThemePreference
import com.shohan.cleanspace.data.models.AppPermissions
import com.shohan.cleanspace.data.models.AppStorageInfo
import com.shohan.cleanspace.data.models.BulkActionProgress
import com.shohan.cleanspace.data.models.StorageOverview
import com.shohan.cleanspace.data.models.ThemeMode
import com.shohan.cleanspace.shizuku.ShizukuHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StorageRepository(application)
    private val themePreference = ThemePreference(application)

    private var loadJob: Job? = null
    private var bulkJob: Job? = null

    private val _storageOverview = MutableStateFlow(StorageOverview())
    val storageOverview: StateFlow<StorageOverview> = _storageOverview.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppStorageInfo>>(emptyList())
    val installedApps: StateFlow<List<AppStorageInfo>> = _installedApps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _permissions = MutableStateFlow(AppPermissions())
    val permissions: StateFlow<AppPermissions> = _permissions.asStateFlow()

    // Fix: this now carries the current app's NAME + index/total (not just a
    // count), so the UI can show a genuine live "Clearing: WhatsApp (12/45)"
    // screen instead of a generic spinner.
    private val _bulkActionProgress = MutableStateFlow<BulkActionProgress?>(null)
    val bulkActionProgress: StateFlow<BulkActionProgress?> = _bulkActionProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        refreshPermissions()
        viewModelScope.launch {
            themePreference.themeMode.collect { _themeMode.value = it }
        }
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _permissions.value = AppPermissions(
            usageAccess = PermissionHelper.hasUsageAccess(ctx),
            shizukuInstalled = ShizukuHelper.isShizukuInstalled(ctx),
            shizukuRunning = ShizukuHelper.isShizukuRunning(),
            shizukuPermission = ShizukuHelper.hasPermission()
        )
        if (_permissions.value.shizukuPermission) {
            ShizukuHelper.bindService(ctx)
        }
    }

    fun onShizukuPermissionResult(granted: Boolean) {
        refreshPermissions()
        if (granted) ShizukuHelper.bindService(getApplication())
    }

    fun requestShizukuPermission() { ShizukuHelper.requestPermission() }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreference.setThemeMode(mode) }
    }

    fun loadHome() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _appsLoading.value = true
            try {
                _storageOverview.value = repository.getStorageOverview()
                _installedApps.value = repository.getInstalledApps()
            } finally { _appsLoading.value = false }
        }
    }

    fun toggleAppSelection(app: AppStorageInfo) {
        _installedApps.value = _installedApps.value.map {
            if (it.packageName == app.packageName) it.copy(selected = !it.selected) else it
        }
    }

    fun selectAllApps() {
        _installedApps.value = _installedApps.value.map { it.copy(selected = true) }
    }

    fun unselectAllApps() {
        _installedApps.value = _installedApps.value.map { it.copy(selected = false) }
    }

    fun cancelBulkAction() {
        bulkJob?.cancel()
        _bulkActionProgress.value = null
        _statusMessage.value = "Cancelled"
    }

    fun clearSingleAppCache(app: AppStorageInfo) {
        viewModelScope.launch {
            val service = ShizukuHelper.cacheService
            if (service == null) {
                _statusMessage.value = "Shizuku is not connected"
                return@launch
            }
            val ok = repository.clearAppCache(service, app.packageName)
            _statusMessage.value = if (ok) "${app.appName} cache cleared" else "Failed to clear ${app.appName}"
            loadHome()
        }
    }

    fun forceStopSingleApp(app: AppStorageInfo) {
        viewModelScope.launch {
            val service = ShizukuHelper.cacheService
            if (service == null) {
                _statusMessage.value = "Shizuku is not connected"
                return@launch
            }
            val ok = repository.forceStopApp(service, app.packageName)
            _statusMessage.value = if (ok) "${app.appName} stopped" else "Failed to stop ${app.appName}"
        }
    }

    fun clearSelectedAppsCache() {
        val targets = _installedApps.value.filter { it.selected }
        if (targets.isEmpty()) return
        runBulkAction(targets, actionLabel = "Clearing cache") { service, app ->
            repository.clearAppCache(service, app.packageName)
        }
    }

    fun forceStopSelectedApps() {
        val targets = _installedApps.value.filter { it.selected }
        if (targets.isEmpty()) return
        runBulkAction(targets, actionLabel = "Force stopping") { service, app ->
            repository.forceStopApp(service, app.packageName)
        }
    }

    private fun runBulkAction(
        targets: List<AppStorageInfo>,
        actionLabel: String,
        action: suspend (com.shohan.cleanspace.shizuku.ICacheService, AppStorageInfo) -> Boolean
    ) {
        if (bulkJob?.isActive == true) return
        bulkJob = viewModelScope.launch {
            val service = ShizukuHelper.cacheService
            if (service == null) {
                _statusMessage.value = "Shizuku service not connected. Is Shizuku running?"
                return@launch
            }
            var success = 0
            targets.forEachIndexed { index, app ->
                _bulkActionProgress.value = BulkActionProgress(
                    actionLabel = actionLabel,
                    currentAppName = app.appName,
                    currentIndex = index + 1,
                    total = targets.size
                )
                if (action(service, app)) success++
            }
            _bulkActionProgress.value = null
            _statusMessage.value = "$actionLabel: $success/${targets.size} apps done"
            loadHome()
        }
    }

    fun clearMessage() { _statusMessage.value = null }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unitIndex = 0
            while (value >= 1024 && unitIndex < units.size - 1) {
                value /= 1024
                unitIndex++
            }
            return String.format("%.1f %s", value, units[unitIndex])
        }
    }
}
