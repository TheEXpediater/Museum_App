package com.example.museumapp.ui.admin.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.AiHealthResponse
import com.example.museumapp.data.model.AiIndexStatusResponse
import com.example.museumapp.data.model.AiWarmupResponse
import com.example.museumapp.data.model.HealthResponse
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SystemStatusUiState(
    val backendHealth: HealthResponse? = null,
    val aiHealth: AiHealthResponse? = null,
    val indexStatus: AiIndexStatusResponse? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val warmupStatus: AiWarmupResponse? = null,
    val isWarmingUp: Boolean = false,
    val isPollingWarmup: Boolean = false,
    val isIndexingAll: Boolean = false,
    val isRetryingFailed: Boolean = false,
    val isRebuildingIndex: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val confirmIndexAll: Boolean = false,
    val confirmRetryFailed: Boolean = false,
    val confirmRebuildIndex: Boolean = false
)

class SystemStatusViewModel(private val repository: AdminRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(SystemStatusUiState())
    val uiState: StateFlow<SystemStatusUiState> = _uiState.asStateFlow()
    private var warmupPollingJob: Job? = null

    init {
        refresh(initial = true)
        viewModelScope.launch {
            repository.session.collect { session ->
                if (!session.isAuthenticated) {
                    stopWarmupPolling()
                }
            }
        }
    }

    fun refresh() {
        refresh(initial = false)
    }

    fun requestIndexAll() {
        _uiState.update { it.copy(confirmIndexAll = true) }
    }

    fun requestRetryFailed() {
        _uiState.update { it.copy(confirmRetryFailed = true) }
    }

    fun requestRebuildIndex() {
        _uiState.update { it.copy(confirmRebuildIndex = true) }
    }

    fun loadAiModel() {
        if (_uiState.value.isWarmingUp || _uiState.value.isPollingWarmup) return
        _uiState.update {
            it.copy(
                isWarmingUp = true,
                errorMessage = null,
                actionMessage = null
            )
        }
        viewModelScope.launch {
            when (val result = repository.warmupAi()) {
                is RepositoryResult.Success -> handleWarmupStatus(result.data)
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isWarmingUp = false, errorMessage = result.message)
                }
            }
        }
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(confirmIndexAll = false, confirmRetryFailed = false, confirmRebuildIndex = false) }
    }

    fun confirmIndexAll() {
        dismissConfirmation()
        runIndexAction(IndexAction.IndexAll)
    }

    fun confirmRetryFailed() {
        dismissConfirmation()
        runIndexAction(IndexAction.RetryFailed)
    }

    fun confirmRebuildIndex() {
        dismissConfirmation()
        runIndexAction(IndexAction.Rebuild)
    }

    private fun refresh(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initial,
                    isRefreshing = !initial,
                    errorMessage = null
                )
            }
            val backend = repository.checkHealth()
            val ai = repository.aiHealth()
            val index = repository.indexStatus()
            _uiState.update {
                it.copy(
                    backendHealth = (backend as? RepositoryResult.Success)?.data,
                    aiHealth = (ai as? RepositoryResult.Success)?.data,
                    indexStatus = (index as? RepositoryResult.Success)?.data,
                    warmupStatus = it.warmupStatus?.takeUnless { status -> status.state == "idle" },
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = listOf(backend, ai, index).firstNotNullOfOrNull { result ->
                        (result as? RepositoryResult.Error)?.message
                    }
                )
            }
            val openclip = (_uiState.value.aiHealth?.openclip ?: "").lowercase()
            if (openclip == "loading") {
                startWarmupPolling()
            }
        }
    }

    private fun handleWarmupStatus(status: AiWarmupResponse) {
        _uiState.update {
            it.copy(
                warmupStatus = status,
                isWarmingUp = status.state == "loading",
                errorMessage = if (status.state == "failed") status.error ?: status.message else it.errorMessage,
                actionMessage = when (status.state) {
                    "loaded" -> status.message
                    "loading" -> status.message
                    else -> it.actionMessage
                }
            )
        }
        when (status.state) {
            "loading" -> startWarmupPolling()
            "loaded" -> {
                stopWarmupPolling()
                refresh(initial = false)
            }
            "failed" -> stopWarmupPolling()
        }
    }

    private fun startWarmupPolling() {
        if (warmupPollingJob?.isActive == true) return
        _uiState.update { it.copy(isPollingWarmup = true) }
        warmupPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                when (val result = repository.warmupAiStatus()) {
                    is RepositoryResult.Success -> {
                        handleWarmupStatus(result.data)
                        if (result.data.state == "loaded" || result.data.state == "failed") {
                            break
                        }
                    }
                    is RepositoryResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isWarmingUp = false,
                                isPollingWarmup = false,
                                errorMessage = result.message
                            )
                        }
                        break
                    }
                }
            }
            _uiState.update { it.copy(isPollingWarmup = false, isWarmingUp = false) }
        }
    }

    private fun stopWarmupPolling() {
        warmupPollingJob?.cancel()
        warmupPollingJob = null
        _uiState.update { it.copy(isPollingWarmup = false, isWarmingUp = false) }
    }

    private fun runIndexAction(action: IndexAction) {
        if (_uiState.value.isIndexingAll || _uiState.value.isRetryingFailed || _uiState.value.isRebuildingIndex) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isIndexingAll = action == IndexAction.IndexAll,
                    isRetryingFailed = action == IndexAction.RetryFailed,
                    isRebuildingIndex = action == IndexAction.Rebuild,
                    errorMessage = null,
                    actionMessage = null
                )
            }
            val result = when (action) {
                IndexAction.IndexAll -> repository.indexAllArtifacts()
                IndexAction.RetryFailed -> repository.retryFailedIndexes()
                IndexAction.Rebuild -> repository.rebuildArtifactIndex()
            }
            when (result) {
                is RepositoryResult.Success -> {
                    val message = if (result.data.totalImages == 0) {
                        "Add at least one artifact image before indexing."
                    } else {
                        "Indexed ${result.data.indexedImages} image(s); ${result.data.failedImages} failed; ${result.data.skippedImages} skipped."
                    }
                    _uiState.update {
                        it.copy(
                            isIndexingAll = false,
                            isRetryingFailed = false,
                            isRebuildingIndex = false,
                            actionMessage = message
                        )
                    }
                    refresh(initial = false)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(
                        isIndexingAll = false,
                        isRetryingFailed = false,
                        isRebuildingIndex = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    private enum class IndexAction {
        IndexAll,
        RetryFailed,
        Rebuild
    }

    override fun onCleared() {
        stopWarmupPolling()
        super.onCleared()
    }

    companion object {
        fun factory(repository: AdminRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SystemStatusViewModel(repository) as T
        }
    }
}
