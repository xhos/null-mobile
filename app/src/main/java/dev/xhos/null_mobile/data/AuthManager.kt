package dev.xhos.null_mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AuthManager(context: Context, private val serverConfig: ServerConfig) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val httpClient = OkHttpClient.Builder().build()
    private val refreshMutex = Mutex()

    var sessionToken: String?
        get() = prefs.getString(KEY_SESSION_TOKEN, null)
        private set(value) = prefs.edit().putString(KEY_SESSION_TOKEN, value).apply()

    var jwt: String?
        get() = prefs.getString(KEY_JWT, null)
        private set(value) = prefs.edit().putString(KEY_JWT, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        private set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    fun isAuthenticated(): Boolean = sessionToken != null

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            val request = Request.Builder()
                .url("${serverConfig.webUrl}/api/auth/sign-in/email")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(Exception("Sign-in failed (${response.code}): $body"))
            }

            // Extract session token from set-cookie header
            val cookies = response.headers("set-cookie")
            val sessionCookie = cookies.firstOrNull { it.startsWith("better-auth.session_token=") }
                ?: cookies.firstOrNull { it.startsWith("session_token=") }

            val token = sessionCookie
                ?.substringAfter("=")
                ?.substringBefore(";")

            if (token.isNullOrBlank()) {
                return@withContext Result.failure(Exception("No session token in response"))
            }

            sessionToken = token
            this@AuthManager.email = email

            // Fetch JWT
            getToken().getOrThrow()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val session = sessionToken
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val request = Request.Builder()
                .url("${serverConfig.webUrl}/api/auth/token")
                .get()
                .addHeader("Cookie", "better-auth.session_token=$session")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Token fetch failed (${response.code})"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val tokenJson = JSONObject(body)
            val token = tokenJson.getString("token")
            jwt = token
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): Result<String> = refreshMutex.withLock {
        getToken()
    }

    suspend fun getSession(): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val session = sessionToken
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val request = Request.Builder()
                .url("${serverConfig.webUrl}/api/auth/get-session")
                .get()
                .addHeader("Cookie", "better-auth.session_token=$session")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Session check failed (${response.code})"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            Result.success(JSONObject(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        sessionToken = null
        jwt = null
        email = null
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_JWT = "jwt"
        private const val KEY_EMAIL = "email"
    }
}
