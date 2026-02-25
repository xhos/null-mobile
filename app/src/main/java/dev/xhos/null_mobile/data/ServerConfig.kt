package dev.xhos.null_mobile.data

import android.content.Context
import android.content.SharedPreferences

class ServerConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("server_config", Context.MODE_PRIVATE)

    var webUrl: String
        get() = prefs.getString(KEY_WEB_URL, DEFAULT_WEB_URL) ?: DEFAULT_WEB_URL
        set(value) = prefs.edit().putString(KEY_WEB_URL, value).apply()

    var coreUrl: String
        get() = prefs.getString(KEY_CORE_URL, DEFAULT_CORE_URL) ?: DEFAULT_CORE_URL
        set(value) = prefs.edit().putString(KEY_CORE_URL, value).apply()

    companion object {
        private const val KEY_WEB_URL = "null_web_url"
        private const val KEY_CORE_URL = "null_core_url"
        // TODO: change to production URLs before release
        const val DEFAULT_WEB_URL = "https://null.lab.xhos.dev"
        const val DEFAULT_CORE_URL = "https://null-api.lab.xhos.dev"
    }
}
