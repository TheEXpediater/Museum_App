package com.example.museumapp.ui.admin.artifactform

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.museumapp.data.model.ArtifactCategoryDto
import com.example.museumapp.data.model.ArtifactCustomFieldDto
import com.example.museumapp.data.model.ArtifactMetadataFieldDto
import com.example.museumapp.data.model.ArtifactMetadataSectionDto
import com.example.museumapp.data.model.ArtifactMetadataSectionIds
import com.example.museumapp.data.model.ArtifactValidationLimits
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.ui.admin.components.ArtifactAiStatusChip

private val FieldTypes = listOf("text", "number", "long_text", "date")

private enum class ImagePickerMode {
    Add,
    Replace
}

private data class ImageCandidateUi(
    val key: String,
    val model: Any,
    val label: String,
    val isPrimary: Boolean,
    val removed: Boolean = false
)

private data class VisitorImageCandidateUi(
    val path: String,
    val url: String,
    val label: String,
    val isPrimary: Boolean,
    val selected: Boolean,
    val removed: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactFormScreen(
    repository: AdminRepository,
    artifactId: String?,
    onClose: () -> Unit,
    onViewArtifacts: () -> Unit = onClose,
    onManageCategories: () -> Unit = {},
    categoryResultName: String? = null,
    onCategoryResultConsumed: () -> Unit = {}
) {
    val viewModel: ArtifactFormViewModel = viewModel(
        key = artifactId ?: "create_artifact",
        factory = ArtifactFormViewModel.factory(repository, artifactId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomFieldDialog by rememberSaveable { mutableStateOf(false) }
    var showAddSectionDialog by rememberSaveable { mutableStateOf(false) }
    var renamingSectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingCustomFieldId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingCustomField = uiState.customFields.firstOrNull { it.id == editingCustomFieldId }
    val renamingSection = uiState.metadataSections.firstOrNull { it.id == renamingSectionId }
    var imagePickerMode by remember { mutableStateOf<ImagePickerMode?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            when (imagePickerMode) {
                ImagePickerMode.Add -> viewModel.addImagesFromPicker(uris)
                ImagePickerMode.Replace -> viewModel.replaceWithSelectedImages(uris)
                null -> Unit
            }
            imagePickerMode = null
        }
    )

    fun launchImagePicker(mode: ImagePickerMode) {
        imagePickerMode = mode
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCategoriesAfterReturn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(categoryResultName) {
        val categoryName = categoryResultName?.trim()
        if (!categoryName.isNullOrBlank()) {
            viewModel.refreshCategoriesAfterReturn(categoryName)
            onCategoryResultConsumed()
        }
    }

    LaunchedEffect(uiState.shouldClose) {
        if (uiState.shouldClose) {
            viewModel.clearCloseRequest()
            onClose()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.successMessage) {
        val message = uiState.successMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSuccessMessage()
        }
    }

    fun handleBack() {
        if (uiState.hasUnsavedChanges && !uiState.isSubmitting) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(onBack = ::handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (artifactId == null) "Add Artifact" else "Edit Artifact") },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::saveDraftOrChanges, enabled = !uiState.isSubmitting && !uiState.isLoading) {
                        Icon(Icons.Outlined.Save, contentDescription = "Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item { StatusBanner(uiState.status) }
            if (uiState.primaryImageNeedsReview) {
                item { PrimaryReviewBanner(onReview = viewModel::reviewMainImage) }
            }
            item { SectionTitle("Basic Information") }
            item {
                FormTextField(
                    value = uiState.artifactCode,
                    onValueChange = viewModel::updateArtifactCode,
                    label = "Artifact code *",
                    error = uiState.fieldErrors["artifactCode"]
                )
            }
            item {
                FormTextField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    label = "Artifact name *",
                    error = uiState.fieldErrors["name"]
                )
            }
            item {
                FormTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    label = "Description",
                    error = uiState.fieldErrors["description"],
                    minLines = 4,
                    showCounter = true
                )
            }
            item {
                CategorySection(
                    category = uiState.category,
                    categories = uiState.categories,
                    error = uiState.fieldErrors["category"],
                    onCategoryChange = viewModel::updateCategory,
                    onManageCategories = onManageCategories
                )
            }
            item {
                MetadataSectionCard(
                    section = uiState.metadataSection(ArtifactMetadataSectionIds.HistoricalDetails),
                    error = uiState.fieldErrors["metadataSections"],
                    onAddField = { viewModel.addMetadataField(ArtifactMetadataSectionIds.HistoricalDetails) },
                    onLabelChange = viewModel::updateMetadataFieldLabel,
                    onValueChange = viewModel::updateMetadataFieldValue,
                    onTypeChange = viewModel::updateMetadataFieldType,
                    onUnitChange = viewModel::updateMetadataFieldUnit,
                    onRemoveField = viewModel::removeMetadataField
                ) {
                    FormTextField(uiState.origin, viewModel::updateOrigin, "Origin", error = uiState.fieldErrors["origin"], minLines = 2)
                    FormTextField(uiState.historicalPeriod, viewModel::updateHistoricalPeriod, "Historical period", error = uiState.fieldErrors["historicalPeriod"])
                }
            }
            item {
                MetadataSectionCard(
                    section = uiState.metadataSection(ArtifactMetadataSectionIds.PhysicalDetails),
                    error = uiState.fieldErrors["metadataSections"],
                    onAddField = { viewModel.addMetadataField(ArtifactMetadataSectionIds.PhysicalDetails) },
                    onLabelChange = viewModel::updateMetadataFieldLabel,
                    onValueChange = viewModel::updateMetadataFieldValue,
                    onTypeChange = viewModel::updateMetadataFieldType,
                    onUnitChange = viewModel::updateMetadataFieldUnit,
                    onRemoveField = viewModel::removeMetadataField
                ) {
                    FormTextField(uiState.material, viewModel::updateMaterial, "Material", error = uiState.fieldErrors["material"], minLines = 2)
                    FormTextField(uiState.dimensions, viewModel::updateDimensions, "Dimensions", error = uiState.fieldErrors["dimensions"])
                    FormTextField(uiState.condition, viewModel::updateCondition, "Condition", error = uiState.fieldErrors["condition"], minLines = 3)
                }
            }
            item {
                OutlinedButton(
                    onClick = { showAddSectionDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add Section", maxLines = 1)
                }
            }
            items(uiState.customMetadataSections(), key = { it.id }) { section ->
                MetadataSectionCard(
                    section = section,
                    error = uiState.fieldErrors["metadataSections"],
                    onAddField = { viewModel.addMetadataField(section.id) },
                    onLabelChange = viewModel::updateMetadataFieldLabel,
                    onValueChange = viewModel::updateMetadataFieldValue,
                    onTypeChange = viewModel::updateMetadataFieldType,
                    onUnitChange = viewModel::updateMetadataFieldUnit,
                    onRemoveField = viewModel::removeMetadataField,
                    onRenameSection = { renamingSectionId = section.id },
                    onDeleteSection = { viewModel.requestDeleteMetadataSection(section.id) }
                )
            }
            item {
                CustomFieldsSection(
                    fields = uiState.customFields,
                    error = uiState.fieldErrors["customFields"],
                    onAdd = {
                        editingCustomFieldId = null
                        showCustomFieldDialog = true
                    },
                    onEdit = {
                        editingCustomFieldId = it.id
                        showCustomFieldDialog = true
                    },
                    onRemove = { viewModel.removeCustomField(it.id) }
                )
            }
            item {
                ImageSectionHeader(
                    imageCount = (if (uiState.replaceImages) 0 else uiState.existingImages.count { !it.markedForRemoval }) + uiState.selectedImages.size,
                    error = uiState.fieldErrors["images"] ?: uiState.fieldErrors["primaryImage"],
                    isBusy = uiState.isImageOperationInProgress,
                    onManageImages = viewModel::openImageManagement
                )
            }
            if (uiState.existingImages.isNotEmpty() && !uiState.replaceImages) {
                item {
                    ExistingImagesRow(
                        images = uiState.adminExistingImages(),
                        primaryPath = uiState.primaryExistingPath,
                        onRequestRemove = viewModel::requestExistingImageRemoval
                    )
                }
            }
            if (uiState.selectedImages.isNotEmpty()) {
                item {
                    SelectedImagesRow(
                        images = uiState.adminSelectedImages(),
                        primaryUri = uiState.primarySelectedUri,
                        onRemove = viewModel::removeSelectedImage
                    )
                }
            }
            if (uiState.errorMessage != null) {
                item { Text(uiState.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
            item {
                SaveActions(
                    isNewArtifact = uiState.isNewArtifact,
                    status = uiState.status,
                    isSubmitting = uiState.isSubmitting,
                    onSaveDraft = viewModel::saveDraft,
                    onPublish = viewModel::publish,
                    onSaveChanges = viewModel::saveDraftOrChanges
                )
            }
        }
    }

    if (uiState.showPublishConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelPublish,
            title = { Text("Publish artifact?") },
            text = {
                Text(
                    "\"${uiState.name.ifBlank { "This artifact" }}\" will become visible to museum visitors.\n\nPublishing does not automatically add the artifact to the AI Library."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmPublish, enabled = !uiState.isSubmitting) {
                    Text("Publish", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPublish, enabled = !uiState.isSubmitting) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }

    if (uiState.showNoChangesModal) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNoChangesModal,
            title = { Text("No changes were made.") },
            text = { Text("There is nothing to save on this artifact yet.") },
            confirmButton = {
                Button(onClick = viewModel::dismissNoChangesModal) {
                    Text("Remain", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissNoChangesModal()
                        onViewArtifacts()
                    }
                ) {
                    Text("Back to Artifact List", maxLines = 1)
                }
            }
        )
    }

    if (uiState.showCreateSuccess) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Artifact Added") },
            text = {
                Text(
                    "\"${uiState.successArtifactName}\" was added successfully.\n\nStatus: ${uiState.successArtifactStatus.statusTitle()}"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissCreateSuccess()
                        onViewArtifacts()
                    }
                ) {
                    Text("View Artifacts", maxLines = 1)
                }
            }
        )
    }

    if (uiState.showPublishSuccess) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPublishSuccess,
            title = { Text("Artifact Published") },
            text = {
                Text(
                    "\"${uiState.successArtifactName}\" is now visible to visitors.\n\nThis artifact has not yet been added to the AI Library."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::dismissPublishSuccess) {
                    Text("Done", maxLines = 1)
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes") },
            text = { Text("Leave this artifact without saving?") },
            confirmButton = {
                Button(onClick = {
                    showDiscardDialog = false
                    onClose()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCustomFieldDialog) {
        CustomFieldDialog(
            field = editingCustomField,
            onDismiss = {
                editingCustomFieldId = null
                showCustomFieldDialog = false
            },
            onConfirm = { label, type, value, unit ->
                if (editingCustomField == null) {
                    viewModel.addCustomField(label, type, value, unit)
                } else {
                    viewModel.updateCustomField(
                        editingCustomField.copy(
                            label = label.trim(),
                            type = type,
                            value = value.trim(),
                            unit = unit?.trim()?.ifBlank { null }
                        )
                    )
                }
                editingCustomFieldId = null
                showCustomFieldDialog = false
            }
        )
    }

    if (showAddSectionDialog) {
        SectionNameDialog(
            title = "New Information Section",
            confirmLabel = "Create Section",
            initialValue = "",
            onDismiss = { showAddSectionDialog = false },
            onConfirm = {
                showAddSectionDialog = false
                viewModel.createMetadataSection(it)
            }
        )
    }

    if (renamingSection != null) {
        SectionNameDialog(
            title = "Rename Section",
            confirmLabel = "Rename",
            initialValue = renamingSection.title,
            onDismiss = { renamingSectionId = null },
            onConfirm = {
                renamingSectionId = null
                viewModel.renameMetadataSection(renamingSection.id, it)
            }
        )
    }

    if (uiState.imageManagementSheetVisible) {
        ImageManagementSheet(
            imageCount = (if (uiState.replaceImages) 0 else uiState.existingImages.count { !it.markedForRemoval }) + uiState.selectedImages.size,
            status = uiState.savedAiIndexStatus,
            indexError = uiState.savedAiIndexError,
            isIndexing = uiState.isIndexingArtifact,
            isBusy = uiState.isImageOperationInProgress,
            canIndex = artifactId != null && uiState.status.equals("published", ignoreCase = true),
            onDismiss = viewModel::closeImageManagement,
            onAddImages = {
                viewModel.closeImageManagement()
                launchImagePicker(ImagePickerMode.Add)
            },
            onSelectMainImage = viewModel::openPrimarySelection,
            onSelectVisitorImages = viewModel::openVisitorSelection,
            onReplaceImages = viewModel::requestReplaceImages,
            onIndexArtifact = viewModel::indexArtifactImages
        )
    }

    if (uiState.primarySelectionVisible) {
        PrimarySelectionSheet(
            candidates = imageCandidates(uiState),
            selectedKey = uiState.selectedPrimaryCandidateKey,
            isBusy = uiState.isImageOperationInProgress,
            onDismiss = viewModel::closePrimarySelection,
            onSelect = viewModel::selectPrimaryCandidate,
            onConfirm = viewModel::confirmPrimarySelection
        )
    }

    if (uiState.visitorSelectionVisible) {
        VisitorGallerySelectionSheet(
            candidates = visitorImageCandidates(uiState),
            selectedPaths = uiState.visitorSelectionDraftPaths,
            primaryPath = uiState.primaryExistingPath,
            message = uiState.visitorSelectionMessage,
            onDismiss = viewModel::closeVisitorSelection,
            onToggle = viewModel::toggleVisitorImage,
            onConfirm = viewModel::confirmVisitorSelection
        )
    }

    if (uiState.replaceImagesConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissReplaceImages,
            title = { Text("Replace all existing images?") },
            text = { Text("The currently stored artifact images will be removed and replaced with the new selection.\n\nThis action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissReplaceImages()
                        launchImagePicker(ImagePickerMode.Replace)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Continue", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReplaceImages) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }

    uiState.pendingDeleteMetadataSection?.let { section ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteMetadataSection,
            title = { Text("Delete \"${section.title}\"?") },
            text = { Text("The fields inside this section will also be removed from this artifact.") },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDeleteMetadataSection,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Section", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteMetadataSection) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }

    uiState.pendingRemoveImage?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissExistingImageRemoval,
            title = { Text("Remove image?") },
            text = { Text("This image will be removed from this artifact when you save your changes.") },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmExistingImageRemoval,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Remove", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissExistingImageRemoval) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }

    uiState.primaryRemovalBlockedImage?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissExistingImageRemoval,
            title = { Text("This is the current Main Image.") },
            text = { Text("Choose another Main Image before removing it.") },
            confirmButton = {
                Button(onClick = viewModel::chooseMainImageAfterBlockedRemoval) {
                    Text("Choose Main Image", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissExistingImageRemoval) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }
}

@Composable
private fun StatusBanner(status: String) {
    val published = status.equals("published", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (published) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (published) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (published) Icons.Outlined.CheckCircle else Icons.Outlined.Edit, contentDescription = null)
            Text(if (published) "Published" else "Draft", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PrimaryReviewBanner(onReview: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Main image was selected automatically during bulk import.", fontWeight = FontWeight.SemiBold)
                Text("Review the image before publishing.", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onReview) {
                Text("Review Main Image", maxLines = 1)
            }
        }
    }
}

@Composable
private fun CategorySection(
    category: String,
    categories: List<ArtifactCategoryDto>,
    error: String?,
    onCategoryChange: (String) -> Unit,
    onManageCategories: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormTextField(category, onCategoryChange, "Category", error = error)
        if (categories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { item ->
                    FilterChip(
                        selected = item.name.equals(category, ignoreCase = true),
                        onClick = { onCategoryChange(item.name) },
                        label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
        TextButton(onClick = onManageCategories, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Manage Categories", maxLines = 1)
        }
    }
}

@Composable
private fun MetadataSectionCard(
    section: ArtifactMetadataSectionDto,
    error: String?,
    onAddField: () -> Unit,
    onLabelChange: (String, String, String) -> Unit,
    onValueChange: (String, String, String) -> Unit,
    onTypeChange: (String, String, String) -> Unit,
    onUnitChange: (String, String, String) -> Unit,
    onRemoveField: (String, String) -> Unit,
    onRenameSection: (() -> Unit)? = null,
    onDeleteSection: (() -> Unit)? = null,
    coreContent: @Composable ColumnScope.() -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (onRenameSection != null && onDeleteSection != null) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Section actions")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename Section") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRenameSection()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Section", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteSection()
                                }
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            coreContent()
            section.fields.sortedBy { it.order }.forEach { field ->
                MetadataFieldEditorRow(
                    sectionId = section.id,
                    field = field,
                    onLabelChange = onLabelChange,
                    onValueChange = onValueChange,
                    onTypeChange = onTypeChange,
                    onUnitChange = onUnitChange,
                    onRemove = onRemoveField
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onAddField, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add Field", maxLines = 1)
            }
        }
    }
}

@Composable
private fun MetadataFieldEditorRow(
    sectionId: String,
    field: ArtifactMetadataFieldDto,
    onLabelChange: (String, String, String) -> Unit,
    onValueChange: (String, String, String) -> Unit,
    onTypeChange: (String, String, String) -> Unit,
    onUnitChange: (String, String, String) -> Unit,
    onRemove: (String, String) -> Unit
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = field.label,
            onValueChange = { onLabelChange(sectionId, field.id, it) },
            label = { Text("Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = field.value,
            onValueChange = { onValueChange(sectionId, field.id, it) },
            label = { Text("Value") },
            minLines = if (field.type == "long_text") 3 else 1,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(fieldTypeLabel(field.type), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                    FieldTypes.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(fieldTypeLabel(option)) },
                            onClick = {
                                typeMenuExpanded = false
                                onTypeChange(sectionId, field.id, option)
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { onRemove(sectionId, field.id) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove field")
            }
        }
        if (field.type == "number") {
            OutlinedTextField(
                value = field.unit.orEmpty(),
                onValueChange = { onUnitChange(sectionId, field.id, it) },
                label = { Text("Unit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SectionNameDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable(title, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Section Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", maxLines = 1)
            }
        }
    )
}

@Composable
private fun CustomFieldsSection(
    fields: List<ArtifactCustomFieldDto>,
    error: String?,
    onAdd: () -> Unit,
    onEdit: (ArtifactCustomFieldDto) -> Unit,
    onRemove: (ArtifactCustomFieldDto) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 360.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Additional Information")
                    OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("Add Field", maxLines = 1)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Additional Information")
                    OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("Add Field", maxLines = 1)
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        fields.forEach { field ->
            CustomFieldRow(field, onEdit = { onEdit(field) }, onRemove = { onRemove(field) })
        }
    }
}

@Composable
private fun CustomFieldRow(
    field: ArtifactCustomFieldDto,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(field.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(customFieldDisplayValue(field), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Field actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFieldDialog(
    field: ArtifactCustomFieldDto?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String?) -> Unit
) {
    var label by rememberSaveable(field?.id ?: "new") { mutableStateOf(field?.label.orEmpty()) }
    var type by rememberSaveable(field?.id ?: "new") { mutableStateOf(field?.type ?: "text") }
    var value by rememberSaveable(field?.id ?: "new") { mutableStateOf(field?.value.orEmpty()) }
    var unit by rememberSaveable(field?.id ?: "new") { mutableStateOf(field?.unit.orEmpty()) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (field == null) "Add Field" else "Edit Field") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(fieldTypeLabel(type))
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        FieldTypes.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(fieldTypeLabel(option)) },
                                onClick = {
                                    type = option
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    minLines = if (type == "long_text") 3 else 1,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == "number") {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(label, type, value, unit.takeIf { type == "number" }) }, enabled = label.isNotBlank()) {
                Text(if (field == null) "Add Field" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    minLines: Int = 1,
    showCounter: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        isError = error != null,
        supportingText = {
            when {
                error != null -> Text(error)
                showCounter -> Text("${value.length} characters")
            }
        }
    )
}

@Composable
private fun ImageSectionHeader(
    imageCount: Int,
    error: String?,
    isBusy: Boolean,
    onManageImages: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Images ($imageCount)", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onManageImages, enabled = !isBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Manage artifact images")
                Text("Manage Images", maxLines = 1)
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ExistingImagesRow(
    images: List<ExistingImageUi>,
    primaryPath: String?,
    onRequestRemove: (ExistingImageUi) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(images, key = { it.path }) { image ->
            ServerImageTile(
                image = image,
                isPrimary = primaryPath == image.path,
                onRequestRemove = { onRequestRemove(image) }
            )
        }
    }
}

@Composable
private fun SelectedImagesRow(
    images: List<Uri>,
    primaryUri: Uri?,
    onRemove: (Uri) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(images, key = { it.toString() }) { uri ->
            LocalImageTile(
                uri = uri,
                isPrimary = primaryUri == uri,
                onRemove = { onRemove(uri) }
            )
        }
    }
}

@Composable
private fun ServerImageTile(
    image: ExistingImageUi,
    isPrimary: Boolean,
    onRequestRemove: () -> Unit
) {
    ImageTile(
        model = image.url,
        label = if (image.markedForRemoval) "Removed" else "Stored",
        isPrimary = isPrimary,
        visitorSelected = image.visitorSelected,
        onRemove = onRequestRemove,
        removed = image.markedForRemoval
    )
}

@Composable
private fun LocalImageTile(
    uri: Uri,
    isPrimary: Boolean,
    onRemove: () -> Unit
) {
    ImageTile(
        model = uri,
        label = "Selected",
        isPrimary = isPrimary,
        visitorSelected = false,
        onRemove = onRemove,
        removed = false
    )
}

@Composable
private fun ImageTile(
    model: Any,
    label: String,
    isPrimary: Boolean,
    visitorSelected: Boolean,
    onRemove: () -> Unit,
    removed: Boolean
) {
    Card(
        modifier = Modifier.size(width = 132.dp, height = 184.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPrimary) {
                    MainBadge(modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                } else if (visitorSelected) {
                    SelectedBadge(modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                if (removed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Removed", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (removed) Icons.Outlined.Refresh else Icons.Outlined.Close,
                        contentDescription = if (removed) "Restore image" else "Remove image"
                    )
                }
            }
            Text(
                when {
                    isPrimary -> "Main image"
                    visitorSelected -> "Visitor gallery"
                    else -> "Admin image"
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageManagementSheet(
    imageCount: Int,
    status: String?,
    indexError: String?,
    isIndexing: Boolean,
    isBusy: Boolean,
    canIndex: Boolean,
    onDismiss: () -> Unit,
    onAddImages: () -> Unit,
    onSelectMainImage: () -> Unit,
    onSelectVisitorImages: () -> Unit,
    onReplaceImages: () -> Unit,
    onIndexArtifact: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Manage Images", style = MaterialTheme.typography.headlineSmall)
            ImageSheetAction("Add Images", Icons.Outlined.AddPhotoAlternate, enabled = !isBusy, onClick = onAddImages)
            ImageSheetAction("Select Main Image", Icons.Outlined.PhotoLibrary, enabled = !isBusy && imageCount > 0, onClick = onSelectMainImage)
            ImageSheetAction("Select Visitor Images", Icons.Outlined.CheckCircle, enabled = !isBusy && imageCount > 0, onClick = onSelectVisitorImages)
            ImageSheetAction("Replace Existing Images", Icons.Outlined.Sync, enabled = !isBusy, onClick = onReplaceImages, destructive = true)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Text("AI Library Status", style = MaterialTheme.typography.titleMedium)
                    }
                    ArtifactAiStatusChip(status)
                    Text("${imageCount} stored image(s)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (imageCount == 0) {
                            "Add images before feeding this artifact to the AI Library."
                        } else if (canIndex) {
                            "Add published artifact images to the AI recognition library."
                        } else {
                            "Publish this artifact before feeding it to the AI Library."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!indexError.isNullOrBlank()) {
                        Text(indexError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        onClick = onIndexArtifact,
                        enabled = canIndex && imageCount > 0 && !isIndexing && !isBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        if (isIndexing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = null)
                        }
                        Text(if (status.equals("stale", ignoreCase = true)) "Update AI Library" else "Feed to AI Library", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrimarySelectionSheet(
    candidates: List<ImageCandidateUi>,
    selectedKey: String?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select Main Image", style = MaterialTheme.typography.headlineSmall)
            if (candidates.isEmpty()) {
                Text("Add images before selecting a main image.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(candidates, key = { it.key }) { candidate ->
                        PrimaryCandidateTile(
                            candidate = candidate,
                            selected = selectedKey == candidate.key,
                            onSelect = { onSelect(candidate.key) }
                        )
                    }
                }
            }
            Button(
                onClick = onConfirm,
                enabled = selectedKey != null && !isBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("Set as Main Image", maxLines = 1)
            }
        }
    }
}

@Composable
private fun PrimaryCandidateTile(
    candidate: ImageCandidateUi,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        enabled = !candidate.removed,
        modifier = Modifier.size(width = 104.dp, height = 142.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = candidate.model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (candidate.isPrimary) {
                    MainBadge(modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
                }
                if (selected) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(candidate.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitorGallerySelectionSheet(
    candidates: List<VisitorImageCandidateUi>,
    selectedPaths: List<String>,
    primaryPath: String?,
    message: String?,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Visitor Gallery", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The Main Image is always shown to visitors. Select up to 5 additional images.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("${selectedPaths.size} of ${ArtifactValidationLimits.VisitorAdditionalImages} selected", style = MaterialTheme.typography.titleMedium)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (candidates.isEmpty()) {
                Text("Add stored images before selecting visitor gallery images.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gridItems(candidates, key = { it.path }) { candidate ->
                        VisitorGalleryCandidateTile(
                            candidate = candidate,
                            primaryPath = primaryPath,
                            onToggle = onToggle
                        )
                    }
                }
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("Save Selection", maxLines = 1)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancel", maxLines = 1)
            }
        }
    }
}

@Composable
private fun VisitorGalleryCandidateTile(
    candidate: VisitorImageCandidateUi,
    primaryPath: String?,
    onToggle: (String) -> Unit
) {
    Card(
        onClick = { if (!candidate.isPrimary) onToggle(candidate.path) },
        enabled = !candidate.removed,
        modifier = Modifier.size(width = 112.dp, height = 150.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (candidate.selected || candidate.path == primaryPath) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = candidate.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (candidate.isPrimary) {
                    MainBadge(modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
                } else if (candidate.selected) {
                    SelectedBadge(modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
                }
                if (!candidate.isPrimary) {
                    Checkbox(
                        checked = candidate.selected,
                        onCheckedChange = { onToggle(candidate.path) },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
            Text(candidate.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MainBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
            Text("MAIN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
            Text("SELECTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

private fun imageCandidates(state: ArtifactFormUiState): List<ImageCandidateUi> {
    val existing = if (state.replaceImages) {
        emptyList()
    } else {
        state.adminExistingImages().mapIndexed { index, image ->
            ImageCandidateUi(
                key = ArtifactFormViewModel.EXISTING_IMAGE_KEY_PREFIX + image.path,
                model = image.url,
                label = "Stored ${index + 1}",
                isPrimary = state.primaryExistingPath == image.path,
                removed = image.markedForRemoval
            )
        }
    }
    val selected = state.selectedImages.mapIndexed { index, uri ->
        ImageCandidateUi(
            key = ArtifactFormViewModel.SELECTED_IMAGE_KEY_PREFIX + uri,
            model = uri,
            label = "Selected ${index + 1}",
            isPrimary = state.primarySelectedUri == uri
        )
    }
    return existing + selected
}

private fun visitorImageCandidates(state: ArtifactFormUiState): List<VisitorImageCandidateUi> {
    return state.adminExistingImages().mapIndexed { index, image ->
        VisitorImageCandidateUi(
            path = image.path,
            url = image.url,
            label = "Stored ${index + 1}",
            isPrimary = image.path == state.primaryExistingPath,
            selected = image.path == state.primaryExistingPath || image.path in state.visitorSelectionDraftPaths,
            removed = image.markedForRemoval
        )
    }
}

@Composable
private fun SaveActions(
    isNewArtifact: Boolean,
    status: String,
    isSubmitting: Boolean,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
    onSaveChanges: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status.equals("published", ignoreCase = true)) {
            Button(onClick = onSaveChanges, modifier = Modifier.fillMaxWidth(), enabled = !isSubmitting) {
                SaveButtonContent(isSubmitting, "Save Changes")
            }
        } else {
            Button(onClick = onSaveDraft, modifier = Modifier.fillMaxWidth(), enabled = !isSubmitting) {
                SaveButtonContent(isSubmitting, if (isNewArtifact) "Save Draft" else "Save Changes")
            }
            OutlinedButton(onClick = onPublish, modifier = Modifier.fillMaxWidth(), enabled = !isSubmitting) {
                Text("Publish")
            }
        }
    }
}

@Composable
private fun SaveButtonContent(isSubmitting: Boolean, label: String) {
    if (isSubmitting) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    } else {
        Text(label)
    }
}

private fun customFieldDisplayValue(field: ArtifactCustomFieldDto): String {
    return listOf(field.value, field.unit).filter { !it.isNullOrBlank() }.joinToString(" ")
}

private fun fieldTypeLabel(type: String): String {
    return when (type) {
        "number" -> "Number"
        "long_text" -> "Long Text"
        "date" -> "Date"
        else -> "Text"
    }
}

private fun String.statusTitle(): String {
    return replaceFirstChar { it.uppercase() }
}
