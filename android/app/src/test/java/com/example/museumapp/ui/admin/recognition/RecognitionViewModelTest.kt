package com.example.museumapp.ui.admin.recognition

import android.net.Uri
import android.net.TestUri
import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.ArtifactMatchDto
import com.example.museumapp.data.model.RecognizedArtifactDto
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun recognitionSuccessStoresResponse() = runTest {
        val selected = TestUri("content://test/image")
        val repository = FakeAdminRepository().apply {
            recognitionResult = RepositoryResult.Success(
                RecognitionResponseDto(
                    matched = true,
                    matchLevel = "strong",
                    bestMatch = ArtifactMatchDto(
                        artifact = RecognizedArtifactDto(
                            id = "1",
                            artifactCode = "ART-1",
                            name = "Jar",
                            description = "Clay jar",
                            category = "Ceramics",
                            origin = null,
                            historicalPeriod = null,
                            material = "Clay",
                            dimensions = null,
                            condition = "Good",
                            primaryImageUrl = null
                        ),
                        similarityScore = 0.91,
                        matchedImagePath = "uploads/images/jar.jpg",
                        supportingImageHits = 1
                    ),
                    otherMatches = emptyList(),
                    message = "ok"
                )
            )
        }

        val viewModel = RecognitionViewModel(repository)
        viewModel.selectImage(selected)
        viewModel.recognize()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecognizing)
        assertTrue(viewModel.uiState.value.response!!.matched)
        assertEquals(selected, repository.recognizedUri)
    }

    @Test
    fun noMatchResponseIsStored() = runTest {
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
        viewModel.selectImage(TestUri("content://test/image"))
        viewModel.recognize()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.response!!.matched)
        assertEquals("no_match", viewModel.uiState.value.response!!.matchLevel)
    }

    @Test
    fun recognitionFailureShowsError() = runTest {
        val repository = FakeAdminRepository().apply {
            recognitionResult = RepositoryResult.Error("AI unavailable")
        }

        val viewModel = RecognitionViewModel(repository)
        viewModel.selectImage(TestUri("content://test/image"))
        viewModel.recognize()
        advanceUntilIdle()

        assertEquals("AI unavailable", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isRecognizing)
    }
}
