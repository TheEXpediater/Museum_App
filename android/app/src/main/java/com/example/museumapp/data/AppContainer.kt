package com.example.museumapp.data

import android.content.Context
import com.example.museumapp.data.api.NetworkModule
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.data.session.SessionManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val sessionManager = SessionManager(appContext)
    private val apiService = NetworkModule.create(sessionManager)
    val adminRepository = AdminRepository(apiService, sessionManager, appContext)
}
