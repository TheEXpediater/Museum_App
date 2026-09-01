package com.example.museumapp.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites every outgoing request to the currently discovered LAN backend host/port. The Retrofit
 * base URL stays a fixed placeholder; this interceptor is what makes the address dynamic so the
 * APK never bakes in a specific laptop IP.
 */
class BackendConnectionInterceptor(private val manager: BackendConnectionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = manager.activeHost
        val request = if (host != null) {
            val newUrl = original.url.newBuilder()
                .scheme("http")
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
