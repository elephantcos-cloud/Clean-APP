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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shohan.cleanspace.data.models.MediaAppInfo
import com.shohan.cleanspace.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCleanerScreen(viewModel: MainViewModel, navController: NavController) {
    val mediaApps by viewModel.mediaApps.collectAsState()
    val isLoading by viewModel.mediaLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.scanMediaApps() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media Cleaner") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && mediaApps.isEmpty() -> {
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
                mediaApps.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        com.shohan.cleanspace.ui.components.EmptyState(
                            icon = Icons.Filled.ChatBubble,
                            title = "Nothing Found",
                            subtitle = "No media found for WhatsApp, Telegram, Messenger, or Facebook"
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(mediaApps, key = { it.packageName }) { app ->
                            MediaAppCard(
                                app = app,
                                onDeleteCategory = { categoryName ->
                                    viewModel.deleteMediaCategory(app.packageName, categoryName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaAppCard(app: MediaAppInfo, onDeleteCategory: (String) -> Unit) {
    val appIcon = when (app.packageName) {
        "com.facebook.katana" -> Icons.Filled.Facebook
        "org.telegram.messenger" -> Icons.Filled.Forum
        else -> Icons.Filled.ChatBubble
    }
    var categoryPendingDelete by remember { mutableStateOf<com.shohan.cleanspace.data.models.MediaCategory?>(null) }

    categoryPendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryPendingDelete = null },
            title = { Text("Delete ${category.name}?") },
            text = {
                Text(
                    "This will permanently delete ${category.fileCount} files " +
                        "(${MainViewModel.formatBytes(category.bytes)}) from ${app.displayName}. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    categoryPendingDelete = null
                    onDeleteCategory(category.name)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { categoryPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(appIcon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Total: ${MainViewModel.formatBytes(app.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            app.categories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(category.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${category.fileCount} files  •  ${MainViewModel.formatBytes(category.bytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { categoryPendingDelete = category }) {
                        Text("Clean")
                    }
                }
            }
        }
    }
}
