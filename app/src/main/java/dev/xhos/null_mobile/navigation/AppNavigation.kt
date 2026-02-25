package dev.xhos.null_mobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.xhos.null_mobile.data.ApiClient
import dev.xhos.null_mobile.data.AuthManager
import dev.xhos.null_mobile.data.ServerConfig
import dev.xhos.null_mobile.ui.home.HomeScreen
import dev.xhos.null_mobile.ui.login.LoginScreen
import dev.xhos.null_mobile.ui.login.LoginViewModel

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val serverConfig = remember { ServerConfig(context) }
    val authManager = remember { AuthManager(context, serverConfig) }
    val apiClient = remember { ApiClient(authManager, serverConfig) }

    val navController = rememberNavController()
    val startDestination = if (authManager.isAuthenticated()) Routes.HOME else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            val viewModel = remember { LoginViewModel(authManager, serverConfig) }
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                email = authManager.email,
                onLogout = {
                    authManager.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
    }
}
