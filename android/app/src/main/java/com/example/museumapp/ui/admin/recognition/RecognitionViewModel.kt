package com.example.museumapp.ui.admin.recognition

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecognitionUiState(
    val selectedImage: Uri? = null,
    val response: RecognitionResponseDto? = null,
    val isRecognizing: Boolean = false,
    val indexedVectors: Int = 0,
    val aiStatus: String = "unknown",
    val errorMessage: String? = null
)

class RecognitionViewModel(private val repository: AdminRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(RecognitionUiState())
    val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()

    init {
        refreshAiReadiness()
    }

    fun selectImage(uri: Uri?) {
        if (uri == null) return
        _uiState.update {
            it.copy(selectedImage = uri, response = null, errorMessage = null)
        }
    }

    fun tryAnotherImage() {
        _uiState.update { RecognitionUiState() }
    }

    fun recognize() {
        val image = _uiState.value.selectedImage ?: return
        if (_uiState.value.isRecognizing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRecognizing = true, errorMessage = null, response = null) }
            when (val result = repository.recognizeArtifact(image, limit = 5)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(isRecognizing = false, response = result.data)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isRecognizing = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun refreshAiReadiness() {
        viewModelScope.launch {
            when (val result = repository.aiHealth()) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(indexedVectors = result.data.indexedVectors, aiStatus = result.data.openclip)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(aiStatus = "unknown")
                }
            }
        }
    }

    companion object {
        fun factory(repository: AdminRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RecognitionViewModel(repository) as T
        }
    }
}
