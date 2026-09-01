package com.example.museumapp.ui.admin.artifactlist

import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun artifactTopDestinationsIncludeCategories() {
        assertEquals("all", ArtifactListDestinations.All)
        assertEquals("published", ArtifactListDestinations.Published)
        assertEquals("draft", ArtifactListDestinations.Drafts)
        assertEquals("categories", ArtifactListDestinations.Categories)
    }

    @Test
    fun categoriesDestinationDoesNotLoadArtifactRows() = runTest {
        val repository = FakeAdminRepository()

        val viewModel = ArtifactListViewModel(repository, ArtifactListDestinations.Categories)
        advanceUntilIdle()

        assertEquals(ArtifactListDestinations.Categories, viewModel.uiState.value.selectedDestination)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(repository.lastListArtifactsStatus)
    }

    @Test
    fun publishedAndDraftTabsLoadMatchingStatusFilters() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = ArtifactListViewModel(repository)
        advanceUntilIdle()

        viewModel.selectDestination(ArtifactListDestinations.Published)
        advanceUntilIdle()
        assertEquals("published", repository.lastListArtifactsStatus)

        viewModel.selectDestination(ArtifactListDestinations.Drafts)
        advanceUntilIdle()
        assertEquals("draft", repository.lastListArtifactsStatus)
    }
}
