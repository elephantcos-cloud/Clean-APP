package com.shohan.cleanspace.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shohan.cleanspace.ui.components.DonutChart
import com.shohan.cleanspace.ui.components.DonutSlice
import com.shohan.cleanspace.ui.components.ToolCard
import com.shohan.cleanspace.ui.theme.Amber
import com.shohan.cleanspace.ui.theme.CategoryColors
import com.shohan.cleanspace.ui.theme.Emerald600
import com.shohan.cleanspace.ui.theme.Orange
import com.shohan.cleanspace.ui.theme.Rose
import com.shohan.cleanspace.ui.theme.Sky
import com.shohan.cleanspace.ui.theme.Violet
import com.shohan.cleanspace.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val overview        by viewModel.storageOverview.collectAsState()
    val breakdown       by viewModel.categoryBreakdown.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val lowStorage      by viewModel.lowStorageWarning.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadDashboard() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reclaim") },
                actions = {
                    if (isLoading) {
                        IconButton(onClick = { viewModel.cancelCurrentScan() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    } else {
                        IconButton(onClick = { viewModel.loadDashboard() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Low storage warning
            if (lowStorage) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Rose.copy(alpha = 0.10f)),
                        border = BorderStroke(1.dp, Rose.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Rose, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Storage critically low — less than 500 MB remaining",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Rose
                            )
                        }
                    }
                }
            }

            // Storage overview card
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
                        if (isLoading && breakdown.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Scanning storage...", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val usedPct = if (overview.totalBytes > 0)
                                (overview.usedBytes * 100 / overview.totalBytes).toInt() else 0
                            val slices = if (breakdown.isNotEmpty())
                                breakdown.mapIndexed { i, c ->
                                    DonutSlice(c.bytes.toFloat(), CategoryColors[i % CategoryColors.size], c.name)
                                }
                            else listOf(DonutSlice(1f, Color.LightGray, ""))

                            DonutChart(
                                slices = slices,
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .align(Alignment.CenterHorizontally),
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

                            if (breakdown.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                breakdown.forEachIndexed { i, cat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(10.dp)
                                                .clip(CircleShape)
                                                .background(CategoryColors[i % CategoryColors.size])
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(cat.name, modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium)
                                        Text(MainViewModel.formatBytes(cat.bytes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section header
            item {
                Text(
                    "Tools",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
                )
            }

            // Tool grid — 2 columns
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    toolItems.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { tool ->
                                ToolCard(
                                    icon = tool.icon,
                                    title = tool.title,
                                    subtitle = tool.subtitle,
                                    accentColor = tool.color,
                                    modifier = Modifier.weight(1f),
                                    onClick = { navController.navigate(tool.route) }
                                )
                            }
                            // Fill last row if odd count
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

private data class ToolItem(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

private val toolItems = listOf(
    ToolItem("junk",          "Junk Cleaner",    "Temp & cache files",    Icons.Filled.DeleteSweep, Rose),
    ToolItem("large_files",   "Large Files",     "Find space hogs",       Icons.Filled.FolderOpen,  Orange),
    ToolItem("duplicates",    "Duplicates",      "Remove copies",         Icons.Filled.ContentCopy, Violet),
    ToolItem("orphaned",      "Orphaned Data",   "App leftovers",         Icons.Filled.FolderOff,   Sky),
    ToolItem("media_cleaner", "Media Cleaner",   "WhatsApp / Telegram",   Icons.Filled.ChatBubble,  Amber),
    ToolItem("apps",          "App Manager",     "Storage by app",        Icons.Filled.Apps,        Emerald600)
)
