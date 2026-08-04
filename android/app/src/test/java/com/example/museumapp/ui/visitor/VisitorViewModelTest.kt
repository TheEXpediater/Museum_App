package com.example.museumapp.ui.visitor

import com.example.museumapp.FakeVisitorRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.PublicHomeResponseDto
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.ui.visitor.home.VisitorHomeViewModel
import com.example.museumapp.ui.visitor.scan.VisitorScanViewModel
import com.example.museumapp.ui.visitor.settings.VisitorSettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisitorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun homeViewModelLoadsHomeContent() = runTest {
        val viewModel = VisitorHomeViewModel(FakeVisitorRepository())
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("News", viewModel.uiState.value.home!!.latestNews.first().title)
        assertEquals("Maria Santos", viewModel.uiState.value.session.displayName)
    }

    @Test
    fun homeViewModelKeepsEmptyStateAsContent() = runTest {
        val repository = FakeVisitorRepository().apply {
            homeResult = RepositoryResult.Success(PublicHomeResponseDto())
        }
        val viewModel = VisitorHomeViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.home!!.latestNews.isEmpty())
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun scanReadinessRequiresBackendAndIndexedArtifacts() = runTest {
        val viewModel = VisitorScanViewModel(FakeVisitorRepository())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canOpenCamera)
        assertEquals(5, viewModel.uiState.value.indexedArtifacts)
    }

    @Test
    fun scanReadinessBlocksWhenNoIndexedArtifacts() = runTest {
        val repository = FakeVisitorRepository().apply {
            aiHealthResult = RepositoryResult.Success(
                AiHealthResponse(
                    status = "healthy",
                    aiEnabled = true,
                    openclip = "loaded",
                    modelName = "ViT-B-32",
                    pretrained = "laion2b_s34b_b79k",
                    device = "cpu",
                    embeddingDimension = 512,
                    qdrant = "connected",
                    collection = "artifact_images",
                    collectionStatus = "ready",
                    indexedVectors = 0
                )
            )
        }
        val viewModel = VisitorScanViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canOpenCamera)
        assertEquals("Artifact scanning is temporarily unavailable.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun settingsLogoutClearsVisitorSession() = runTest {
        val repository = FakeVisitorRepository()
        val viewModel = VisitorSettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertTrue(repository.logoutCalled)
        assertTrue(viewModel.uiState.value.isLoggedOut)
    }
}
