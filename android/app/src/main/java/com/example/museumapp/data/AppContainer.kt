package com.example.museumapp.data

import android.content.Context
import com.example.museumapp.data.api.NetworkModule
import com.example.museumapp.data.network.BackendConnectionManager
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.data.repository.VisitorRepository
import com.example.museumapp.data.session.SessionManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val sessionManager = SessionManager(appContext)
    val backendConnectionManager = BackendConnectionManager(appContext)
    private val apiService = NetworkModule.create(sessionManager, backendConnectionManager)
    val adminRepository = AdminRepository(apiService, sessionManager, appContext, backendConnectionManager)
    val visitorRepository = VisitorRepository(apiService, sessionManager, appContext, backendConnectionManager)
}
