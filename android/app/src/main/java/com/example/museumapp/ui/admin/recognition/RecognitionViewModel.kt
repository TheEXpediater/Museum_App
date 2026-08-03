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
import java.io.File

enum class RecognitionUiMode {
    CameraInitializing,
    CameraReady,
    Capturing,
    Processing,
    Success,
    NoMatch,
    Failure
}

data class RecognitionUiState(
    val mode: RecognitionUiMode = RecognitionUiMode.CameraInitializing,
    val selectedImage: Uri? = null,
    val response: RecognitionResponseDto? = null,
    val isRecognizing: Boolean = false,
    val indexedVectors: Int = 0,
    val aiStatus: String = "unknown",
    val errorMessage: String? = null,
    val capturedFilePath: String? = null,
    val cleanupFilePath: String? = null,
    val hasFlashUnit: Boolean = false,
    val torchEnabled: Boolean = false
) {
    val isAiModelReady: Boolean
        get() = aiStatus.equals("loaded", ignoreCase = true)

    val hasIndexedVectors: Boolean
        get() = indexedVectors > 0

    val recognitionBlockedMessage: String?
        get() = when {
            !hasIndexedVectors -> "No indexed artifact images are available."
            !isAiModelReady -> "AI model is not ready."
            else -> null
        }

    val canRecognize: Boolean
        get() = recognitionBlockedMessage == null && !isRecognizing && mode == RecognitionUiMode.CameraReady
}

class RecognitionViewModel(private val repository: AdminRepositoryContract) : ViewModel() {
    private val _uiState = MutableStateFlow(RecognitionUiState())
    val uiState: StateFlow<RecognitionUiState> = _uiState.asStateFlow()
    private var pendingCaptureFile: File? = null

    init {
        refreshAiReadiness()
    }

    fun onCameraInitializing() {
        _uiState.update {
            if (it.mode == RecognitionUiMode.CameraReady || it.mode == RecognitionUiMode.Failure) {
                it.copy(mode = RecognitionUiMode.CameraInitializing, errorMessage = null)
            } else {
                it
            }
        }
    }

    fun onCameraReady(hasFlashUnit: Boolean) {
        _uiState.update {
            when (it.mode) {
                RecognitionUiMode.CameraInitializing,
                RecognitionUiMode.CameraReady,
                RecognitionUiMode.Failure -> it.copy(
                    mode = RecognitionUiMode.CameraReady,
                    hasFlashUnit = hasFlashUnit,
                    errorMessage = null
                )
                RecognitionUiMode.Capturing,
                RecognitionUiMode.Processing,
                RecognitionUiMode.Success,
                RecognitionUiMode.NoMatch -> it.copy(hasFlashUnit = hasFlashUnit)
            }
        }
    }

    fun onCameraError(message: String = "Camera capture failed. Please try again.") {
        val cleanupPath = cleanupPendingCapture()
        _uiState.update {
            it.copy(
                mode = RecognitionUiMode.Failure,
                isRecognizing = false,
                errorMessage = safeErrorMessage(message),
                capturedFilePath = null,
                cleanupFilePath = cleanupPath ?: it.cleanupFilePath
            )
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        _uiState.update {
            if (it.hasFlashUnit) it.copy(torchEnabled = enabled) else it.copy(torchEnabled = false)
        }
    }

    fun selectImage(uri: Uri?) {
        if (uri == null) return
        _uiState.update {
            it.copy(
                selectedImage = uri,
                response = null,
                errorMessage = null,
                mode = RecognitionUiMode.CameraReady
            )
        }
    }

    fun tryAnotherImage() {
        scanAgain()
    }

    fun scanAgain() {
        val cleanupPath = cleanupPendingCapture()
        _uiState.update {
            it.copy(
                mode = RecognitionUiMode.CameraReady,
                selectedImage = null,
                response = null,
                isRecognizing = false,
                errorMessage = null,
                capturedFilePath = null,
                cleanupFilePath = cleanupPath ?: it.cleanupFilePath
            )
        }
        refreshAiReadiness()
    }

    fun beginCameraCapture(): Boolean {
        val state = _uiState.value
        if (!state.canRecognize) return false

        val cleanupPath = cleanupPendingCapture()
        _uiState.update {
            it.copy(
                mode = RecognitionUiMode.Capturing,
                selectedImage = null,
                response = null,
                isRecognizing = true,
                errorMessage = null,
                capturedFilePath = null,
                cleanupFilePath = cleanupPath ?: it.cleanupFilePath
            )
        }
        return true
    }

    fun onCaptureFailed(message: String = "Camera capture failed. Please try again.", file: File? = null) {
        if (file != null && pendingCaptureFile?.absolutePath == file.absolutePath) {
            pendingCaptureFile = null
        }
        val cleanupPath = cleanupFile(file) ?: cleanupPendingCapture()
        _uiState.update {
            it.copy(
                mode = RecognitionUiMode.Failure,
                isRecognizing = false,
                errorMessage = safeErrorMessage(message),
                capturedFilePath = null,
                cleanupFilePath = cleanupPath ?: it.cleanupFilePath
            )
        }
    }

    fun trackTemporaryCapture(file: File) {
        pendingCaptureFile = file
        _uiState.update {
            if (it.mode == RecognitionUiMode.Capturing) {
                it.copy(capturedFilePath = file.absolutePath)
            } else {
                it
            }
        }
    }

    fun processCapturedFile(file: File) {
        if (_uiState.value.mode == RecognitionUiMode.Processing) return
        pendingCaptureFile = file
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mode = RecognitionUiMode.Processing,
                    selectedImage = null,
                    response = null,
                    errorMessage = null,
                    capturedFilePath = file.absolutePath,
                    isRecognizing = true
                )
            }
            when (val result = repository.recognizeArtifactFile(file, limit = 5)) {
                is RepositoryResult.Success -> {
                    val cleanupPath = cleanupFile(file)
                    pendingCaptureFile = null
                    _uiState.update {
                        it.copy(
                            mode = result.data.resultMode(),
                            response = result.data,
                            isRecognizing = false,
                            errorMessage = null,
                            capturedFilePath = null,
                            cleanupFilePath = cleanupPath ?: it.cleanupFilePath
                        )
                    }
                }
                is RepositoryResult.Error -> {
                    val cleanupPath = cleanupFile(file)
                    pendingCaptureFile = null
                    _uiState.update {
                        it.copy(
                            mode = RecognitionUiMode.Failure,
                            isRecognizing = false,
                            errorMessage = safeErrorMessage(result.message),
                            capturedFilePath = null,
                            cleanupFilePath = cleanupPath ?: it.cleanupFilePath
                        )
                    }
                }
            }
        }
    }

    fun recognize() {
        val image = _uiState.value.selectedImage ?: return
        if (!_uiState.value.canRecognize) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    mode = RecognitionUiMode.Processing,
                    isRecognizing = true,
                    errorMessage = null,
                    response = null
                )
            }
            when (val result = repository.recognizeArtifact(image, limit = 5)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        mode = result.data.resultMode(),
                        isRecognizing = false,
                        response = result.data,
                        errorMessage = null
                    )
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(
                        mode = RecognitionUiMode.Failure,
                        isRecognizing = false,
                        errorMessage = safeErrorMessage(result.message)
                    )
                }
            }
        }
    }

    fun bestMatchArtifactId(): String? = _uiState.value.response?.bestMatch?.artifact?.id

    fun alternativeMatchArtifactId(index: Int): String? = _uiState.value.response?.otherMatches?.getOrNull(index)?.artifact?.id

    fun refreshAiReadiness() {
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

    override fun onCleared() {
        cleanupPendingCapture()
        super.onCleared()
    }

    private fun RecognitionResponseDto.resultMode(): RecognitionUiMode {
        return if (matched && bestMatch != null) RecognitionUiMode.Success else RecognitionUiMode.NoMatch
    }

    private fun cleanupPendingCapture(): String? {
        val file = pendingCaptureFile ?: return null
        pendingCaptureFile = null
        return cleanupFile(file)
    }

    private fun cleanupFile(file: File?): String? {
        val target = file ?: return null
        val path = target.absolutePath
        runCatching {
            if (target.exists()) {
                target.delete()
            }
        }
        return path
    }

    private fun safeErrorMessage(message: String): String {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return "Recognition failed. Please try again."
        return when {
            trimmed.contains("timeout", ignoreCase = true) ||
                trimmed.contains("Unable to reach", ignoreCase = true) ||
                trimmed.contains("connection", ignoreCase = true) -> "Connection to the backend was lost."
            trimmed.contains("OpenCLIP", ignoreCase = true) -> "AI model is not ready."
            trimmed.contains("Qdrant", ignoreCase = true) -> "AI services are unavailable. Check System Status."
            trimmed.length > 180 -> "Recognition failed. Please try again."
            else -> trimmed
        }
    }

    companion object {
        fun factory(repository: AdminRepositoryContract): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RecognitionViewModel(repository) as T
        }
    }
}
