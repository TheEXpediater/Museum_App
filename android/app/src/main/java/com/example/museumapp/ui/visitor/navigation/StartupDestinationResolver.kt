package com.example.museumapp.ui.visitor.navigation

import com.example.museumapp.data.session.AdminSession
import com.example.museumapp.data.session.VisitorSession

enum class StartupDestination {
    Loading,
    VisitorOnboarding,
    VisitorEntry,
    VisitorHome,
    Admin
}

fun resolveStartupDestination(
    onboardingCompleted: Boolean,
    adminSession: AdminSession,
    visitorSession: VisitorSession
): StartupDestination {
    return when {
        adminSession.isAuthenticated -> StartupDestination.Admin
        visitorSession.isVisitorAuthenticated -> StartupDestination.VisitorHome
        !onboardingCompleted -> StartupDestination.VisitorOnboarding
        else -> StartupDestination.VisitorEntry
    }
}
