package com.example.museumapp.ui.visitor.model3d

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.repository.RepositoryResult
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.model3d.Model3DCacheRepository
import com.example.museumapp.model3d.Model3DDownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class Model3DViewerState {
    data object Loading : Model3DViewerState()
    data class Downloading(val progressFraction: Float?) : Model3DViewerState()
    data class Ready(val localFilePath: String) : Model3DViewerState()
    data class Error(val message: String) : Model3DViewerState()
}

/**
 * Fetches the visitor artifact (which now carries the `model_3d_*` fields) and, when a model is
 * available, drives [Model3DCacheRepository] to obtain a verified local copy for the viewer
 * screen. Deliberately reuses [VisitorRepositoryContract.visitorArtifactDetails] rather than
 * adding a redundant new repository call just for the 3D fields.
 */
class ArtifactModel3DViewModel(
    private val repository: VisitorRepositoryContract,
    private val cacheRepository: Model3DCacheRepository,
    private val artifactId: String?
) : ViewModel() {
    private val _state = MutableStateFlow<Model3DViewerState>(Model3DViewerState.Loading)
    val state: StateFlow<Model3DViewerState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        val id = artifactId
        if (id.isNullOrBlank()) {
            _state.value = Model3DViewerState.Error("The requested artifact was not found.")
            return
        }
        _state.value = Model3DViewerState.Loading
        viewModelScope.launch {
            when (val result = repository.visitorArtifactDetails(id)) {
                is RepositoryResult.Success -> {
                    val artifact = result.data
                    val version = artifact.model3dVersion
                    val sha256 = artifact.model3dSha256
                    val url = artifact.model3dUrl
                    if (!artifact.model3dAvailable || version == null || sha256.isNullOrBlank() || url.isNullOrBlank()) {
                        _state.value = Model3DViewerState.Error("A 3D model is not available for this artifact.")
                        return@launch
                    }
                    downloadAndCache(id, version, sha256, url)
                }
                is RepositoryResult.Error -> _state.value = Model3DViewerState.Error(result.message)
            }
        }
    }

    private fun downloadAndCache(artifactId: String, version: Int, sha256: String, url: String) {
        viewModelScope.launch {
            cacheRepository.ensureDownloaded(artifactId, version, sha256, url)
                .catch { throwable ->
                    _state.value = Model3DViewerState.Error(
                        throwable.message?.takeIf { it.isNotBlank() } ?: "Could not download the 3D model."
                    )
                }
                .collect { progress ->
                    _state.value = when (progress) {
                        is Model3DDownloadProgress.InProgress -> Model3DViewerState.Downloading(progress.fraction)
                        is Model3DDownloadProgress.Completed -> Model3DViewerState.Ready(progress.file.absolutePath)
                    }
                }
        }
    }

    companion object {
        fun factory(
            repository: VisitorRepositoryContract,
            cacheRepository: Model3DCacheRepository,
            artifactId: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArtifactModel3DViewModel(repository, cacheRepository, artifactId) as T
        }
    }
}
