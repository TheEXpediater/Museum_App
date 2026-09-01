package com.example.museumapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.museumapp.data.AppContainer
import com.example.museumapp.data.network.BackendConnectionState
import com.example.museumapp.data.session.AdminSession
import com.example.museumapp.data.session.VisitorSession
import com.example.museumapp.ui.connection.BackendConnectionGate
import com.example.museumapp.ui.theme.MuseumAdminTheme
import com.example.museumapp.ui.visitor.navigation.StartupDestination
import com.example.museumapp.ui.visitor.navigation.VisitorNavGraph
import com.example.museumapp.ui.visitor.navigation.resolveStartupDestination
import com.example.museumapp.ui.visitor.theme.VisitorTheme

@Composable
fun AppRoot(container: AppContainer) {
    val connectionState by container.backendConnectionManager.state.collectAsStateWithLifecycle()

    DisposableEffect(container) {
        container.backendConnectionManager.start()
        onDispose { }
    }

    if (connectionState is BackendConnectionState.Connected) {
        MuseumAppContent(container)
    } else {
        VisitorTheme {
            BackendConnectionGate(state = connectionState, manager = container.backendConnectionManager)
        }
    }
}

@Composable
private fun MuseumAppContent(container: AppContainer) {
    val adminSession by container.sessionManager.session.collectAsStateWithLifecycle(initialValue = AdminSession())
    val visitorSession by container.sessionManager.visitorSession.collectAsStateWithLifecycle(initialValue = VisitorSession())
    val onboardingCompleted by container.sessionManager.onboardingCompleted.collectAsStateWithLifecycle(initialValue = false)
    var adminLoginRequested by rememberSaveable { mutableStateOf(false) }

    val destination = resolveStartupDestination(onboardingCompleted, adminSession, visitorSession)

    if (destination == StartupDestination.Admin || adminLoginRequested) {
        MuseumAdminTheme {
            AdminNavGraph(
                repository = container.adminRepository,
                onBackToVisitor = { adminLoginRequested = false }
            )
        }
    } else {
        VisitorTheme {
            if (destination == StartupDestination.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                VisitorNavGraph(
                    repository = container.visitorRepository,
                    startupDestination = destination,
                    onAdminLogin = { adminLoginRequested = true }
                )
            }
        }
    }
}
