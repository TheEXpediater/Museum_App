package com.example.museumapp.ui.admin.students

import com.example.museumapp.FakeAdminRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminStudentAccountsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun defaultFilterIsPendingOnLoad() = runTest {
        val repository = FakeAdminRepository()
        AdminStudentAccountsViewModel(repository)
        advanceUntilIdle()

        assertEquals("pending", repository.lastListStudentAccountsStatus)
    }

    @Test
    fun changingFilterRefetchesWithNewStatus() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = AdminStudentAccountsViewModel(repository)
        advanceUntilIdle()

        viewModel.selectFilter(StudentAccountFilters.Active)
        advanceUntilIdle()

        assertEquals("active", repository.lastListStudentAccountsStatus)
        assertEquals("active", viewModel.uiState.value.selectedFilter)
    }

    @Test
    fun searchQueryRefetchesWithSearchTerm() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = AdminStudentAccountsViewModel(repository)
        advanceUntilIdle()

        viewModel.updateSearchQuery("Reyes")
        advanceUntilIdle()

        assertEquals("Reyes", repository.lastListStudentAccountsSearch)
    }

    @Test
    fun confirmingStatusChangeTwiceWhileUpdatingOnlyIssuesOneCall() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = AdminStudentAccountsViewModel(repository)
        advanceUntilIdle()
        viewModel.openDetails("student-1")
        advanceUntilIdle()
        viewModel.requestStatusChange(StudentAccountFilters.Active)

        viewModel.confirmStatusChange()
        viewModel.confirmStatusChange()
        advanceUntilIdle()

        assertEquals(1, repository.updateStudentAccountStatusCalls)
    }

    @Test
    fun successfulActivationShowsConfirmationSnackbarMessage() = runTest {
        val repository = FakeAdminRepository().apply {
            updateStudentAccountStatusResult = RepositoryResult.Success(
                FakeAdminRepository.sampleStudentDetail(accountStatus = "active")
            )
        }
        val viewModel = AdminStudentAccountsViewModel(repository)
        advanceUntilIdle()
        viewModel.openDetails("student-1")
        advanceUntilIdle()
        viewModel.requestStatusChange(StudentAccountFilters.Active)

        viewModel.confirmStatusChange()
        advanceUntilIdle()

        assertEquals("Student account activated.", viewModel.uiState.value.snackbarMessage)
    }
}
