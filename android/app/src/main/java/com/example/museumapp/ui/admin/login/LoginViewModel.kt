package com.example.museumapp.ui.admin.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.BuildConfig
import com.example.museumapp.data.model.HealthResponse
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun debugOnly(value: String): String = if (BuildConfig.DEBUG) value else ""

data class LoginUiState(
    val email: String = debugOnly(BuildConfig.DEBUG_ADMIN_EMAIL),
    val password: String = debugOnly(BuildConfig.DEBUG_ADMIN_PASSWORD),
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isTestingConnection: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val errorMessage: String? = null,
    val connectionMessage: String? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    val backendBaseUrl: String = repository.backendBaseUrl

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
            _uiState.update { it.copy(isLoading = true, errorMessage = null, connectionMessage = null) }
            when (val healthResult = repository.checkHealth()) {
                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = healthResult.message)
                    }
                    return@launch
                }
                is RepositoryResult.Success -> {
                    val health = healthResult.data
                    val healthMessage = health.toConnectionMessage()
                    if (!health.isDatabaseConnected) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = healthMessage,
                                connectionMessage = healthMessage
                            )
                        }
                        return@launch
                    }
                    _uiState.update { it.copy(connectionMessage = healthMessage) }
                }
            }

            when (val result = repository.login(email, state.password)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message.ifBlank { "Invalid email or password." })
                }
            }
        }
    }

    fun testConnection() {
        if (_uiState.value.isLoading || _uiState.value.isTestingConnection) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, errorMessage = null, connectionMessage = null) }
            when (val result = repository.checkHealth()) {
                is RepositoryResult.Success -> {
                    val message = result.data.toConnectionMessage()
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionMessage = message,
                            errorMessage = if (result.data.isDatabaseConnected) null else message
                        )
                    }
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isTestingConnection = false, errorMessage = result.message)
                }
            }
        }
    }

    private val HealthResponse.isDatabaseConnected: Boolean
        get() = database.equals("connected", ignoreCase = true)

    private fun HealthResponse.toConnectionMessage(): String {
        return if (status.equals("healthy", ignoreCase = true)) {
            "Backend connected: database connected, uploads available."
        } else {
            "Backend reached, but MongoDB or the upload directory is unavailable. Database: $database, uploads: $uploadsDirectory."
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
