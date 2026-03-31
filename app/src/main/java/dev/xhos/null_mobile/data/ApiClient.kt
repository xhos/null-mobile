package dev.xhos.null_mobile.data

import com.connectrpc.ProtocolClientConfig
import com.connectrpc.extensions.GoogleJavaJSONStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient

class ApiClient(
    private val authManager: AuthManager,
    private val serverConfig: ServerConfig,
) {

    private val sessionCookieInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = if (authManager.sessionToken != null) {
            original.newBuilder()
                .addHeader("Cookie", authManager.buildSessionCookie())
                .build()
        } else {
            original
        }
        chain.proceed(request)
    }

    private val apiPathPrefixInterceptor = Interceptor { chain ->
        val original = chain.request()
        val prefixedUrl = original.url.newBuilder()
            .encodedPath("/api" + original.url.encodedPath)
            .build()
        chain.proceed(original.newBuilder().url(prefixedUrl).build())
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(apiPathPrefixInterceptor)
            .addInterceptor(sessionCookieInterceptor)
            .build()
    }

    val connectClient by lazy {
        ProtocolClient(
            httpClient = ConnectOkHttpClient(okHttpClient),
            ProtocolClientConfig(
                host = serverConfig.gatewayUrl,
                serializationStrategy = GoogleJavaJSONStrategy(),
            ),
        )
    }
}
