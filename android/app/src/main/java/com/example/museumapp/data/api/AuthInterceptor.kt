package com.example.museumapp.data.api

import com.example.museumapp.data.session.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val session = runBlocking { sessionManager.session.first() }
        val requestBuilder = chain.request().newBuilder()
        if (session.accessToken.isNotBlank() && session.tokenType.equals("bearer", ignoreCase = true)) {
            requestBuilder.header("Authorization", "Bearer ${session.accessToken}")
        }
        val response = chain.proceed(requestBuilder.build())
        if (response.code == 401) {
            runBlocking { sessionManager.clearSession() }
        }
        return response
    }
}
