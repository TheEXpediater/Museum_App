package com.example.museumapp.data.api

import com.example.museumapp.BuildConfig
import com.example.museumapp.data.network.BackendConnectionInterceptor
import com.example.museumapp.data.network.BackendConnectionManager
import com.example.museumapp.data.session.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    /**
     * The Retrofit base URL is a fixed placeholder. [BackendConnectionInterceptor] rewrites the
     * host/port on every request to whatever [BackendConnectionManager] has discovered at
     * runtime, so no laptop IP is ever compiled into the APK (see [BuildConfig.API_BASE_URL],
     * which now only matters for the emulator-only debug fallback documented in local.properties).
     */
    private const val PLACEHOLDER_BASE_URL = "http://backend.local/"

    fun create(sessionManager: SessionManager, backendConnectionManager: BackendConnectionManager): AdminApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(BackendConnectionInterceptor(backendConnectionManager))
            .addInterceptor(AuthInterceptor(sessionManager))
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AdminApiService::class.java)
    }
}
