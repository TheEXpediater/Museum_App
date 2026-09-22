package com.example.museumapp.ui.visitor

import com.example.museumapp.FakeVisitorRepository
import com.example.museumapp.MainDispatcherRule
import com.example.museumapp.data.model.ProgramDto
import com.example.museumapp.data.model.StudentRegistrationResponseDto
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

    @Test
    fun studentRegistrationSuccessLeavesAccountPendingWithoutError() = runTest {
        val repository = FakeVisitorRepository().apply {
            registerStudentResult = RepositoryResult.Success(
                StudentRegistrationResponseDto(
                    id = "student-1",
                    studentId = "PSAU-2026-001",
                    status = "pending",
                    message = "Your student account has been submitted for approval."
                )
            )
        }
        val viewModel = StudentRegistrationViewModel(repository)
        advanceUntilIdle()
        viewModel.updateStudentId("PSAU-2026-001")
        viewModel.updateFirstName("Juan")
        viewModel.updateLastName("Reyes")
        viewModel.updateYearLevel("Third Year")
        viewModel.updateCourse("Bachelor of Science in Agriculture")
        viewModel.updateEmail("juan.reyes@example.com")
        viewModel.updatePassword("Student123")
        viewModel.updateConfirmPassword("Student123")

        viewModel.register()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isComplete)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun studentRegistrationFailureSurfacesErrorMessageAndStaysIncomplete() = runTest {
        val repository = FakeVisitorRepository().apply {
            registerStudentResult = RepositoryResult.Error("A student account with this Student ID already exists.")
        }
        val viewModel = StudentRegistrationViewModel(repository)
        advanceUntilIdle()
        viewModel.updateStudentId("PSAU-2026-001")
        viewModel.updateFirstName("Juan")
        viewModel.updateLastName("Reyes")
        viewModel.updateYearLevel("Third Year")
        viewModel.updateCourse("Bachelor of Science in Agriculture")
        viewModel.updateEmail("juan.reyes@example.com")
        viewModel.updatePassword("Student123")
        viewModel.updateConfirmPassword("Student123")

        viewModel.register()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isComplete)
        assertEquals("A student account with this Student ID already exists.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun studentLoginSurfacesPendingApprovalMessageVerbatim() = runTest {
        val pendingMessage = "Your student account is awaiting administrator approval. Please allow up to 24 hours for review."
        val repository = FakeVisitorRepository().apply {
            loginStudentResult = RepositoryResult.Error(pendingMessage)
        }
        val viewModel = StudentLoginViewModel(repository)
        viewModel.updateIdentifier("PSAU-2026-001")
        viewModel.updatePassword("Student123")

        viewModel.login()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isComplete)
        assertEquals(pendingMessage, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun studentLoginSurfacesInactiveMessageVerbatim() = runTest {
        val inactiveMessage = "Your student account is inactive. Please contact the museum administrator."
        val repository = FakeVisitorRepository().apply {
            loginStudentResult = RepositoryResult.Error(inactiveMessage)
        }
        val viewModel = StudentLoginViewModel(repository)
        viewModel.updateIdentifier("PSAU-2026-001")
        viewModel.updatePassword("Student123")

        viewModel.login()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isComplete)
        assertEquals(inactiveMessage, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun studentLoginKeepsGenericMessageForInvalidCredentials() = runTest {
        val repository = FakeVisitorRepository().apply {
            loginStudentResult = RepositoryResult.Error("Invalid student ID, email, or password.")
        }
        val viewModel = StudentLoginViewModel(repository)
        viewModel.updateIdentifier("PSAU-2026-001")
        viewModel.updatePassword("wrong")

        viewModel.login()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isComplete)
        assertEquals("Invalid student ID, email, or password.", viewModel.uiState.value.errorMessage)
    }
}
