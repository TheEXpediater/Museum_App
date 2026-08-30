package com.example.museumapp.ui.admin.artifactform

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.museumapp.data.model.ArtifactCategoryDto
import com.example.museumapp.data.model.ArtifactCustomFieldDto
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.ui.admin.components.ArtifactAiStatusChip

private val FieldTypes = listOf("text", "number", "long_text", "date")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactFormScreen(
    repository: AdminRepository,
    artifactId: String?,
    onClose: () -> Unit
) {
    val viewModel: ArtifactFormViewModel = viewModel(
        key = artifactId ?: "create_artifact",
        factory = ArtifactFormViewModel.factory(repository, artifactId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showAddCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showDeactivateCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomFieldDialog by rememberSaveable { mutableStateOf(false) }
    var editingCustomFieldId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingCustomField = uiState.customFields.firstOrNull { it.id == editingCustomFieldId }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = viewModel::addSelectedImages
    )

    LaunchedEffect(uiState.shouldClose) {
        if (uiState.shouldClose) {
            viewModel.clearCloseRequest()
            onClose()
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
        }
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
                item { PrimaryReviewBanner() }
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
                    actionMessage = uiState.categoryActionMessage,
                    onCategoryChange = viewModel::updateCategory,
                    onAddCategory = { showAddCategoryDialog = true },
                    onRenameCategory = { showRenameCategoryDialog = true },
                    onDeactivateCategory = { showDeactivateCategoryDialog = true }
                )
            }
            item { SectionTitle("Historical Details") }
            item { FormTextField(uiState.origin, viewModel::updateOrigin, "Origin") }
            item { FormTextField(uiState.historicalPeriod, viewModel::updateHistoricalPeriod, "Historical period") }
            item { SectionTitle("Physical Details") }
            item { FormTextField(uiState.material, viewModel::updateMaterial, "Material") }
            item { FormTextField(uiState.dimensions, viewModel::updateDimensions, "Dimensions") }
            item { FormTextField(uiState.condition, viewModel::updateCondition, "Condition") }
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
            if (artifactId != null && uiState.existingImages.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Replace existing images", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = uiState.replaceImages, onCheckedChange = { viewModel.toggleReplaceImages() })
                    }
                }
            }
            item {
                ImageSectionHeader(
                    imageCount = (if (uiState.replaceImages) 0 else uiState.existingImages.count { !it.markedForRemoval }) + uiState.selectedImages.size,
                    error = uiState.fieldErrors["images"] ?: uiState.fieldErrors["primaryImage"],
                    onPickImages = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }
            if (uiState.existingImages.isNotEmpty() && !uiState.replaceImages) {
                item {
                    ExistingImagesRow(
                        images = uiState.existingImages,
                        primaryPath = uiState.primaryExistingPath,
                        onToggleRemove = viewModel::toggleExistingImageRemoval,
                        onSelectPrimary = viewModel::selectPrimaryExisting
                    )
                }
            }
            if (uiState.selectedImages.isNotEmpty()) {
                item {
                    SelectedImagesRow(
                        images = uiState.selectedImages,
                        primaryUri = uiState.primarySelectedUri,
                        onRemove = viewModel::removeSelectedImage,
                        onSelectPrimary = viewModel::selectPrimarySelected
                    )
                }
            }
            item {
                AiIndexStatusSection(
                    status = uiState.savedAiIndexStatus,
                    indexedCount = uiState.savedAiIndexedImageCount,
                    error = uiState.savedAiIndexError
                )
            }
            if (uiState.successMessage != null) {
                item { SuccessCard(uiState.successMessage.orEmpty()) }
            }
            if (uiState.errorMessage != null) {
                item { Text(uiState.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
            item {
                SaveActions(
                    status = uiState.status,
                    isSubmitting = uiState.isSubmitting,
                    onSaveDraft = viewModel::saveDraft,
                    onPublish = viewModel::publish,
                    onSaveChanges = viewModel::saveDraftOrChanges,
                    onMoveToDraft = viewModel::moveToDraft
                )
            }
        }
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

    if (showAddCategoryDialog) {
        CategoryNameDialog(
            title = "Add Category",
            confirmLabel = "Add Category",
            initialValue = "",
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = {
                showAddCategoryDialog = false
                viewModel.createCategory(it)
            }
        )
    }

    if (showRenameCategoryDialog) {
        CategoryNameDialog(
            title = "Rename Category",
            confirmLabel = "Rename",
            initialValue = uiState.category,
            onDismiss = { showRenameCategoryDialog = false },
            onConfirm = {
                showRenameCategoryDialog = false
                viewModel.renameSelectedCategory(it)
            }
        )
    }

    if (showDeactivateCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateCategoryDialog = false },
            title = { Text("Deactivate category") },
            text = { Text("Artifacts keep their current category text. The category will no longer appear as an active choice.") },
            confirmButton = {
                Button(onClick = {
                    showDeactivateCategoryDialog = false
                    viewModel.deactivateSelectedCategory()
                }) {
                    Text("Deactivate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateCategoryDialog = false }) {
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
private fun PrimaryReviewBanner() {
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
            Text("Review Images", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CategorySection(
    category: String,
    categories: List<ArtifactCategoryDto>,
    error: String?,
    actionMessage: String?,
    onCategoryChange: (String) -> Unit,
    onAddCategory: () -> Unit,
    onRenameCategory: () -> Unit,
    onDeactivateCategory: () -> Unit
) {
    val selectedManagedCategory = categories.any { it.name.equals(category, ignoreCase = true) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onAddCategory) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add")
            }
            OutlinedButton(onClick = onRenameCategory, enabled = selectedManagedCategory) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Text("Rename")
            }
            OutlinedButton(onClick = onDeactivateCategory, enabled = selectedManagedCategory) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text("Deactivate")
            }
        }
        actionMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("Additional Information")
            OutlinedButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add Field")
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
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit field")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove field")
            }
        }
    }
}

@Composable
private fun CategoryNameDialog(
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
                label = { Text("Category name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
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
private fun AiIndexStatusSection(status: String?, indexedCount: Int?, error: String?) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI Index Status", style = MaterialTheme.typography.titleMedium)
            ArtifactAiStatusChip(status)
            Text(
                text = "${indexedCount ?: 0} indexed image(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!error.isNullOrBlank()) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SuccessCard(message: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ImageSectionHeader(
    imageCount: Int,
    error: String?,
    onPickImages: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Images ($imageCount)", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onPickImages) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Text("Select")
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ExistingImagesRow(
    images: List<ExistingImageUi>,
    primaryPath: String?,
    onToggleRemove: (String) -> Unit,
    onSelectPrimary: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(images, key = { it.path }) { image ->
            ServerImageTile(
                image = image,
                isPrimary = primaryPath == image.path,
                onToggleRemove = { onToggleRemove(image.path) },
                onSelectPrimary = { onSelectPrimary(image.path) }
            )
        }
    }
}

@Composable
private fun SelectedImagesRow(
    images: List<Uri>,
    primaryUri: Uri?,
    onRemove: (Uri) -> Unit,
    onSelectPrimary: (Uri) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(images, key = { it.toString() }) { uri ->
            LocalImageTile(
                uri = uri,
                isPrimary = primaryUri == uri,
                onRemove = { onRemove(uri) },
                onSelectPrimary = { onSelectPrimary(uri) }
            )
        }
    }
}

@Composable
private fun ServerImageTile(
    image: ExistingImageUi,
    isPrimary: Boolean,
    onToggleRemove: () -> Unit,
    onSelectPrimary: () -> Unit
) {
    ImageTile(
        model = image.url,
        label = if (image.markedForRemoval) "Removed" else "Stored",
        isPrimary = isPrimary,
        onRemove = onToggleRemove,
        onSelectPrimary = onSelectPrimary,
        removed = image.markedForRemoval
    )
}

@Composable
private fun LocalImageTile(
    uri: Uri,
    isPrimary: Boolean,
    onRemove: () -> Unit,
    onSelectPrimary: () -> Unit
) {
    ImageTile(
        model = uri,
        label = "Selected",
        isPrimary = isPrimary,
        onRemove = onRemove,
        onSelectPrimary = onSelectPrimary,
        removed = false
    )
}

@Composable
private fun ImageTile(
    model: Any,
    label: String,
    isPrimary: Boolean,
    onRemove: () -> Unit,
    onSelectPrimary: () -> Unit,
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
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
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
                            Text("MAIN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
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
                    Icon(Icons.Outlined.Close, contentDescription = "Remove image")
                }
            }
            TextButton(onClick = onSelectPrimary, enabled = !removed, modifier = Modifier.heightIn(min = 36.dp)) {
                Icon(
                    imageVector = if (isPrimary) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null
                )
                Text(if (isPrimary) "Main" else "Set Main")
            }
        }
    }
}

@Composable
private fun SaveActions(
    status: String,
    isSubmitting: Boolean,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
    onSaveChanges: () -> Unit,
    onMoveToDraft: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status.equals("published", ignoreCase = true)) {
            Button(onClick = onSaveChanges, modifier = Modifier.fillMaxWidth(), enabled = !isSubmitting) {
                SaveButtonContent(isSubmitting, "Save Changes")
            }
            OutlinedButton(onClick = onMoveToDraft, modifier = Modifier.fillMaxWidth(), enabled = !isSubmitting) {
                Text("Move to Draft")
            }
        } else {
            Button(onClick = onSaveDraft, modifier = Modifier.fillMaxWidth(), enabled = !isSubmitting) {
                SaveButtonContent(isSubmitting, "Save Draft")
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
