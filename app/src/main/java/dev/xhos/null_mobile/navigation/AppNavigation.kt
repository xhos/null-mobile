package dev.xhos.null_mobile.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.xhos.null_mobile.data.ApiClient
import dev.xhos.null_mobile.data.AuthManager
import dev.xhos.null_mobile.data.ServerConfig
import dev.xhos.null_mobile.ui.login.LoginScreen
import dev.xhos.null_mobile.ui.login.LoginViewModel
import dev.xhos.null_mobile.ui.transactions.TransactionListScreen
import dev.xhos.null_mobile.ui.transactions.TransactionListViewModel

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
}

object Tabs {
    const val TRANSACTIONS = "transactions"
    const val DASHBOARD = "dashboard"
    const val ADD = "add"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Tabs.TRANSACTIONS, "transactions", Icons.AutoMirrored.Outlined.List),
    BottomNavItem(Tabs.DASHBOARD, "dashboard", Icons.Outlined.Home),
    BottomNavItem(Tabs.ADD, "add", Icons.Outlined.Add),
)

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val serverConfig = remember { ServerConfig(context) }
    val authManager = remember { AuthManager(context, serverConfig) }
    val apiClient = remember { ApiClient(authManager, serverConfig) }

    val rootNavController = rememberNavController()
    val startDestination = if (authManager.isAuthenticated()) Routes.MAIN else Routes.LOGIN

    NavHost(
        navController = rootNavController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        composable(Routes.LOGIN) {
            val viewModel = remember { LoginViewModel(authManager, serverConfig) }
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    rootNavController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainScaffold(
                apiClient = apiClient,
                authManager = authManager,
                onLogout = {
                    authManager.logout()
                    rootNavController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
fun MainScaffold(
    apiClient: ApiClient,
    authManager: AuthManager,
    onLogout: () -> Unit,
) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            tabNavController.navigate(item.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onBackground,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = Tabs.TRANSACTIONS,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable(Tabs.TRANSACTIONS) {
                val viewModel = remember { TransactionListViewModel(apiClient, authManager) }
                TransactionListScreen(viewModel = viewModel)
            }
            composable(Tabs.DASHBOARD) {
                PlaceholderTab(label = "dashboard")
            }
            composable(Tabs.ADD) {
                PlaceholderTab(label = "add transaction")
            }
        }
    }
}

@Composable
private fun PlaceholderTab(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
