package com.example.museumapp.ui.admin.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.BuildConfig
import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.HealthResponse
import com.example.museumapp.data.model.UserDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.session.AdminSession
import com.example.museumapp.ui.admin.components.healthStatusLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsAccountUi(
    val fullName: String = "Administrator",
    val email: String = "",
    val role: String = "admin",
    val signedIn: Boolean = false
)

data class SettingsUiState(
    val account: SettingsAccountUi = SettingsAccountUi(),
    val backendSummary: String = "Unknown",
    val aiModelSummary: String = "Unknown",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val isLoading: Boolean = true,
    val isLoggingOut: Boolean = false,
    val showLogoutConfirmation: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(private val repository: AdminRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                _uiState.update { it.copy(account = session.toAccountUi()) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val account = repository.currentAdmin()
            val backend = repository.checkHealth()
            val ai = repository.aiHealth()

            _uiState.update { state ->
                state.copy(
                    account = (account as? RepositoryResult.Success)?.data?.toAccountUi() ?: state.account,
                    backendSummary = (backend as? RepositoryResult.Success)?.data?.toBackendSummary() ?: state.backendSummary,
                    aiModelSummary = (ai as? RepositoryResult.Success)?.data?.toAiSummary() ?: state.aiModelSummary,
                    isLoading = false,
                    errorMessage = listOf(account, backend, ai).firstNotNullOfOrNull {
                        (it as? RepositoryResult.Error)?.message
                    }
                )
            }
        }
    }

    fun requestLogout() {
        _uiState.update { it.copy(showLogoutConfirmation = true) }
    }

    fun dismissLogout() {
        _uiState.update { it.copy(showLogoutConfirmation = false) }
    }

    fun confirmLogout() {
        if (_uiState.value.isLoggingOut) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, showLogoutConfirmation = false) }
            repository.logout()
        }
    }

    companion object {
        fun factory(repository: AdminRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
        }
    }
}

fun AdminSession.toAccountUi(): SettingsAccountUi {
    return SettingsAccountUi(
        fullName = adminName.ifBlank { "Administrator" },
        email = adminEmail,
        role = role.ifBlank { "admin" },
        signedIn = isAuthenticated
    )
}

fun UserDto.toAccountUi(): SettingsAccountUi {
    return SettingsAccountUi(
        fullName = fullName,
        email = email,
        role = role,
        signedIn = role == "admin"
    )
}

fun accountInitials(fullName: String): String {
    val initials = fullName.split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
    return initials.ifBlank { "A" }
}

private fun HealthResponse.toBackendSummary(): String {
    return if (status == "healthy" && database == "connected" && uploadsDirectory == "available") {
        "Healthy"
    } else {
        healthStatusLabel(status)
    }
}

private fun AiHealthResponse.toAiSummary(): String {
    return healthStatusLabel(openclip)
}
