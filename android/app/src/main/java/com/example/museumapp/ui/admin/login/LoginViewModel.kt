package com.example.museumapp.ui.admin.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                if (session.isAuthenticated) {
                    _uiState.update { it.copy(isLoggedIn = true) }
                }
            }
        }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun login() {
        val state = _uiState.value
        val email = state.email.trim()
        val emailError = if (email.isBlank() || !email.contains("@")) "Enter a valid email." else null
        val passwordError = if (state.password.isBlank()) "Password is required." else null
        if (emailError != null || passwordError != null || state.isLoading) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.login(email, state.password)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message.ifBlank { "Invalid email or password." })
                }
            }
        }
    }

    companion object {
        fun factory(repository: AdminRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LoginViewModel(repository) as T
            }
        }
    }
}
