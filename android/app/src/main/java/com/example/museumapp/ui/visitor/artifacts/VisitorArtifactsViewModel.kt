package com.example.museumapp.ui.visitor.artifacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArticleDto
import com.example.museumapp.data.model.MuseumInformationDto
import com.example.museumapp.data.model.PublicArtifactDto
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VisitorArtifactsTab {
    Artifacts,
    Articles,
    MuseumInfo
}

data class VisitorArtifactsUiState(
    val selectedTab: VisitorArtifactsTab = VisitorArtifactsTab.Artifacts,
    val search: String = "",
    val selectedCategory: String = "",
    val artifacts: List<PublicArtifactDto> = emptyList(),
    val totalArtifacts: Int = 0,
    val articles: List<ArticleDto> = emptyList(),
    val museumInformation: MuseumInformationDto = MuseumInformationDto(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val page: Int = 1,
    val totalPages: Int = 0
) {
    val categories: List<String>
        get() = artifacts.map { it.category }.distinct().sorted()
}

class VisitorArtifactsViewModel(private val repository: VisitorRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(VisitorArtifactsUiState())
    val uiState: StateFlow<VisitorArtifactsUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
    }

    fun selectTab(tab: VisitorArtifactsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateSearch(value: String) {
        _uiState.update { it.copy(search = value) }
        loadArtifacts(reset = true)
        loadArticles()
    }

    fun selectCategory(value: String) {
        _uiState.update { it.copy(selectedCategory = value) }
        loadArtifacts(reset = true)
    }

    fun refreshAll() {
        loadArtifacts(reset = true)
        loadArticles()
        loadMuseumInformation()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.page >= state.totalPages) return
        loadArtifacts(reset = false)
    }

    private fun loadArtifacts(reset: Boolean) {
        viewModelScope.launch {
            val nextPage = if (reset) 1 else _uiState.value.page + 1
            _uiState.update {
                it.copy(
                    isLoading = reset && it.artifacts.isEmpty(),
                    isLoadingMore = !reset,
                    errorMessage = null
                )
            }
            val state = _uiState.value
            when (val result = repository.visitorArtifacts(nextPage, 20, state.search, state.selectedCategory, "newest")) {
                is RepositoryResult.Success -> _uiState.update {
                    val items = if (reset) result.data.items else it.artifacts + result.data.items
                    it.copy(
                        artifacts = items,
                        totalArtifacts = result.data.totalItems,
                        page = result.data.page,
                        totalPages = result.data.totalPages,
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null
                    )
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoading = false, isLoadingMore = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun loadArticles() {
        viewModelScope.launch {
            when (val result = repository.articles(search = _uiState.value.search, category = null)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(articles = result.data) }
                is RepositoryResult.Error -> _uiState.update { if (it.errorMessage == null) it.copy(errorMessage = result.message) else it }
            }
        }
    }

    private fun loadMuseumInformation() {
        viewModelScope.launch {
            when (val result = repository.museumInformation()) {
                is RepositoryResult.Success -> _uiState.update { it.copy(museumInformation = result.data) }
                is RepositoryResult.Error -> _uiState.update { if (it.errorMessage == null) it.copy(errorMessage = result.message) else it }
            }
        }
    }

    companion object {
        fun factory(repository: VisitorRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = VisitorArtifactsViewModel(repository) as T
        }
    }
}
