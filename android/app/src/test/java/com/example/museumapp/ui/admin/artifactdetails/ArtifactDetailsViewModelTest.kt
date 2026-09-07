package com.example.museumapp.ui.admin.artifactdetails

import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.Model3DBuildResponseDto
import com.example.museumapp.data.model.Model3DStateDto
import com.example.museumapp.data.model.Model3DStatus
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactDetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load3DStateRunsAfterTheArtifactLoadsSuccessfully() = runTest {
        val repository = FakeAdminRepository()

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        assertEquals(1, repository.get3DStateCalls)
        assertEquals(Model3DStatus.None, viewModel.uiState.value.model3D?.status)
        assertFalse(viewModel.uiState.value.model3DLoading)
    }

    @Test
    fun buildModelCallsRepositoryAndRefreshesStateUntilPollingSettles() = runTest {
        val repository = FakeAdminRepository()
        // get3DState always reports a job in progress; get3DStatus (polled) resolves it to
        // "ready" on its first tick, per FakeAdminRepository's terminal-by-default status result.
        repository.model3DStateResult = RepositoryResult.Success(
            Model3DStateDto(status = Model3DStatus.Queued, activeJobId = "job-9")
        )
        repository.build3DModelResult = RepositoryResult.Success(Model3DBuildResponseDto(jobId = "job-9", status = "queued"))

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.buildModel()
        advanceUntilIdle()

        assertEquals(1, repository.build3DModelCalls)
        assertEquals(Model3DStatus.Ready, viewModel.uiState.value.model3D?.status)
        assertFalse(viewModel.uiState.value.isPolling)
        assertFalse(viewModel.uiState.value.model3DBusy)
        assertNull(viewModel.uiState.value.model3DError)
    }

    @Test
    fun buildModelConflictErrorSurfacesAsUserVisibleMessage() = runTest {
        val repository = FakeAdminRepository()
        repository.build3DModelResult = RepositoryResult.Error("A reconstruction job is already running.")

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.buildModel()
        advanceUntilIdle()

        assertEquals(1, repository.build3DModelCalls)
        assertEquals("A reconstruction job is already running.", viewModel.uiState.value.model3DError)
        assertFalse(viewModel.uiState.value.model3DBusy)
    }

    @Test
    fun addImagesPassesReusePathsAndUrisToTheRepository() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.add3DImages(listOf("uploads/images/a.jpg"), emptyList())
        advanceUntilIdle()

        assertEquals("artifact-1", repository.lastAdd3DImagesArtifactId)
        assertEquals(listOf("uploads/images/a.jpg"), repository.lastAdd3DImagesReusePaths)
    }

    @Test
    fun confirmDeleteReconstructionResetsStateOnSuccess() = runTest {
        val repository = FakeAdminRepository()
        repository.delete3DReconstructionResult = RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.None))

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.requestDeleteReconstruction()
        assertEquals(true, viewModel.uiState.value.pendingDeleteReconstruction)

        viewModel.confirmDeleteReconstruction()
        advanceUntilIdle()

        assertEquals(1, repository.delete3DReconstructionCalls)
        assertEquals(false, viewModel.uiState.value.pendingDeleteReconstruction)
        assertFalse(viewModel.uiState.value.deletingReconstruction)
        assertEquals(Model3DStatus.None, viewModel.uiState.value.model3D?.status)
    }

    @Test
    fun getStatusErrorDuringPollingSurfacesAsModel3DError() = runTest {
        val repository = FakeAdminRepository()
        repository.model3DStateResult = RepositoryResult.Success(
            Model3DStateDto(status = Model3DStatus.Queued, activeJobId = "job-1")
        )
        repository.get3DStatusResult = RepositoryResult.Error("The server could not complete the request.")

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        assertEquals("The server could not complete the request.", viewModel.uiState.value.model3DError)
        assertFalse(viewModel.uiState.value.isPolling)
    }
}
