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

/** Where an [ArtifactModel3DViewModel] gets the model's url/version/sha256 from. */
private sealed class Model3DSource {
    /** Visitor flow: fetch the artifact's ACCEPTED model via [VisitorRepositoryContract]. */
    data class VisitorArtifact(val artifactId: String) : Model3DSource()

    /** Admin draft-review flow: coordinates are already known from the 3D state response, so no
     * extra fetch is needed - and a draft is never visible through the visitor endpoint anyway. */
    data class Direct(val artifactId: String, val version: Int, val sha256: String, val url: String) : Model3DSource()
}

/**
 * Drives [Model3DCacheRepository] to obtain a verified local copy of a GLB for the viewer screen,
 * either the visitor's ACCEPTED model (fetched via [VisitorRepositoryContract.visitorArtifactDetails],
 * reusing that call rather than adding a redundant one just for the 3D fields) or an admin's
 * PENDING_REVIEW draft (coordinates passed in directly - drafts are never exposed to visitors).
 */
class ArtifactModel3DViewModel private constructor(
    private val repository: VisitorRepositoryContract?,
    private val cacheRepository: Model3DCacheRepository,
    private val source: Model3DSource
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
        when (val current = source) {
            is Model3DSource.Direct -> {
                downloadAndCache(current.artifactId, current.version, current.sha256, current.url)
            }
            is Model3DSource.VisitorArtifact -> {
                val repo = repository ?: return
                _state.value = Model3DViewerState.Loading
                viewModelScope.launch {
                    when (val result = repo.visitorArtifactDetails(current.artifactId)) {
                        is RepositoryResult.Success -> {
                            val artifact = result.data
                            val version = artifact.model3dVersion
                            val sha256 = artifact.model3dSha256
                            val url = artifact.model3dUrl
                            if (!artifact.model3dAvailable || version == null || sha256.isNullOrBlank() || url.isNullOrBlank()) {
                                _state.value = Model3DViewerState.Error("A 3D model is not available for this artifact.")
                                return@launch
                            }
                            downloadAndCache(current.artifactId, version, sha256, url)
                        }
                        is RepositoryResult.Error -> _state.value = Model3DViewerState.Error(result.message)
                    }
                }
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
                ArtifactModel3DViewModel(
                    repository,
                    cacheRepository,
                    Model3DSource.VisitorArtifact(artifactId.orEmpty())
                ) as T
        }

        /** Admin preview of a PENDING_REVIEW draft: url/version/sha256 come straight from the
         * 3D state response the admin already fetched, so no visitor lookup is involved. */
        fun factoryForDraft(
            cacheRepository: Model3DCacheRepository,
            artifactId: String,
            version: Int,
            sha256: String,
            url: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArtifactModel3DViewModel(
                    null,
                    cacheRepository,
                    Model3DSource.Direct(artifactId, version, sha256, url)
                ) as T
        }
    }
}
