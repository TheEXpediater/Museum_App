package com.example.museumapp.ui.visitor.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.BuildConfig
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.data.session.VisitorSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisitorSettingsUiState(
    val session: VisitorSession = VisitorSession(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val isLoggedOut: Boolean = false
)

class VisitorSettingsViewModel(private val repository: VisitorRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(VisitorSettingsUiState())
    val uiState: StateFlow<VisitorSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                _uiState.update { it.copy(session = session) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }

    companion object {
        fun factory(repository: VisitorRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = VisitorSettingsViewModel(repository) as T
        }
    }
}
