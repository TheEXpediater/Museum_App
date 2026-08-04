package com.example.museumapp.ui.visitor

import com.example.museumapp.data.session.AdminSession
import com.example.museumapp.data.session.VisitorSession
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorFormValidation
import com.example.museumapp.ui.visitor.navigation.StartupDestination
import com.example.museumapp.ui.visitor.navigation.VisitorRoutes
import com.example.museumapp.ui.visitor.navigation.VisitorTopLevelDestinations
import com.example.museumapp.ui.visitor.navigation.resolveStartupDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitorStartupAndValidationTest {
    @Test
    fun freshInstallRoutesToVisitorOnboarding() {
        assertEquals(
            StartupDestination.VisitorOnboarding,
            resolveStartupDestination(false, AdminSession(), VisitorSession())
        )
    }

    @Test
    fun completedOnboardingWithoutSessionRoutesToVisitorEntry() {
        assertEquals(
            StartupDestination.VisitorEntry,
            resolveStartupDestination(true, AdminSession(), VisitorSession())
        )
    }

    @Test
    fun guestAndStudentSessionsRouteToVisitorHome() {
        assertEquals(
            StartupDestination.VisitorHome,
            resolveStartupDestination(true, AdminSession(), VisitorSession(accessToken = "token", accountType = "guest"))
        )
        assertEquals(
            StartupDestination.VisitorHome,
            resolveStartupDestination(true, AdminSession(), VisitorSession(accessToken = "token", accountType = "student"))
        )
    }

    @Test
    fun adminSessionRoutesToAdministratorApp() {
        assertEquals(
            StartupDestination.Admin,
            resolveStartupDestination(false, AdminSession(accessToken = "token", role = "admin"), VisitorSession(accessToken = "token", accountType = "guest"))
        )
    }

    @Test
    fun guestValidationRequiresConditionalOtherDetailAndRejectsCurrentStudent() {
        assertTrue(VisitorFormValidation.guestErrors("Maria", "Santos", "General Visitor", "").isEmpty())
        assertEquals(
            "Please specify your relationship.",
            VisitorFormValidation.guestErrors("Maria", "Santos", "Other", "")["otherDetail"]
        )
        assertFalse(VisitorFormValidation.GuestRelationships.contains("Current Student"))
        assertEquals(
            "Select a valid PSAU relationship.",
            VisitorFormValidation.guestErrors("Maria", "Santos", "Current Student", "")["relationship"]
        )
    }

    @Test
    fun studentRegistrationValidationRequiresPasswordShape() {
        val errors = VisitorFormValidation.studentRegistrationErrors(
            studentId = "psau-1",
            firstName = "Juan",
            middleInitial = "D",
            lastName = "Reyes",
            yearLevel = "Third Year",
            course = "Agriculture",
            email = "juan@example.com",
            password = "studentabc",
            confirmPassword = "studentabc"
        )
        assertEquals("Add one uppercase letter.", errors["password"])
    }

    @Test
    fun visitorNavigationDestinationsKeepCenterScanOutOfTopLevelItems() {
        assertEquals(listOf("Home", "Artifacts", "Settings"), VisitorTopLevelDestinations.map { it.label })
        assertEquals(VisitorRoutes.Home, VisitorTopLevelDestinations[0].route)
        assertEquals(VisitorRoutes.Artifacts, VisitorTopLevelDestinations[1].route)
        assertEquals(VisitorRoutes.Settings, VisitorTopLevelDestinations[2].route)
    }

    @Test
    fun assetUrisUseAndroidAssetsFolder() {
        assertEquals(10, VisitorAssets.RequiredAssets.size)
        assertTrue(VisitorAssets.RequiredAssets.all { it.startsWith("file:///android_asset/visitor_ui/") })
        assertTrue(VisitorAssets.RequiredAssets.none { it.contains("visitor_images_source") })
    }
}
