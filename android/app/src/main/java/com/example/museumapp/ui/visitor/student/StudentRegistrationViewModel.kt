package com.example.museumapp.ui.visitor.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ProgramDto
import com.example.museumapp.data.model.StudentRegisterRequestDto
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.VisitorFormValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentRegistrationUiState(
    val studentId: String = "",
    val firstName: String = "",
    val middleInitial: String = "",
    val lastName: String = "",
    val yearLevel: String = "",
    val course: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val programs: List<ProgramDto> = emptyList(),
    val isProgramLoading: Boolean = false,
    val allowsFreeTextCourse: Boolean = true,
    val errors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false
)

class StudentRegistrationViewModel(private val repository: VisitorRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(StudentRegistrationUiState())
    val uiState: StateFlow<StudentRegistrationUiState> = _uiState.asStateFlow()

    init {
        loadPrograms()
    }

    fun updateStudentId(value: String) = update { it.copy(studentId = value, errors = it.errors - "studentId", errorMessage = null) }
    fun updateFirstName(value: String) = update { it.copy(firstName = value, errors = it.errors - "firstName", errorMessage = null) }
    fun updateMiddleInitial(value: String) = update { it.copy(middleInitial = value.take(1), errors = it.errors - "middleInitial", errorMessage = null) }
    fun updateLastName(value: String) = update { it.copy(lastName = value, errors = it.errors - "lastName", errorMessage = null) }
    fun updateYearLevel(value: String) = update { it.copy(yearLevel = value, errors = it.errors - "yearLevel", errorMessage = null) }
    fun updateCourse(value: String) = update { it.copy(course = value, errors = it.errors - "course", errorMessage = null) }
    fun updateEmail(value: String) = update { it.copy(email = value, errors = it.errors - "email", errorMessage = null) }
    fun updatePassword(value: String) = update { it.copy(password = value, errors = it.errors - "password" - "confirmPassword", errorMessage = null) }
    fun updateConfirmPassword(value: String) = update { it.copy(confirmPassword = value, errors = it.errors - "confirmPassword", errorMessage = null) }
    fun togglePasswordVisibility() = update { it.copy(passwordVisible = !it.passwordVisible) }
    fun toggleConfirmPasswordVisibility() = update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }

    fun loadPrograms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProgramLoading = true) }
            when (val result = repository.programs()) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        programs = result.data,
                        allowsFreeTextCourse = result.data.isEmpty(),
                        isProgramLoading = false
                    )
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(programs = emptyList(), allowsFreeTextCourse = true, isProgramLoading = false)
                }
            }
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.isLoading) return
        val errors = VisitorFormValidation.studentRegistrationErrors(
            studentId = state.studentId,
            firstName = state.firstName,
            middleInitial = state.middleInitial,
            lastName = state.lastName,
            yearLevel = state.yearLevel,
            course = state.course,
            email = state.email,
            password = state.password,
            confirmPassword = state.confirmPassword
        )
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        if (!state.allowsFreeTextCourse && state.programs.none { it.name.equals(state.course.trim(), ignoreCase = true) }) {
            _uiState.update { it.copy(errors = it.errors + ("course" to "Select a course or program from the list.")) }
            return
        }

        val request = StudentRegisterRequestDto(
            studentId = VisitorFormValidation.clean(state.studentId, 40).uppercase(),
            firstName = VisitorFormValidation.clean(state.firstName, 80),
            middleInitial = state.middleInitial.trim().takeIf { it.isNotBlank() }?.uppercase(),
            lastName = VisitorFormValidation.clean(state.lastName, 80),
            yearLevel = state.yearLevel,
            course = VisitorFormValidation.clean(state.course, 120),
            email = state.email.trim().lowercase(),
            password = state.password,
            confirmPassword = state.confirmPassword
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.registerStudent(request)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(isLoading = false, isComplete = true) }
                is RepositoryResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    private fun update(block: (StudentRegistrationUiState) -> StudentRegistrationUiState) {
        _uiState.update(block)
    }

    companion object {
        fun factory(repository: VisitorRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StudentRegistrationViewModel(repository) as T
        }
    }
}
