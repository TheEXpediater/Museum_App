package com.example.museumapp.ui.admin.artifactdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtifactDetailsUiState(
    val artifact: ArtifactDto? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val feedingAi: Boolean = false,
    val feedError: String? = null,
    val pendingDelete: Boolean = false,
    val deleting: Boolean = false,
    val deleteError: String? = null,
    val deleted: Boolean = false
)

class ArtifactDetailsViewModel(
    private val repository: AdminRepositoryContract,
    private val artifactId: String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArtifactDetailsUiState())
    val uiState: StateFlow<ArtifactDetailsUiState> = _uiState.asStateFlow()

    init {
        loadArtifact()
    }

    fun retry() {
        loadArtifact()
    }

    fun feedToAiLibrary() {
        val id = artifactId ?: return
        if (_uiState.value.feedingAi) return
        viewModelScope.launch {
            _uiState.update { it.copy(feedingAi = true, feedError = null) }
            when (val result = repository.indexArtifact(id)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(feedingAi = false) }
                    loadArtifact()
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(feedingAi = false, feedError = result.message)
                }
            }
        }
    }

    fun dismissFeedError() {
        _uiState.update { it.copy(feedError = null) }
    }

    fun requestDelete() {
        _uiState.update { it.copy(pendingDelete = true) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(pendingDelete = false) }
    }

    fun confirmDelete() {
        val id = artifactId ?: return
        if (_uiState.value.deleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, deleteError = null) }
            when (val result = repository.deleteArtifact(id)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(deleting = false, pendingDelete = false, deleted = true)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(deleting = false, pendingDelete = false, deleteError = result.message)
                }
            }
        }
    }

    fun dismissDeleteError() {
        _uiState.update { it.copy(deleteError = null) }
    }

    private fun loadArtifact() {
        val id = artifactId
        if (id.isNullOrBlank()) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "The requested artifact was not found.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getArtifact(id)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(artifact = result.data, isLoading = false, errorMessage = null)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    companion object {
        fun factory(repository: AdminRepositoryContract, artifactId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ArtifactDetailsViewModel(repository, artifactId) as T
            }
    }
}
