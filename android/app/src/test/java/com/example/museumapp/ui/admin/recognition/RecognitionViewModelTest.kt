package com.example.museumapp.ui.admin.recognition

import android.net.TestUri
import androidx.camera.core.ImageCapture
import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.ArtifactMatchDto
import com.example.museumapp.data.model.RecognizedArtifactDto
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cameraRecognitionStateTransitionsMoveFromReadyToCapturing() = runTest {
        val viewModel = RecognitionViewModel(FakeAdminRepository())
        advanceUntilIdle()

        viewModel.onCameraReady(hasFlashUnit = true)
        val captureStarted = viewModel.beginCameraCapture()

        assertTrue(captureStarted)
        assertEquals(RecognitionUiMode.Capturing, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.isRecognizing)
        assertTrue(viewModel.uiState.value.hasFlashUnit)
    }

    @Test
    fun duplicateCaptureRequestsArePrevented() = runTest {
        val viewModel = RecognitionViewModel(FakeAdminRepository())
        advanceUntilIdle()
        viewModel.onCameraReady(hasFlashUnit = false)

        assertTrue(viewModel.beginCameraCapture())
        assertFalse(viewModel.beginCameraCapture())
        assertEquals(RecognitionUiMode.Capturing, viewModel.uiState.value.mode)
    }

    @Test
    fun cameraReadyToCapturingKeepsStableScannerSurface() {
        assertTrue(keepsRecognitionCameraHost(RecognitionUiMode.CameraReady, RecognitionUiMode.Capturing))
        assertEquals(RecognitionSurface.Scanner, RecognitionUiMode.CameraReady.toRecognitionSurface())
        assertEquals(RecognitionSurface.Scanner, RecognitionUiMode.Capturing.toRecognitionSurface())
    }

    @Test
    fun capturingToProcessingKeepsStableScannerSurface() {
        assertTrue(keepsRecognitionCameraHost(RecognitionUiMode.Capturing, RecognitionUiMode.Processing))
        assertEquals(RecognitionSurface.Scanner, RecognitionUiMode.Processing.toRecognitionSurface())
    }

    @Test
    fun stableSurfaceMappingGroupsScannerResultAndFailureStates() {
        assertEquals(RecognitionSurface.Scanner, RecognitionUiMode.CameraInitializing.toRecognitionSurface())
        assertEquals(RecognitionSurface.Scanner, RecognitionUiMode.CameraReady.toRecognitionSurface())
        assertEquals(RecognitionSurface.Scanner, RecognitionUiMode.Capturing.toRecognitionSurface())
        assertEquals(RecognitionSurface.Scanner, RecognitionUiMode.Processing.toRecognitionSurface())
        assertEquals(RecognitionSurface.Result, RecognitionUiMode.Success.toRecognitionSurface())
        assertEquals(RecognitionSurface.Result, RecognitionUiMode.NoMatch.toRecognitionSurface())
        assertEquals(RecognitionSurface.Failure, RecognitionUiMode.Failure.toRecognitionSurface())
    }

    @Test
    fun executorStaysActiveUntilCameraHostDisposesIt() {
        val executor = Executors.newSingleThreadExecutor()

        assertTrue(executor.canAcceptCaptureWork())
        executor.shutdown()
        assertFalse(executor.canAcceptCaptureWork())
    }

    @Test
    fun imageCaptureFailureMessagesAreMappedToSafeUserText() {
        assertEquals("The camera image could not be saved. Please try again.", captureFailureUserMessage(ImageCapture.ERROR_FILE_IO))
        assertEquals(
            "The camera could not capture the image. Hold the phone steady and try again.",
            captureFailureUserMessage(ImageCapture.ERROR_CAPTURE_FAILED)
        )
        assertEquals("The camera stopped before capture completed. Please scan again.", captureFailureUserMessage(ImageCapture.ERROR_CAMERA_CLOSED))
        assertEquals("The camera is not ready. Please scan again.", captureFailureUserMessage(ImageCapture.ERROR_INVALID_CAMERA))
        assertEquals("Camera capture failed. Please try again.", captureFailureUserMessage(ImageCapture.ERROR_UNKNOWN))
    }

    @Test
    fun processingCapturedFileSuccessStoresResponseAndCleansTemporaryFile() = runTest {
        val captureFile = temporaryFolder.newFile("capture-success.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val repository = FakeAdminRepository().apply {
            recognitionResult = RepositoryResult.Success(successResponse())
        }
        val viewModel = RecognitionViewModel(repository)
        advanceUntilIdle()

        viewModel.onCameraReady(hasFlashUnit = false)
        assertTrue(viewModel.beginCameraCapture())
        viewModel.processCapturedFile(captureFile)
        advanceUntilIdle()

        assertEquals(RecognitionUiMode.Success, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.response!!.matched)
        assertEquals("best-id", viewModel.bestMatchArtifactId())
        assertEquals(captureFile.absolutePath, repository.recognizedFile?.absolutePath)
        assertEquals(captureFile.absolutePath, viewModel.uiState.value.cleanupFilePath)
        assertFalse(captureFile.exists())
    }

    @Test
    fun noMatchResponseUsesNoMatchState() = runTest {
        val captureFile = temporaryFolder.newFile("capture-no-match.jpg").apply { writeBytes(byteArrayOf(1)) }
        val repository = FakeAdminRepository().apply {
            recognitionResult = RepositoryResult.Success(
                RecognitionResponseDto(
                    matched = false,
                    matchLevel = "no_match",
                    bestMatch = null,
                    otherMatches = emptyList(),
                    message = "No reliable artifact match was found."
                )
            )
        }
        val viewModel = RecognitionViewModel(repository)
        advanceUntilIdle()

        viewModel.onCameraReady(hasFlashUnit = false)
        assertTrue(viewModel.beginCameraCapture())
        viewModel.processCapturedFile(captureFile)
        advanceUntilIdle()

        assertEquals(RecognitionUiMode.NoMatch, viewModel.uiState.value.mode)
        assertFalse(viewModel.uiState.value.response!!.matched)
        assertEquals("no_match", viewModel.uiState.value.response!!.matchLevel)
        assertFalse(captureFile.exists())
    }

    @Test
    fun recognitionFailureShowsSafeMessageAndCleansTemporaryFile() = runTest {
        val captureFile = temporaryFolder.newFile("capture-failure.jpg").apply { writeBytes(byteArrayOf(1)) }
        val repository = FakeAdminRepository().apply {
            recognitionResult = RepositoryResult.Error("Qdrant unavailable")
        }
        val viewModel = RecognitionViewModel(repository)
        advanceUntilIdle()

        viewModel.onCameraReady(hasFlashUnit = false)
        assertTrue(viewModel.beginCameraCapture())
        viewModel.processCapturedFile(captureFile)
        advanceUntilIdle()

        assertEquals(RecognitionUiMode.Failure, viewModel.uiState.value.mode)
        assertEquals("AI services are unavailable. Check System Status.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isRecognizing)
        assertFalse(captureFile.exists())
    }

    @Test
    fun scanAgainClearsResultAndReturnsToCameraReady() = runTest {
        val captureFile = temporaryFolder.newFile("capture-reset.jpg").apply { writeBytes(byteArrayOf(1)) }
        val repository = FakeAdminRepository().apply {
            recognitionResult = RepositoryResult.Success(successResponse())
        }
        val viewModel = RecognitionViewModel(repository)
        advanceUntilIdle()

        viewModel.onCameraReady(hasFlashUnit = false)
        assertTrue(viewModel.beginCameraCapture())
        viewModel.processCapturedFile(captureFile)
        advanceUntilIdle()
        viewModel.scanAgain()
        advanceUntilIdle()

        assertEquals(RecognitionUiMode.CameraReady, viewModel.uiState.value.mode)
        assertNull(viewModel.uiState.value.response)
        assertNull(viewModel.uiState.value.selectedImage)
        assertFalse(viewModel.uiState.value.isRecognizing)
    }

    @Test
    fun bestMatchArtifactIdNavigationUsesRecognitionResponseId() = runTest {
        val repository = FakeAdminRepository().apply {
            recognitionResult = RepositoryResult.Success(successResponse())
        }
        val viewModel = RecognitionViewModel(repository)
        advanceUntilIdle()
        val selected = TestUri("content://test/image")

        viewModel.selectImage(selected)
        viewModel.recognize()
        advanceUntilIdle()

        assertEquals("best-id", viewModel.bestMatchArtifactId())
        assertEquals(selected, repository.recognizedUri)
    }

    @Test
    fun alternativeMatchArtifactIdNavigationUsesSelectedAlternativeId() = runTest {
        val captureFile = temporaryFolder.newFile("capture-alt.jpg").apply { writeBytes(byteArrayOf(1)) }
        val repository = FakeAdminRepository().apply {
            recognitionResult = RepositoryResult.Success(
                successResponse(otherMatches = listOf(match(id = "alt-1", score = 0.76), match(id = "alt-2", score = 0.73)))
            )
        }
        val viewModel = RecognitionViewModel(repository)
        advanceUntilIdle()

        viewModel.onCameraReady(hasFlashUnit = false)
        assertTrue(viewModel.beginCameraCapture())
        viewModel.processCapturedFile(captureFile)
        advanceUntilIdle()

        assertEquals("alt-1", viewModel.alternativeMatchArtifactId(0))
        assertEquals("alt-2", viewModel.alternativeMatchArtifactId(1))
        assertNull(viewModel.alternativeMatchArtifactId(2))
    }

    @Test
    fun temporaryCaptureCleanupStateIsUpdatedAfterCaptureFailure() = runTest {
        val captureFile = temporaryFolder.newFile("capture-cleanup.jpg").apply { writeBytes(byteArrayOf(1)) }
        val viewModel = RecognitionViewModel(FakeAdminRepository())
        advanceUntilIdle()

        viewModel.onCameraReady(hasFlashUnit = false)
        assertTrue(viewModel.beginCameraCapture())
        viewModel.onCaptureFailed(file = captureFile)

        assertEquals(RecognitionUiMode.Failure, viewModel.uiState.value.mode)
        assertFalse(viewModel.uiState.value.isRecognizing)
        assertEquals(captureFile.absolutePath, viewModel.uiState.value.cleanupFilePath)
        assertFalse(captureFile.exists())
    }

    @Test
    fun zeroIndexedVectorsDisableRecognition() = runTest {
        val repository = FakeAdminRepository().apply {
            aiHealthResult = RepositoryResult.Success(readyHealth(indexedVectors = 0, openclip = "loaded"))
        }
        val viewModel = RecognitionViewModel(repository)
        advanceUntilIdle()

        viewModel.onCameraReady(hasFlashUnit = false)

        assertFalse(viewModel.uiState.value.canRecognize)
        assertEquals("No indexed artifact images are available.", viewModel.uiState.value.recognitionBlockedMessage)
        assertFalse(viewModel.beginCameraCapture())
        assertEquals(RecognitionUiMode.CameraReady, viewModel.uiState.value.mode)
    }

    private fun successResponse(otherMatches: List<ArtifactMatchDto> = emptyList()): RecognitionResponseDto {
        return RecognitionResponseDto(
            matched = true,
            matchLevel = "strong",
            bestMatch = match(id = "best-id", score = 0.91),
            otherMatches = otherMatches,
            message = "ok"
        )
    }

    private fun match(id: String, score: Double): ArtifactMatchDto {
        return ArtifactMatchDto(
            artifact = RecognizedArtifactDto(
                id = id,
                artifactCode = "ART-$id",
                name = "Artifact $id",
                description = "Artifact description",
                category = "Ceramics",
                origin = "Local",
                historicalPeriod = "19th century",
                material = "Clay",
                dimensions = "10 x 20 cm",
                condition = "Good",
                primaryImageUrl = null
            ),
            similarityScore = score,
            matchedImagePath = "uploads/images/$id.jpg",
            supportingImageHits = 1
        )
    }

    private fun readyHealth(indexedVectors: Int, openclip: String): AiHealthResponse {
        return AiHealthResponse(
            status = "healthy",
            aiEnabled = true,
            openclip = openclip,
            modelName = "ViT-B-32",
            pretrained = "laion2b_s34b_b79k",
            device = "cpu",
            embeddingDimension = 512,
            qdrant = "connected",
            collection = "artifact_images",
            collectionStatus = "ready",
            indexedVectors = indexedVectors
        )
    }
}
