package com.example.museumapp.data.api

import com.example.museumapp.data.session.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isPublicEndpoint = path in setOf(
            "/api/v1/auth/login",
            "/api/v1/health",
            "/api/v1/ai/health",
            "/api/v1/visitor/guest-session",
            "/api/v1/student/register",
            "/api/v1/student/login"
        ) || path.startsWith("/api/v1/public/")
        val requestBuilder = request.newBuilder()
        if (!isPublicEndpoint) {
            val session = runBlocking { sessionManager.session.first() }
            if (session.accessToken.isNotBlank() && session.tokenType.equals("bearer", ignoreCase = true)) {
                requestBuilder.header("Authorization", "Bearer ${session.accessToken}")
            }
        }
        val response = chain.proceed(requestBuilder.build())
        if (response.code == 401 && !isPublicEndpoint) {
            runBlocking { sessionManager.clearSession() }
        }
        return response
    }
}
