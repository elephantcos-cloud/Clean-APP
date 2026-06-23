package com.shohan.cleanspace.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shohan.cleanspace.ui.screens.AppsScreen
import com.shohan.cleanspace.ui.screens.PermissionScreen
import com.shohan.cleanspace.ui.screens.SettingsScreen
import com.shohan.cleanspace.viewmodel.MainViewModel

@Composable
fun CleanSpaceRoot(viewModel: MainViewModel) {
    val permissions by viewModel.permissions.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Scaffold wraps EVERYTHING — Snackbar works on PermissionScreen too
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val navController = rememberNavController()

        // Fix: All Files Access is no longer required — this simplified app only
        // clears app cache and force-stops apps, both via Shizuku + Usage Access.
        if (!permissions.usageAccess) {
            PermissionScreen(
                permissions = permissions,
                onRefresh = { viewModel.refreshPermissions() },
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { AppsScreen(viewModel, navController) }
            composable("settings") { SettingsScreen(viewModel, navController) }
        }
    }
}
