package com.example.museumapp.ui.admin.artifactform

import android.net.TestUri
import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.ArtifactMetadataSectionIds
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectedImagesRequireExplicitMainImageBeforeSaving() = runTest {
        val viewModel = ArtifactFormViewModel(FakeAdminRepository(), artifactId = null)
        viewModel.updateArtifactCode("DRAFT-1")
        viewModel.updateName("Draft Artifact")
        viewModel.addSelectedImages(listOf(TestUri("content://images/one"), TestUri("content://images/two")))

        viewModel.saveDraft()
        advanceUntilIdle()

        assertEquals("Select a main image before saving.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.primarySelectedUri)
    }

    @Test
    fun selectedMainImageBecomesPrimaryImageIndexWithoutReorderingUploads() = runTest {
        val repository = FakeAdminRepository()
        val first = TestUri("content://images/one")
        val second = TestUri("content://images/two")
        val viewModel = ArtifactFormViewModel(repository, artifactId = null)
        viewModel.updateArtifactCode("DRAFT-2")
        viewModel.updateName("Draft Artifact")
        viewModel.addSelectedImages(listOf(first, second))
        viewModel.selectPrimarySelected(second)

        viewModel.saveDraft()
        advanceUntilIdle()

        assertEquals(listOf(first, second), repository.createdImages)
        assertEquals(1, repository.createdForm?.primaryImageIndex)
        assertEquals("draft", repository.createdForm?.status)
    }

    @Test
    fun manySelectedImagesAreSubmittedWithoutBeingCappedOrReordered() = runTest {
        val repository = FakeAdminRepository()
        val images = (1..42).map { TestUri("content://images/$it") }
        val viewModel = ArtifactFormViewModel(repository, artifactId = null)
        viewModel.updateArtifactCode("DRAFT-42")
        viewModel.updateName("Many Image Draft")
        viewModel.addSelectedImages(images)
        viewModel.selectPrimarySelected(images[20])

        viewModel.saveDraft()
        advanceUntilIdle()

        assertEquals(images, repository.createdImages)
        assertEquals(20, repository.createdForm?.primaryImageIndex)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun customFieldsArePreservedInSubmittedForm() = runTest {
        val repository = FakeAdminRepository()
        val image = TestUri("content://images/one")
        val viewModel = ArtifactFormViewModel(repository, artifactId = null)
        viewModel.updateArtifactCode("DRAFT-3")
        viewModel.updateName("Draft Artifact")
        viewModel.addSelectedImages(listOf(image))
        viewModel.selectPrimarySelected(image)
        viewModel.addCustomField("Weight", "number", "3.5", "kg")

        viewModel.saveDraft()
        advanceUntilIdle()

        val field = repository.createdForm!!.customFields.single()
        assertEquals("Weight", field.label)
        assertEquals("3.5", field.value)
        assertEquals("kg", field.unit)
    }

    @Test
    fun createSuccessShowsArtifactAddedWithoutAiFeed() = runTest {
        val repository = FakeAdminRepository().apply {
            createArtifactResult = RepositoryResult.Success(
                FakeAdminRepository.sampleArtifact(status = "draft").copy(aiIndexStatus = "not_indexed")
            )
        }
        val viewModel = ArtifactFormViewModel(repository, artifactId = null)
        viewModel.updateArtifactCode("DRAFT-SUCCESS")
        viewModel.updateName("Created Artifact")

        viewModel.saveDraft()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showCreateSuccess)
        assertEquals("draft", repository.createdForm?.status)
        assertEquals(0, repository.indexArtifactCalls)
    }

    @Test
    fun multilineMetadataWithWhitespacePassesAndroidValidation() = runTest {
        val repository = FakeAdminRepository()
        val description = "Paragraph one.\n\nParagraph two with enough text to exceed the old one hundred character limit while preserving formatting."
        val material = "Woven bamboo.\n\nAdditional fibers are visible."
        val condition = "Surface wear is visible.\r\nMinor discoloration is present."
        val note = "First paragraph.\n\nSecond paragraph with\ttabbed context."
        val viewModel = ArtifactFormViewModel(repository, artifactId = null)
        viewModel.updateArtifactCode("DRAFT-MULTILINE")
        viewModel.updateName("Multiline Artifact")
        viewModel.updateDescription(description)
        viewModel.updateMaterial(material)
        viewModel.updateCondition(condition)
        viewModel.addCustomField("Curatorial Notes", "long_text", note, null)
        viewModel.addMetadataField(ArtifactMetadataSectionIds.HistoricalDetails)
        val fieldId = viewModel.uiState.value.metadataSection(ArtifactMetadataSectionIds.HistoricalDetails).fields.single().id
        viewModel.updateMetadataFieldLabel(ArtifactMetadataSectionIds.HistoricalDetails, fieldId, "Use Notes")
        viewModel.updateMetadataFieldValue(ArtifactMetadataSectionIds.HistoricalDetails, fieldId, note)
        viewModel.updateMetadataFieldType(ArtifactMetadataSectionIds.HistoricalDetails, fieldId, "long_text")

        viewModel.saveDraft()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(description, repository.createdForm?.description)
        assertEquals(material, repository.createdForm?.material)
        assertEquals(condition, repository.createdForm?.condition)
        assertEquals(note, repository.createdForm?.customFields?.single()?.value)
        assertEquals(note, repository.createdForm?.metadataSections?.first()?.fields?.single()?.value)
    }

    @Test
    fun unsafeControlCharacterFailsAndroidValidation() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = ArtifactFormViewModel(repository, artifactId = null)
        viewModel.updateArtifactCode("DRAFT-CONTROL")
        viewModel.updateName("Control Artifact")
        viewModel.updateDescription("Bad\u0000description")

        viewModel.saveDraft()
        advanceUntilIdle()

        assertEquals("Description contains unsupported control characters.", viewModel.uiState.value.fieldErrors["description"])
        assertNull(repository.createdForm)
    }

    @Test
    fun importedDraftLoadsPrimaryReviewWarning() = runTest {
        val repository = FakeAdminRepository().apply {
            artifactResult = RepositoryResult.Success(
                FakeAdminRepository.sampleArtifact(status = "draft").copy(primaryImageNeedsReview = true)
            )
        }

        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.primaryImageNeedsReview)
        assertEquals("draft", viewModel.uiState.value.status)
    }

    @Test
    fun editFormLoadsAllExistingImagesForLargeImportedDraft() = runTest {
        val repository = FakeAdminRepository().apply {
            artifactResult = RepositoryResult.Success(FakeAdminRepository.sampleArtifact(status = "draft", imageCount = 42))
        }

        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()

        assertEquals(42, viewModel.uiState.value.existingImages.size)
        assertEquals("uploads/images/image-1.jpg", viewModel.uiState.value.primaryExistingPath)
    }

    @Test
    fun manageImagesSheetStateCanOpenAndClose() = runTest {
        val viewModel = ArtifactFormViewModel(FakeAdminRepository(), artifactId = null)

        viewModel.openImageManagement()
        assertTrue(viewModel.uiState.value.imageManagementSheetVisible)

        viewModel.closeImageManagement()
        assertTrue(!viewModel.uiState.value.imageManagementSheetVisible)
    }

    @Test
    fun addImagesForExistingArtifactPreservesAndRefreshesGallery() = runTest {
        val newImages = listOf(TestUri("content://images/add-one"), TestUri("content://images/add-two"))
        val repository = FakeAdminRepository().apply {
            artifactResult = RepositoryResult.Success(FakeAdminRepository.sampleArtifact(imageCount = 20))
            addImagesResult = RepositoryResult.Success(FakeAdminRepository.sampleArtifact(imageCount = 42))
        }
        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()

        viewModel.addImagesFromPicker(newImages)
        advanceUntilIdle()

        assertEquals(newImages, repository.addedImages)
        assertEquals(42, viewModel.uiState.value.existingImages.size)
        assertEquals("uploads/images/image-1.jpg", viewModel.uiState.value.primaryExistingPath)
    }

    @Test
    fun selectingMainImageDoesNotCommitUntilConfirmed() = runTest {
        val repository = FakeAdminRepository().apply {
            artifactResult = RepositoryResult.Success(FakeAdminRepository.sampleArtifact(imageCount = 3))
            setPrimaryImageResult = RepositoryResult.Success(FakeAdminRepository.sampleArtifact(imageCount = 3, primaryIndex = 1))
        }
        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()

        viewModel.openPrimarySelection()
        viewModel.selectPrimaryCandidate(ArtifactFormViewModel.EXISTING_IMAGE_KEY_PREFIX + "uploads/images/image-2.jpg")

        assertNull(repository.primaryImagePathSet)

        viewModel.confirmPrimarySelection()
        advanceUntilIdle()

        assertEquals("uploads/images/image-2.jpg", repository.primaryImagePathSet)
        assertEquals("uploads/images/image-2.jpg", viewModel.uiState.value.primaryExistingPath)
    }

    @Test
    fun confirmingImportedPrimaryReviewClearsReviewFlag() = runTest {
        val repository = FakeAdminRepository().apply {
            artifactResult = RepositoryResult.Success(
                FakeAdminRepository.sampleArtifact(status = "draft", imageCount = 3).copy(primaryImageNeedsReview = true)
            )
            setPrimaryImageResult = RepositoryResult.Success(
                FakeAdminRepository.sampleArtifact(status = "draft", imageCount = 3, primaryIndex = 0).copy(primaryImageNeedsReview = false)
            )
        }
        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()

        viewModel.reviewMainImage()
        viewModel.selectPrimaryCandidate(ArtifactFormViewModel.EXISTING_IMAGE_KEY_PREFIX + "uploads/images/image-1.jpg")
        viewModel.confirmPrimarySelection()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.primaryImageNeedsReview)
    }

    @Test
    fun replaceImagesRequiresConfirmationAndCancelDoesNotMutate() = runTest {
        val viewModel = ArtifactFormViewModel(FakeAdminRepository(), artifactId = "artifact-1")
        advanceUntilIdle()

        viewModel.requestReplaceImages()
        assertTrue(viewModel.uiState.value.replaceImagesConfirmationVisible)

        viewModel.dismissReplaceImages()
        assertEquals(false, viewModel.uiState.value.replaceImages)
        assertTrue(viewModel.uiState.value.selectedImages.isEmpty())

        val replacements = listOf(TestUri("content://images/replacement-one"), TestUri("content://images/replacement-two"))
        viewModel.replaceWithSelectedImages(replacements)

        assertEquals(true, viewModel.uiState.value.replaceImages)
        assertEquals(replacements, viewModel.uiState.value.selectedImages)
        assertNull(viewModel.uiState.value.primaryExistingPath)
    }

    @Test
    fun removeExistingImageRequiresConfirmationAndBlocksCurrentMainImage() = runTest {
        val repository = FakeAdminRepository().apply {
            artifactResult = RepositoryResult.Success(FakeAdminRepository.sampleArtifact(imageCount = 3))
        }
        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()

        val nonPrimary = viewModel.uiState.value.existingImages[1]
        viewModel.requestExistingImageRemoval(nonPrimary)
        assertEquals(nonPrimary.path, viewModel.uiState.value.pendingRemoveImage?.path)

        viewModel.confirmExistingImageRemoval()
        assertTrue(viewModel.uiState.value.existingImages[1].markedForRemoval)

        val primary = viewModel.uiState.value.existingImages[0]
        viewModel.requestExistingImageRemoval(primary)
        assertEquals(primary.path, viewModel.uiState.value.primaryRemovalBlockedImage?.path)
    }

    @Test
    fun imageManagementCanIndexArtifactImages() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()

        viewModel.indexArtifactImages()
        advanceUntilIdle()

        assertEquals(1, repository.indexArtifactCalls)
        assertEquals("indexed", viewModel.uiState.value.savedAiIndexStatus)
        assertEquals(1, viewModel.uiState.value.savedAiIndexedImageCount)
    }

    @Test
    fun draftArtifactsCannotBeFedToAiLibraryFromForm() = runTest {
        val repository = FakeAdminRepository().apply {
            artifactResult = RepositoryResult.Success(FakeAdminRepository.sampleArtifact(status = "draft", imageCount = 2))
        }
        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()

        viewModel.indexArtifactImages()
        advanceUntilIdle()

        assertEquals(0, repository.indexArtifactCalls)
        assertEquals("Publish this artifact before feeding it to the AI Library.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun publishRequiresConfirmationBeforeUpdatingArtifact() = runTest {
        val repository = FakeAdminRepository().apply {
            artifactResult = RepositoryResult.Success(FakeAdminRepository.sampleArtifact(status = "draft", imageCount = 1))
            updateArtifactResult = RepositoryResult.Success(
                FakeAdminRepository.sampleArtifact(status = "published", imageCount = 1).copy(aiIndexStatus = "not_indexed")
            )
        }
        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()
        viewModel.updateCategory("Farm Tools")

        viewModel.publish()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showPublishConfirmation)
        assertNull(repository.updatedForm)

        viewModel.confirmPublish()
        advanceUntilIdle()

        assertEquals("published", repository.updatedForm?.status)
        assertTrue(viewModel.uiState.value.showPublishSuccess)
        assertEquals("not_indexed", viewModel.uiState.value.savedAiIndexStatus)
        assertEquals(0, repository.indexArtifactCalls)
    }

    @Test
    fun visitorGalleryDefaultsCanBeChangedAndIntentionalZeroPersistsInForm() = runTest {
        val defaultSelection = listOf(
            "uploads/images/image-1.jpg",
            "uploads/images/image-2.jpg",
            "uploads/images/image-4.jpg",
            "uploads/images/image-5.jpg",
            "uploads/images/image-6.jpg"
        )
        val repository = FakeAdminRepository().apply {
            artifactResult = RepositoryResult.Success(
                FakeAdminRepository.sampleArtifact(
                    status = "published",
                    imageCount = 11,
                    primaryIndex = 2,
                    visitorGalleryImagePaths = defaultSelection,
                    visitorGalleryConfigured = false
                )
            )
            updateArtifactResult = RepositoryResult.Success(
                FakeAdminRepository.sampleArtifact(
                    status = "published",
                    imageCount = 11,
                    primaryIndex = 2,
                    visitorGalleryImagePaths = emptyList(),
                    visitorGalleryConfigured = true
                )
            )
        }
        val viewModel = ArtifactFormViewModel(repository, artifactId = "artifact-1")
        advanceUntilIdle()

        assertEquals(defaultSelection, viewModel.uiState.value.visitorGalleryImagePaths)
        assertEquals(false, viewModel.uiState.value.visitorGalleryConfigured)

        viewModel.openVisitorSelection()
        defaultSelection.forEach { viewModel.toggleVisitorImage(it) }
        viewModel.confirmVisitorSelection()
        viewModel.saveDraftOrChanges()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.visitorGalleryConfigured)
        assertEquals(emptyList<String>(), repository.updatedForm?.visitorGalleryImagePaths)
        assertEquals(true, repository.updatedForm?.visitorGalleryConfigured)
    }

    @Test
    fun publishRequiresManagedCategoryAndPrimaryImage() = runTest {
        val viewModel = ArtifactFormViewModel(FakeAdminRepository(), artifactId = null)
        viewModel.updateArtifactCode("DRAFT-4")
        viewModel.updateName("Draft Artifact")

        viewModel.publish()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Primary image"))
        assertEquals("Choose a category before publishing.", viewModel.uiState.value.fieldErrors["category"])
    }
}
