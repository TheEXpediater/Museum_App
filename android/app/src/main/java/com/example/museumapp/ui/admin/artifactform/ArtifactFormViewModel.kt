package com.example.museumapp.ui.admin.artifactform

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.museumapp.data.model.ArtifactCategoryDto
import com.example.museumapp.data.model.ArtifactCustomFieldDto
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.ArtifactMetadataFieldDto
import com.example.museumapp.data.model.ArtifactMetadataSectionDto
import com.example.museumapp.data.model.ArtifactMetadataSectionIds
import com.example.museumapp.data.model.ArtifactValidationLimits
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
    val markedForRemoval: Boolean = false,
    val visitorSelected: Boolean = false
)

data class ArtifactFormUiState(
    val isNewArtifact: Boolean = true,
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
    val metadataSections: List<ArtifactMetadataSectionDto> = defaultMetadataSections(),
    val categories: List<ArtifactCategoryDto> = emptyList(),
    val primaryImageNeedsReview: Boolean = false,
    val existingImages: List<ExistingImageUi> = emptyList(),
    val selectedImages: List<Uri> = emptyList(),
    val primaryExistingPath: String? = null,
    val primarySelectedUri: Uri? = null,
    val visitorGalleryImagePaths: List<String> = emptyList(),
    val visitorGalleryConfigured: Boolean = false,
    val visitorSelectionVisible: Boolean = false,
    val visitorSelectionDraftPaths: List<String> = emptyList(),
    val visitorSelectionMessage: String? = null,
    val imageManagementSheetVisible: Boolean = false,
    val primarySelectionVisible: Boolean = false,
    val selectedPrimaryCandidateKey: String? = null,
    val replaceImagesConfirmationVisible: Boolean = false,
    val pendingRemoveImage: ExistingImageUi? = null,
    val primaryRemovalBlockedImage: ExistingImageUi? = null,
    val pendingDeleteMetadataSection: ArtifactMetadataSectionDto? = null,
    val replaceImages: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isImageOperationInProgress: Boolean = false,
    val isIndexingArtifact: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showPublishConfirmation: Boolean = false,
    val showCreateSuccess: Boolean = false,
    val showPublishSuccess: Boolean = false,
    val successArtifactName: String = "",
    val successArtifactStatus: String = "draft",
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
    private val _uiState = MutableStateFlow(ArtifactFormUiState(isNewArtifact = artifactId == null, isLoading = artifactId != null))
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

    fun refreshCategoriesAfterReturn(preselectName: String? = null) {
        loadCategories(preselectName)
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

    fun addImagesFromPicker(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val id = artifactId
        if (id == null) {
            addSelectedImages(uris)
            return
        }
        if (_uiState.value.isImageOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImageOperationInProgress = true, errorMessage = null, successMessage = null) }
            when (val result = repository.addImages(id, uris)) {
                is RepositoryResult.Success -> _uiState.update {
                    applyImageUpdate(
                        state = it,
                        artifact = result.data,
                        successMessage = "Images added."
                    ).copy(isImageOperationInProgress = false)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isImageOperationInProgress = false, errorMessage = result.message)
                }
            }
        }
    }

    fun requestReplaceImages() {
        _uiState.update { it.copy(replaceImagesConfirmationVisible = true, imageManagementSheetVisible = false, errorMessage = null) }
    }

    fun dismissReplaceImages() {
        _uiState.update { it.copy(replaceImagesConfirmationVisible = false) }
    }

    fun replaceWithSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _uiState.update { it.copy(replaceImagesConfirmationVisible = false) }
            return
        }
        _uiState.update {
            it.copy(
                selectedImages = uris.distinct(),
                primarySelectedUri = null,
                primaryExistingPath = null,
                visitorGalleryImagePaths = emptyList(),
                visitorGalleryConfigured = false,
                visitorSelectionDraftPaths = emptyList(),
                visitorSelectionVisible = false,
                replaceImages = true,
                replaceImagesConfirmationVisible = false,
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
                fieldErrors = emptyMap()
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

    private fun toggleExistingImageRemoval(path: String) {
        _uiState.update { state ->
            val wasMarked = state.existingImages.firstOrNull { it.path == path }?.markedForRemoval == true
            val willBeRemoved = !wasMarked
            val visitorGallery = if (willBeRemoved) {
                state.visitorGalleryImagePaths.filterNot { it == path }
            } else {
                state.visitorGalleryImagePaths
            }
            val updated = state.existingImages.map { image ->
                if (image.path == path) {
                    image.copy(markedForRemoval = willBeRemoved, visitorSelected = !willBeRemoved && image.visitorSelected)
                } else {
                    image
                }
            }.withVisitorSelection(visitorGallery).primaryFirst(state.primaryExistingPath)
            state.copy(
                existingImages = updated,
                visitorGalleryImagePaths = visitorGallery,
                visitorSelectionDraftPaths = state.visitorSelectionDraftPaths.filterNot { willBeRemoved && it == path },
                primaryExistingPath = if (willBeRemoved && state.primaryExistingPath == path) null else state.primaryExistingPath,
                hasUnsavedChanges = true,
                successMessage = null
            )
        }
    }

    fun requestExistingImageRemoval(image: ExistingImageUi) {
        if (image.markedForRemoval) {
            toggleExistingImageRemoval(image.path)
            return
        }
        _uiState.update {
            if (image.path == it.primaryExistingPath) {
                it.copy(primaryRemovalBlockedImage = image, errorMessage = null)
            } else {
                it.copy(pendingRemoveImage = image, errorMessage = null)
            }
        }
    }

    fun dismissExistingImageRemoval() {
        _uiState.update { it.copy(pendingRemoveImage = null, primaryRemovalBlockedImage = null) }
    }

    fun confirmExistingImageRemoval() {
        val image = _uiState.value.pendingRemoveImage ?: return
        _uiState.update { it.copy(pendingRemoveImage = null) }
        toggleExistingImageRemoval(image.path)
    }

    fun chooseMainImageAfterBlockedRemoval() {
        _uiState.update {
            it.copy(
                primaryRemovalBlockedImage = null,
                imageManagementSheetVisible = false,
                primarySelectionVisible = true,
                selectedPrimaryCandidateKey = currentPrimaryCandidateKey(it)
            )
        }
    }

    fun selectPrimaryExisting(path: String) {
        val id = artifactId
        if (id == null) {
            _uiState.update { state ->
                val visitorGallery = state.visitorGalleryImagePaths.filterNot { it == path }
                state.copy(
                    primaryExistingPath = path,
                    primarySelectedUri = null,
                    visitorGalleryImagePaths = visitorGallery,
                    existingImages = state.existingImages.withVisitorSelection(visitorGallery).primaryFirst(path),
                    primaryImageNeedsReview = false,
                    hasUnsavedChanges = true,
                    successMessage = null
                )
            }
            return
        }
        if (_uiState.value.isImageOperationInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImageOperationInProgress = true, errorMessage = null, successMessage = null) }
            when (val result = repository.setPrimaryImage(id, path)) {
                is RepositoryResult.Success -> _uiState.update {
                    applyImageUpdate(
                        state = it,
                        artifact = result.data,
                        successMessage = "Main image updated."
                    ).copy(isImageOperationInProgress = false)
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isImageOperationInProgress = false, errorMessage = result.message)
                }
            }
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

    fun openImageManagement() {
        _uiState.update { it.copy(imageManagementSheetVisible = true, errorMessage = null, successMessage = null) }
    }

    fun closeImageManagement() {
        _uiState.update { it.copy(imageManagementSheetVisible = false) }
    }

    fun openPrimarySelection() {
        _uiState.update {
            it.copy(
                imageManagementSheetVisible = false,
                primarySelectionVisible = true,
                selectedPrimaryCandidateKey = currentPrimaryCandidateKey(it),
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun closePrimarySelection() {
        _uiState.update { it.copy(primarySelectionVisible = false, selectedPrimaryCandidateKey = null) }
    }

    fun selectPrimaryCandidate(key: String) {
        _uiState.update { it.copy(selectedPrimaryCandidateKey = key) }
    }

    fun confirmPrimarySelection() {
        val state = _uiState.value
        val key = state.selectedPrimaryCandidateKey ?: return
        _uiState.update { it.copy(primarySelectionVisible = false, selectedPrimaryCandidateKey = null) }
        when {
            key.startsWith(EXISTING_IMAGE_KEY_PREFIX) -> {
                val path = key.removePrefix(EXISTING_IMAGE_KEY_PREFIX)
                if (state.existingImages.any { it.path == path && !it.markedForRemoval }) {
                    selectPrimaryExisting(path)
                }
            }
            key.startsWith(SELECTED_IMAGE_KEY_PREFIX) -> {
                val uriText = key.removePrefix(SELECTED_IMAGE_KEY_PREFIX)
                state.selectedImages.firstOrNull { it.toString() == uriText }?.let(::selectPrimarySelected)
            }
        }
    }

    fun reviewMainImage() {
        openPrimarySelection()
    }

    fun openVisitorSelection() {
        _uiState.update { state ->
            val draft = reconcileVisitorGalleryFromImages(
                selectedPaths = state.visitorGalleryImagePaths,
                existingImages = state.existingImages,
                primaryPath = state.primaryExistingPath
            )
            state.copy(
                imageManagementSheetVisible = false,
                visitorSelectionVisible = true,
                visitorSelectionDraftPaths = draft,
                visitorSelectionMessage = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun closeVisitorSelection() {
        _uiState.update {
            it.copy(
                visitorSelectionVisible = false,
                visitorSelectionDraftPaths = emptyList(),
                visitorSelectionMessage = null
            )
        }
    }

    fun toggleVisitorImage(path: String) {
        _uiState.update { state ->
            if (path == state.primaryExistingPath || state.existingImages.none { it.path == path && !it.markedForRemoval }) {
                return@update state
            }
            val selected = state.visitorSelectionDraftPaths
            val updated = if (path in selected) {
                selected.filterNot { it == path }
            } else {
                if (selected.size >= ArtifactValidationLimits.VisitorAdditionalImages) {
                    return@update state.copy(visitorSelectionMessage = "You can select up to 5 additional visitor images.")
                }
                selected + path
            }
            state.copy(visitorSelectionDraftPaths = updated, visitorSelectionMessage = null)
        }
    }

    fun confirmVisitorSelection() {
        _uiState.update { state ->
            val selected = reconcileVisitorGalleryFromImages(
                selectedPaths = state.visitorSelectionDraftPaths,
                existingImages = state.existingImages,
                primaryPath = state.primaryExistingPath
            )
            state.copy(
                visitorGalleryImagePaths = selected,
                visitorGalleryConfigured = true,
                existingImages = state.existingImages.withVisitorSelection(selected).primaryFirst(state.primaryExistingPath),
                visitorSelectionVisible = false,
                visitorSelectionDraftPaths = emptyList(),
                visitorSelectionMessage = null,
                hasUnsavedChanges = true,
                successMessage = null,
                errorMessage = null,
                fieldErrors = emptyMap()
            )
        }
    }

    fun indexArtifactImages() {
        val id = artifactId ?: run {
            _uiState.update { it.copy(errorMessage = "Save and publish this artifact before feeding it to the AI Library.") }
            return
        }
        if (!_uiState.value.status.equals("published", ignoreCase = true)) {
            _uiState.update { it.copy(errorMessage = "Publish this artifact before feeding it to the AI Library.") }
            return
        }
        if (_uiState.value.isIndexingArtifact) return
        viewModelScope.launch {
            _uiState.update { it.copy(isIndexingArtifact = true, errorMessage = null, successMessage = null) }
            when (val result = repository.indexArtifact(id)) {
                is RepositoryResult.Success -> _uiState.update {
                    it.copy(
                        isIndexingArtifact = false,
                        savedAiIndexStatus = result.data.aiIndexStatus,
                        savedAiIndexedImageCount = result.data.indexedImages,
                        savedAiIndexError = result.data.errors.firstOrNull(),
                        successMessage = "AI Library updated."
                    )
                }
                is RepositoryResult.Error -> _uiState.update {
                    it.copy(isIndexingArtifact = false, errorMessage = result.message)
                }
            }
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

    fun addMetadataField(sectionId: String) {
        _uiState.update { state ->
            val sections = updateSection(state.metadataSections, sectionId) { section ->
                val nextOrder = (section.fields.maxOfOrNull { it.order } ?: -1) + 1
                section.copy(fields = section.fields + blankMetadataField(nextOrder))
            }
            state.copy(
                metadataSections = sections,
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
                fieldErrors = emptyMap()
            )
        }
    }

    fun updateMetadataFieldLabel(sectionId: String, fieldId: String, value: String) {
        updateMetadataField(sectionId, fieldId) { it.copy(label = value) }
    }

    fun updateMetadataFieldValue(sectionId: String, fieldId: String, value: String) {
        updateMetadataField(sectionId, fieldId) { it.copy(value = value) }
    }

    fun updateMetadataFieldType(sectionId: String, fieldId: String, value: String) {
        updateMetadataField(sectionId, fieldId) {
            it.copy(type = value, unit = if (value == "number") it.unit else null)
        }
    }

    fun updateMetadataFieldUnit(sectionId: String, fieldId: String, value: String) {
        updateMetadataField(sectionId, fieldId) { it.copy(unit = value) }
    }

    fun removeMetadataField(sectionId: String, fieldId: String) {
        _uiState.update { state ->
            val sections = updateSection(state.metadataSections, sectionId) { section ->
                section.copy(fields = section.fields.filterNot { it.id == fieldId }.reorderedFields())
            }
            state.copy(metadataSections = sections, hasUnsavedChanges = true, successMessage = null, fieldErrors = emptyMap())
        }
    }

    fun createMetadataSection(title: String) {
        val cleanTitle = title.trim()
        when {
            cleanTitle.isBlank() -> _uiState.update { it.copy(errorMessage = "Section name is required.") }
            cleanTitle.length > ArtifactValidationLimits.MetadataSectionTitle -> _uiState.update {
                it.copy(errorMessage = "Section name contains more text than the supported limit.")
            }
            else -> _uiState.update { state ->
                val nextOrder = (state.metadataSections.maxOfOrNull { it.order } ?: 1) + 1
                val section = ArtifactMetadataSectionDto(
                    id = "section-${UUID.randomUUID()}",
                    title = cleanTitle,
                    order = nextOrder,
                    fields = listOf(blankMetadataField(0))
                )
                state.copy(
                    metadataSections = (state.metadataSections + section).normalizedMetadataSections(),
                    hasUnsavedChanges = true,
                    errorMessage = null,
                    successMessage = null,
                    fieldErrors = emptyMap()
                )
            }
        }
    }

    fun renameMetadataSection(sectionId: String, title: String) {
        val cleanTitle = title.trim()
        when {
            ArtifactMetadataSectionIds.SystemSections.contains(sectionId) -> Unit
            cleanTitle.isBlank() -> _uiState.update { it.copy(errorMessage = "Section name is required.") }
            cleanTitle.length > ArtifactValidationLimits.MetadataSectionTitle -> _uiState.update {
                it.copy(errorMessage = "Section name contains more text than the supported limit.")
            }
            else -> _uiState.update { state ->
                state.copy(
                    metadataSections = state.metadataSections.map { section ->
                        if (section.id == sectionId) section.copy(title = cleanTitle) else section
                    },
                    hasUnsavedChanges = true,
                    errorMessage = null,
                    successMessage = null,
                    fieldErrors = emptyMap()
                )
            }
        }
    }

    fun requestDeleteMetadataSection(sectionId: String) {
        if (ArtifactMetadataSectionIds.SystemSections.contains(sectionId)) return
        _uiState.update { state ->
            state.copy(
                pendingDeleteMetadataSection = state.metadataSections.firstOrNull { it.id == sectionId },
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun dismissDeleteMetadataSection() {
        _uiState.update { it.copy(pendingDeleteMetadataSection = null) }
    }

    fun confirmDeleteMetadataSection() {
        val section = _uiState.value.pendingDeleteMetadataSection ?: return
        if (ArtifactMetadataSectionIds.SystemSections.contains(section.id)) return
        _uiState.update { state ->
            state.copy(
                metadataSections = state.metadataSections.filterNot { it.id == section.id }.normalizedMetadataSections(),
                pendingDeleteMetadataSection = null,
                hasUnsavedChanges = true,
                successMessage = null,
                fieldErrors = emptyMap()
            )
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
        val state = _uiState.value
        if (state.isSubmitting) return
        val errors = validate(state, "published")
        if (errors.isNotEmpty()) {
            val message = errors["primaryImage"] ?: errors["publish"] ?: "Please check the highlighted fields."
            _uiState.update { it.copy(fieldErrors = errors, errorMessage = message) }
            return
        }
        _uiState.update {
            it.copy(
                showPublishConfirmation = true,
                errorMessage = null,
                successMessage = null,
                fieldErrors = emptyMap()
            )
        }
    }

    fun cancelPublish() {
        _uiState.update { it.copy(showPublishConfirmation = false) }
    }

    fun confirmPublish() {
        _uiState.update { it.copy(showPublishConfirmation = false) }
        save("published", publishConfirmed = true)
    }

    fun moveToDraft() {
        save("draft")
    }

    private fun save(targetStatus: String, publishConfirmed: Boolean = false) {
        val state = _uiState.value
        if (state.isSubmitting) return
        val publishingExistingDraft = !state.isNewArtifact && !state.status.equals("published", ignoreCase = true) && targetStatus == "published"
        if (publishingExistingDraft && !publishConfirmed) {
            publish()
            return
        }
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
            val activeExistingPaths = state.activeExistingImagePaths()
            val visitorGalleryPaths = reconcileVisitorGallerySelection(
                selectedPaths = state.visitorGalleryImagePaths,
                availablePaths = activeExistingPaths,
                primaryPath = state.primaryExistingPath
            )
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
                customFields = state.customFields.map { it.cleaned() },
                metadataSections = state.metadataSections.cleanedMetadataSections(),
                visitorGalleryImagePaths = visitorGalleryPaths,
                visitorGalleryConfigured = state.visitorGalleryConfigured,
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
                is RepositoryResult.Success -> applySavedArtifact(
                    artifact = result.data,
                    wasNew = state.isNewArtifact,
                    publishingExistingDraft = publishingExistingDraft
                )
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

    fun dismissCreateSuccess() {
        _uiState.update { it.copy(showCreateSuccess = false) }
    }

    fun dismissPublishSuccess() {
        _uiState.update { it.copy(showPublishSuccess = false) }
    }

    private fun applySavedArtifact(
        artifact: ArtifactDto,
        wasNew: Boolean,
        publishingExistingDraft: Boolean
    ) {
        _uiState.update {
            it.copy(
                isNewArtifact = false,
                artifactCode = artifact.artifactCode,
                name = artifact.name,
                description = artifact.description,
                category = artifact.category,
                origin = artifact.origin.orEmpty(),
                historicalPeriod = artifact.historicalPeriod.orEmpty(),
                material = artifact.material.orEmpty(),
                dimensions = artifact.dimensions.orEmpty(),
                condition = artifact.condition.orEmpty(),
                customFields = artifact.customFields,
                metadataSections = artifact.metadataSections.normalizedMetadataSections(),
                visitorGalleryImagePaths = artifact.visitorGalleryImagePaths,
                visitorGalleryConfigured = artifact.visitorGalleryConfigured,
                isSubmitting = false,
                successMessage = when {
                    wasNew -> null
                    publishingExistingDraft -> null
                    else -> "Changes Saved"
                },
                showCreateSuccess = wasNew,
                showPublishSuccess = publishingExistingDraft,
                successArtifactName = artifact.name,
                successArtifactStatus = artifact.status,
                savedAiIndexStatus = artifact.aiIndexStatus,
                savedAiIndexedImageCount = artifact.aiIndexedImageCount,
                savedAiIndexError = artifact.aiIndexError,
                status = artifact.status,
                existingImages = artifact.toExistingImages(),
                selectedImages = emptyList(),
                primaryExistingPath = artifact.primaryImagePath,
                primarySelectedUri = null,
                replaceImages = false,
                primaryImageNeedsReview = artifact.primaryImageNeedsReview,
                hasUnsavedChanges = false
            )
        }
    }

    private fun loadCategories(preselectName: String? = null) {
        viewModelScope.launch {
            when (val result = repository.listCategories()) {
                is RepositoryResult.Success -> _uiState.update { state ->
                    val selectedName = preselectName
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { requested -> result.data.firstOrNull { it.name.equals(requested, ignoreCase = true) }?.name ?: requested }
                    state.copy(
                        categories = result.data,
                        category = selectedName ?: state.category,
                        hasUnsavedChanges = state.hasUnsavedChanges || (selectedName != null && selectedName != state.category)
                    )
                }
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
                            metadataSections = artifact.metadataSections.normalizedMetadataSections(),
                            visitorGalleryImagePaths = artifact.visitorGalleryImagePaths,
                            visitorGalleryConfigured = artifact.visitorGalleryConfigured,
                            existingImages = artifact.toExistingImages(),
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
        validateTextSafety("artifactCode", "Artifact code", state.artifactCode, errors)
        validateTextSafety("name", "Artifact name", state.name, errors)
        validateTextSafety("description", "Description", state.description, errors)
        validateTextSafety("category", "Category", state.category, errors)
        validateTextSafety("origin", "Origin", state.origin, errors)
        validateTextSafety("historicalPeriod", "Historical period", state.historicalPeriod, errors)
        validateTextSafety("material", "Material", state.material, errors)
        validateTextSafety("dimensions", "Dimensions", state.dimensions, errors)
        validateTextSafety("condition", "Condition", state.condition, errors)
        validateLength("artifactCode", "Artifact code", state.artifactCode, ArtifactValidationLimits.ArtifactCode, errors)
        validateLength("name", "Artifact name", state.name, ArtifactValidationLimits.ArtifactName, errors)
        validateLength("description", "Description", state.description, ArtifactValidationLimits.Description, errors)
        validateLength("category", "Category", state.category, ArtifactValidationLimits.CategoryName, errors)
        validateLength("origin", "Origin", state.origin, ArtifactValidationLimits.LongMetadataValue, errors)
        validateLength("historicalPeriod", "Historical period", state.historicalPeriod, ArtifactValidationLimits.ShortMetadataValue, errors)
        validateLength("material", "Material", state.material, ArtifactValidationLimits.ShortMetadataValue, errors)
        validateLength("dimensions", "Dimensions", state.dimensions, ArtifactValidationLimits.ShortMetadataValue, errors)
        validateLength("condition", "Condition", state.condition, ArtifactValidationLimits.LongMetadataValue, errors)

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
        validateMetadataSections(state.metadataSections, errors)
        validateVisitorGallery(state, errors)
        return errors
    }

    private fun validateLength(key: String, label: String, value: String, limit: Int, errors: MutableMap<String, String>) {
        if (value.trim().length > limit) {
            errors[key] = "$label contains more text than the supported limit."
        }
    }

    private fun validateTextSafety(key: String, label: String, value: String?, errors: MutableMap<String, String>) {
        if (value.orEmpty().hasUnsupportedControlCharacters()) {
            errors[key] = "$label contains unsupported control characters."
        }
    }

    private fun validateCustomFields(fields: List<ArtifactCustomFieldDto>, errors: MutableMap<String, String>) {
        val labels = mutableSetOf<String>()
        fields.forEach { field ->
            val label = field.label.trim()
            val value = field.value.trim()
            validateTextSafety("customFields", "Additional information label", field.label, errors)
            validateTextSafety("customFields", "Additional information value", field.value, errors)
            validateTextSafety("customFields", "Additional information unit", field.unit, errors)
            if (label.isBlank()) {
                errors["customFields"] = "Additional information fields need labels."
            }
            if (label.length > ArtifactValidationLimits.MetadataLabel) {
                errors["customFields"] = "Additional information label contains more text than the supported limit."
            }
            if (!labels.add(label.lowercase())) {
                errors["customFields"] = "Additional information labels must be unique."
            }
            val valueLimit = if (field.type == "long_text") {
                ArtifactValidationLimits.LongMetadataValue
            } else {
                ArtifactValidationLimits.ShortMetadataValue
            }
            if (value.length > valueLimit) {
                errors["customFields"] = "Additional information value contains more text than the supported limit."
            }
            if (field.type == "number" && value.isNotBlank() && value.toDoubleOrNull() == null) {
                errors["customFields"] = "Number fields must contain valid numbers."
            }
            if (!field.unit.isNullOrBlank() && field.unit.length > ArtifactValidationLimits.MetadataUnit) {
                errors["customFields"] = "Additional information unit contains more text than the supported limit."
            }
        }
    }

    private fun validateMetadataSections(sections: List<ArtifactMetadataSectionDto>, errors: MutableMap<String, String>) {
        sections.forEach { section ->
            validateTextSafety("metadataSections", "Section name", section.title, errors)
            if (section.title.trim().isBlank()) {
                errors["metadataSections"] = "Section name is required."
            }
            if (section.title.trim().length > ArtifactValidationLimits.MetadataSectionTitle) {
                errors["metadataSections"] = "Section name contains more text than the supported limit."
            }
            section.fields.forEach { field ->
                val label = field.label.trim()
                val value = field.value.trim()
                validateTextSafety("metadataSections", "Metadata field label", field.label, errors)
                validateTextSafety("metadataSections", "Metadata field value", field.value, errors)
                validateTextSafety("metadataSections", "Metadata field unit", field.unit, errors)
                if (label.length > ArtifactValidationLimits.MetadataLabel) {
                    errors["metadataSections"] = "Metadata field label contains more text than the supported limit."
                }
                if (label.isBlank() && value.isNotBlank()) {
                    errors["metadataSections"] = "Metadata field label is required when a value is provided."
                }
                val valueLimit = if (field.type == "long_text") {
                    ArtifactValidationLimits.LongMetadataValue
                } else {
                    ArtifactValidationLimits.ShortMetadataValue
                }
                if (value.length > valueLimit) {
                    errors["metadataSections"] = "Metadata field value contains more text than the supported limit."
                }
                if (field.type == "number" && value.isNotBlank() && value.toDoubleOrNull() == null) {
                    errors["metadataSections"] = "Number metadata fields must contain valid numbers."
                }
                if (!field.unit.isNullOrBlank() && field.unit.length > ArtifactValidationLimits.MetadataUnit) {
                    errors["metadataSections"] = "Metadata field unit contains more text than the supported limit."
                }
            }
        }
    }

    private fun validateVisitorGallery(state: ArtifactFormUiState, errors: MutableMap<String, String>) {
        val activePaths = state.activeExistingImagePaths()
        val selected = state.visitorGalleryImagePaths
            .filterNot { it == state.primaryExistingPath }
            .distinct()
        if (selected.size > ArtifactValidationLimits.VisitorAdditionalImages) {
            errors["visitorGallery"] = "You can select up to 5 additional visitor images."
        }
        if (selected.any { it !in activePaths }) {
            errors["visitorGallery"] = "Visitor gallery images must belong to this artifact."
        }
    }

    private fun updateMetadataField(sectionId: String, fieldId: String, transform: (ArtifactMetadataFieldDto) -> ArtifactMetadataFieldDto) {
        _uiState.update { state ->
            val sections = updateSection(state.metadataSections, sectionId) { section ->
                section.copy(fields = section.fields.map { field -> if (field.id == fieldId) transform(field) else field })
            }
            state.copy(
                metadataSections = sections,
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
                fieldErrors = emptyMap()
            )
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

    private fun applyImageUpdate(
        state: ArtifactFormUiState,
        artifact: ArtifactDto,
        successMessage: String
    ): ArtifactFormUiState {
        return state.copy(
            existingImages = artifact.toExistingImages(),
            primaryExistingPath = artifact.primaryImagePath,
            visitorGalleryImagePaths = artifact.visitorGalleryImagePaths,
            visitorGalleryConfigured = artifact.visitorGalleryConfigured,
            primarySelectedUri = null,
            replaceImages = false,
            selectedImages = emptyList(),
            primaryImageNeedsReview = artifact.primaryImageNeedsReview,
            savedAiIndexStatus = artifact.aiIndexStatus,
            savedAiIndexedImageCount = artifact.aiIndexedImageCount,
            savedAiIndexError = artifact.aiIndexError,
            successMessage = successMessage,
            errorMessage = null
        )
    }

    private fun currentPrimaryCandidateKey(state: ArtifactFormUiState): String? {
        state.primarySelectedUri?.let { return SELECTED_IMAGE_KEY_PREFIX + it }
        state.primaryExistingPath?.let { return EXISTING_IMAGE_KEY_PREFIX + it }
        return null
    }

    companion object {
        const val EXISTING_IMAGE_KEY_PREFIX = "existing:"
        const val SELECTED_IMAGE_KEY_PREFIX = "selected:"

        fun factory(repository: AdminRepositoryContract, artifactId: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ArtifactFormViewModel(repository, artifactId) as T
            }
        }
    }
}

fun defaultMetadataSections(): List<ArtifactMetadataSectionDto> {
    return listOf(
        ArtifactMetadataSectionDto(
            id = ArtifactMetadataSectionIds.HistoricalDetails,
            title = "Historical Details",
            order = 0,
            fields = emptyList()
        ),
        ArtifactMetadataSectionDto(
            id = ArtifactMetadataSectionIds.PhysicalDetails,
            title = "Physical Details",
            order = 1,
            fields = emptyList()
        )
    )
}

private fun blankMetadataField(order: Int): ArtifactMetadataFieldDto {
    return ArtifactMetadataFieldDto(
        id = "field-${UUID.randomUUID()}",
        label = "",
        value = "",
        type = "text",
        unit = null,
        order = order
    )
}

private fun updateSection(
    sections: List<ArtifactMetadataSectionDto>,
    sectionId: String,
    transform: (ArtifactMetadataSectionDto) -> ArtifactMetadataSectionDto
): List<ArtifactMetadataSectionDto> {
    return sections.normalizedMetadataSections().map { section ->
        if (section.id == sectionId) {
            transform(section).let { updated -> updated.copy(fields = updated.fields.reorderedFields()) }
        } else {
            section
        }
    }.normalizedMetadataSections()
}

private fun List<ArtifactMetadataFieldDto>.reorderedFields(): List<ArtifactMetadataFieldDto> {
    return sortedBy { it.order }.mapIndexed { index, field -> field.copy(order = index) }
}

fun List<ArtifactMetadataSectionDto>.normalizedMetadataSections(): List<ArtifactMetadataSectionDto> {
    val byId = associateBy { it.id }
    val historical = (byId[ArtifactMetadataSectionIds.HistoricalDetails]
        ?: defaultMetadataSections()[0]).copy(title = "Historical Details", order = 0)
    val physical = (byId[ArtifactMetadataSectionIds.PhysicalDetails]
        ?: defaultMetadataSections()[1]).copy(title = "Physical Details", order = 1)
    val custom = filterNot { it.id in ArtifactMetadataSectionIds.SystemSections }
        .sortedBy { it.order }
        .mapIndexed { index, section -> section.copy(order = index + 2, fields = section.fields.reorderedFields()) }
    return listOf(historical.copy(fields = historical.fields.reorderedFields()), physical.copy(fields = physical.fields.reorderedFields())) + custom
}

private fun List<ArtifactMetadataSectionDto>.cleanedMetadataSections(): List<ArtifactMetadataSectionDto> {
    return normalizedMetadataSections().map { section ->
        section.copy(
            title = section.title.trim(),
            fields = section.fields.map { field ->
                field.copy(
                    label = field.label.trim(),
                    value = field.value.trim(),
                    type = field.type,
                    unit = field.unit?.trim()?.ifBlank { null }
                )
            }.reorderedFields()
        )
    }
}

private fun ArtifactCustomFieldDto.cleaned(): ArtifactCustomFieldDto {
    return copy(
        label = label.trim(),
        value = value.trim(),
        unit = unit?.trim()?.ifBlank { null }
    )
}

fun ArtifactFormUiState.metadataSection(sectionId: String): ArtifactMetadataSectionDto {
    return metadataSections.normalizedMetadataSections().first { it.id == sectionId }
}

fun ArtifactFormUiState.customMetadataSections(): List<ArtifactMetadataSectionDto> {
    return metadataSections.normalizedMetadataSections().filterNot { it.id in ArtifactMetadataSectionIds.SystemSections }
}

fun ArtifactFormUiState.activeExistingImagePaths(): List<String> {
    if (replaceImages) return emptyList()
    return existingImages.filterNot { it.markedForRemoval }.map { it.path }
}

fun ArtifactFormUiState.adminExistingImages(): List<ExistingImageUi> {
    return if (replaceImages) emptyList() else existingImages.primaryFirst(primaryExistingPath)
}

fun ArtifactFormUiState.adminSelectedImages(): List<Uri> {
    val primary = primarySelectedUri
    return if (primary != null && primary in selectedImages) {
        listOf(primary) + selectedImages.filterNot { it == primary }
    } else {
        selectedImages
    }
}

private fun ArtifactDto.toExistingImages(): List<ExistingImageUi> {
    val selected = visitorGalleryImagePaths.toSet()
    return imagePaths.mapIndexed { index, path ->
        ExistingImageUi(
            path = path,
            url = imageUrls.getOrElse(index) { "" },
            visitorSelected = path in selected
        )
    }.primaryFirst(primaryImagePath)
}

private fun List<ExistingImageUi>.withVisitorSelection(selectedPaths: List<String>): List<ExistingImageUi> {
    val selected = selectedPaths.toSet()
    return map { it.copy(visitorSelected = it.path in selected && !it.markedForRemoval) }
}

private fun List<ExistingImageUi>.primaryFirst(primaryPath: String?): List<ExistingImageUi> {
    val primary = firstOrNull { it.path == primaryPath }
    return if (primary == null) this else listOf(primary) + filterNot { it.path == primaryPath }
}

private fun reconcileVisitorGalleryFromImages(
    selectedPaths: List<String>,
    existingImages: List<ExistingImageUi>,
    primaryPath: String?
): List<String> {
    return reconcileVisitorGallerySelection(
        selectedPaths = selectedPaths,
        availablePaths = existingImages.filterNot { it.markedForRemoval }.map { it.path },
        primaryPath = primaryPath
    )
}

private fun reconcileVisitorGallerySelection(
    selectedPaths: List<String>,
    availablePaths: List<String>,
    primaryPath: String?
): List<String> {
    val available = availablePaths.toSet()
    return selectedPaths
        .filter { it in available && it != primaryPath }
        .distinct()
        .take(ArtifactValidationLimits.VisitorAdditionalImages)
}

private fun String.hasUnsupportedControlCharacters(): Boolean {
    return any { character ->
        val code = character.code
        (code < 32 && code !in setOf(9, 10, 13)) || code == 127 || code in 128..159
    }
}
