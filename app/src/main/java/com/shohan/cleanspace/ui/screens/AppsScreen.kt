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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shohan.cleanspace.data.PermissionHelper
import com.shohan.cleanspace.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(viewModel: MainViewModel, navController: NavController) {
    val apps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.appsLoading.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val bulkProgress by viewModel.bulkClearProgress.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadApps() }

    val shizukuReady = permissions.shizukuRunning && permissions.shizukuPermission

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Manager") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (!shizukuReady) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("One-tap cache clearing", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Activate Shizuku to clear all app caches at once. Without it, you can still open each app's settings individually.",
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
            } else {
                Button(
                    onClick = { viewModel.clearAllAppsCacheViaShizuku() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    enabled = bulkProgress == null
                ) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (bulkProgress != null) "Cleaning... ${bulkProgress!!.first}/${bulkProgress!!.second}"
                        else "Clear Cache — All Apps"
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isLoading && apps.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                        Text(
                                            "Cache: ${MainViewModel.formatBytes(app.cacheBytes)}  •  Total: ${MainViewModel.formatBytes(app.totalBytes)}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (shizukuReady) {
                                        TextButton(onClick = { viewModel.clearSingleAppCacheViaShizuku(app) }) {
                                            Text("Clear")
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            context.startActivity(
                                                PermissionHelper.appSettingsIntent(context, app.packageName)
                                            )
                                        }) {
                                            Icon(Icons.Filled.Info, contentDescription = "App Settings")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
