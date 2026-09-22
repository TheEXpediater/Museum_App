package com.example.museumapp.ui.visitor.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.museumapp.data.api.NetworkModule
import com.example.museumapp.data.network.BackendConnectionManager
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.data.session.SessionManager
import com.example.museumapp.ui.admin.login.AdminLoginDialogTestTags
import com.example.museumapp.ui.visitor.navigation.VisitorRoutes
import com.example.museumapp.ui.visitor.theme.VisitorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisitorEntryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun visitorEntryDisplaysGuestStudentAndAdministratorAccess() {
        setEntryContent()

        composeRule.onNodeWithText("Guest").assertIsDisplayed()
        composeRule.onNodeWithText("Student").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sign in as Guest").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Sign in as Student").assertHasClickAction()
        composeRule.onNodeWithTag(VisitorEntryTestTags.AdminAccess).assertHasClickAction()
        composeRule.onNodeWithContentDescription("Administrator access").assertIsDisplayed()
    }

    @Test
    fun largeAdministratorLoginButtonIsNoLongerPresent() {
        setEntryContent()

        composeRule.onNodeWithText("Administrator Login").assertDoesNotExist()
    }

    @Test
    fun guestActionUsesExistingGuestRoute() {
        var route: String? = null
        setEntryContent(onGuest = { route = VisitorRoutes.GuestInfo })

        composeRule.onNodeWithTag(VisitorEntryTestTags.GuestCard).performClick()

        composeRule.runOnIdle {
            assertEquals(VisitorRoutes.GuestInfo, route)
        }
    }

    @Test
    fun studentLoginActionUsesExistingStudentLoginRoute() {
        var route: String? = null
        setEntryContent(onStudentLogin = { route = VisitorRoutes.StudentLogin })

        composeRule.onNodeWithTag(VisitorEntryTestTags.StudentCard).performClick()
        composeRule.onNodeWithText("Student Access").assertIsDisplayed()
        composeRule.onNodeWithTag(VisitorEntryTestTags.StudentLogin).performClick()

        composeRule.runOnIdle {
            assertEquals(VisitorRoutes.StudentLogin, route)
        }
    }

    @Test
    fun studentRegistrationCapabilityRemainsAvailable() {
        var route: String? = null
        setEntryContent(onStudentRegister = { route = VisitorRoutes.StudentRegister })

        composeRule.onNodeWithTag(VisitorEntryTestTags.StudentCard).performClick()
        composeRule.onNodeWithText("Create Student Account").assertIsDisplayed()
        composeRule.onNodeWithTag(VisitorEntryTestTags.StudentRegister).performClick()

        composeRule.runOnIdle {
            assertEquals(VisitorRoutes.StudentRegister, route)
        }
    }

    @Test
    fun tappingAdministratorAccessOpensLoginModal() {
        setEntryContent()

        composeRule.onNodeWithTag(VisitorEntryTestTags.AdminAccess).performClick()

        composeRule.onNodeWithTag(AdminLoginDialogTestTags.Root).assertIsDisplayed()
        composeRule.onNodeWithTag(AdminLoginDialogTestTags.Email).assertIsDisplayed()
        composeRule.onNodeWithTag(AdminLoginDialogTestTags.Password).assertIsDisplayed()
        composeRule.onNodeWithTag(AdminLoginDialogTestTags.Submit).assertIsDisplayed()
    }

    @Test
    fun cancelClosesAdministratorLoginModalAndLeavesEntryScreenInPlace() {
        var adminLoginSuccessCalls = 0
        setEntryContent(onAdminLogin = { adminLoginSuccessCalls += 1 })

        composeRule.onNodeWithTag(VisitorEntryTestTags.AdminAccess).performClick()
        composeRule.onNodeWithTag(AdminLoginDialogTestTags.Root).assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag(AdminLoginDialogTestTags.Root).assertDoesNotExist()
        composeRule.onNodeWithTag(VisitorEntryTestTags.GuestCard).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, adminLoginSuccessCalls)
        }
    }

    @Test
    fun characterImageIsNotRequiredAsTheClickTarget() {
        var route: String? = null
        setEntryContent(onGuest = { route = VisitorRoutes.GuestInfo })

        composeRule.onNodeWithTag(VisitorEntryTestTags.GuestCharacter, useUnmergedTree = true)
            .assertHasNoClickAction()
        composeRule.onNodeWithText("Guest").performClick()

        composeRule.runOnIdle {
            assertEquals(VisitorRoutes.GuestInfo, route)
        }
    }

    @Test
    fun guestAndStudentCardsAreIndependentlyClickable() {
        var guestClicks = 0
        setEntryContent(onGuest = { guestClicks += 1 })

        composeRule.onNodeWithTag(VisitorEntryTestTags.GuestCard).performClick()
        composeRule.runOnIdle {
            assertEquals(1, guestClicks)
        }

        composeRule.onNodeWithTag(VisitorEntryTestTags.StudentCard).performClick()
        composeRule.onNodeWithText("Student Access").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, guestClicks)
        }
    }

    @Test
    fun requestedNarrowWidthsKeepActionsUsable() {
        listOf(320.dp, 360.dp, 411.dp).forEach { width ->
            setEntryContent(width = width, height = 640.dp)

            composeRule.onNodeWithTag(VisitorEntryTestTags.GuestCard).assertIsDisplayed()
            composeRule.onNodeWithTag(VisitorEntryTestTags.StudentCard).assertIsDisplayed()
            composeRule.onNodeWithTag(VisitorEntryTestTags.AdminAccess).assertIsDisplayed()
        }
    }

    @Test
    fun fontScaleOnePointThreeKeepsActionsUsable() {
        setEntryContent(width = 360.dp, height = 640.dp, fontScale = 1.3f)

        composeRule.onNodeWithText("Guest").assertIsDisplayed()
        composeRule.onNodeWithText("Student").assertIsDisplayed()
        composeRule.onNodeWithTag(VisitorEntryTestTags.AdminAccess).assertHasClickAction()
    }

    @Test
    fun studentCardDoesNotBypassExistingStudentAuthChoice() {
        var loginRoute: String? = null
        setEntryContent(onStudentLogin = { loginRoute = VisitorRoutes.StudentLogin })

        composeRule.onNodeWithTag(VisitorEntryTestTags.StudentCard).performClick()

        composeRule.onNodeWithText("Student Access").assertIsDisplayed()
        composeRule.onNodeWithText("Sign In").assertIsDisplayed()
        composeRule.onNodeWithText("Create Student Account").assertIsDisplayed()
        composeRule.runOnIdle {
            assertNull(loginRoute)
        }
    }

    private fun setEntryContent(
        width: Dp = 411.dp,
        height: Dp = 780.dp,
        fontScale: Float = 1f,
        onGuest: () -> Unit = {},
        onStudentLogin: () -> Unit = {},
        onStudentRegister: () -> Unit = {},
        onAdminLogin: () -> Unit = {}
    ) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sessionManager = SessionManager(context)
        val backendConnectionManager = BackendConnectionManager(context)
        val adminRepository = AdminRepository(
            NetworkModule.create(sessionManager, backendConnectionManager),
            sessionManager,
            context,
            backendConnectionManager
        )

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                VisitorTheme {
                    Box(Modifier.requiredSize(width = width, height = height)) {
                        VisitorEntryScreen(
                            adminRepository = adminRepository,
                            onGuest = onGuest,
                            onStudentLogin = onStudentLogin,
                            onStudentRegister = onStudentRegister,
                            onAdminLogin = onAdminLogin
                        )
                    }
                }
            }
        }
    }
}
