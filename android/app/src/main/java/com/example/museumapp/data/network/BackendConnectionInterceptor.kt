package com.example.museumapp.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites every outgoing request to the currently active backend - either the discovered LAN
 * host/port (http) or the hosted VPS API (https), per [BackendConnectionManager.activeScheme]. The
 * Retrofit base URL stays a fixed placeholder; this interceptor is what makes the address dynamic
 * so the APK never bakes in a specific laptop IP. It must never force the hosted HTTPS endpoint
 * back onto http or the LAN default port - that would silently break the production connection.
 */
class BackendConnectionInterceptor(private val manager: BackendConnectionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = manager.activeHost
        val request = if (host != null) {
            val newUrl = original.url.newBuilder()
                .scheme(manager.activeScheme)
                .host(host)
                .port(manager.activePort)
                .build()
            original.newBuilder().url(newUrl).build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
