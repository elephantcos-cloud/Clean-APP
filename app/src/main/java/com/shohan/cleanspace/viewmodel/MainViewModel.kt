package com.shohan.cleanspace.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.cleanspace.data.IgnoreListPreference
import com.shohan.cleanspace.data.PermissionHelper
import com.shohan.cleanspace.data.StorageRepository
import com.shohan.cleanspace.data.ThemePreference
import com.shohan.cleanspace.data.models.AppFilter
import com.shohan.cleanspace.data.models.AppPermissions
import com.shohan.cleanspace.data.models.AppSortKey
import com.shohan.cleanspace.data.models.AppStorageInfo
import com.shohan.cleanspace.data.models.AppTab
import com.shohan.cleanspace.data.models.BulkActionProgress
import com.shohan.cleanspace.data.models.StorageOverview
import com.shohan.cleanspace.data.models.ThemeMode
import com.shohan.cleanspace.shizuku.ICacheService
import com.shohan.cleanspace.shizuku.ShizukuHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StorageRepository(application)
    private val themePreference = ThemePreference(application)
    private val ignoreListPreference = IgnoreListPreference(application)

    private var loadJob: Job? = null
    private var bulkJob: Job? = null

    private val _storageOverview = MutableStateFlow(StorageOverview())
    val storageOverview: StateFlow<StorageOverview> = _storageOverview.asStateFlow()

    // Master list — holds the only mutable per-app field that matters here
    // (`selected`). isIgnored is recomputed below via combine(), never stored
    // here directly, so it can never go stale relative to the preference.
    private val _rawApps = MutableStateFlow<List<AppStorageInfo>>(emptyList())

    private val _ignoredPackages = MutableStateFlow<Set<String>>(emptySet())

    private val _appFilter = MutableStateFlow(AppFilter.ALL)
    val appFilter: StateFlow<AppFilter> = _appFilter.asStateFlow()

    private val _activeTab = MutableStateFlow(AppTab.CACHE_CLEAN)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _sortKey = MutableStateFlow(AppSortKey.SIZE)
    val sortKey: StateFlow<AppSortKey> = _sortKey.asStateFlow()

    private val _sortAscending = MutableStateFlow(false)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    // Bundles the four "how to view the list" settings into one value so they
    // fit kotlinx.coroutines' typed combine() overloads (which only go up to 5
    // differently-typed flows) alongside _rawApps and _ignoredPackages below.
    private data class ViewSettings(
        val filter: AppFilter,
        val tab: AppTab,
        val sortKey: AppSortKey,
        val sortAscending: Boolean
    )

    private val _viewSettings: StateFlow<ViewSettings> =
        combine(_appFilter, _activeTab, _sortKey, _sortAscending) { filter, tab, key, asc ->
            ViewSettings(filter, tab, key, asc)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ViewSettings(AppFilter.ALL, AppTab.CACHE_CLEAN, AppSortKey.SIZE, false))

    // What the UI actually renders: raw apps with isIgnored freshly applied,
    // narrowed to the active tab (Cache Clean hides zero-cache apps; Force Stop
    // hides already-stopped apps and anything Android itself won't let you
    // force-stop), then the All/User/System filter, then sorted.
    val installedApps: StateFlow<List<AppStorageInfo>> =
        combine(_rawApps, _ignoredPackages, _viewSettings) { apps, ignored, vs ->
            val withIgnoreFlag = apps.map { it.copy(isIgnored = it.packageName in ignored) }
            val tabFiltered = when (vs.tab) {
                AppTab.CACHE_CLEAN -> withIgnoreFlag.filter { it.cacheBytes > 0 }
                AppTab.FORCE_STOP -> withIgnoreFlag.filter { !it.isStopped && it.canForceStop }
            }
            val filtered = tabFiltered.filter {
                when (vs.filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !it.isSystemApp
                    AppFilter.SYSTEM -> it.isSystemApp
                }
            }
            val sorted = when (vs.sortKey) {
                AppSortKey.SIZE -> filtered.sortedBy { if (vs.tab == AppTab.CACHE_CLEAN) it.cacheBytes else it.totalBytes }
                AppSortKey.DATE -> filtered.sortedBy { it.lastUsedTime }
            }
            if (vs.sortAscending) sorted else sorted.reversed()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _permissions = MutableStateFlow(AppPermissions())
    val permissions: StateFlow<AppPermissions> = _permissions.asStateFlow()

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
        viewModelScope.launch {
            ignoreListPreference.ignoredPackages.collect { _ignoredPackages.value = it }
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

    fun setAppFilter(filter: AppFilter) { _appFilter.value = filter }

    // Switching tabs clears selection — Cache Clean and Force Stop are now
    // separate options/lists, so carrying a selection from one into the other
    // could otherwise apply the wrong action to apps the user never intended.
    fun setActiveTab(tab: AppTab) {
        _activeTab.value = tab
        _rawApps.value = _rawApps.value.map { it.copy(selected = false) }
    }

    // Tapping the same sort key again flips direction; tapping the other key
    // switches to it (defaulting to descending — biggest / most recent first).
    fun setSortKey(key: AppSortKey) {
        if (_sortKey.value == key) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortKey.value = key
            _sortAscending.value = false
        }
    }

    fun loadHome() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _appsLoading.value = true
            try {
                _storageOverview.value = repository.getStorageOverview()
                _rawApps.value = repository.getInstalledApps()
            } finally { _appsLoading.value = false }
        }
    }

    fun toggleAppSelection(app: AppStorageInfo) {
        if (app.isIgnored) return
        _rawApps.value = _rawApps.value.map {
            if (it.packageName == app.packageName) it.copy(selected = !it.selected) else it
        }
    }

    // Single toggle button drives both directions: selects everything
    // currently visible (under the active tab+filter, excluding ignored apps)
    // if not all of it is selected yet, otherwise clears all of it. The UI
    // shows one button whose label flips between "Select All" / "Unselect All".
    fun toggleSelectAllVisible() {
        val visible = installedApps.value
        val selectable = visible.filter { !it.isIgnored }
        val allSelected = selectable.isNotEmpty() && selectable.all { it.selected }
        val visiblePkgs = visible.map { it.packageName }.toSet()
        _rawApps.value = _rawApps.value.map {
            when {
                it.packageName !in visiblePkgs -> it
                allSelected -> it.copy(selected = false)
                it.isIgnored -> it
                else -> it.copy(selected = true)
            }
        }
    }

    fun toggleIgnore(app: AppStorageInfo) {
        viewModelScope.launch {
            if (app.isIgnored) {
                ignoreListPreference.removeFromIgnoreList(app.packageName)
            } else {
                ignoreListPreference.addToIgnoreList(app.packageName)
                // Can no longer be selected once ignored — clear any existing selection.
                _rawApps.value = _rawApps.value.map {
                    if (it.packageName == app.packageName) it.copy(selected = false) else it
                }
            }
        }
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
        if (app.isIgnored) {
            _statusMessage.value = "${app.appName} is on the ignore list"
            return
        }
        viewModelScope.launch {
            val service = ShizukuHelper.cacheService
            if (service == null) {
                _statusMessage.value = "Shizuku is not connected"
                return@launch
            }
            val ok = repository.forceStopApp(service, app.packageName)
            _statusMessage.value = if (ok) "${app.appName} stopped" else "Failed to stop ${app.appName}"
            loadHome()
        }
    }

    fun clearSelectedAppsCache() {
        // Only ever act on apps currently visible in the active tab+filter —
        // a stale selection left over from switching tabs/filters (if any
        // somehow survives) can never leak into a bulk action it wasn't meant for.
        val visiblePkgs = installedApps.value.map { it.packageName }.toSet()
        val targets = _rawApps.value.filter { it.selected && it.packageName in visiblePkgs }
        if (targets.isEmpty()) return
        runBulkAction(targets, actionLabel = "Clearing cache") { service, app ->
            repository.clearAppCache(service, app.packageName)
        }
    }

    fun forceStopSelectedApps() {
        val visiblePkgs = installedApps.value.map { it.packageName }.toSet()
        // Defense in depth: even though ignored / non-stoppable apps' checkboxes
        // are disabled in the UI, never force-stop one even if it somehow ended
        // up selected.
        val targets = _rawApps.value.filter {
            it.selected && it.packageName in visiblePkgs &&
                it.packageName !in _ignoredPackages.value && it.canForceStop
        }
        if (targets.isEmpty()) return
        runBulkAction(targets, actionLabel = "Force stopping") { service, app ->
            repository.forceStopApp(service, app.packageName)
        }
    }

    private fun runBulkAction(
        targets: List<AppStorageInfo>,
        actionLabel: String,
        action: suspend (ICacheService, AppStorageInfo) -> Boolean
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
