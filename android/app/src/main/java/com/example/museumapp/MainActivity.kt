package com.example.museumapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.museumapp.ui.navigation.AdminNavGraph
import com.example.museumapp.ui.theme.MuseumAdminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as MuseumAdminApplication).container.adminRepository
        setContent {
            MuseumAdminTheme {
                AdminNavGraph(repository = repository)
            }
        }
    }
}
