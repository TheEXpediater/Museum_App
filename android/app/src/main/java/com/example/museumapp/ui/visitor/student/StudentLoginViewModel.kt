package com.example.museumapp.ui.visitor.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.VisitorFormValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentLoginUiState(
    val identifier: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false
)

class StudentLoginViewModel(private val repository: VisitorRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(StudentLoginUiState())
    val uiState: StateFlow<StudentLoginUiState> = _uiState.asStateFlow()

    fun updateIdentifier(value: String) = update { it.copy(identifier = value, errors = it.errors - "identifier", errorMessage = null) }
    fun updatePassword(value: String) = update { it.copy(password = value, errors = it.errors - "password", errorMessage = null) }
    fun togglePasswordVisibility() = update { it.copy(passwordVisible = !it.passwordVisible) }

    fun login() {
        val state = _uiState.value
        if (state.isLoading) return
        val errors = VisitorFormValidation.studentLoginErrors(state.identifier, state.password)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.loginStudent(state.identifier, state.password)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(isLoading = false, isComplete = true) }
                is RepositoryResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message.ifBlank { "Invalid student ID, email, or password." }) }
            }
        }
    }

    private fun update(block: (StudentLoginUiState) -> StudentLoginUiState) {
        _uiState.update(block)
    }

    companion object {
        fun factory(repository: VisitorRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StudentLoginViewModel(repository) as T
        }
    }
}
