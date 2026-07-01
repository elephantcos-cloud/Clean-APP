package com.shohan.cleanspace.ui.screens

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shohan.cleanspace.data.PermissionHelper
import com.shohan.cleanspace.data.models.AppFilter
import com.shohan.cleanspace.data.models.AppSortKey
import com.shohan.cleanspace.data.models.AppStorageInfo
import com.shohan.cleanspace.data.models.AppTab
import com.shohan.cleanspace.ui.components.DonutChart
import com.shohan.cleanspace.ui.components.DonutSlice
import com.shohan.cleanspace.ui.theme.Emerald600
import com.shohan.cleanspace.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AppsScreen(viewModel: MainViewModel, navController: NavController) {
    val apps by viewModel.installedApps.collectAsState()
    val appIcons by viewModel.appIcons.collectAsState()
    val appFilter by viewModel.appFilter.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val sortKey by viewModel.sortKey.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showProtected by viewModel.showProtected.collectAsState()
    val overview by viewModel.storageOverview.collectAsState()
    val isLoading by viewModel.appsLoading.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val bulkProgress by viewModel.bulkActionProgress.collectAsState()
    val context = LocalContext.current

    var pendingBulkClear by remember { mutableStateOf(false) }
    var pendingBulkForceStop by remember { mutableStateOf(false) }
    var pendingForceStopApp by remember { mutableStateOf<AppStorageInfo?>(null) }
    var pendingDisableApp by remember { mutableStateOf<AppStorageInfo?>(null) }
    var pendingClearDataApp by remember { mutableStateOf<AppStorageInfo?>(null) }

    LaunchedEffect(Unit) { viewModel.loadHome() }

    val shizukuReady = permissions.shizukuRunning && permissions.shizukuPermission
    val selectedApps = apps.filter { it.selected }
    val selectedCacheBytes = selectedApps.sumOf { it.cacheBytes }
    val totalCacheBytes = apps.sumOf { it.cacheBytes }
    val selectableApps = apps.filter { !it.isIgnored }
    val allVisibleSelected = selectableApps.isNotEmpty() && selectableApps.all { it.selected }

    fun openAppInfo(app: AppStorageInfo) {
        context.startActivity(PermissionHelper.appSettingsIntent(context, app.packageName))
    }

    // Single app force-stop confirmation — mirrors Android's own native
    // "Force stop?" warning dialog, since force-stopping can make an app misbehave.
    pendingForceStopApp?.let { app ->
        ConfirmActionDialog(
            title = "Force stop ${app.appName}?",
            text = "If you force stop an app, it may misbehave.",
            confirmLabel = "OK",
            onConfirm = { viewModel.forceStopSingleApp(app) },
            onDismiss = { pendingForceStopApp = null }
        )
    }

    // Disable confirmation — only shown when disabling (enabling needs no
    // confirmation, it's the safe/reversible direction).
    pendingDisableApp?.let { app ->
        ConfirmActionDialog(
            title = "Disable ${app.appName}?",
            text = "The app will stop running and disappear from the app drawer until you re-enable it here. It will not be uninstalled.",
            confirmLabel = "Disable",
            onConfirm = { viewModel.toggleAppEnabled(app) },
            onDismiss = { pendingDisableApp = null }
        )
    }

    // Clear Data confirmation — the most destructive single-app action here,
    // so the wording is deliberately blunt about what's lost.
    pendingClearDataApp?.let { app ->
        ConfirmActionDialog(
            title = "Clear ALL data for ${app.appName}?",
            text = "This erases everything — login, settings, saved files — not just cache. The app resets to a fresh install. This cannot be undone.",
            confirmLabel = "Erase",
            onConfirm = { viewModel.clearSingleAppData(app) },
            onDismiss = { pendingClearDataApp = null }
        )
    }

    if (pendingBulkClear) {
        ConfirmActionDialog(
            title = "Clear cache for ${selectedApps.size} apps?",
            text = "This will free up approximately ${MainViewModel.formatBytes(selectedCacheBytes)}.",
            confirmLabel = "OK",
            onConfirm = { viewModel.clearSelectedAppsCache() },
            onDismiss = { pendingBulkClear = false }
        )
    }

    if (pendingBulkForceStop) {
        ConfirmActionDialog(
            title = "Force stop ${selectedApps.size} apps?",
            text = "If you force stop these apps, they may misbehave.",
            confirmLabel = "OK",
            onConfirm = { viewModel.forceStopSelectedApps() },
            onDismiss = { pendingBulkForceStop = false }
        )
    }

    // Live progress screen — shows exactly which app is being processed right now,
    // updating in real time as the bulk job works through the selected apps.
    bulkProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(progress.actionLabel) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("${progress.currentAppName} (${progress.currentIndex}/${progress.total})")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = progress.currentIndex / progress.total.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelBulkAction() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CleanSpace") },
                actions = {
                    IconButton(onClick = { viewModel.loadHome() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            // Cache Clean and Force Stop are separate options — only the bulk
            // button relevant to the active tab is ever shown.
            if (selectedApps.isNotEmpty() && shizukuReady) {
                BottomAppBar {
                    Text(
                        if (activeTab == AppTab.CACHE_CLEAN)
                            "${selectedApps.size} selected  •  ${MainViewModel.formatBytes(selectedCacheBytes)}"
                        else
                            "${selectedApps.size} selected",
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (activeTab == AppTab.CACHE_CLEAN) {
                        Button(
                            onClick = { pendingBulkClear = true },
                            modifier = Modifier.padding(end = 16.dp)
                        ) { Text("Clear Cache") }
                    } else {
                        Button(
                            onClick = { pendingBulkForceStop = true },
                            modifier = Modifier.padding(end = 16.dp)
                        ) { Text("Force Stop") }
                    }
                }
            }
        }
    ) { padding ->
        val pullRefreshState = rememberPullRefreshState(
            refreshing = isLoading,
            onRefresh = { viewModel.loadHome() }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val usedPct = if (overview.totalBytes > 0)
                            (overview.usedBytes * 100 / overview.totalBytes).toInt() else 0

                        DonutChart(
                            slices = listOf(
                                DonutSlice(overview.usedBytes.toFloat().coerceAtLeast(0f), Emerald600, "Used"),
                                DonutSlice(overview.freeBytes.toFloat().coerceAtLeast(0f), Color.LightGray, "Free")
                            ),
                            modifier = Modifier.fillMaxWidth(0.6f),
                            centerLabel = "$usedPct%",
                            centerSubLabel = "Used"
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "${MainViewModel.formatBytes(overview.usedBytes)} / ${MainViewModel.formatBytes(overview.totalBytes)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${MainViewModel.formatBytes(overview.freeBytes)} free",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (totalCacheBytes > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Reclaimable app cache: ${MainViewModel.formatBytes(totalCacheBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (!shizukuReady) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("One-tap cache clearing & force stop", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Activate Shizuku to clear app caches and force-stop apps. Without it, you can still open each app's settings individually.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            Row {
                                when {
                                    !permissions.shizukuInstalled -> {
                                        Button(onClick = {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse("https://shizuku.rikka.app/download/")
                                                )
                                            )
                                        }) { Text("Install Shizuku") }
                                    }
                                    !permissions.shizukuRunning -> {
                                        Text("Shizuku is installed but not running — open Shizuku and tap Start")
                                    }
                                    else -> {
                                        Button(onClick = { viewModel.requestShizukuPermission() }) {
                                            Text("Grant Permission")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                TabRow(selectedTabIndex = if (activeTab == AppTab.CACHE_CLEAN) 0 else 1) {
                    Tab(
                        selected = activeTab == AppTab.CACHE_CLEAN,
                        onClick = { viewModel.setActiveTab(AppTab.CACHE_CLEAN) },
                        text = { Text("Cache Clean") }
                    )
                    Tab(
                        selected = activeTab == AppTab.FORCE_STOP,
                        onClick = { viewModel.setActiveTab(AppTab.FORCE_STOP) },
                        text = { Text("Force Stop") }
                    )
                }
            }

            // Search by app name
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    singleLine = true
                )
            }

            // Filter chips: All / User Apps / System Apps
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = appFilter == AppFilter.ALL,
                        onClick = { viewModel.setAppFilter(AppFilter.ALL) },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = appFilter == AppFilter.USER,
                        onClick = { viewModel.setAppFilter(AppFilter.USER) },
                        label = { Text("User Apps") }
                    )
                    FilterChip(
                        selected = appFilter == AppFilter.SYSTEM,
                        onClick = { viewModel.setAppFilter(AppFilter.SYSTEM) },
                        label = { Text("System Apps") }
                    )
                }
            }

            // Sort controls: tap Size/Date/Name to sort by it, tap again to flip
            // ascending/descending — an arrow shows the active key + direction.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sort:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    FilterChip(
                        selected = sortKey == AppSortKey.SIZE,
                        onClick = { viewModel.setSortKey(AppSortKey.SIZE) },
                        label = { Text("Size") },
                        trailingIcon = if (sortKey == AppSortKey.SIZE) { { SortDirectionIcon(sortAscending) } } else null
                    )
                    FilterChip(
                        selected = sortKey == AppSortKey.DATE,
                        onClick = { viewModel.setSortKey(AppSortKey.DATE) },
                        label = { Text("Date") },
                        trailingIcon = if (sortKey == AppSortKey.DATE) { { SortDirectionIcon(sortAscending) } } else null
                    )
                    FilterChip(
                        selected = sortKey == AppSortKey.NAME,
                        onClick = { viewModel.setSortKey(AppSortKey.NAME) },
                        label = { Text("Name") },
                        trailingIcon = if (sortKey == AppSortKey.NAME) { { SortDirectionIcon(sortAscending) } } else null
                    )
                }
            }

            // "Show protected apps" only matters on the Force Stop tab — Cache
            // Clean was never filtering anything related to protection.
            if (activeTab == AppTab.FORCE_STOP) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show protected apps", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showProtected, onCheckedChange = { viewModel.setShowProtected(it) })
                    }
                }
            }

            if (shizukuReady) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${apps.size} apps", style = MaterialTheme.typography.bodyMedium)
                        // ONE button in one place — label/action flip with state,
                        // instead of separate "Select All" / "Unselect All" buttons.
                        TextButton(onClick = { viewModel.toggleSelectAllVisible() }) {
                            Text(if (allVisibleSelected) "Unselect All" else "Select All")
                        }
                    }
                }
                item {
                    Text(
                        "Tip: swipe a row to quickly clear its cache / force stop it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }

            if (isLoading && apps.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (apps.isEmpty()) {
                item {
                    com.shohan.cleanspace.ui.components.EmptyState(
                        icon = Icons.Filled.Info,
                        title = "No Apps Found",
                        subtitle = if (activeTab == AppTab.CACHE_CLEAN)
                            "No apps currently have cache to clear."
                        else
                            "No stoppable apps right now — try \"Show protected apps\" to see why."
                    )
                }
            } else {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        icon = appIcons[app.packageName],
                        activeTab = activeTab,
                        shizukuReady = shizukuReady,
                        showProtected = showProtected,
                        onToggleSelect = { viewModel.toggleAppSelection(app) },
                        onForceStopClick = { pendingForceStopApp = app },
                        onClearCacheClick = { viewModel.clearSingleAppCache(app) },
                        onOpenAppInfo = { openAppInfo(app) },
                        onToggleIgnore = { viewModel.toggleIgnore(app) },
                        onToggleEnabled = {
                            if (app.isDisabled) viewModel.toggleAppEnabled(app) else pendingDisableApp = app
                        },
                        onClearDataClick = { pendingClearDataApp = app }
                    )
                }
            }
        }
        PullRefreshIndicator(
            refreshing = isLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
        }
    }
}

// Shared confirmation dialog used by every destructive single/bulk action in
// this screen (force stop, disable, clear data, bulk clear, bulk force stop)
// — was previously 5 separately hand-written AlertDialogs with near-identical
// structure. Consolidating them here also means haptic feedback on confirm
// only needs to be wired up once instead of 5 times.
@Composable
private fun ConfirmActionDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SortDirectionIcon(ascending: Boolean) {
    Icon(
        if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
        contentDescription = if (ascending) "Ascending" else "Descending",
        modifier = Modifier.size(16.dp)
    )
}

private fun formatLastUsed(timeMillis: Long): String {
    if (timeMillis <= 0L) return "Never used"
    return DateUtils.getRelativeTimeSpanString(
        timeMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()
}

// Why an app is being shown even though it would normally be excluded from
// the Force Stop tab — only meaningful when "Show protected apps" is on.
private fun protectionReason(app: AppStorageInfo): String? = when {
    app.isIgnored -> "Ignored"
    app.isStopped -> "Already stopped"
    !app.canForceStop -> "Protected by Android"
    else -> null
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
private fun AppRow(
    app: AppStorageInfo,
    icon: Bitmap?,
    activeTab: AppTab,
    shizukuReady: Boolean,
    showProtected: Boolean,
    onToggleSelect: () -> Unit,
    onForceStopClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onToggleIgnore: () -> Unit,
    onToggleEnabled: () -> Unit,
    onClearDataClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val reason = if (activeTab == AppTab.FORCE_STOP && showProtected) protectionReason(app) else null
    val canActOnThis = activeTab != AppTab.FORCE_STOP || reason == null
    val canSwipeAct = shizukuReady && canActOnThis && !app.isIgnored

    val rowContent: @Composable () -> Unit = {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (shizukuReady && canActOnThis) {
                    Checkbox(
                        checked = app.selected,
                        enabled = !app.isIgnored,
                        onCheckedChange = { onToggleSelect() }
                    )
                }

                if (icon != null) {
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                }

                // Tap the app name to open its native App Info screen. Long-press
                // it for more actions (ignore list, disable, clear data) — a
                // floating menu pops up right where you pressed.
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = onOpenAppInfo,
                                onLongClick = { showMenu = true }
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(app.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            if (app.isIgnored) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Ignored",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (app.isDisabled) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Block,
                                    contentDescription = "Disabled",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Text(
                            if (activeTab == AppTab.CACHE_CLEAN)
                                "Cache: ${MainViewModel.formatBytes(app.cacheBytes)}  •  Total: ${MainViewModel.formatBytes(app.totalBytes)}"
                            else
                                "Size: ${MainViewModel.formatBytes(app.totalBytes)}  •  Last used: ${formatLastUsed(app.lastUsedTime)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (app.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        if (reason != null) {
                            Text(
                                reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (app.isIgnored) "Remove from Ignore List" else "Add to Ignore List") },
                            onClick = {
                                showMenu = false
                                onToggleIgnore()
                            }
                        )
                        if (shizukuReady && !app.isIgnored) {
                            DropdownMenuItem(
                                text = { Text(if (app.isDisabled) "Enable App" else "Disable App") },
                                onClick = {
                                    showMenu = false
                                    onToggleEnabled()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Data (resets app)") },
                                onClick = {
                                    showMenu = false
                                    onClearDataClick()
                                }
                            )
                        }
                    }
                }

                if (shizukuReady && canActOnThis) {
                    if (activeTab == AppTab.CACHE_CLEAN) {
                        IconButton(onClick = onClearCacheClick) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = "Clear Cache")
                        }
                    } else if (!app.isIgnored) {
                        IconButton(onClick = onForceStopClick) {
                            Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Force Stop")
                        }
                    }
                }
                if (shizukuReady) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                }
            }
        }
    }

    // rememberDismissState must be called unconditionally on every
    // recomposition of this row (Compose requires composable/remember calls
    // happen in a stable order) — so it's always created here, and we branch
    // on canSwipeAct afterward with a plain if/else rather than an early
    // return before this call.
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberDismissState(
        confirmStateChange = { value ->
            if (value != DismissValue.Default && canSwipeAct) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (activeTab == AppTab.CACHE_CLEAN) onClearCacheClick() else onForceStopClick()
            }
            false
        }
    )

    if (!canSwipeAct) {
        rowContent()
        return
    }

    // Swipe gesture: swiping a row triggers its primary action (Clear Cache /
    // Force Stop) through the SAME confirmation dialog the icon button uses —
    // confirmStateChange always returns false so the row visually snaps back
    // instead of disappearing (the list refreshes naturally once the action
    // completes, removing it the normal way if it no longer qualifies).
    val isCacheTab = activeTab == AppTab.CACHE_CLEAN
    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(
                        if (isCacheTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    if (isCacheTab) Icons.Filled.CleaningServices else Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 24.dp).size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        dismissContent = { rowContent() }
    )
}
