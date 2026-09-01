package com.example.museumapp.ui.admin.artifactlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.repository.AdminRepositoryContract
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
    val selectedCategories: Set<String> = emptySet(),
    val availableCategories: List<String> = emptyList(),
    val isFilterSheetOpen: Boolean = false,
    val sort: String = "newest",
    val statusFilter: String = "all",
    val selectedDestination: String = ArtifactListDestinations.All,
    val page: Int = 1,
    val totalPages: Int = 0,
    val totalItems: Int = 0,
    val deletingId: String? = null,
    val pendingDelete: ArtifactDto? = null,
    val feedingArtifactId: String? = null
)

object ArtifactListDestinations {
    const val All = "all"
    const val Published = "published"
    const val Drafts = "draft"
    const val Categories = "categories"
}

class ArtifactListViewModel(
    private val repository: AdminRepositoryContract,
    initialDestination: String = ArtifactListDestinations.All
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ArtifactListUiState(
            isLoading = initialDestination != ArtifactListDestinations.Categories,
            statusFilter = statusForDestination(initialDestination),
            selectedDestination = normalizedDestination(initialDestination)
        )
    )
    val uiState: StateFlow<ArtifactListUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.artifactMutations.collect {
                if (_uiState.value.selectedDestination != ArtifactListDestinations.Categories) {
                    loadArtifacts(reset = true, refreshing = true)
                }
            }
        }
        if (_uiState.value.selectedDestination != ArtifactListDestinations.Categories) {
            loadArtifacts(reset = true)
        }
        loadAvailableCategories()
    }

    fun updateSearch(value: String) {
        _uiState.update { it.copy(search = value) }
        loadArtifacts(reset = true)
    }

    fun toggleCategory(category: String) {
        _uiState.update {
            val updated = if (category in it.selectedCategories) it.selectedCategories - category else it.selectedCategories + category
            it.copy(selectedCategories = updated)
        }
        loadArtifacts(reset = true)
    }

    fun clearCategories() {
        _uiState.update { it.copy(selectedCategories = emptySet()) }
        loadArtifacts(reset = true)
    }

    fun setFilterSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isFilterSheetOpen = open) }
    }

    fun updateSort(value: String) {
        _uiState.update { it.copy(sort = value) }
        loadArtifacts(reset = true)
    }

    fun updateStatusFilter(value: String) {
        _uiState.update { it.copy(statusFilter = value) }
        loadArtifacts(reset = true)
    }

    fun selectDestination(value: String) {
        val destination = normalizedDestination(value)
        if (destination == _uiState.value.selectedDestination) return
        loadJob?.cancel()
        if (destination == ArtifactListDestinations.Categories) {
            _uiState.update {
                it.copy(
                    selectedDestination = destination,
                    statusFilter = ArtifactListDestinations.All,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedDestination = destination,
                statusFilter = statusForDestination(destination),
                page = 1,
                totalPages = 0,
                totalItems = 0,
                artifacts = emptyList()
            )
        }
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

    fun feedArtifactToAiLibrary(artifact: ArtifactDto) {
        if (_uiState.value.feedingArtifactId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(feedingArtifactId = artifact.id, errorMessage = null) }
            when (val result = repository.indexArtifact(artifact.id)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(feedingArtifactId = null) }
                is RepositoryResult.Error -> _uiState.update { it.copy(feedingArtifactId = null, errorMessage = result.message) }
            }
        }
    }

    private fun loadAvailableCategories() {
        viewModelScope.launch {
            when (val result = repository.listCategories(includeInactive = false)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(availableCategories = result.data.map { category -> category.name }.filter { name -> name.isNotBlank() }.sorted())
                }
                is RepositoryResult.Error -> Unit
            }
        }
    }

    private fun loadArtifacts(reset: Boolean, refreshing: Boolean = false) {
        loadJob?.cancel()
        if (_uiState.value.selectedDestination == ArtifactListDestinations.Categories) return
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
            // The backend only matches a single exact category per request, so a single
            // selection is filtered server-side; multiple selections are filtered here instead.
            val serverCategory = state.selectedCategories.singleOrNull()
            when (val result = repository.listArtifacts(nextPage, 20, state.search, serverCategory, state.sort, state.statusFilter)) {
                is RepositoryResult.Success -> _uiState.update {
                    val fetched = if (state.selectedCategories.size > 1) {
                        result.data.items.filter { artifact -> artifact.category in state.selectedCategories }
                    } else {
                        result.data.items
                    }
                    val items = if (reset) fetched else it.artifacts + fetched
                    it.copy(
                        artifacts = items,
                        page = result.data.page,
                        totalPages = result.data.totalPages,
                        totalItems = if (state.selectedCategories.size > 1) items.size else result.data.totalItems,
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
        private fun normalizedDestination(value: String): String {
            return when (value) {
                ArtifactListDestinations.Published -> ArtifactListDestinations.Published
                ArtifactListDestinations.Drafts, "drafts" -> ArtifactListDestinations.Drafts
                ArtifactListDestinations.Categories -> ArtifactListDestinations.Categories
                else -> ArtifactListDestinations.All
            }
        }

        private fun statusForDestination(value: String): String {
            return when (normalizedDestination(value)) {
                ArtifactListDestinations.Published -> ArtifactListDestinations.Published
                ArtifactListDestinations.Drafts -> ArtifactListDestinations.Drafts
                else -> ArtifactListDestinations.All
            }
        }

        fun factory(repository: AdminRepositoryContract, initialDestination: String = ArtifactListDestinations.All): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ArtifactListViewModel(repository, initialDestination) as T
        }
    }
}
