package com.example.museumapp.ui.visitor.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val UNAVAILABLE_MESSAGE = "Artifact scanning is temporarily unavailable."
private const val POLL_INTERVAL_MS = 2_500L
private const val MAX_POLL_ATTEMPTS = 24 // ~60 seconds of polling while OpenCLIP warms up

data class VisitorScanUiState(
    val isLoading: Boolean = true,
    val isPreparingAi: Boolean = false,
    val backendConnected: Boolean = false,
    val indexedArtifacts: Int? = null,
    val errorMessage: String? = null
) {
    val canOpenCamera: Boolean
        get() = backendConnected && (indexedArtifacts ?: 0) > 0 && errorMessage == null && !isPreparingAi
}

/**
 * Reflects the actual server-side OpenCLIP warmup state (see AiHealthResponse.openclip) rather
 * than assuming readiness just because this screen opened. Polls while the model is still
 * loading so the sheet transitions to "Scanner Ready" on its own once warmup completes.
 */
class VisitorScanViewModel(private val repository: VisitorRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(VisitorScanUiState())
    val uiState: StateFlow<VisitorScanUiState> = _uiState.asStateFlow()
    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var attempts = 0
            while (true) {
                if (attempts == 0) {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                }
                val health = repository.checkHealth()
                val aiHealth = repository.aiHealth()
                if (health !is RepositoryResult.Success || aiHealth !is RepositoryResult.Success) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isPreparingAi = false,
                            backendConnected = false,
                            indexedArtifacts = null,
                            errorMessage = UNAVAILABLE_MESSAGE
                        )
                    }
                    return@launch
                }

                val connected = health.data.status.equals("healthy", ignoreCase = true)
                val openclipLoading = aiHealth.data.openclip.equals("loading", ignoreCase = true)
                val ready = aiHealth.data.openclip.equals("loaded", ignoreCase = true) &&
                    aiHealth.data.qdrant.equals("connected", ignoreCase = true) &&
                    aiHealth.data.indexedVectors > 0

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPreparingAi = connected && openclipLoading,
                        backendConnected = connected,
                        indexedArtifacts = aiHealth.data.indexedVectors,
                        errorMessage = if (connected && (ready || openclipLoading)) null else UNAVAILABLE_MESSAGE
                    )
                }

                if (!connected || !openclipLoading || ready || attempts >= MAX_POLL_ATTEMPTS) {
                    return@launch
                }
                attempts += 1
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(repository: VisitorRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = VisitorScanViewModel(repository) as T
        }
    }
}
