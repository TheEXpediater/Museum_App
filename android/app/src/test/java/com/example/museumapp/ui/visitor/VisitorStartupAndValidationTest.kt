package com.example.museumapp.ui.visitor

import com.example.museumapp.data.session.AdminSession
import com.example.museumapp.data.session.VisitorSession
import com.example.museumapp.ui.navigation.AdminRoutes
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorFormValidation
import com.example.museumapp.ui.visitor.entry.VisitorEntrySelections
import com.example.museumapp.ui.visitor.navigation.StartupDestination
import com.example.museumapp.ui.visitor.navigation.VisitorRoutes
import com.example.museumapp.ui.visitor.navigation.VisitorTopLevelDestinations
import com.example.museumapp.ui.visitor.navigation.resolveStartupDestination
import com.example.museumapp.ui.visitor.onboarding.VisitorOnboardingPages
import com.example.museumapp.ui.visitor.onboarding.isVisitorOnboardingLastPage
import com.example.museumapp.ui.visitor.onboarding.visitorOnboardingActionLabel
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
    fun completedOnboardingWithoutSessionStillRoutesToVisitorOnboarding() {
        assertEquals(
            StartupDestination.VisitorOnboarding,
            resolveStartupDestination(true, AdminSession(), VisitorSession())
        )
    }

    @Test
    fun unauthenticatedStartupAlwaysRoutesToOnboarding() {
        listOf(false, true).forEach { onboardingCompleted ->
            assertEquals(
                StartupDestination.VisitorOnboarding,
                resolveStartupDestination(onboardingCompleted, AdminSession(), VisitorSession())
            )
        }
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
    fun onboardingCompletionTargetIsVisitorEntry() {
        assertEquals("visitor_entry", VisitorRoutes.Entry)
    }

    @Test
    fun visitorRoutesPreserveGuestAndStudentAuthDestinations() {
        assertEquals("visitor_guest_info", VisitorRoutes.GuestInfo)
        assertEquals("visitor_student_login", VisitorRoutes.StudentLogin)
        assertEquals("visitor_student_register", VisitorRoutes.StudentRegister)
    }

    @Test
    fun visitorEntryUsesLayeredImageCardsWithAccessibleTargets() {
        assertEquals("file:///android_asset/visitor/background/campus-background.png", VisitorAssets.VisitorEntryBackground)
        assertEquals("file:///android_asset/visitor_entry/characters/char_guest_female.png", VisitorAssets.VisitorGuestCharacter)
        assertEquals("file:///android_asset/visitor_entry/characters/char_student_male.png", VisitorAssets.VisitorStudentCharacter)
        assertEquals("file:///android_asset/visitor_entry/icons/icon_guest.png", VisitorAssets.VisitorGuestIcon)
        assertEquals("file:///android_asset/visitor_entry/icons/icon_student.png", VisitorAssets.VisitorStudentIcon)
        assertEquals("file:///android_asset/visitor_entry/icons/icon_admin.png", VisitorAssets.VisitorAdminIcon)
        assertEquals(listOf("Guest", "Student"), VisitorEntrySelections.map { it.target })
        assertEquals(
            listOf("Sign in as Guest", "Sign in as Student"),
            VisitorEntrySelections.map { it.contentDescription }
        )
        assertEquals(listOf(VisitorAssets.VisitorGuestIcon, VisitorAssets.VisitorStudentIcon), VisitorEntrySelections.map { it.icon })
        assertEquals(
            listOf(VisitorAssets.VisitorGuestCharacter, VisitorAssets.VisitorStudentCharacter),
            VisitorEntrySelections.map { it.illustration }
        )
    }

    @Test
    fun administratorLoginRouteIsPreserved() {
        assertEquals("admin_login", AdminRoutes.Login)
    }

    @Test
    fun onboardingPagesUseApprovedAssetsAndCopy() {
        assertEquals(3, VisitorOnboardingPages.size)
        assertEquals(VisitorAssets.OnboardingWelcome, VisitorOnboardingPages[0].image)
        assertEquals("Welcome to PSAU Museum Guide", VisitorOnboardingPages[0].title)
        assertEquals("Explore the heritage, artifacts, and stories of the museum.", VisitorOnboardingPages[0].body)

        assertEquals(VisitorAssets.OnboardingExplore, VisitorOnboardingPages[1].image)
        assertEquals("Discover the Collection", VisitorOnboardingPages[1].title)
        assertEquals("Browse artifacts, historical information, facts, and museum stories.", VisitorOnboardingPages[1].body)

        assertEquals(VisitorAssets.OnboardingAiScan, VisitorOnboardingPages[2].image)
        assertEquals("Discover with AI", VisitorOnboardingPages[2].title)
        assertEquals("Scan an artifact and let the museum guide help identify it.", VisitorOnboardingPages[2].body)
    }

    @Test
    fun onboardingPrimaryActionBecomesGetStartedOnFinalPage() {
        assertFalse(isVisitorOnboardingLastPage(0))
        assertEquals("Next", visitorOnboardingActionLabel(0))
        assertEquals("Next", visitorOnboardingActionLabel(1))
        assertTrue(isVisitorOnboardingLastPage(2))
        assertEquals("Get Started", visitorOnboardingActionLabel(2))
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
    fun visitorNavigationDestinationsExposeVisitorScanAction() {
        assertEquals(listOf("Home", "Artifacts", "Scan", "Settings"), VisitorTopLevelDestinations.map { it.label })
        assertEquals(VisitorRoutes.Home, VisitorTopLevelDestinations[0].route)
        assertEquals(VisitorRoutes.Artifacts, VisitorTopLevelDestinations[1].route)
        assertEquals(VisitorRoutes.Scan, VisitorTopLevelDestinations[2].route)
        assertTrue(VisitorTopLevelDestinations[2].opensScanSheet)
        assertEquals(VisitorRoutes.Settings, VisitorTopLevelDestinations[3].route)
    }

    @Test
    fun assetUrisUseAndroidAssetsFolder() {
        assertEquals(31, VisitorAssets.RequiredAssets.size)
        assertTrue(VisitorAssets.RequiredAssets.contains(VisitorAssets.VisitorEntryBackground))
        assertTrue(VisitorAssets.RequiredAssets.contains(VisitorAssets.VisitorEntrySceneTop))
        assertTrue(VisitorAssets.RequiredAssets.contains(VisitorAssets.VisitorEntrySceneBottom))
        assertTrue(VisitorAssets.RequiredAssets.contains(VisitorAssets.VisitorGuestCharacter))
        assertTrue(VisitorAssets.RequiredAssets.contains(VisitorAssets.VisitorStudentCharacter))
        assertTrue(VisitorAssets.RequiredAssets.contains(VisitorAssets.VisitorGuestIcon))
        assertTrue(VisitorAssets.RequiredAssets.contains(VisitorAssets.VisitorStudentIcon))
        assertTrue(VisitorAssets.RequiredAssets.contains(VisitorAssets.VisitorAdminIcon))
        assertTrue(
            VisitorAssets.RequiredAssets.all {
                it.startsWith("file:///android_asset/visitor_ui/") ||
                    it.startsWith("file:///android_asset/visitor_entry/") ||
                    it.startsWith("file:///android_asset/visitor/")
            }
        )
        assertTrue(VisitorAssets.RequiredAssets.none { it.contains("/visitor_ui/entry/") })
        assertTrue(VisitorAssets.RequiredAssets.none { it.contains("visitor_images_source") })
    }

    @Test
    fun scanAssetReferencesUseApprovedAiScanIcon() {
        assertEquals("file:///android_asset/visitor_ui/icons/ai_scan_icon.webp", VisitorAssets.AiScanIcon)
        assertEquals(VisitorAssets.AiScanIcon, VisitorAssets.ScanIcon)
        assertTrue(VisitorAssets.RequiredAssets.contains(VisitorAssets.AiScanIcon))
        assertFalse(VisitorAssets.RequiredAssets.any { it.endsWith("/scan_icon.webp") })
    }
}
