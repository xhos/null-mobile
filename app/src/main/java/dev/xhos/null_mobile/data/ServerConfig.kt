package dev.xhos.null_mobile.data

import android.content.Context
import android.content.SharedPreferences

class ServerConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("server_config", Context.MODE_PRIVATE)

    var gatewayUrl: String
        get() = prefs.getString(KEY_GATEWAY_URL, DEFAULT_GATEWAY_URL) ?: DEFAULT_GATEWAY_URL
        set(value) = prefs.edit().putString(KEY_GATEWAY_URL, value).apply()

    companion object {
        private const val KEY_GATEWAY_URL = "null_gateway_url"
        const val DEFAULT_GATEWAY_URL = "https://gateway.null.lab.xhos.dev"
    }
}
