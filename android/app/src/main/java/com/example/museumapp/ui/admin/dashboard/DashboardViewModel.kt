package com.example.museumapp.ui.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.DashboardSummaryResponse
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val adminName: String = "",
    val summary: DashboardSummaryResponse? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isIndexing: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

class DashboardViewModel(private val repository: AdminRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                _uiState.update { it.copy(adminName = session.adminName.ifBlank { session.adminEmail }) }
            }
        }
        load(refreshing = false)
    }

    fun refresh() {
        load(refreshing = true)
    }

    fun reindexAll() {
        if (_uiState.value.isIndexing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isIndexing = true, errorMessage = null, actionMessage = null) }
            when (val result = repository.indexAllArtifacts()) {
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isIndexing = false,
                            actionMessage = "Indexed ${result.data.indexedImages} image(s); ${result.data.failedImages} failed."
                        )
                    }
                    load(refreshing = true)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isIndexing = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun load(refreshing: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing && it.summary == null,
                    isRefreshing = refreshing,
                    errorMessage = null
                )
            }
            when (val result = repository.dashboardSummary()) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        summary = result.data,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    companion object {
        fun factory(repository: AdminRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repository) as T
        }
    }
}
