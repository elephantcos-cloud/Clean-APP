package com.shohan.cleanspace.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shohan.cleanspace.data.models.HistoryActionType
import com.shohan.cleanspace.data.models.HistoryEntry
import com.shohan.cleanspace.ui.components.EmptyState
import com.shohan.cleanspace.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, navController: NavController) {
    val history by viewModel.history.collectAsState()
    var pendingClear by remember { mutableStateOf(false) }

    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text("Clear history?") },
            text = { Text("This only deletes the log — it doesn't undo anything or affect any app.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingClear = false
                    viewModel.clearHistory()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { pendingClear = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear history")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = "No History Yet",
                    subtitle = "Cache clears, force stops, and app enable/disable actions will show up here."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(history, key = { it.timestamp.toString() + it.appName }) { entry ->
                    HistoryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val (icon, label) = when (entry.action) {
        HistoryActionType.CACHE_CLEARED -> Icons.Filled.CleaningServices to "Cache cleared"
        HistoryActionType.FORCE_STOPPED -> Icons.Filled.PowerSettingsNew to "Force stopped"
        HistoryActionType.DATA_CLEARED -> Icons.Filled.Delete to "Data cleared"
        HistoryActionType.DISABLED -> Icons.Filled.Block to "Disabled"
        HistoryActionType.ENABLED -> Icons.Filled.CheckCircle to "Enabled"
        HistoryActionType.AUTO_CLEAN_RUN -> Icons.Filled.CleaningServices to "Auto-clean"
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp).fillMaxWidth()) {
                Text(
                    if (entry.bytesFreed > 0) "$label — ${entry.appName}  (${MainViewModel.formatBytes(entry.bytesFreed)})"
                    else "$label — ${entry.appName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        entry.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
