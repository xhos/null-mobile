package dev.xhos.null_mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
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

    var sessionToken: String?
        get() = prefs.getString(KEY_SESSION_TOKEN, null)
        private set(value) = prefs.edit().putString(KEY_SESSION_TOKEN, value).apply()

    var sessionCookieName: String?
        get() = prefs.getString(KEY_SESSION_COOKIE_NAME, null)
        private set(value) = prefs.edit().putString(KEY_SESSION_COOKIE_NAME, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        private set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    val userId: String?
        get() = prefs.getString(KEY_USER_ID, null)

    fun isAuthenticated(): Boolean = sessionToken != null

    suspend fun signIn(emailInput: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", emailInput)
                put("password", password)
            }
            val request = Request.Builder()
                .url("${serverConfig.gatewayUrl}/api/auth/sign-in/email")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Sign-in failed (${response.code}): $responseBody"))
            }

            val cookies = response.headers("set-cookie")

            var cookieName: String? = null
            var cookieValue: String? = null
            for (cookie in cookies) {
                val nameValue = cookie.substringBefore(";")
                val name = nameValue.substringBefore("=")
                if (name.endsWith("session_token")) {
                    cookieName = name
                    cookieValue = nameValue.substringAfter("=")
                    break
                }
            }

            if (cookieName == null || cookieValue.isNullOrBlank()) {
                return@withContext Result.failure(Exception("No session cookie in response"))
            }

            sessionToken = cookieValue
            sessionCookieName = cookieName
            email = emailInput

            val responseJson = runCatching { JSONObject(responseBody) }.getOrNull()
            val bodyUserId = responseJson?.optJSONObject("user")?.optString("id")
                ?.takeIf { it.isNotBlank() }

            if (bodyUserId != null) {
                prefs.edit().putString(KEY_USER_ID, bodyUserId).apply()
            } else {
                fetchAndStoreUserId()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchAndStoreUserId() {
        val sessionResult = getSession()
        sessionResult.getOrNull()?.let { sessionJson ->
            val fetchedUserId = sessionJson.optJSONObject("user")?.optString("id")
            if (!fetchedUserId.isNullOrBlank()) {
                prefs.edit().putString(KEY_USER_ID, fetchedUserId).apply()
            }
        }
    }

    fun buildSessionCookie(): String {
        val name = sessionCookieName ?: "better-auth.session_token"
        return "$name=$sessionToken"
    }

    suspend fun getSession(): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val session = sessionToken
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val request = Request.Builder()
                .url("${serverConfig.gatewayUrl}/api/auth/get-session")
                .get()
                .addHeader("Cookie", buildSessionCookie())
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Session check failed (${response.code})"))
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))
            Result.success(JSONObject(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun refreshToken(): Result<String> =
        Result.failure(Exception("Session expired — please sign in again"))

    fun logout() {
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_SESSION_COOKIE_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_USER_ID)
            .apply()
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SESSION_COOKIE_NAME = "session_cookie_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_USER_ID = "user_id"
    }
}
