package com.example.museumapp.ui.visitor.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisitorScanUiState(
    val isLoading: Boolean = true,
    val backendConnected: Boolean = false,
    val indexedArtifacts: Int? = null,
    val errorMessage: String? = null
) {
    val canOpenCamera: Boolean
        get() = backendConnected && (indexedArtifacts ?: 0) > 0 && errorMessage == null
}

class VisitorScanViewModel(private val repository: VisitorRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(VisitorScanUiState())
    val uiState: StateFlow<VisitorScanUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val health = repository.checkHealth()
            val aiHealth = repository.aiHealth()
            if (health is RepositoryResult.Success && aiHealth is RepositoryResult.Success) {
                val connected = health.data.status.equals("healthy", ignoreCase = true)
                val ready = aiHealth.data.openclip.equals("loaded", ignoreCase = true) &&
                    aiHealth.data.qdrant.equals("connected", ignoreCase = true) &&
                    aiHealth.data.indexedVectors > 0
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backendConnected = connected,
                        indexedArtifacts = aiHealth.data.indexedVectors,
                        errorMessage = if (connected && ready) null else "Artifact scanning is temporarily unavailable."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backendConnected = false,
                        indexedArtifacts = null,
                        errorMessage = "Artifact scanning is temporarily unavailable."
                    )
                }
            }
        }
    }

    companion object {
        fun factory(repository: VisitorRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = VisitorScanViewModel(repository) as T
        }
    }
}
