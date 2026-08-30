package com.example.museumapp.ui.admin.artifactform

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArtifactCategoryDto
import com.example.museumapp.data.model.ArtifactCustomFieldDto
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.data.repository.ArtifactFormData
import com.example.museumapp.data.repository.RepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ExistingImageUi(
    val path: String,
    val url: String,
    val markedForRemoval: Boolean = false
)

data class ArtifactFormUiState(
    val artifactCode: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "Uncategorized",
    val status: String = "draft",
    val origin: String = "",
    val historicalPeriod: String = "",
    val material: String = "",
    val dimensions: String = "",
    val condition: String = "",
    val customFields: List<ArtifactCustomFieldDto> = emptyList(),
    val categories: List<ArtifactCategoryDto> = emptyList(),
    val categoryActionMessage: String? = null,
    val primaryImageNeedsReview: Boolean = false,
    val existingImages: List<ExistingImageUi> = emptyList(),
    val selectedImages: List<Uri> = emptyList(),
    val primaryExistingPath: String? = null,
    val primarySelectedUri: Uri? = null,
    val replaceImages: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val savedAiIndexStatus: String? = null,
    val savedAiIndexedImageCount: Int? = null,
    val savedAiIndexError: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val hasUnsavedChanges: Boolean = false,
    val shouldClose: Boolean = false
)

class ArtifactFormViewModel(
    private val repository: AdminRepositoryContract,
    private val artifactId: String?
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArtifactFormUiState(isLoading = artifactId != null))
    val uiState: StateFlow<ArtifactFormUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
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
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun addSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { state ->
            val unique = (state.selectedImages + uris).distinct()
            state.copy(
                selectedImages = unique,
                hasUnsavedChanges = true,
                successMessage = null,
                errorMessage = null
            )
        }
    }

    fun removeSelectedImage(uri: Uri) {
        _uiState.update {
            val remaining = it.selectedImages.filterNot { selected -> selected == uri }
            it.copy(
                selectedImages = remaining,
                primarySelectedUri = if (it.primarySelectedUri == uri) null else it.primarySelectedUri,
                hasUnsavedChanges = true,
                successMessage = null
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
                hasUnsavedChanges = true,
                successMessage = null
            )
        }
    }

    fun selectPrimaryExisting(path: String) {
        _uiState.update {
            it.copy(
                primaryExistingPath = path,
                primarySelectedUri = null,
                primaryImageNeedsReview = false,
                hasUnsavedChanges = true,
                successMessage = null
            )
        }
    }

    fun selectPrimarySelected(uri: Uri) {
        _uiState.update {
            it.copy(
                primarySelectedUri = uri,
                primaryExistingPath = null,
                primaryImageNeedsReview = false,
                hasUnsavedChanges = true,
                successMessage = null
            )
        }
    }

    fun addCustomField(label: String, type: String, value: String, unit: String?) {
        val field = ArtifactCustomFieldDto(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            value = value.trim(),
            unit = unit?.trim()?.ifBlank { null },
            type = type
        )
        _uiState.update {
            it.copy(
                customFields = it.customFields + field,
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
                fieldErrors = emptyMap()
            )
        }
    }

    fun updateCustomField(field: ArtifactCustomFieldDto) {
        _uiState.update {
            it.copy(
                customFields = it.customFields.map { existing -> if (existing.id == field.id) field else existing },
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
                fieldErrors = emptyMap()
            )
        }
    }

    fun removeCustomField(id: String) {
        _uiState.update {
            it.copy(
                customFields = it.customFields.filterNot { field -> field.id == id },
                hasUnsavedChanges = true,
                successMessage = null
            )
        }
    }

    fun createCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.createCategory(trimmed)) {
                is RepositoryResult.Success -> _uiState.update {
                    val categories = (it.categories + result.data).distinctBy { category -> category.id }.sortedBy { category -> category.name.lowercase() }
                    it.copy(categories = categories, category = result.data.name, categoryActionMessage = "Category added.", hasUnsavedChanges = true)
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun renameSelectedCategory(name: String) {
        val state = _uiState.value
        val selected = state.categories.firstOrNull { it.name.equals(state.category, ignoreCase = true) } ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.renameCategory(selected.id, trimmed)) {
                is RepositoryResult.Success -> _uiState.update {
                    val categories = it.categories.map { category -> if (category.id == selected.id) result.data else category }
                        .sortedBy { category -> category.name.lowercase() }
                    it.copy(categories = categories, category = result.data.name, categoryActionMessage = "Category renamed.", hasUnsavedChanges = true)
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun deactivateSelectedCategory() {
        val state = _uiState.value
        val selected = state.categories.firstOrNull { it.name.equals(state.category, ignoreCase = true) } ?: return
        viewModelScope.launch {
            when (val result = repository.deactivateCategory(selected.id)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        categories = it.categories.filterNot { category -> category.id == result.data.id },
                        category = "Uncategorized",
                        categoryActionMessage = "Category deactivated.",
                        hasUnsavedChanges = true
                    )
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun saveDraftOrChanges() {
        val status = if (_uiState.value.status == "published") "published" else "draft"
        save(status)
    }

    fun saveDraft() {
        save("draft")
    }

    fun publish() {
        save("published")
    }

    fun moveToDraft() {
        save("draft")
    }

    private fun save(targetStatus: String) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (!state.hasUnsavedChanges && state.successMessage != null && targetStatus == state.status) return
        val errors = validate(state, targetStatus)
        if (errors.isNotEmpty()) {
            val message = errors["primaryImage"] ?: errors["publish"] ?: "Please check the highlighted fields."
            _uiState.update { it.copy(fieldErrors = errors, errorMessage = message) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, fieldErrors = emptyMap()) }
            val selectedUris = state.selectedImages
            val primaryImageIndex = state.primarySelectedUri?.let { state.selectedImages.indexOf(it) }?.takeIf { it >= 0 }
            val form = ArtifactFormData(
                artifactCode = state.artifactCode.trim(),
                name = state.name.trim(),
                description = state.description.trim(),
                category = state.category.trim(),
                status = targetStatus,
                origin = state.origin.trim().ifBlank { null },
                historicalPeriod = state.historicalPeriod.trim().ifBlank { null },
                material = state.material.trim().ifBlank { null },
                dimensions = state.dimensions.trim().ifBlank { null },
                condition = state.condition.trim().ifBlank { null },
                customFields = state.customFields,
                removeImagePaths = if (state.replaceImages) emptyList() else state.existingImages.filter { it.markedForRemoval }.map { it.path },
                replaceImages = state.replaceImages,
                primaryImagePath = state.primaryExistingPath,
                primaryImageIndex = primaryImageIndex
            )

            val result = if (artifactId == null) {
                repository.createArtifact(form, selectedUris)
            } else {
                repository.updateArtifact(artifactId, form, selectedUris)
            }

            when (result) {
                is RepositoryResult.Success -> applySavedArtifact(result.data)
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

    private fun applySavedArtifact(artifact: ArtifactDto) {
        _uiState.update {
            it.copy(
                isSubmitting = false,
                successMessage = "Artifact saved.",
                savedAiIndexStatus = artifact.aiIndexStatus,
                savedAiIndexedImageCount = artifact.aiIndexedImageCount,
                savedAiIndexError = artifact.aiIndexError,
                status = artifact.status,
                primaryImageNeedsReview = artifact.primaryImageNeedsReview,
                hasUnsavedChanges = false
            )
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            when (val result = repository.listCategories()) {
                is RepositoryResult.Success -> _uiState.update { it.copy(categories = result.data) }
                is RepositoryResult.Error -> Unit
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
                            status = artifact.status,
                            origin = artifact.origin.orEmpty(),
                            historicalPeriod = artifact.historicalPeriod.orEmpty(),
                            material = artifact.material.orEmpty(),
                            dimensions = artifact.dimensions.orEmpty(),
                            condition = artifact.condition.orEmpty(),
                            customFields = artifact.customFields,
                            existingImages = artifact.imagePaths.mapIndexed { index, path ->
                                ExistingImageUi(path = path, url = artifact.imageUrls.getOrElse(index) { "" })
                            },
                            primaryExistingPath = artifact.primaryImagePath,
                            savedAiIndexStatus = artifact.aiIndexStatus,
                            savedAiIndexedImageCount = artifact.aiIndexedImageCount,
                            savedAiIndexError = artifact.aiIndexError,
                            primaryImageNeedsReview = artifact.primaryImageNeedsReview,
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

    private fun validate(state: ArtifactFormUiState, targetStatus: String): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (state.artifactCode.isBlank()) errors["artifactCode"] = "Artifact code is required."
        if (state.name.isBlank()) errors["name"] = "Name is required."
        val existingCount = if (state.replaceImages) 0 else state.existingImages.count { !it.markedForRemoval }
        val hasImages = existingCount + state.selectedImages.size > 0
        val hasPrimary = state.primaryExistingPath != null || state.primarySelectedUri != null
        if (hasImages && !hasPrimary) errors["primaryImage"] = "Select a main image before saving."
        if (targetStatus == "published") {
            if (state.category.isBlank() || state.category.equals("Uncategorized", ignoreCase = true)) {
                errors["category"] = "Choose a category before publishing."
            }
            if (!hasImages || !hasPrimary) {
                errors["publish"] = "Complete these fields before publishing: Primary image"
            }
        }
        validateCustomFields(state.customFields, errors)
        return errors
    }

    private fun validateCustomFields(fields: List<ArtifactCustomFieldDto>, errors: MutableMap<String, String>) {
        val labels = mutableSetOf<String>()
        fields.forEach { field ->
            val label = field.label.trim()
            val value = field.value.trim()
            if (label.isBlank()) {
                errors["customFields"] = "Additional information fields need labels."
            }
            if (!labels.add(label.lowercase())) {
                errors["customFields"] = "Additional information labels must be unique."
            }
            if (field.type == "number" && value.isNotBlank() && value.toDoubleOrNull() == null) {
                errors["customFields"] = "Number fields must contain valid numbers."
            }
        }
    }

    private fun updateField(transform: (ArtifactFormUiState) -> ArtifactFormUiState) {
        _uiState.update {
            transform(it).copy(
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
                fieldErrors = emptyMap()
            )
        }
    }

    companion object {
        fun factory(repository: AdminRepositoryContract, artifactId: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ArtifactFormViewModel(repository, artifactId) as T
            }
        }
    }
}
