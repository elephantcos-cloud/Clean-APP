package com.shohan.cleanspace.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import com.shohan.cleanspace.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shohan.cleanspace.data.models.AutoCleanAggressiveness
import com.shohan.cleanspace.data.models.AutoCleanFrequency
import com.shohan.cleanspace.data.models.ThemeMode
import com.shohan.cleanspace.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, navController: NavController) {
    val themeMode by viewModel.themeMode.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    val ignoredApps by viewModel.ignoredAppsInfo.collectAsState()
    val autoClean by viewModel.autoCleanSettings.collectAsState()
    val context = LocalContext.current

    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Ignore List") },
            text = {
                Column {
                    Text("Paste a comma-separated list of package names.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("com.example.app, com.example.app2") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    viewModel.importIgnoreList(importText)
                    importText = ""
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            ThemeOption("Light", ThemeMode.LIGHT, themeMode) { viewModel.setThemeMode(it) }
            ThemeOption("Dark", ThemeMode.DARK, themeMode) { viewModel.setThemeMode(it) }
            ThemeOption("Follow System", ThemeMode.SYSTEM, themeMode) { viewModel.setThemeMode(it) }

            // Material You dynamic color only exists on Android 12+ (API 31) —
            // hidden entirely below that, since the toggle would do nothing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use wallpaper colors (Material You)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useDynamicColor, onCheckedChange = { viewModel.setUseDynamicColor(it) })
                }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            Text("Auto-Clean", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Scores every app by cache size and idle time, then clears only the top candidates each run — and skips apps whose cache keeps regrowing fast (a sign it's actively used). Runs in the background via Shizuku. Rule-based, not AI: fully offline, no network or account needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Auto-Clean", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = autoClean.enabled,
                    onCheckedChange = { viewModel.setAutoCleanEnabled(it) }
                )
            }

            if (autoClean.enabled) {
                Spacer(Modifier.height(8.dp))
                Text("How often", style = MaterialTheme.typography.bodyMedium)
                FrequencyOption("Daily", AutoCleanFrequency.DAILY, autoClean.frequency) { viewModel.setAutoCleanFrequency(it) }
                FrequencyOption("Weekly", AutoCleanFrequency.WEEKLY, autoClean.frequency) { viewModel.setAutoCleanFrequency(it) }

                Spacer(Modifier.height(8.dp))
                Text("How aggressive", style = MaterialTheme.typography.bodyMedium)
                AggressivenessOption(
                    "Conservative — only very large, long-unused caches",
                    AutoCleanAggressiveness.CONSERVATIVE, autoClean.aggressiveness
                ) { viewModel.setAutoCleanAggressiveness(it) }
                AggressivenessOption(
                    "Balanced — recommended",
                    AutoCleanAggressiveness.BALANCED, autoClean.aggressiveness
                ) { viewModel.setAutoCleanAggressiveness(it) }
                AggressivenessOption(
                    "Aggressive — clears more, more often",
                    AutoCleanAggressiveness.AGGRESSIVE, autoClean.aggressiveness
                ) { viewModel.setAutoCleanAggressiveness(it) }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Ignored apps and anything used in the last hour are never touched. Below 1 GB (or 10%) free space, Auto-Clean temporarily escalates to Aggressive; below 300 MB it escalates further and keeps going until enough is freed. Actual run timing depends on Android's battery optimization — this is a minimum interval, not a guarantee.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("history") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("History", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            Text("Ignore List", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Protected from force-stop and Auto-Clean. Tap ✕ to remove, or long-press an app's name on the main screen to add one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (ignoredApps.isEmpty()) {
                Text(
                    "No apps ignored yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ignoredApps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(app.appName, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { viewModel.toggleIgnore(app) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove ${app.appName} from ignore list")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("CleanSpace Ignore List", viewModel.exportIgnoreList()))
                }) { Text("Export") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showImportDialog = true }) { Text("Import") }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            Text("CleanSpace v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text("App cache cleaner & force stop", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun FrequencyOption(
    label: String,
    value: AutoCleanFrequency,
    current: AutoCleanFrequency,
    onSelect: (AutoCleanFrequency) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = current == value, onClick = { onSelect(value) })
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun AggressivenessOption(
    label: String,
    value: AutoCleanAggressiveness,
    current: AutoCleanAggressiveness,
    onSelect: (AutoCleanAggressiveness) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = current == value, onClick = { onSelect(value) })
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
