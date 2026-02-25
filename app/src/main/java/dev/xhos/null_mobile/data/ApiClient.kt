package dev.xhos.null_mobile.data

import com.connectrpc.ProtocolClientConfig
import com.connectrpc.extensions.GoogleJavaProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient

class ApiClient(
    private val authManager: AuthManager,
    private val serverConfig: ServerConfig,
) {

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = authManager.jwt
        val request = if (token != null) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        chain.proceed(request)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    val connectClient by lazy {
        ProtocolClient(
            httpClient = ConnectOkHttpClient(okHttpClient),
            ProtocolClientConfig(
                host = serverConfig.coreUrl,
                serializationStrategy = GoogleJavaProtobufStrategy(),
            ),
        )
    }
}
