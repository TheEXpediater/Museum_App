package com.example.museumapp.ui.admin.dashboard

import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.DashboardSummaryResponse
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsDashboardSummary() = runTest {
        val repository = FakeAdminRepository().apply {
            dashboardResult = RepositoryResult.Success(
                DashboardSummaryResponse(
                    totalArtifacts = 3,
                    totalImages = 4,
                    totalCategories = 2,
                    indexedArtifacts = 1,
                    pendingArtifacts = 1,
                    failedArtifacts = 1,
                    indexedVectors = 4,
                    aiStatus = "healthy",
                    databaseStatus = "connected",
                    uploadsStatus = "available"
                )
            )
        }

        val viewModel = DashboardViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(3, viewModel.uiState.value.summary!!.totalArtifacts)
        assertEquals("Museum Admin", viewModel.uiState.value.adminName)
    }

    @Test
    fun exposesDashboardError() = runTest {
        val repository = FakeAdminRepository().apply {
            dashboardResult = RepositoryResult.Error("Dashboard unavailable")
        }

        val viewModel = DashboardViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Dashboard unavailable", viewModel.uiState.value.errorMessage)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }
}
