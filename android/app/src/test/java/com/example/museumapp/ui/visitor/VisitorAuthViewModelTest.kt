package com.example.museumapp.ui.visitor

import com.example.museumapp.FakeVisitorRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.ProgramDto
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.ui.visitor.guest.GuestInfoViewModel
import com.example.museumapp.ui.visitor.student.StudentLoginViewModel
import com.example.museumapp.ui.visitor.student.StudentRegistrationViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisitorAuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun guestOtherRelationshipRequiresDetailBeforeSubmission() = runTest {
        val viewModel = GuestInfoViewModel(FakeVisitorRepository())
        viewModel.updateFirstName("Maria")
        viewModel.updateLastName("Santos")
        viewModel.updateRelationship("Other")

        viewModel.continueToMuseum()
        advanceUntilIdle()

        assertEquals("Please specify your relationship.", viewModel.uiState.value.errors["otherDetail"])
        assertFalse(viewModel.uiState.value.isComplete)
    }

    @Test
    fun guestSubmissionCompletesWhenValid() = runTest {
        val viewModel = GuestInfoViewModel(FakeVisitorRepository())
        viewModel.updateFirstName("Maria")
        viewModel.updateLastName("Santos")
        viewModel.updateRelationship("General Visitor")

        viewModel.continueToMuseum()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isComplete)
    }

    @Test
    fun studentRegistrationUsesConfiguredProgramListWhenPresent() = runTest {
        val repository = FakeVisitorRepository().apply {
            programsResult = RepositoryResult.Success(listOf(ProgramDto("program-1", "Agriculture")))
        }
        val viewModel = StudentRegistrationViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.allowsFreeTextCourse)
        assertEquals(listOf("Agriculture"), viewModel.uiState.value.programs.map { it.name })
    }

    @Test
    fun studentLoginCompletesWhenValid() = runTest {
        val viewModel = StudentLoginViewModel(FakeVisitorRepository())
        viewModel.updateIdentifier("PSAU-1")
        viewModel.updatePassword("Student123")

        viewModel.login()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isComplete)
    }
}
