package dev.xhos.null_mobile.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.xhos.null_mobile.data.AuthManager
import dev.xhos.null_mobile.data.ServerConfig
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authManager: AuthManager,
    private val serverConfig: ServerConfig,
) : ViewModel() {

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var webUrl by mutableStateOf(serverConfig.webUrl)
    var coreUrl by mutableStateOf(serverConfig.coreUrl)
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loginSuccess by mutableStateOf(false)
        private set
    var showServerConfig by mutableStateOf(false)

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            error = "Email and password are required"
            return
        }

        // Save server URLs before login
        serverConfig.webUrl = webUrl.trimEnd('/')
        serverConfig.coreUrl = coreUrl.trimEnd('/')

        viewModelScope.launch {
            isLoading = true
            error = null

            val result = authManager.signIn(email, password)
            result.fold(
                onSuccess = { loginSuccess = true },
                onFailure = { error = it.message }
            )

            isLoading = false
        }
    }
}
