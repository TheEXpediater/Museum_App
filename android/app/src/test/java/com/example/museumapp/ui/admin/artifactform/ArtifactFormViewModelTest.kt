package com.example.museumapp.ui.admin.artifactform

import android.net.TestUri
import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
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
