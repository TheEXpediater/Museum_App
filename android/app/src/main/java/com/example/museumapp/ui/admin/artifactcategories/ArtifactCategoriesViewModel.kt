package com.example.museumapp.ui.admin.artifactcategories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArtifactCategoryDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtifactCategoriesUiState(
    val categories: List<ArtifactCategoryDto> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val pendingDeactivate: ArtifactCategoryDto? = null,
    val createdCategoryResult: ArtifactCategoryDto? = null
)

class ArtifactCategoriesViewModel(
    private val repository: AdminRepositoryContract
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArtifactCategoriesUiState())
    val uiState: StateFlow<ArtifactCategoriesUiState> = _uiState.asStateFlow()

    init {
        load(refreshing = false)
    }

    fun refresh() {
        load(refreshing = true)
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        val duplicate = _uiState.value.categories.any { it.normalizedName == categoryKey(trimmed) }
        when {
            trimmed.isBlank() -> _uiState.update { it.copy(errorMessage = "Category name is required.") }
            duplicate -> _uiState.update { it.copy(errorMessage = "Category already exists.") }
            else -> mutate("Category added.", returnCreatedCategory = true) { repository.createCategory(trimmed) }
        }
    }

    fun renameCategory(category: ArtifactCategoryDto, name: String) {
        val trimmed = name.trim()
        val normalized = categoryKey(trimmed)
        val duplicate = _uiState.value.categories.any { it.id != category.id && it.normalizedName == normalized }
        when {
            trimmed.isBlank() -> _uiState.update { it.copy(errorMessage = "Category name is required.") }
            duplicate -> _uiState.update { it.copy(errorMessage = "Category already exists.") }
            else -> mutate("Category renamed.") { repository.renameCategory(category.id, trimmed) }
        }
    }

    fun activateCategory(category: ArtifactCategoryDto) {
        mutate("Category activated.") { repository.activateCategory(category.id) }
    }

    fun requestDeactivate(category: ArtifactCategoryDto) {
        _uiState.update { it.copy(pendingDeactivate = category, errorMessage = null, actionMessage = null) }
    }

    fun dismissDeactivate() {
        _uiState.update { it.copy(pendingDeactivate = null) }
    }

    fun clearCreatedCategoryResult() {
        _uiState.update { it.copy(createdCategoryResult = null) }
    }

    fun confirmDeactivate() {
        val category = _uiState.value.pendingDeactivate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null, actionMessage = null) }
            when (val result = repository.deactivateCategory(category.id)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        categories = replaceCategory(it.categories, result.data),
                        pendingDeactivate = null,
                        isMutating = false,
                        actionMessage = "Category deactivated."
                    )
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isMutating = false, pendingDeactivate = null, errorMessage = result.message)
                }
            }
        }
    }

    private fun mutate(
        message: String,
        returnCreatedCategory: Boolean = false,
        block: suspend () -> RepositoryResult<ArtifactCategoryDto>
    ) {
        if (_uiState.value.isMutating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true, errorMessage = null, actionMessage = null) }
            when (val result = block()) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        categories = replaceCategory(it.categories, result.data),
                        isMutating = false,
                        actionMessage = message,
                        createdCategoryResult = if (returnCreatedCategory) result.data else it.createdCategoryResult
                    )
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(isMutating = false, errorMessage = result.message) }
            }
        }
    }

    private fun load(refreshing: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing && it.categories.isEmpty(),
                    isRefreshing = refreshing,
                    errorMessage = null
                )
            }
            when (val result = repository.listCategories(includeInactive = true)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        categories = result.data.sortedBy { category -> category.name.lowercase() },
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

    private fun replaceCategory(categories: List<ArtifactCategoryDto>, category: ArtifactCategoryDto): List<ArtifactCategoryDto> {
        return (categories.filterNot { it.id == category.id } + category).sortedBy { it.name.lowercase() }
    }

    private fun categoryKey(value: String): String = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ").lowercase()

    companion object {
        fun factory(repository: AdminRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ArtifactCategoriesViewModel(repository) as T
        }
    }
}
