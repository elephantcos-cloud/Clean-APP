package com.shohan.cleanspace.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shohan.cleanspace.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrphanedDataScreen(viewModel: MainViewModel, navController: NavController) {
    val orphanedList by viewModel.orphanedItems.collectAsState()
    val isLoading by viewModel.orphanedLoading.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val shizukuReady = permissions.shizukuRunning && permissions.shizukuPermission

    LaunchedEffect(shizukuReady) {
        if (shizukuReady) viewModel.scanOrphanedData()
    }

    val totalSelected = orphanedList.filter { it.selected }.sumOf { it.sizeBytes }
    val selectedCount = orphanedList.count { it.selected }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete $selectedCount folders?") },
            text = { Text("This will permanently delete the selected orphaned folders (${MainViewModel.formatBytes(totalSelected)}). This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteSelectedOrphaned()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orphaned Data") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (orphanedList.isNotEmpty()) {
                BottomAppBar {
                    Text(
                        "Selected: ${MainViewModel.formatBytes(totalSelected)}",
                        modifier = Modifier.padding(start = 16.dp).weight(1f)
                    )
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.padding(end = 16.dp),
                        enabled = orphanedList.any { it.selected }
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !shizukuReady -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.FolderOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).padding(bottom = 12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Shizuku is required to scan Android/data and Android/obb folders",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Android 11+ blocks cross-app folder access. Shizuku grants shell-level access to scan these leftover folders. See README for setup.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                isLoading && orphanedList.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Scanning...")
                    }
                }
                orphanedList.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        com.shohan.cleanspace.ui.components.EmptyState(
                            icon = Icons.Filled.FolderOff,
                            title = "All Clear!",
                            subtitle = "No orphaned data found"
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(orphanedList, key = { it.path }) { item ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.selected,
                                        onCheckedChange = { viewModel.toggleOrphanedSelection(item) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.packageName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                        Text(item.location, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text(MainViewModel.formatBytes(item.sizeBytes))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
