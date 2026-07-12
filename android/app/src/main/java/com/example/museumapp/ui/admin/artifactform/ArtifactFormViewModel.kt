package com.example.museumapp.ui.admin.artifactform

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.data.repository.ArtifactFormData
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExistingImageUi(
    val path: String,
    val url: String,
    val markedForRemoval: Boolean = false
)

data class ArtifactFormUiState(
    val artifactCode: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val origin: String = "",
    val historicalPeriod: String = "",
    val material: String = "",
    val dimensions: String = "",
    val condition: String = "",
    val existingImages: List<ExistingImageUi> = emptyList(),
    val selectedImages: List<Uri> = emptyList(),
    val primaryExistingPath: String? = null,
    val primarySelectedUri: Uri? = null,
    val replaceImages: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val hasUnsavedChanges: Boolean = false,
    val shouldClose: Boolean = false
)

class ArtifactFormViewModel(
    private val repository: AdminRepository,
    private val artifactId: String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArtifactFormUiState(isLoading = artifactId != null))
    val uiState: StateFlow<ArtifactFormUiState> = _uiState.asStateFlow()

    init {
        if (artifactId != null) {
            loadArtifact(artifactId)
        }
    }

    fun updateArtifactCode(value: String) = updateField { it.copy(artifactCode = value) }
    fun updateName(value: String) = updateField { it.copy(name = value) }
    fun updateDescription(value: String) = updateField { it.copy(description = value) }
    fun updateCategory(value: String) = updateField { it.copy(category = value) }
    fun updateOrigin(value: String) = updateField { it.copy(origin = value) }
    fun updateHistoricalPeriod(value: String) = updateField { it.copy(historicalPeriod = value) }
    fun updateMaterial(value: String) = updateField { it.copy(material = value) }
    fun updateDimensions(value: String) = updateField { it.copy(dimensions = value) }
    fun updateCondition(value: String) = updateField { it.copy(condition = value) }

    fun toggleReplaceImages() {
        _uiState.update {
            it.copy(
                replaceImages = !it.replaceImages,
                primaryExistingPath = if (!it.replaceImages) null else it.primaryExistingPath,
                hasUnsavedChanges = true,
                errorMessage = null
            )
        }
    }

    fun addSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { state ->
            val unique = (state.selectedImages + uris).distinct()
            val existingCount = if (state.replaceImages) 0 else state.existingImages.count { !it.markedForRemoval }
            if (existingCount + unique.size > 5) {
                state.copy(errorMessage = "An artifact can have at most five images.")
            } else {
                state.copy(
                    selectedImages = unique,
                    primarySelectedUri = state.primarySelectedUri ?: unique.firstOrNull(),
                    hasUnsavedChanges = true,
                    errorMessage = null
                )
            }
        }
    }

    fun removeSelectedImage(uri: Uri) {
        _uiState.update {
            val remaining = it.selectedImages.filterNot { selected -> selected == uri }
            it.copy(
                selectedImages = remaining,
                primarySelectedUri = if (it.primarySelectedUri == uri) remaining.firstOrNull() else it.primarySelectedUri,
                hasUnsavedChanges = true
            )
        }
    }

    fun toggleExistingImageRemoval(path: String) {
        _uiState.update { state ->
            val updated = state.existingImages.map {
                if (it.path == path) it.copy(markedForRemoval = !it.markedForRemoval) else it
            }
            val removed = updated.firstOrNull { it.path == path }?.markedForRemoval == true
            state.copy(
                existingImages = updated,
                primaryExistingPath = if (removed && state.primaryExistingPath == path) null else state.primaryExistingPath,
                hasUnsavedChanges = true
            )
        }
    }

    fun selectPrimaryExisting(path: String) {
        _uiState.update {
            it.copy(primaryExistingPath = path, primarySelectedUri = null, hasUnsavedChanges = true)
        }
    }

    fun selectPrimarySelected(uri: Uri) {
        _uiState.update {
            it.copy(primarySelectedUri = uri, primaryExistingPath = null, hasUnsavedChanges = true)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val errors = validate(state)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, errorMessage = "Please check the highlighted fields.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, fieldErrors = emptyMap()) }
            val activeExistingPaths = if (state.replaceImages) {
                emptyList()
            } else {
                state.existingImages.filterNot { it.markedForRemoval }.map { it.path }
            }
            val selectedUris = state.selectedImages.orderedWithPrimary(state.primarySelectedUri)
            val form = ArtifactFormData(
                artifactCode = state.artifactCode.trim(),
                name = state.name.trim(),
                description = state.description.trim(),
                category = state.category.trim(),
                origin = state.origin.trim().ifBlank { null },
                historicalPeriod = state.historicalPeriod.trim().ifBlank { null },
                material = state.material.trim().ifBlank { null },
                dimensions = state.dimensions.trim().ifBlank { null },
                condition = state.condition.trim().ifBlank { null },
                removeImagePaths = if (state.replaceImages) emptyList() else state.existingImages.filter { it.markedForRemoval }.map { it.path },
                replaceImages = state.replaceImages,
                primaryImagePath = state.primaryExistingPath
            )

            val result = if (artifactId == null) {
                repository.createArtifact(form, selectedUris)
            } else {
                repository.updateArtifact(artifactId, form, selectedUris)
            }

            when (result) {
                is RepositoryResult.Success -> finishSuccessfulSave(result.data, activeExistingPaths, state.primarySelectedUri != null)
                is RepositoryResult.Error -> _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
            }
        }
    }

    fun requestClose() {
        _uiState.update { it.copy(shouldClose = true) }
    }

    fun clearCloseRequest() {
        _uiState.update { it.copy(shouldClose = false) }
    }

    private fun finishSuccessfulSave(artifact: ArtifactDto, oldPaths: List<String>, selectedNewPrimary: Boolean) {
        if (artifactId == null || !selectedNewPrimary) {
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    successMessage = "Artifact saved.",
                    hasUnsavedChanges = false,
                    shouldClose = true
                )
            }
            return
        }

        viewModelScope.launch {
            val newPrimary = artifact.imagePaths.firstOrNull { it !in oldPaths }
            if (newPrimary == null) {
                _uiState.update { it.copy(isSubmitting = false, successMessage = "Artifact saved.", hasUnsavedChanges = false, shouldClose = true) }
                return@launch
            }
            when (val primaryResult = repository.setPrimaryImage(artifact.id, newPrimary)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(isSubmitting = false, successMessage = "Artifact saved.", hasUnsavedChanges = false, shouldClose = true)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = primaryResult.message)
                }
            }
        }
    }

    private fun loadArtifact(id: String) {
        viewModelScope.launch {
            when (val result = repository.getArtifact(id)) {
                is RepositoryResult.Success -> {
                    val artifact = result.data
                    _uiState.update {
                        it.copy(
                            artifactCode = artifact.artifactCode,
                            name = artifact.name,
                            description = artifact.description,
                            category = artifact.category,
                            origin = artifact.origin.orEmpty(),
                            historicalPeriod = artifact.historicalPeriod.orEmpty(),
                            material = artifact.material.orEmpty(),
                            dimensions = artifact.dimensions.orEmpty(),
                            condition = artifact.condition.orEmpty(),
                            existingImages = artifact.imagePaths.mapIndexed { index, path ->
                                ExistingImageUi(path = path, url = artifact.imageUrls.getOrElse(index) { "" })
                            },
                            primaryExistingPath = artifact.primaryImagePath,
                            isLoading = false
                        )
                    }
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    private fun validate(state: ArtifactFormUiState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (state.artifactCode.isBlank()) errors["artifactCode"] = "Artifact code is required."
        if (state.name.isBlank()) errors["name"] = "Name is required."
        if (state.description.isBlank()) errors["description"] = "Description is required."
        if (state.category.isBlank()) errors["category"] = "Category is required."
        val existingCount = if (state.replaceImages) 0 else state.existingImages.count { !it.markedForRemoval }
        if (existingCount + state.selectedImages.size > 5) errors["images"] = "Use five images or fewer."
        return errors
    }

    private fun updateField(transform: (ArtifactFormUiState) -> ArtifactFormUiState) {
        _uiState.update { transform(it).copy(hasUnsavedChanges = true, errorMessage = null, fieldErrors = emptyMap()) }
    }

    private fun List<Uri>.orderedWithPrimary(primary: Uri?): List<Uri> {
        if (primary == null || primary !in this) return this
        return listOf(primary) + filterNot { it == primary }
    }

    companion object {
        fun factory(repository: AdminRepository, artifactId: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ArtifactFormViewModel(repository, artifactId) as T
            }
        }
    }
}
