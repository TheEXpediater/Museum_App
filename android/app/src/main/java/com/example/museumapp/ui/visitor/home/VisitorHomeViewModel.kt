package com.example.museumapp.ui.visitor.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.PublicHomeResponseDto
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.data.session.VisitorSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisitorHomeUiState(
    val session: VisitorSession = VisitorSession(),
    val home: PublicHomeResponseDto? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

class VisitorHomeViewModel(private val repository: VisitorRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(VisitorHomeUiState())
    val uiState: StateFlow<VisitorHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                _uiState.update { it.copy(session = session) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hadContent = _uiState.value.home != null
            _uiState.update { it.copy(isLoading = !hadContent, isRefreshing = hadContent, errorMessage = null) }
            when (val result = repository.publicHome()) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(home = result.data, isLoading = false, isRefreshing = false, errorMessage = null)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
            }
        }
    }

    companion object {
        fun factory(repository: VisitorRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = VisitorHomeViewModel(repository) as T
        }
    }
}
