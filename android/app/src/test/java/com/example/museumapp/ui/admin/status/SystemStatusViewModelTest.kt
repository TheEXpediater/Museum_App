package com.example.museumapp.ui.admin.status

import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.AiWarmupResponse
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.session.AdminSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemStatusViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun warmupPollingStopsAfterSuccess() = runTest {
        val repository = FakeAdminRepository().apply {
            warmupResult = RepositoryResult.Success(warmup("loading"))
            warmupStatusResults.add(RepositoryResult.Success(warmup("loaded")))
        }
        val viewModel = SystemStatusViewModel(repository)
        advanceUntilIdle()

        viewModel.loadAiModel()
        runCurrent()
        assertTrue(viewModel.uiState.value.isPollingWarmup)

        advanceTimeBy(2_000)
        runCurrent()
        advanceUntilIdle()

        assertEquals(1, repository.warmupCalls)
        assertEquals(1, repository.warmupStatusCalls)
        assertEquals("loaded", viewModel.uiState.value.warmupStatus!!.state)
        assertFalse(viewModel.uiState.value.isPollingWarmup)
        repository.sessionState.value = AdminSession()
        advanceUntilIdle()
    }

    @Test
    fun warmupPollingStopsAfterFailure() = runTest {
        val repository = FakeAdminRepository().apply {
            warmupResult = RepositoryResult.Success(warmup("loading"))
            warmupStatusResults.add(RepositoryResult.Success(warmup("failed", error = "OpenCLIP failed")))
        }
        val viewModel = SystemStatusViewModel(repository)
        advanceUntilIdle()

        viewModel.loadAiModel()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals("failed", viewModel.uiState.value.warmupStatus!!.state)
        assertEquals("OpenCLIP failed", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isPollingWarmup)
        repository.sessionState.value = AdminSession()
        advanceUntilIdle()
    }

    @Test
    fun warmupPollingStopsOnLogout() = runTest {
        val repository = FakeAdminRepository().apply {
            warmupResult = RepositoryResult.Success(warmup("loading"))
            warmupStatusResults.add(RepositoryResult.Success(warmup("loaded")))
        }
        val viewModel = SystemStatusViewModel(repository)
        advanceUntilIdle()

        viewModel.loadAiModel()
        runCurrent()
        repository.sessionState.value = AdminSession()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(0, repository.warmupStatusCalls)
        assertFalse(viewModel.uiState.value.isPollingWarmup)
    }

    @Test
    fun duplicateWarmupRequestDoesNotStartAnotherPollingJob() = runTest {
        val repository = FakeAdminRepository().apply {
            warmupResult = RepositoryResult.Success(warmup("loading"))
        }
        val viewModel = SystemStatusViewModel(repository)
        advanceUntilIdle()

        viewModel.loadAiModel()
        viewModel.loadAiModel()
        runCurrent()

        assertEquals(1, repository.warmupCalls)
        assertTrue(viewModel.uiState.value.isPollingWarmup)
        repository.sessionState.value = AdminSession()
        runCurrent()
    }

    @Test
    fun rebuildRequiresConfirmationAndRefreshesAfterCompletion() = runTest {
        val repository = FakeAdminRepository().apply {
            rebuildResult = RepositoryResult.Success(
                com.example.museumapp.data.model.AiIndexAllResponse(
                    totalArtifacts = 1,
                    totalImages = 1,
                    indexedImages = 1,
                    failedImages = 0,
                    skippedImages = 0,
                    duration = 0.001
                )
            )
        }
        val viewModel = SystemStatusViewModel(repository)
        advanceUntilIdle()

        viewModel.requestRebuildIndex()
        assertTrue(viewModel.uiState.value.confirmRebuildIndex)
        viewModel.confirmRebuildIndex()
        advanceUntilIdle()

        assertEquals(1, repository.rebuildCalls)
        assertFalse(viewModel.uiState.value.confirmRebuildIndex)
        assertEquals("Indexed 1 image(s); 0 failed; 0 skipped.", viewModel.uiState.value.actionMessage)
    }

    private fun warmup(state: String, error: String? = null): AiWarmupResponse {
        return AiWarmupResponse(
            state = state,
            message = when (state) {
                "loaded" -> "OpenCLIP is ready."
                "failed" -> error ?: "OpenCLIP failed"
                else -> "OpenCLIP is loading."
            },
            modelName = "ViT-B-32",
            pretrained = "laion2b_s34b_b79k",
            device = if (state == "loaded") "cpu" else "auto",
            embeddingDimension = if (state == "loaded") 512 else null,
            error = error
        )
    }
}
