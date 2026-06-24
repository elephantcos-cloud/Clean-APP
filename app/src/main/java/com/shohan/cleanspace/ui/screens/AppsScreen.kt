package com.shohan.cleanspace.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(viewModel: MainViewModel, navController: NavController) {
    val apps by viewModel.installedApps.collectAsState()
    val appFilter by viewModel.appFilter.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val sortKey by viewModel.sortKey.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val overview by viewModel.storageOverview.collectAsState()
    val isLoading by viewModel.appsLoading.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val bulkProgress by viewModel.bulkActionProgress.collectAsState()
    val context = LocalContext.current

    var pendingBulkClear by remember { mutableStateOf(false) }
    var pendingBulkForceStop by remember { mutableStateOf(false) }
    var pendingForceStopApp by remember { mutableStateOf<AppStorageInfo?>(null) }

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
        AlertDialog(
            onDismissRequest = { pendingForceStopApp = null },
            title = { Text("Force stop ${app.appName}?") },
            text = { Text("If you force stop an app, it may misbehave.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingForceStopApp = null
                    viewModel.forceStopSingleApp(app)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pendingForceStopApp = null }) { Text("Cancel") }
            }
        )
    }

    if (pendingBulkClear) {
        AlertDialog(
            onDismissRequest = { pendingBulkClear = false },
            title = { Text("Clear cache for ${selectedApps.size} apps?") },
            text = { Text("This will free up approximately ${MainViewModel.formatBytes(selectedCacheBytes)}.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingBulkClear = false
                    viewModel.clearSelectedAppsCache()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkClear = false }) { Text("Cancel") }
            }
        )
    }

    if (pendingBulkForceStop) {
        AlertDialog(
            onDismissRequest = { pendingBulkForceStop = false },
            title = { Text("Force stop ${selectedApps.size} apps?") },
            text = { Text("If you force stop these apps, they may misbehave.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingBulkForceStop = false
                    viewModel.forceStopSelectedApps()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkForceStop = false }) { Text("Cancel") }
            }
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
            // Fix: Cache Clean and Force Stop are now separate options — only the
            // bulk button relevant to the active tab is ever shown, instead of
            // both buttons always sitting side by side regardless of context.
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
        // Fix: everything is now ONE scrollable LazyColumn (storage card, tabs,
        // filter chips, sort controls, select-all row are header items inside
        // it) instead of a fixed Column + a separately-scrolling app list
        // below. Previously the storage panel never scrolled away, permanently
        // eating screen height and squeezing the app list.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
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

            // Fix: Cache Clean and Force Stop are now separate options — each
            // tab shows its own filtered list (Cache Clean hides apps with no
            // cache to clear; Force Stop hides already-stopped apps and
            // anything Android itself won't let you force-stop, like
            // persistent system processes) and its own bulk action below.
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

            // Sort controls: tap Size/Date to sort by it, tap again to flip
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
                        trailingIcon = if (sortKey == AppSortKey.SIZE) {
                            { SortDirectionIcon(sortAscending) }
                        } else null
                    )
                    FilterChip(
                        selected = sortKey == AppSortKey.DATE,
                        onClick = { viewModel.setSortKey(AppSortKey.DATE) },
                        label = { Text("Date") },
                        trailingIcon = if (sortKey == AppSortKey.DATE) {
                            { SortDirectionIcon(sortAscending) }
                        } else null
                    )
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
                        // Fix: ONE button in one place instead of two separate
                        // "Select All" / "Unselect All" buttons side by side —
                        // its label and action flip depending on current state.
                        TextButton(onClick = { viewModel.toggleSelectAllVisible() }) {
                            Text(if (allVisibleSelected) "Unselect All" else "Select All")
                        }
                    }
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
                            "No stoppable apps right now — already stopped or protected apps are hidden."
                    )
                }
            } else {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        activeTab = activeTab,
                        shizukuReady = shizukuReady,
                        onToggleSelect = { viewModel.toggleAppSelection(app) },
                        onForceStopClick = { pendingForceStopApp = app },
                        onClearCacheClick = { viewModel.clearSingleAppCache(app) },
                        onOpenAppInfo = { openAppInfo(app) },
                        onToggleIgnore = { viewModel.toggleIgnore(app) }
                    )
                }
            }
        }
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppStorageInfo,
    activeTab: AppTab,
    shizukuReady: Boolean,
    onToggleSelect: () -> Unit,
    onForceStopClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onToggleIgnore: () -> Unit
) {
    var showIgnoreMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (shizukuReady) {
                Checkbox(
                    checked = app.selected,
                    enabled = !app.isIgnored,
                    onCheckedChange = { onToggleSelect() }
                )
            }

            // Tap the app name to open its native App Info screen. Long-press
            // it to add/remove it from the ignore list — a floating menu pops
            // up right where you pressed.
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = onOpenAppInfo,
                            onLongClick = { showIgnoreMenu = true }
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
                    }
                    Text(
                        if (activeTab == AppTab.CACHE_CLEAN)
                            "Cache: ${MainViewModel.formatBytes(app.cacheBytes)}  •  Total: ${MainViewModel.formatBytes(app.totalBytes)}"
                        else
                            "Size: ${MainViewModel.formatBytes(app.totalBytes)}  •  Last used: ${formatLastUsed(app.lastUsedTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (app.isIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(expanded = showIgnoreMenu, onDismissRequest = { showIgnoreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (app.isIgnored) "Remove from Ignore List" else "Add to Ignore List") },
                        onClick = {
                            showIgnoreMenu = false
                            onToggleIgnore()
                        }
                    )
                }
            }

            if (shizukuReady) {
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
        }
    }
}
