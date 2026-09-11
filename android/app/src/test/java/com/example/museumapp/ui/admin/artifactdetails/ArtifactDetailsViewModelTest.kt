package com.example.museumapp.ui.admin.artifactdetails

import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.Model3DBuildResponseDto
import com.example.museumapp.data.model.Model3DImageDto
import com.example.museumapp.data.model.Model3DStateDto
import com.example.museumapp.data.model.Model3DStatus
import com.example.museumapp.data.model.Model3DStatusResponseDto
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun buildAiModelCallsRepositoryWithSelectedImageIdsAndRefreshesState() = runTest {
        val repository = FakeAdminRepository()
        repository.model3DStateResult = RepositoryResult.Success(
            Model3DStateDto(status = Model3DStatus.AiQueued, activeJobId = "ai-job-1")
        )
        repository.buildAi3DModelResult = RepositoryResult.Success(Model3DBuildResponseDto(jobId = "ai-job-1", status = "ai_queued"))

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.buildAiModel(listOf("img-1", "img-2"))
        advanceUntilIdle()

        assertEquals(1, repository.buildAi3DModelCalls)
        assertEquals(listOf("img-1", "img-2"), repository.lastBuildAi3DModelImageIds)
        assertEquals(Model3DStatus.Ready, viewModel.uiState.value.model3D?.status)
        assertFalse(viewModel.uiState.value.model3DBusy)
        assertNull(viewModel.uiState.value.model3DError)
    }

    @Test
    fun buildAiModelForwardsVisibleRegionsToTheRepository() = runTest {
        val repository = FakeAdminRepository()
        repository.buildAi3DModelResult = RepositoryResult.Success(Model3DBuildResponseDto(jobId = "ai-job-1", status = "ai_queued"))

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.buildAiModel(listOf("img-1"), listOf("front", "right", "top"))
        advanceUntilIdle()

        assertEquals(listOf("front", "right", "top"), repository.lastBuildAi3DModelVisibleRegions)
    }

    @Test
    fun buildAiModelWithoutRegionsForwardsEmptyList() = runTest {
        val repository = FakeAdminRepository()
        repository.buildAi3DModelResult = RepositoryResult.Success(Model3DBuildResponseDto(jobId = "ai-job-1", status = "ai_queued"))

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.buildAiModel(listOf("img-1"))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.lastBuildAi3DModelVisibleRegions)
    }

    @Test
    fun buildAiModelWithNoSelectionDoesNotCallRepository() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.buildAiModel(emptyList())
        advanceUntilIdle()

        assertEquals(0, repository.buildAi3DModelCalls)
    }

    @Test
    fun buildAiModelUnavailableErrorSurfacesAsUserVisibleMessage() = runTest {
        val repository = FakeAdminRepository()
        repository.buildAi3DModelResult = RepositoryResult.Error("AI 3D preview is not available.")

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.buildAiModel(listOf("img-1"))
        advanceUntilIdle()

        assertEquals(1, repository.buildAi3DModelCalls)
        assertEquals("AI 3D preview is not available.", viewModel.uiState.value.model3DError)
        assertFalse(viewModel.uiState.value.model3DBusy)
    }

    @Test
    fun acceptModelCallsRepositoryAndAdoptsPublishedState() = runTest {
        val repository = FakeAdminRepository()
        repository.model3DStateResult = RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.PendingReview, draftVersion = 1))
        repository.accept3DModelResult = RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.Ready, version = 1))

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.acceptModel()
        advanceUntilIdle()

        assertEquals(1, repository.accept3DModelCalls)
        assertEquals(Model3DStatus.Ready, viewModel.uiState.value.model3D?.status)
        assertFalse(viewModel.uiState.value.model3DBusy)
        assertNull(viewModel.uiState.value.model3DError)
    }

    @Test
    fun rejectModelCallsRepositoryAndDiscardsDraft() = runTest {
        val repository = FakeAdminRepository()
        repository.model3DStateResult = RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.PendingReview, draftVersion = 1))
        repository.reject3DModelResult = RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.NeedsImages))

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.rejectModel()
        advanceUntilIdle()

        assertEquals(1, repository.reject3DModelCalls)
        assertEquals(Model3DStatus.NeedsImages, viewModel.uiState.value.model3D?.status)
        assertNull(viewModel.uiState.value.model3D?.draftVersion)
        assertFalse(viewModel.uiState.value.model3DBusy)
    }

    @Test
    fun rejectModelConflictErrorSurfacesAsUserVisibleMessage() = runTest {
        val repository = FakeAdminRepository()
        repository.reject3DModelResult = RepositoryResult.Error("No pending 3D preview to reject.")

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.rejectModel()
        advanceUntilIdle()

        assertEquals(1, repository.reject3DModelCalls)
        assertEquals("No pending 3D preview to reject.", viewModel.uiState.value.model3DError)
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

    // --- createOrUpdate3DPreview: the unified "Create 3D Preview" flow ----------------------

    private fun reconstructionImage(id: String, filename: String) =
        Model3DImageDto(id = id, origin = "reused", originalFilename = filename)

    @Test
    fun createOrUpdate3DPreviewAddsImagesThenStartsAiGenerationInOneCall() = runTest {
        val repository = FakeAdminRepository()
        repository.add3DImagesResult = RepositoryResult.Success(
            Model3DStateDto(status = Model3DStatus.NeedsImages, aiMaxImages = 1, images = listOf(reconstructionImage("img-1", "a.jpg")))
        )
        repository.buildAi3DModelResult = RepositoryResult.Success(Model3DBuildResponseDto(jobId = "ai-job-1", status = "ai_queued"))
        repository.model3DStateResult = RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.AiQueued, activeJobId = "ai-job-1"))

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.createOrUpdate3DPreview(listOf("uploads/images/a.jpg"))
        advanceUntilIdle()

        assertEquals(1, repository.add3DImagesCalls)
        assertEquals(1, repository.buildAi3DModelCalls)
        assertEquals(listOf("img-1"), repository.lastBuildAi3DModelImageIds)
        assertFalse(viewModel.uiState.value.model3DBusy)
        assertNull(viewModel.uiState.value.model3DSubmitStage)
        assertNull(viewModel.uiState.value.model3DError)
    }

    @Test
    fun createOrUpdate3DPreviewCapsSelectionToProviderMaxKeepingSelectionOrder() = runTest {
        val repository = FakeAdminRepository()
        repository.add3DImagesResult = RepositoryResult.Success(
            Model3DStateDto(
                status = Model3DStatus.NeedsImages,
                aiMaxImages = 1,
                images = listOf(reconstructionImage("img-1", "a.jpg"), reconstructionImage("img-2", "b.jpg"))
            )
        )
        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.createOrUpdate3DPreview(listOf("uploads/images/b.jpg", "uploads/images/a.jpg"))
        advanceUntilIdle()

        assertEquals(listOf("img-2"), repository.lastBuildAi3DModelImageIds)
    }

    @Test
    fun createOrUpdate3DPreviewDoesNothingWhenAJobIsAlreadyActive() = runTest {
        val repository = FakeAdminRepository()
        val activeState = Model3DStateDto(status = Model3DStatus.AiGenerating, activeJobId = "ai-job-1")
        repository.model3DStateResult = RepositoryResult.Success(activeState)
        // The poll tick errors out (stops polling) WITHOUT touching model3D, so it stays active -
        // unlike the fake's terminal-by-default get3DStatusResult, which would settle it to
        // "ready" on the very first tick, and unlike leaving it perpetually active, which would
        // never stop polling and hang advanceUntilIdle() forever.
        repository.get3DStatusResult = RepositoryResult.Error("stub: polling stops here in this test")

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()
        assertEquals(Model3DStatus.AiGenerating, viewModel.uiState.value.model3D?.status)
        assertFalse(viewModel.uiState.value.isPolling)

        viewModel.createOrUpdate3DPreview(listOf("uploads/images/a.jpg"))
        advanceUntilIdle()

        assertEquals(0, repository.add3DImagesCalls)
        assertEquals(0, repository.buildAi3DModelCalls)
    }

    @Test
    fun timeoutOnAddImagesRecoversByCheckingRealStateInsteadOfFailing() = runTest {
        val repository = FakeAdminRepository()
        repository.add3DImagesResult = RepositoryResult.Error("Read timeout", recoverable = true)
        // A terminal poll result so that once recovery resumes polling, the flow actually settles
        // within advanceUntilIdle() instead of spinning forever.
        repository.get3DStatusResult = RepositoryResult.Success(
            Model3DStatusResponseDto(state = Model3DStateDto(status = Model3DStatus.PendingReview))
        )

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle() // the ViewModel's OWN initial load3DState() consumes one get3DState call here

        // Only NOW queue the recovery-specific response, so it is used by
        // createOrUpdate3DPreview's reconciliation call, not the initial load above.
        repository.get3DStateResults.add(
            RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.AiGenerating, activeJobId = "ai-job-1"))
        )

        viewModel.createOrUpdate3DPreview(listOf("uploads/images/a.jpg"))
        advanceUntilIdle()

        assertEquals(1, repository.add3DImagesCalls)
        assertEquals(0, repository.buildAi3DModelCalls) // never double-submits a second job
        assertNull(viewModel.uiState.value.model3DError)
        assertFalse(viewModel.uiState.value.model3DBusy)
    }

    @Test
    fun nonRecoverableAddImagesFailureShowsARealError() = runTest {
        val repository = FakeAdminRepository()
        repository.add3DImagesResult = RepositoryResult.Error("Selected image does not belong to this artifact.")

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle()

        viewModel.createOrUpdate3DPreview(listOf("uploads/images/a.jpg"))
        advanceUntilIdle()

        assertEquals(0, repository.buildAi3DModelCalls)
        assertEquals("Selected image does not belong to this artifact.", viewModel.uiState.value.model3DError)
        assertFalse(viewModel.uiState.value.model3DBusy)
    }

    @Test
    fun timeoutOnBuildAiRecoversWhenTheJobActuallyStarted() = runTest {
        val repository = FakeAdminRepository()
        repository.add3DImagesResult = RepositoryResult.Success(
            Model3DStateDto(status = Model3DStatus.NeedsImages, aiMaxImages = 1, images = listOf(reconstructionImage("img-1", "a.jpg")))
        )
        repository.buildAi3DModelResult = RepositoryResult.Error("Read timeout", recoverable = true)
        repository.get3DStatusResult = RepositoryResult.Success(
            Model3DStatusResponseDto(state = Model3DStateDto(status = Model3DStatus.PendingReview))
        )

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle() // consumes the initial load3DState() get3DState call

        // The recovery get3DState() call proves the POST actually created the job server-side.
        repository.get3DStateResults.add(
            RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.AiQueued, activeJobId = "ai-job-1"))
        )

        viewModel.createOrUpdate3DPreview(listOf("uploads/images/a.jpg"))
        advanceUntilIdle()

        assertEquals(1, repository.buildAi3DModelCalls) // exactly one attempt, no blind retry
        assertNull(viewModel.uiState.value.model3DError)
        assertFalse(viewModel.uiState.value.model3DBusy)
    }

    @Test
    fun timeoutOnBuildAiWithNoConfirmedJobShowsATimeoutSpecificMessage() = runTest {
        val repository = FakeAdminRepository()
        repository.add3DImagesResult = RepositoryResult.Success(
            Model3DStateDto(status = Model3DStatus.NeedsImages, aiMaxImages = 1, images = listOf(reconstructionImage("img-1", "a.jpg")))
        )
        repository.buildAi3DModelResult = RepositoryResult.Error("Read timeout", recoverable = true)

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle() // consumes the initial load3DState() get3DState call

        // Recovery shows no job actually started - a genuine failure, not just a slow response.
        repository.get3DStateResults.add(
            RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.NeedsImages))
        )

        viewModel.createOrUpdate3DPreview(listOf("uploads/images/a.jpg"))
        advanceUntilIdle()

        assertEquals(1, repository.buildAi3DModelCalls)
        assertNotNull(viewModel.uiState.value.model3DError)
        assertTrue(viewModel.uiState.value.model3DError!!.contains("timed out", ignoreCase = true))
        assertFalse(viewModel.uiState.value.model3DBusy)
    }

    @Test
    fun conflictOnBuildAiRecoversInsteadOfShowingAGenericFailure() = runTest {
        val repository = FakeAdminRepository()
        repository.add3DImagesResult = RepositoryResult.Success(
            Model3DStateDto(status = Model3DStatus.NeedsImages, aiMaxImages = 1, images = listOf(reconstructionImage("img-1", "a.jpg")))
        )
        // A 409 "already running" is also recoverable - something (very possibly our own prior
        // request) is already in flight; reconcile instead of showing a hard error.
        repository.buildAi3DModelResult = RepositoryResult.Error("A reconstruction job is already running.", recoverable = true)
        repository.get3DStatusResult = RepositoryResult.Success(
            Model3DStatusResponseDto(state = Model3DStateDto(status = Model3DStatus.PendingReview))
        )

        val viewModel = ArtifactDetailsViewModel(repository, "artifact-1")
        advanceUntilIdle() // consumes the initial load3DState() get3DState call

        repository.get3DStateResults.add(
            RepositoryResult.Success(Model3DStateDto(status = Model3DStatus.AiGenerating, activeJobId = "ai-job-1"))
        )

        viewModel.createOrUpdate3DPreview(listOf("uploads/images/a.jpg"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.model3DError)
        assertEquals(1, repository.buildAi3DModelCalls)
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
