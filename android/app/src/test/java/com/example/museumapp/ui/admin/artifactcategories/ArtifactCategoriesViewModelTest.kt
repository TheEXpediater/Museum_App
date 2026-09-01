package com.example.museumapp.ui.admin.artifactcategories

import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
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

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactCategoriesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsActiveAndInactiveCategoriesForManagement() = runTest {
        val repository = FakeAdminRepository().apply {
            categoriesResult = RepositoryResult.Success(
                listOf(
                    FakeAdminRepository.sampleCategory(id = "active", name = "Agricultural Tools", artifactCount = 12),
                    FakeAdminRepository.sampleCategory(id = "inactive", name = "Ceramics", isActive = false, artifactCount = 8)
                )
            )
        }

        val viewModel = ArtifactCategoriesViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(true, repository.lastListCategoriesIncludeInactive)
        assertEquals(2, viewModel.uiState.value.categories.size)
        assertEquals(12, viewModel.uiState.value.categories.first { it.id == "active" }.artifactCount)
    }

    @Test
    fun addAndRenameRejectDuplicateNormalizedNames() = runTest {
        val repository = FakeAdminRepository().apply {
            categoriesResult = RepositoryResult.Success(
                listOf(FakeAdminRepository.sampleCategory(id = "tools", name = "Agricultural Tools"))
            )
        }
        val viewModel = ArtifactCategoriesViewModel(repository)
        advanceUntilIdle()

        viewModel.addCategory("  agricultural   tools  ")
        advanceUntilIdle()

        assertEquals("Category already exists.", viewModel.uiState.value.errorMessage)
        assertNull(repository.lastCreatedCategoryName)
    }

    @Test
    fun deactivateRequiresConfirmationAndCancelDoesNothing() = runTest {
        val category = FakeAdminRepository.sampleCategory(id = "tools", name = "Agricultural Tools")
        val repository = FakeAdminRepository().apply {
            categoriesResult = RepositoryResult.Success(listOf(category))
        }
        val viewModel = ArtifactCategoriesViewModel(repository)
        advanceUntilIdle()

        viewModel.requestDeactivate(category)
        assertEquals(category.id, viewModel.uiState.value.pendingDeactivate?.id)

        viewModel.dismissDeactivate()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingDeactivate)
        assertEquals(0, repository.categoryDeactivateCalls)
    }

    @Test
    fun confirmDeactivateMarksCategoryInactiveWithoutDeletingRows() = runTest {
        val category = FakeAdminRepository.sampleCategory(id = "tools", name = "Agricultural Tools", artifactCount = 20)
        val repository = FakeAdminRepository().apply {
            categoriesResult = RepositoryResult.Success(listOf(category))
        }
        val viewModel = ArtifactCategoriesViewModel(repository)
        advanceUntilIdle()

        viewModel.requestDeactivate(category)
        viewModel.confirmDeactivate()
        advanceUntilIdle()

        assertEquals(1, repository.categoryDeactivateCalls)
        assertEquals("tools", repository.lastDeactivatedCategoryId)
        assertTrue(viewModel.uiState.value.categories.single().artifactCount >= 0)
        assertFalse(viewModel.uiState.value.categories.single().isActive)
    }

    @Test
    fun inactiveCategoryCanBeActivated() = runTest {
        val category = FakeAdminRepository.sampleCategory(id = "ceramics", name = "Ceramics", isActive = false)
        val repository = FakeAdminRepository().apply {
            categoriesResult = RepositoryResult.Success(listOf(category))
        }
        val viewModel = ArtifactCategoriesViewModel(repository)
        advanceUntilIdle()

        viewModel.activateCategory(category)
        advanceUntilIdle()

        assertEquals(1, repository.categoryActivateCalls)
        assertEquals("ceramics", repository.lastActivatedCategoryId)
        assertTrue(viewModel.uiState.value.categories.single().isActive)
    }
}
