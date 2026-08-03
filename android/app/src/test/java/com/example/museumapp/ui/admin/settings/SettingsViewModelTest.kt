package com.example.museumapp.ui.admin.settings

import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.UserDto
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.session.AdminSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun mapsSessionAndCurrentAccount() = runTest {
        val repository = FakeAdminRepository()

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        assertEquals("Museum Admin", viewModel.uiState.value.account.fullName)
        assertEquals("admin@example.com", viewModel.uiState.value.account.email)
        assertEquals("admin", viewModel.uiState.value.account.role)
        assertTrue(viewModel.uiState.value.account.signedIn)
        assertEquals("MA", accountInitials("Museum Admin"))
    }

    @Test
    fun sessionMappingHandlesLoggedOutState() {
        val account = AdminSession().toAccountUi()

        assertFalse(account.signedIn)
        assertEquals("Administrator", account.fullName)
    }

    @Test
    fun logoutClearsSessionThroughRepository() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.requestLogout()
        assertTrue(viewModel.uiState.value.showLogoutConfirmation)
        viewModel.confirmLogout()
        advanceUntilIdle()

        assertTrue(repository.logoutCalled)
        assertFalse(viewModel.uiState.value.showLogoutConfirmation)
    }

    @Test
    fun currentAdminCanOverrideStoredSessionAccount() = runTest {
        val repository = FakeAdminRepository().apply {
            currentAdminResult = RepositoryResult.Success(
                UserDto(
                    id = "2",
                    email = "director@example.com",
                    fullName = "Museum Director",
                    role = "admin"
                )
            )
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        assertEquals("Museum Director", viewModel.uiState.value.account.fullName)
        assertEquals("director@example.com", viewModel.uiState.value.account.email)
    }
}
