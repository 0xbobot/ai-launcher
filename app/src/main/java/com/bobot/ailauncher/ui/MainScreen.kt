package com.bobot.ailauncher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bobot.ailauncher.ui.apps.AllAppsScreen
import com.bobot.ailauncher.ui.capability.CapabilityScreen
import com.bobot.ailauncher.ui.home.HomeScreen

sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Dest("home", "首页", Icons.Filled.Home)
    data object Capability : Dest("capability", "能力", Icons.Filled.AutoAwesome)
    data object Apps : Dest("apps", "应用", Icons.Filled.Apps)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(Dest.Home, Dest.Capability, Dest.Apps)

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                items.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Home.route) { HomeScreen() }
            composable(Dest.Capability.route) {
                CapabilityScreen(onOpenAllApps = {
                    navController.navigate(Dest.Apps.route) {
                        launchSingleTop = true
                    }
                })
            }
            composable(Dest.Apps.route) { AllAppsScreen() }
        }
    }
}
