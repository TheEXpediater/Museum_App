package com.example.museumapp.ui.admin.recognition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.museumapp.data.model.ArtifactMatchDto
import com.example.museumapp.data.model.RecognizedArtifactDto
import com.example.museumapp.ui.theme.MuseumAdminTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun permissionRequiredScreenShowsExplanationAndAction() {
        var requested = false

        composeRule.setContent {
            MuseumAdminTheme {
                PermissionPromptCard(
                    title = "Camera access is required to capture an artifact for recognition.",
                    primaryLabel = "Allow Camera",
                    onPrimary = { requested = true },
                    onOpenSettings = null
                )
            }
        }

        composeRule.onNodeWithText("Camera access is required to capture an artifact for recognition.").assertIsDisplayed()
        composeRule.onNodeWithText("Allow Camera").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun cameraReadyControlsShowEnabledRecognizeButton() {
        composeRule.setContent {
            MuseumAdminTheme {
                RecognitionCaptureButton(
                    uiState = RecognitionUiState(
                        mode = RecognitionUiMode.CameraReady,
                        indexedVectors = 2,
                        aiStatus = "loaded"
                    ),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        composeRule.onNodeWithText("Recognize").assertIsDisplayed()
        composeRule.onNodeWithTag("recognize_button").assertIsEnabled()
    }

    @Test
    fun processingOverlayShowsAnalyzingMessage() {
        composeRule.setContent {
            MuseumAdminTheme {
                Box(Modifier.size(280.dp)) {
                    BlockingOverlay(message = "Analyzing artifact...")
                }
            }
        }

        composeRule.onNodeWithText("Analyzing artifact...").assertIsDisplayed()
    }

    @Test
    fun noMatchResultShowsActions() {
        composeRule.setContent {
            MuseumAdminTheme {
                NoMatchCard(
                    message = "No reliable artifact match was found.",
                    onScanAgain = {},
                    onViewIndexedArtifacts = {}
                )
            }
        }

        composeRule.onNodeWithText("No reliable artifact match was found.").assertIsDisplayed()
        composeRule.onNodeWithText("Scan Again").assertIsDisplayed()
        composeRule.onNodeWithText("View Indexed Artifacts").assertIsDisplayed()
    }

    @Test
    fun bestMatchViewArtifactDetailsActionCallsBack() {
        var viewedId: String? = null
        val match = match("best-id")

        composeRule.setContent {
            MuseumAdminTheme {
                BestMatchCard(
                    match = match,
                    level = "strong",
                    onViewArtifact = { viewedId = match.artifact.id }
                )
            }
        }

        composeRule.onNodeWithText("View Artifact Details").performClick()
        composeRule.runOnIdle { assertEquals("best-id", viewedId) }
    }

    @Test
    fun alternativeMatchCardClickCallsBack() {
        var viewedId: String? = null
        val match = match("alt-id")

        composeRule.setContent {
            MuseumAdminTheme {
                AlternativeMatchCard(
                    match = match,
                    onViewArtifact = { viewedId = match.artifact.id }
                )
            }
        }

        composeRule.onNodeWithTag("alternative_match_card").performClick()
        composeRule.runOnIdle { assertEquals("alt-id", viewedId) }
    }

    @Test
    fun scanAgainActionCallsBack() {
        var scanAgain = false

        composeRule.setContent {
            MuseumAdminTheme {
                NoMatchCard(
                    message = "No reliable artifact match was found.",
                    onScanAgain = { scanAgain = true },
                    onViewIndexedArtifacts = {}
                )
            }
        }

        composeRule.onNodeWithText("Scan Again").performClick()
        composeRule.runOnIdle { assertTrue(scanAgain) }
    }

    @Test
    fun recognizeButtonIsDisabledWhenNoIndexedVectorsExist() {
        composeRule.setContent {
            MuseumAdminTheme {
                RecognitionCaptureButton(
                    uiState = RecognitionUiState(
                        mode = RecognitionUiMode.CameraReady,
                        indexedVectors = 0,
                        aiStatus = "loaded"
                    ),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        composeRule.onNodeWithTag("recognize_button").assertIsNotEnabled()
    }

    private fun match(id: String): ArtifactMatchDto {
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
            similarityScore = 0.91,
            matchedImagePath = "uploads/images/$id.jpg",
            supportingImageHits = 1
        )
    }
}
