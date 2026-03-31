package dev.xhos.null_mobile.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.xhos.null_mobile.data.ApiClient
import dev.xhos.null_mobile.data.AuthManager
import dev.xhos.null_mobile.data.ServerConfig
import dev.xhos.null_mobile.ui.login.LoginScreen
import dev.xhos.null_mobile.ui.login.LoginViewModel
import dev.xhos.null_mobile.ui.transactions.QuickAddScreen
import dev.xhos.null_mobile.ui.transactions.QuickAddViewModel
import dev.xhos.null_mobile.ui.transactions.TransactionDetailScreen
import dev.xhos.null_mobile.ui.transactions.TransactionDetailViewModel
import dev.xhos.null_mobile.ui.transactions.TransactionListScreen
import dev.xhos.null_mobile.ui.transactions.TransactionListViewModel
import dev.xhos.null_mobile.ui.receipts.ReceiptCaptureScreen
import dev.xhos.null_mobile.ui.receipts.ReceiptCaptureViewModel
import dev.xhos.null_mobile.ui.receipts.ReceiptListScreen
import dev.xhos.null_mobile.ui.receipts.ReceiptListViewModel
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
}

object Tabs {
    const val TRANSACTIONS = "transactions"
    const val RECEIPTS = "receipts"
    const val DASHBOARD = "dashboard"
}

object Screens {
    const val TRANSACTION_DETAIL = "transaction/{id}"
    fun transactionDetail(id: Long) = "transaction/$id"
    const val RECEIPT_CAPTURE = "receipt/capture"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Tabs.TRANSACTIONS, "transactions", Icons.AutoMirrored.Outlined.List),
    BottomNavItem(Tabs.RECEIPTS, "receipts", Icons.Outlined.ShoppingCart),
    BottomNavItem(Tabs.DASHBOARD, "dashboard", Icons.Outlined.Home),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    apiClient: ApiClient,
    authManager: AuthManager,
    onLogout: () -> Unit,
) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showChrome = currentRoute?.startsWith("transaction/") != true &&
        currentRoute != Screens.RECEIPT_CAPTURE

    var showAddSheet by remember { mutableStateOf(false) }
    var txListRefreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val addViewModel = remember { QuickAddViewModel(apiClient, authManager) }
    LaunchedEffect(Unit) {
        addViewModel.loadAccounts()
        addViewModel.loadCategories()
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = null,
        ) {
            QuickAddScreen(
                viewModel = addViewModel,
                onClose = {
                    scope.launch {
                        sheetState.hide()
                        showAddSheet = false
                    }
                },
                onSaved = { txListRefreshKey++ },
            )
        }
    }

    Scaffold(
        bottomBar = {
            if (!showChrome) return@Scaffold
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
        floatingActionButton = {
            if (!showChrome) return@Scaffold
            val isReceiptsTab = currentRoute == Tabs.RECEIPTS
            FloatingActionButton(
                onClick = {
                    if (isReceiptsTab) {
                        tabNavController.navigate(Screens.RECEIPT_CAPTURE)
                    } else {
                        addViewModel.resetFlow()
                        showAddSheet = true
                    }
                },
                shape = RoundedCornerShape(4.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                ),
            ) {
                Icon(
                    imageVector = if (isReceiptsTab) Icons.Outlined.Add else Icons.Outlined.Add,
                    contentDescription = if (isReceiptsTab) "capture receipt" else "add transaction",
                    modifier = Modifier.size(24.dp),
                )
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
            composable(Tabs.TRANSACTIONS) { backStackEntry ->
                val viewModel = remember { TransactionListViewModel(apiClient, authManager) }

                val deletedId = backStackEntry.savedStateHandle.get<Long>("tx_deleted")
                LaunchedEffect(deletedId) {
                    if (deletedId != null) {
                        viewModel.removeTransaction(deletedId)
                        backStackEntry.savedStateHandle.remove<Long>("tx_deleted")
                    }
                }

                val txUpdated = backStackEntry.savedStateHandle.get<Boolean>("tx_updated")
                LaunchedEffect(txUpdated) {
                    if (txUpdated == true) {
                        viewModel.refresh()
                        backStackEntry.savedStateHandle.remove<Boolean>("tx_updated")
                    }
                }

                LaunchedEffect(txListRefreshKey) {
                    if (txListRefreshKey > 0) viewModel.refresh()
                }

                TransactionListScreen(
                    viewModel = viewModel,
                    onTransactionClick = { txId ->
                        tabNavController.navigate(Screens.transactionDetail(txId))
                    },
                )
            }
            composable(Tabs.RECEIPTS) {
                val viewModel = remember { ReceiptListViewModel(apiClient, authManager) }

                val receiptUploaded = it.savedStateHandle.get<Boolean>("receipt_uploaded")
                LaunchedEffect(receiptUploaded) {
                    if (receiptUploaded == true) {
                        viewModel.refresh()
                        it.savedStateHandle.remove<Boolean>("receipt_uploaded")
                    }
                }

                ReceiptListScreen(viewModel = viewModel)
            }
            composable(Screens.RECEIPT_CAPTURE) {
                val viewModel = remember { ReceiptCaptureViewModel(apiClient, authManager) }
                ReceiptCaptureScreen(
                    viewModel = viewModel,
                    onBack = {
                        if (viewModel.uploadedReceipt != null) {
                            tabNavController.previousBackStackEntry
                                ?.savedStateHandle?.set("receipt_uploaded", true)
                        }
                        tabNavController.popBackStack()
                    },
                )
            }
            composable(Tabs.DASHBOARD) {
                PlaceholderTab(label = "dashboard")
            }
            composable(
                route = Screens.TRANSACTION_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { backStackEntry ->
                val txId = backStackEntry.arguments?.getLong("id") ?: return@composable
                val viewModel = remember { TransactionDetailViewModel(apiClient, authManager, txId) }
                TransactionDetailScreen(
                    viewModel = viewModel,
                    onBack = {
                        val prevEntry = tabNavController.previousBackStackEntry
                        if (viewModel.deleted) {
                            prevEntry?.savedStateHandle?.set("tx_deleted", txId)
                        } else if (viewModel.wasEdited) {
                            prevEntry?.savedStateHandle?.set("tx_updated", true)
                        }
                        tabNavController.popBackStack()
                    },
                )
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
