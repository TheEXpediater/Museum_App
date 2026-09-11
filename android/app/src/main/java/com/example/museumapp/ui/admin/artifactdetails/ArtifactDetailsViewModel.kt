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

/**
 * Local, UI-only sub-stages that happen BEFORE a backend job exists (image preparation, the
 * create-generation call itself). Once a job exists, [Model3DStateDto.status] /
 * [Model3DJobDto.stageMessage] - backend-driven, polled - are the source of truth; this only
 * covers the brief window the existing job/status model has no vocabulary for.
 */
enum class Model3DSubmitStage { PreparingImages, StartingGeneration }

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
    val model3DSubmitStage: Model3DSubmitStage? = null,
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

    fun buildAiModel(imageIds: List<String>, visibleRegions: List<String> = emptyList()) {
        val id = artifactId ?: return
        if (imageIds.isEmpty()) return
        if (_uiState.value.model3DBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(model3DBusy = true, model3DError = null) }
            when (val result = repository.buildAi3DModel(id, imageIds, visibleRegions)) {
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

    /**
     * The single entry point for the redesigned "Create 3D Preview" flow: prepares the admin's
     * selected artifact photos as reconstruction source images (idempotent - the backend skips
     * images it already has by content digest), then immediately starts a Local AI generation
     * job with those images. No manual "Run Check Again" step: this deliberately never calls the
     * synchronous COLMAP preflight endpoint (see [ArtifactDetailsScreen] root-cause notes) - Quick
     * AI generation does not require it.
     *
     * [selectedImagePaths] order matters: it is used (capped to the provider's per-request image
     * limit) to decide which resulting reconstruction images are actually submitted for
     * generation, so callers should put the most important photo (e.g. the artifact's primary
     * image) first.
     */
    fun createOrUpdate3DPreview(selectedImagePaths: List<String>, visibleRegions: List<String> = emptyList()) {
        val id = artifactId ?: return
        if (selectedImagePaths.isEmpty()) return
        if (_uiState.value.model3DBusy) return
        if (_uiState.value.model3D?.isJobActive() == true) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(model3DBusy = true, model3DError = null, model3DSubmitStage = Model3DSubmitStage.PreparingImages)
            }

            val stateAfterAdd = resolveOrRecover(id, repository.add3DImages(id, selectedImagePaths, emptyList())) ?: return@launch

            if (stateAfterAdd.isJobActive()) {
                // A prior attempt's build request actually landed despite a client-side error -
                // resume watching it instead of starting a second one.
                _uiState.update { it.copy(model3D = stateAfterAdd, model3DBusy = false, model3DSubmitStage = null) }
                resumePollingIfNeeded(stateAfterAdd)
                return@launch
            }

            val effectiveMax = stateAfterAdd.aiMaxImages?.takeIf { it > 0 } ?: 1
            val imageIds = mapSelectedPathsToImageIds(stateAfterAdd, selectedImagePaths, effectiveMax)
            if (imageIds.isEmpty()) {
                failSubmit("None of the selected photos could be prepared for 3D generation.")
                return@launch
            }

            _uiState.update { it.copy(model3D = stateAfterAdd, model3DSubmitStage = Model3DSubmitStage.StartingGeneration) }

            when (val buildResult = repository.buildAi3DModel(id, imageIds, visibleRegions)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(model3DBusy = false, model3DSubmitStage = null) }
                    load3DState()
                }
                is RepositoryResult.Error -> {
                    if (buildResult.recoverable) {
                        // The POST may have timed out (or hit a 409) after the backend already
                        // created the job - reconcile with real state rather than assuming
                        // failure or letting the admin resubmit into a duplicate job.
                        when (val recovered = repository.get3DState(id)) {
                            is RepositoryResult.Success -> {
                                _uiState.update {
                                    it.copy(model3D = recovered.data, model3DBusy = false, model3DSubmitStage = null)
                                }
                                if (recovered.data.isJobActive()) {
                                    resumePollingIfNeeded(recovered.data)
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            model3DError = "The request timed out and no 3D generation job could be " +
                                                "confirmed. Please try again."
                                        )
                                    }
                                }
                            }
                            is RepositoryResult.Error -> failSubmit(recovered.message)
                        }
                    } else {
                        failSubmit(buildResult.message)
                    }
                }
            }
        }
    }

    /** Resolves a possibly-recoverable [RepositoryResult.Error] by re-fetching real backend
     * state; returns null (having already set [ArtifactDetailsUiState.model3DError]) only when
     * the failure is non-recoverable or reconciliation itself fails. */
    private suspend fun resolveOrRecover(
        id: String,
        result: RepositoryResult<Model3DStateDto>
    ): Model3DStateDto? = when (result) {
        is RepositoryResult.Success -> result.data
        is RepositoryResult.Error -> {
            if (result.recoverable) {
                when (val recovered = repository.get3DState(id)) {
                    is RepositoryResult.Success -> recovered.data
                    is RepositoryResult.Error -> {
                        failSubmit(recovered.message)
                        null
                    }
                }
            } else {
                failSubmit(result.message)
                null
            }
        }
    }

    private fun mapSelectedPathsToImageIds(state: Model3DStateDto, selectedPaths: List<String>, effectiveMax: Int): List<String> {
        val byFilename = state.images.associateBy { it.originalFilename }
        return selectedPaths
            .mapNotNull { path -> byFilename[path.substringAfterLast('/')]?.id }
            .distinct()
            .take(effectiveMax)
    }

    private fun failSubmit(message: String) {
        _uiState.update { it.copy(model3DBusy = false, model3DSubmitStage = null, model3DError = message) }
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
