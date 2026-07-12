package com.example.museumapp.ui.admin.artifactlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtifactListUiState(
    val artifacts: List<ArtifactDto> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val search: String = "",
    val category: String = "",
    val sort: String = "newest",
    val page: Int = 1,
    val totalPages: Int = 0,
    val deletingId: String? = null,
    val pendingDelete: ArtifactDto? = null
)

class ArtifactListViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ArtifactListUiState())
    val uiState: StateFlow<ArtifactListUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadArtifacts(reset = true)
    }

    fun updateSearch(value: String) {
        _uiState.update { it.copy(search = value) }
    }

    fun updateCategory(value: String) {
        _uiState.update { it.copy(category = value) }
    }

    fun updateSort(value: String) {
        _uiState.update { it.copy(sort = value) }
        loadArtifacts(reset = true)
    }

    fun applyFilters() {
        loadArtifacts(reset = true)
    }

    fun refresh() {
        loadArtifacts(reset = true, refreshing = true)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.page >= state.totalPages) return
        loadArtifacts(reset = false)
    }

    fun requestDelete(artifact: ArtifactDto) {
        _uiState.update { it.copy(pendingDelete = artifact) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val artifact = _uiState.value.pendingDelete ?: return
        if (_uiState.value.deletingId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = artifact.id, errorMessage = null) }
            when (val result = repository.deleteArtifact(artifact.id)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        artifacts = it.artifacts.filterNot { item -> item.id == artifact.id },
                        pendingDelete = null,
                        deletingId = null
                    )
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(errorMessage = result.message, deletingId = null, pendingDelete = null)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    private fun loadArtifacts(reset: Boolean, refreshing: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = _uiState.value
            val nextPage = if (reset) 1 else state.page + 1
            _uiState.update {
                it.copy(
                    isLoading = !refreshing && reset,
                    isRefreshing = refreshing,
                    errorMessage = null
                )
            }
            when (val result = repository.listArtifacts(nextPage, 20, state.search, state.category, state.sort)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        artifacts = if (reset) result.data.items else it.artifacts + result.data.items,
                        page = result.data.page,
                        totalPages = result.data.totalPages,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
            }
        }
    }

    companion object {
        fun factory(repository: AdminRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ArtifactListViewModel(repository) as T
        }
    }
}
