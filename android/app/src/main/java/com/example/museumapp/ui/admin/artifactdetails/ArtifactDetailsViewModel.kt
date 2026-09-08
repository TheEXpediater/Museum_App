package com.example.museumapp.ui.admin.artifactdetails

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.Model3DJobDto
import com.example.museumapp.data.model.Model3DStateDto
import com.example.museumapp.data.model.isJobActive
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val deleted: Boolean = false,
    val model3D: Model3DStateDto? = null,
    val model3DJob: Model3DJobDto? = null,
    val model3DLoading: Boolean = false,
    val model3DBusy: Boolean = false,
    val model3DError: String? = null,
    val isPolling: Boolean = false,
    val pendingDeleteReconstruction: Boolean = false,
    val deletingReconstruction: Boolean = false
)

class ArtifactDetailsViewModel(
    private val repository: AdminRepositoryContract,
    private val artifactId: String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArtifactDetailsUiState())
    val uiState: StateFlow<ArtifactDetailsUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

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

    /** Loads the 3D reconstruction state. Called once the artifact itself has loaded. */
    fun load3DState() {
        val id = artifactId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(model3DLoading = true, model3DError = null) }
            when (val result = repository.get3DState(id)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(model3D = result.data, model3DLoading = false) }
                    resumePollingIfNeeded(result.data)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(model3DLoading = false, model3DError = result.message)
                }
            }
        }
    }

    fun add3DImages(reuseImagePaths: List<String>, images: List<Uri>) {
        val id = artifactId ?: return
        if (reuseImagePaths.isEmpty() && images.isEmpty()) return
        if (_uiState.value.model3DBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(model3DBusy = true, model3DError = null) }
            when (val result = repository.add3DImages(id, reuseImagePaths, images)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(model3D = result.data, model3DBusy = false) }
                    resumePollingIfNeeded(result.data)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(model3DBusy = false, model3DError = result.message)
                }
            }
        }
    }

    fun remove3DImage(imageId: String) {
        val id = artifactId ?: return
        if (_uiState.value.model3DBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(model3DBusy = true, model3DError = null) }
            when (val result = repository.delete3DImage(id, imageId)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(model3D = result.data, model3DBusy = false)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(model3DBusy = false, model3DError = result.message)
                }
            }
        }
    }

    fun runPreflight() {
        val id = artifactId ?: return
        if (_uiState.value.model3DBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(model3DBusy = true, model3DError = null) }
            when (val result = repository.run3DPreflight(id)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(model3D = result.data, model3DBusy = false) }
                    resumePollingIfNeeded(result.data)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(model3DBusy = false, model3DError = result.message)
                }
            }
        }
    }

    fun buildModel() {
        val id = artifactId ?: return
        if (_uiState.value.model3DBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(model3DBusy = true, model3DError = null) }
            when (val result = repository.build3DModel(id)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(model3DBusy = false) }
                    // Refresh the full state (and start polling) now that a job is queued.
                    load3DState()
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(model3DBusy = false, model3DError = result.message)
                }
            }
        }
    }

    fun acceptModel() {
        val id = artifactId ?: return
        if (_uiState.value.model3DBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(model3DBusy = true, model3DError = null) }
            when (val result = repository.accept3DModel(id)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(model3D = result.data, model3DBusy = false) }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(model3DBusy = false, model3DError = result.message)
                }
            }
        }
    }

    fun rejectModel() {
        val id = artifactId ?: return
        if (_uiState.value.model3DBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(model3DBusy = true, model3DError = null) }
            when (val result = repository.reject3DModel(id)) {
                is RepositoryResult.Success -> _uiState.update { it.copy(model3D = result.data, model3DBusy = false) }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(model3DBusy = false, model3DError = result.message)
                }
            }
        }
    }

    fun dismissModel3DError() {
        _uiState.update { it.copy(model3DError = null) }
    }

    fun requestDeleteReconstruction() {
        _uiState.update { it.copy(pendingDeleteReconstruction = true) }
    }

    fun dismissDeleteReconstruction() {
        _uiState.update { it.copy(pendingDeleteReconstruction = false) }
    }

    fun confirmDeleteReconstruction() {
        val id = artifactId ?: return
        if (_uiState.value.deletingReconstruction) return
        viewModelScope.launch {
            _uiState.update { it.copy(deletingReconstruction = true, model3DError = null) }
            when (val result = repository.delete3DReconstruction(id)) {
                is RepositoryResult.Success -> {
                    stopPolling()
                    _uiState.update {
                        it.copy(
                            model3D = result.data,
                            model3DJob = null,
                            deletingReconstruction = false,
                            pendingDeleteReconstruction = false
                        )
                    }
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(
                        deletingReconstruction = false,
                        pendingDeleteReconstruction = false,
                        model3DError = result.message
                    )
                }
            }
        }
    }

    private fun resumePollingIfNeeded(state: Model3DStateDto) {
        if (state.isJobActive()) {
            startPolling()
        } else {
            stopPolling()
        }
    }

    private fun startPolling() {
        val id = artifactId ?: return
        if (pollingJob?.isActive == true) return
        _uiState.update { it.copy(isPolling = true) }
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                when (val result = repository.get3DStatus(id)) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(model3D = result.data.state, model3DJob = result.data.job)
                        }
                        if (!result.data.state.isJobActive()) break
                    }
                    is RepositoryResult.Error -> {
                        _uiState.update { it.copy(model3DError = result.message) }
                        break
                    }
                }
            }
            pollingJob = null
            _uiState.update { it.copy(isPolling = false) }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update { it.copy(isPolling = false) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
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
                is RepositoryResult.Success -> {
                    _uiState.update {
                        it.copy(artifact = result.data, isLoading = false, errorMessage = null)
                    }
                    load3DState()
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3000L

        fun factory(repository: AdminRepositoryContract, artifactId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ArtifactDetailsViewModel(repository, artifactId) as T
            }
    }
}
