package com.shohan.cleanspace.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shohan.cleanspace.data.AutoCleanPreference
import com.shohan.cleanspace.data.HistoryPreference
import com.shohan.cleanspace.data.IgnoreListPreference
import com.shohan.cleanspace.data.PermissionHelper
import com.shohan.cleanspace.data.StorageRepository
import com.shohan.cleanspace.data.ThemePreference
import com.shohan.cleanspace.data.UiStatePreference
import com.shohan.cleanspace.data.models.AppFilter
import com.shohan.cleanspace.data.models.AppPermissions
import com.shohan.cleanspace.data.models.AppSortKey
import com.shohan.cleanspace.data.models.AppStorageInfo
import com.shohan.cleanspace.data.models.AppTab
import com.shohan.cleanspace.data.models.AutoCleanAggressiveness
import com.shohan.cleanspace.data.models.AutoCleanFrequency
import com.shohan.cleanspace.data.models.AutoCleanSettings
import com.shohan.cleanspace.data.models.BulkActionProgress
import com.shohan.cleanspace.data.models.HistoryActionType
import com.shohan.cleanspace.data.models.HistoryEntry
import com.shohan.cleanspace.data.models.StatusMessage
import com.shohan.cleanspace.data.models.StorageOverview
import com.shohan.cleanspace.data.models.ThemeMode
import com.shohan.cleanspace.shizuku.ICacheService
import com.shohan.cleanspace.shizuku.ShizukuHelper
import com.shohan.cleanspace.worker.AutoCleanScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StorageRepository(application)
    private val themePreference = ThemePreference(application)
    private val ignoreListPreference = IgnoreListPreference(application)
    private val uiStatePreference = UiStatePreference(application)
    private val historyPreference = HistoryPreference(application)
    private val autoCleanPreference = AutoCleanPreference(application)

    private var loadJob: Job? = null
    private var bulkJob: Job? = null

    private val _storageOverview = MutableStateFlow(StorageOverview())
    val storageOverview: StateFlow<StorageOverview> = _storageOverview.asStateFlow()

    // Master list — holds the only mutable per-app field that matters here
    // (`selected`). isIgnored is recomputed below via combine(), never stored
    // here directly, so it can never go stale relative to the preference.
    private val _rawApps = MutableStateFlow<List<AppStorageInfo>>(emptyList())

    private val _appIcons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val appIcons: StateFlow<Map<String, Bitmap>> = _appIcons.asStateFlow()

    private val _ignoredPackages = MutableStateFlow<Set<String>>(emptySet())
    val ignoredPackages: StateFlow<Set<String>> = _ignoredPackages.asStateFlow()

    // Full AppStorageInfo (so the name is available) for every ignored
    // package, independent of whatever tab/filter/search is currently active —
    // _rawApps always holds every installed app once loadHome() has run once.
    val ignoredAppsInfo: StateFlow<List<AppStorageInfo>> =
        combine(_rawApps, _ignoredPackages) { apps, ignored ->
            apps.filter { it.packageName in ignored }
                .map { it.copy(isIgnored = true) }
                .sortedBy { it.appName.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _appFilter = MutableStateFlow(AppFilter.ALL)
    val appFilter: StateFlow<AppFilter> = _appFilter.asStateFlow()

    private val _activeTab = MutableStateFlow(AppTab.CACHE_CLEAN)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _sortKey = MutableStateFlow(AppSortKey.SIZE)
    val sortKey: StateFlow<AppSortKey> = _sortKey.asStateFlow()

    private val _sortAscending = MutableStateFlow(false)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // When off (default), apps on the ignore list or that Android won't let
    // you force-stop are hidden from the Force Stop tab entirely. Turning
    // this on shows them too (read-only, badge + disabled controls) so the
    // user can see WHY something is missing instead of it just vanishing.
    private val _showProtected = MutableStateFlow(false)
    val showProtected: StateFlow<Boolean> = _showProtected.asStateFlow()

    val history: StateFlow<List<HistoryEntry>> =
        historyPreference.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Bundles every "how to view the list" setting into one value so they fit
    // kotlinx.coroutines' typed combine() overloads (max 5 differently-typed
    // flows per call) — five of these six combine in one call, the sixth
    // joins via a second combine, then THAT joins _rawApps/_ignoredPackages
    // in a third. Three small steps instead of one impossible six-way one.
    private data class ViewSettings(
        val filter: AppFilter,
        val tab: AppTab,
        val sortKey: AppSortKey,
        val sortAscending: Boolean,
        val searchQuery: String,
        val showProtected: Boolean
    )

    private data class FiveSettings(
        val filter: AppFilter,
        val tab: AppTab,
        val sortKey: AppSortKey,
        val sortAscending: Boolean,
        val searchQuery: String
    )

    private val _viewSettings: StateFlow<ViewSettings> =
        combine(_appFilter, _activeTab, _sortKey, _sortAscending, _searchQuery) { filter, tab, key, asc, query ->
            FiveSettings(filter, tab, key, asc, query)
        }.combine(_showProtected) { five, showProtected ->
            ViewSettings(
                filter = five.filter,
                tab = five.tab,
                sortKey = five.sortKey,
                sortAscending = five.sortAscending,
                searchQuery = five.searchQuery,
                showProtected = showProtected
            )
        }.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            ViewSettings(AppFilter.ALL, AppTab.CACHE_CLEAN, AppSortKey.SIZE, false, "", false)
        )

    // What the UI actually renders: raw apps with isIgnored freshly applied,
    // narrowed to the active tab (Cache Clean hides zero-cache apps; Force
    // Stop hides already-stopped apps and anything Android itself won't let
    // you force-stop, unless "show protected" is on), then search, then the
    // All/User/System filter, then sorted.
    val installedApps: StateFlow<List<AppStorageInfo>> =
        combine(_rawApps, _ignoredPackages, _viewSettings) { apps, ignored, vs ->
            val withIgnoreFlag = apps.map { it.copy(isIgnored = it.packageName in ignored) }
            val tabFiltered = when (vs.tab) {
                AppTab.CACHE_CLEAN -> withIgnoreFlag.filter { it.cacheBytes > 0 }
                AppTab.FORCE_STOP -> withIgnoreFlag.filter {
                    vs.showProtected || (!it.isStopped && it.canForceStop && !it.isIgnored)
                }
            }
            val searched = if (vs.searchQuery.isBlank()) tabFiltered
                else tabFiltered.filter { it.appName.contains(vs.searchQuery, ignoreCase = true) }
            val filtered = searched.filter {
                when (vs.filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !it.isSystemApp
                    AppFilter.SYSTEM -> it.isSystemApp
                }
            }
            val sorted = when (vs.sortKey) {
                AppSortKey.SIZE -> filtered.sortedBy { if (vs.tab == AppTab.CACHE_CLEAN) it.cacheBytes else it.totalBytes }
                AppSortKey.DATE -> filtered.sortedBy { it.lastUsedTime }
                AppSortKey.NAME -> filtered.sortedBy { it.appName.lowercase() }
            }
            if (vs.sortAscending) sorted else sorted.reversed()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _permissions = MutableStateFlow(AppPermissions())
    val permissions: StateFlow<AppPermissions> = _permissions.asStateFlow()

    private val _bulkActionProgress = MutableStateFlow<BulkActionProgress?>(null)
    val bulkActionProgress: StateFlow<BulkActionProgress?> = _bulkActionProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<StatusMessage?>(null)
    val statusMessage: StateFlow<StatusMessage?> = _statusMessage.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(true)
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    private val _autoCleanSettings = MutableStateFlow(AutoCleanSettings())
    val autoCleanSettings: StateFlow<AutoCleanSettings> = _autoCleanSettings.asStateFlow()

    init {
        refreshPermissions()
        viewModelScope.launch {
            themePreference.themeMode.collect { _themeMode.value = it }
        }
        viewModelScope.launch {
            themePreference.useDynamicColor.collect { _useDynamicColor.value = it }
        }
        viewModelScope.launch {
            autoCleanPreference.settings.collect {
                _autoCleanSettings.value = it
                AutoCleanScheduler.ensureScheduled(getApplication<Application>(), it)
            }
        }
        viewModelScope.launch {
            ignoreListPreference.ignoredPackages.collect { _ignoredPackages.value = it }
        }
        // Restore last filter/tab/sort/showProtected exactly once on startup.
        viewModelScope.launch {
            val saved = uiStatePreference.savedUiState.first()
            _appFilter.value = saved.filter
            _activeTab.value = saved.tab
            _sortKey.value = saved.sortKey
            _sortAscending.value = saved.sortAscending
            _showProtected.value = saved.showProtected
        }
        // Persist whenever any of them change (debounced naturally since these
        // are simple StateFlows — each change is one quick DataStore write).
        viewModelScope.launch {
            combine(_appFilter, _activeTab, _sortKey, _sortAscending, _showProtected) { f, t, sk, asc, sp ->
                UiStatePreference.SavedUiState(f, t, sk, asc, sp)
            }.collect { uiStatePreference.save(it) }
        }
        // One-time seeding: protect critical system apps (System UI, current
        // launcher/dialer/SMS app, CleanSpace itself) before the user ever
        // gets a chance to accidentally force-stop one of them.
        viewModelScope.launch {
            if (!uiStatePreference.hasSeededDefaults.first()) {
                repository.detectDefaultProtectedPackages().forEach {
                    ignoreListPreference.addToIgnoreList(it)
                }
                uiStatePreference.markDefaultsSeeded()
            }
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

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { themePreference.setUseDynamicColor(enabled) }
    }

    fun setAutoCleanEnabled(enabled: Boolean) = updateAutoClean { it.copy(enabled = enabled) }

    fun setAutoCleanFrequency(frequency: AutoCleanFrequency) = updateAutoClean { it.copy(frequency = frequency) }

    fun setAutoCleanAggressiveness(level: AutoCleanAggressiveness) = updateAutoClean { it.copy(aggressiveness = level) }

    fun setAutoCleanNotify(enabled: Boolean) = updateAutoClean { it.copy(notifyOnClean = enabled) }

    private fun updateAutoClean(transform: (AutoCleanSettings) -> AutoCleanSettings) {
        viewModelScope.launch {
            val updated = transform(_autoCleanSettings.value)
            autoCleanPreference.save(updated)
            AutoCleanScheduler.reschedule(getApplication<Application>(), updated)
        }
    }

    fun setAppFilter(filter: AppFilter) { _appFilter.value = filter }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun setShowProtected(show: Boolean) { _showProtected.value = show }

    // Switching tabs clears selection — Cache Clean and Force Stop are now
    // separate options/lists, so carrying a selection from one into the other
    // could otherwise apply the wrong action to apps the user never intended.
    fun setActiveTab(tab: AppTab) {
        _activeTab.value = tab
        _rawApps.value = _rawApps.value.map { it.copy(selected = false) }
    }

    // Tapping the same sort key again flips direction; tapping a different
    // key switches to it (defaulting to descending — biggest / most recent /
    // Z-first — tap once more to flip to ascending).
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
                val apps = repository.getInstalledApps()
                _rawApps.value = apps
                // Only decode icons we don't already have cached. loadHome()
                // runs after nearly every action (clear cache, force stop,
                // disable, bulk actions, pull-to-refresh...) and a person's
                // set of installed apps rarely changes between one action and
                // the next, so re-decoding all of them from scratch every
                // time was pure wasted work — this makes every refresh after
                // the first effectively instant for icons. Also prunes icons
                // for apps that are no longer installed, so the cache doesn't
                // grow unbounded over long-term use.
                val currentPackages = apps.map { it.packageName }.toSet()
                val prunedIcons = _appIcons.value.filterKeys { it in currentPackages }
                val missing = currentPackages.filter { it !in prunedIcons }
                _appIcons.value = if (missing.isNotEmpty()) {
                    prunedIcons + repository.loadAppIcons(missing)
                } else {
                    prunedIcons
                }
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

    // Comma-separated package names — simple enough to copy/paste or share as
    // a text file, no JSON library needed for something this small.
    fun exportIgnoreList(): String = _ignoredPackages.value.sorted().joinToString(",")

    fun importIgnoreList(text: String) {
        viewModelScope.launch {
            val packages = text.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
            packages.forEach { ignoreListPreference.addToIgnoreList(it) }
            _statusMessage.value = StatusMessage("${packages.size} packages added to ignore list")
        }
    }

    fun cancelBulkAction() {
        bulkJob?.cancel()
        _bulkActionProgress.value = null
        _statusMessage.value = StatusMessage("Cancelled")
    }

    private fun recordHistory(action: HistoryActionType, appName: String, bytesFreed: Long = 0L) {
        viewModelScope.launch {
            historyPreference.record(HistoryEntry(System.currentTimeMillis(), action, appName, bytesFreed))
        }
    }

    fun clearHistory() {
        viewModelScope.launch { historyPreference.clear() }
    }

    fun clearSingleAppCache(app: AppStorageInfo) {
        viewModelScope.launch {
            val service = ShizukuHelper.cacheService
            if (service == null) {
                _statusMessage.value = StatusMessage("Shizuku is not connected")
                return@launch
            }
            val ok = repository.clearAppCache(service, app.packageName)
            if (ok) recordHistory(HistoryActionType.CACHE_CLEARED, app.appName, app.cacheBytes)
            _statusMessage.value = StatusMessage(
                if (ok) "${app.appName} cache cleared" else "Failed to clear ${app.appName}"
            )
            loadHome()
        }
    }

    // Full data wipe — far more destructive than clearSingleAppCache, kept as
    // a single-app-only action (no bulk version) to limit how much damage one
    // mistaken tap can do.
    fun clearSingleAppData(app: AppStorageInfo) {
        if (app.isIgnored) {
            _statusMessage.value = StatusMessage("${app.appName} is on the ignore list")
            return
        }
        viewModelScope.launch {
            val service = ShizukuHelper.cacheService
            if (service == null) {
                _statusMessage.value = StatusMessage("Shizuku is not connected")
                return@launch
            }
            val ok = repository.clearAppData(service, app.packageName)
            if (ok) recordHistory(HistoryActionType.DATA_CLEARED, app.appName, app.totalBytes)
            _statusMessage.value = StatusMessage(
                if (ok) "${app.appName} data cleared" else "Failed to clear ${app.appName}"
            )
            loadHome()
        }
    }

    fun forceStopSingleApp(app: AppStorageInfo) {
        if (app.isIgnored) {
            _statusMessage.value = StatusMessage("${app.appName} is on the ignore list")
            return
        }
        viewModelScope.launch {
            val service = ShizukuHelper.cacheService
            if (service == null) {
                _statusMessage.value = StatusMessage("Shizuku is not connected")
                return@launch
            }
            val ok = repository.forceStopApp(service, app.packageName)
            if (ok) recordHistory(HistoryActionType.FORCE_STOPPED, app.appName)
            _statusMessage.value = StatusMessage(
                text = if (ok) "${app.appName} stopped" else "Failed to stop ${app.appName}",
                relaunchPackage = if (ok && app.hasLaunchIntent) app.packageName else null
            )
            loadHome()
        }
    }

    fun toggleAppEnabled(app: AppStorageInfo) {
        if (app.isIgnored) {
            _statusMessage.value = StatusMessage("${app.appName} is on the ignore list")
            return
        }
        viewModelScope.launch {
            val service = ShizukuHelper.cacheService
            if (service == null) {
                _statusMessage.value = StatusMessage("Shizuku is not connected")
                return@launch
            }
            val ok = if (app.isDisabled) repository.enableApp(service, app.packageName)
                      else repository.disableApp(service, app.packageName)
            if (ok) recordHistory(
                if (app.isDisabled) HistoryActionType.ENABLED else HistoryActionType.DISABLED,
                app.appName
            )
            _statusMessage.value = StatusMessage(when {
                !ok -> "Failed to update ${app.appName}"
                app.isDisabled -> "${app.appName} enabled"
                else -> "${app.appName} disabled"
            })
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
            val ok = repository.clearAppCache(service, app.packageName)
            if (ok) recordHistory(HistoryActionType.CACHE_CLEARED, app.appName, app.cacheBytes)
            ok
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
            val ok = repository.forceStopApp(service, app.packageName)
            if (ok) recordHistory(HistoryActionType.FORCE_STOPPED, app.appName)
            ok
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
                _statusMessage.value = StatusMessage("Shizuku service not connected. Is Shizuku running?")
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
            _statusMessage.value = StatusMessage("$actionLabel: $success/${targets.size} apps done")
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
