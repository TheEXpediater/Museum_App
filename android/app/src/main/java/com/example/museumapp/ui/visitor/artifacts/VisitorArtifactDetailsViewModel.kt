package com.example.museumapp.ui.visitor.artifacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArticleDto
import com.example.museumapp.data.model.PublicArtifactDto
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisitorArtifactDetailsUiState(
    val artifact: PublicArtifactDto? = null,
    val relatedArticles: List<ArticleDto> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class VisitorArtifactDetailsViewModel(
    private val repository: VisitorRepositoryContract,
    private val artifactId: String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(VisitorArtifactDetailsUiState())
    val uiState: StateFlow<VisitorArtifactDetailsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        val id = artifactId
        if (id.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "The requested artifact was not found.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.visitorArtifactDetails(id)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(artifact = result.data, isLoading = false) }
                    loadRelatedArticles(result.data.category)
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    private fun loadRelatedArticles(category: String) {
        viewModelScope.launch {
            when (val result = repository.articles(category = category)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(relatedArticles = result.data.take(3)) }
                is RepositoryResult.Error -> Unit
            }
        }
    }

    companion object {
        fun factory(repository: VisitorRepositoryContract, artifactId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    VisitorArtifactDetailsViewModel(repository, artifactId) as T
            }
    }
}
