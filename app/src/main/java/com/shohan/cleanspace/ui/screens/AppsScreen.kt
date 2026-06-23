package com.shohan.cleanspace.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.shohan.cleanspace.data.models.AppStorageInfo
import com.shohan.cleanspace.ui.components.DonutChart
import com.shohan.cleanspace.ui.components.DonutSlice
import com.shohan.cleanspace.ui.theme.Emerald600
import com.shohan.cleanspace.viewmodel.MainViewModel

private enum class PendingBulkAction { CLEAR_CACHE, FORCE_STOP }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(viewModel: MainViewModel, navController: NavController) {
    val apps by viewModel.installedApps.collectAsState()
    val appFilter by viewModel.appFilter.collectAsState()
    val overview by viewModel.storageOverview.collectAsState()
    val isLoading by viewModel.appsLoading.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val bulkProgress by viewModel.bulkActionProgress.collectAsState()
    val context = LocalContext.current

    var pendingBulkAction by remember { mutableStateOf<PendingBulkAction?>(null) }
    var pendingForceStopApp by remember { mutableStateOf<AppStorageInfo?>(null) }

    LaunchedEffect(Unit) { viewModel.loadHome() }

    val shizukuReady = permissions.shizukuRunning && permissions.shizukuPermission
    val selectedApps = apps.filter { it.selected }
    val selectedCacheBytes = selectedApps.sumOf { it.cacheBytes }
    val totalCacheBytes = apps.sumOf { it.cacheBytes }
    val selectableApps = apps.filter { !it.isIgnored }
    val allVisibleSelected = selectableApps.isNotEmpty() && selectableApps.all { it.selected }

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

    // Bulk action confirmation (clear cache / force stop across all selected apps)
    pendingBulkAction?.let { action ->
        val isForceStop = action == PendingBulkAction.FORCE_STOP
        AlertDialog(
            onDismissRequest = { pendingBulkAction = null },
            title = { Text(if (isForceStop) "Force stop ${selectedApps.size} apps?" else "Clear cache for ${selectedApps.size} apps?") },
            text = {
                Text(
                    if (isForceStop) "If you force stop these apps, they may misbehave."
                    else "This will free up approximately ${MainViewModel.formatBytes(selectedCacheBytes)}."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingBulkAction = null
                    if (isForceStop) viewModel.forceStopSelectedApps() else viewModel.clearSelectedAppsCache()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkAction = null }) { Text("Cancel") }
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
            if (selectedApps.isNotEmpty()) {
                BottomAppBar {
                    Text(
                        "${selectedApps.size} selected  •  ${MainViewModel.formatBytes(selectedCacheBytes)}",
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (shizukuReady) {
                        OutlinedButton(onClick = { pendingBulkAction = PendingBulkAction.FORCE_STOP }) {
                            Text("Force Stop")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { pendingBulkAction = PendingBulkAction.CLEAR_CACHE },
                            modifier = Modifier.padding(end = 16.dp)
                        ) { Text("Clear Cache") }
                    }
                }
            }
        }
    ) { padding ->
        // Fix: everything is now ONE scrollable LazyColumn (storage card, filter
        // chips, select-all row are header items inside it) instead of a fixed
        // Column + a separately-scrolling app list below. Previously the storage
        // panel never scrolled away, permanently eating screen height and
        // squeezing the app list. Now scrolling the list also scrolls the
        // header out of view, like any normal scrollable screen.
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
                        subtitle = "Make sure Usage Access is granted, then tap refresh."
                    )
                }
            } else {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        shizukuReady = shizukuReady,
                        onToggleSelect = { viewModel.toggleAppSelection(app) },
                        onForceStopClick = { pendingForceStopApp = app },
                        onClearCacheClick = { viewModel.clearSingleAppCache(app) },
                        onOpenSettingsClick = {
                            context.startActivity(PermissionHelper.appSettingsIntent(context, app.packageName))
                        },
                        onToggleIgnore = { viewModel.toggleIgnore(app) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppStorageInfo,
    shizukuReady: Boolean,
    onToggleSelect: () -> Unit,
    onForceStopClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
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

            // Long-press the app name to add/remove it from the ignore list —
            // a floating menu pops up right where you pressed.
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
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
                        "Cache: ${MainViewModel.formatBytes(app.cacheBytes)}  •  Total: ${MainViewModel.formatBytes(app.totalBytes)}",
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
                if (!app.isIgnored) {
                    IconButton(onClick = onForceStopClick) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Force Stop")
                    }
                }
                IconButton(onClick = onClearCacheClick) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = "Clear Cache")
                }
            } else {
                IconButton(onClick = onOpenSettingsClick) {
                    Icon(Icons.Filled.Info, contentDescription = "App Settings")
                }
            }
        }
    }
}
