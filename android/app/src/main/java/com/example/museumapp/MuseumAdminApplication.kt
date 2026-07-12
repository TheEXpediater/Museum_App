package com.example.museumapp

import android.app.Application
import com.example.museumapp.data.AppContainer

class MuseumAdminApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
