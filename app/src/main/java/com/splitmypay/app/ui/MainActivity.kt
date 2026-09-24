package com.splitmypay.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.splitmypay.app.service.WalletNotificationListenerService
import com.splitmypay.app.ui.screens.GroupManagementScreen
import com.splitmypay.app.ui.screens.HomeScreen
import com.splitmypay.app.ui.screens.SettingsScreen
import com.splitmypay.app.ui.screens.SplitScreen
import com.splitmypay.app.ui.theme.SplitMyPayTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SplitMyPayTheme {
                MainAppNavigation(viewModel, intent)
            }
        }
    }
}

@Composable
fun MainAppNavigation(
    viewModel: MainViewModel,
    initialIntent: Intent?
) {
    val navController = rememberNavController()

    // Handle deep link from intent if opened from notification
    LaunchedEffect(initialIntent) {
        val extraId = initialIntent?.getLongExtra(WalletNotificationListenerService.EXTRA_CAPTURE_ID, -1L) ?: -1L
        if (extraId > 0) {
            navController.navigate("split/$extraId")
        } else {
            val data = initialIntent?.data
            if (data?.scheme == "splitmypay" && data.host == "split") {
                val pathId = data.lastPathSegment?.toLongOrNull()
                if (pathId != null && pathId > 0) {
                    navController.navigate("split/$pathId")
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf("home", "groups", "settings")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentRoute == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Group, contentDescription = "Groups") },
                        label = { Text("Groups") },
                        selected = currentRoute == "groups",
                        onClick = {
                            navController.navigate("groups") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentRoute == "settings",
                        onClick = {
                            navController.navigate("settings") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToSplit = { captureId -> navController.navigate("split/$captureId") },
                    onNavigateToGroups = { navController.navigate("groups") },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable("groups") {
                GroupManagementScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "split/{captureId}",
                arguments = listOf(navArgument("captureId") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = "splitmypay://split/{captureId}" })
            ) { backStackEntry ->
                val captureId = backStackEntry.arguments?.getLong("captureId") ?: 0L
                SplitScreen(
                    captureId = captureId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
