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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.museumapp.data.repository.AdminRepository

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
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
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
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = !uiState.isSubmitting && !uiState.isLoading) {
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                FormTextField(
                    value = uiState.artifactCode,
                    onValueChange = viewModel::updateArtifactCode,
                    label = "Artifact code",
                    error = uiState.fieldErrors["artifactCode"]
                )
            }
            item {
                FormTextField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    label = "Artifact name",
                    error = uiState.fieldErrors["name"]
                )
            }
            item {
                FormTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    label = "Description",
                    error = uiState.fieldErrors["description"],
                    minLines = 4
                )
            }
            item {
                FormTextField(
                    value = uiState.category,
                    onValueChange = viewModel::updateCategory,
                    label = "Category",
                    error = uiState.fieldErrors["category"]
                )
            }
            item { FormTextField(uiState.origin, viewModel::updateOrigin, "Origin") }
            item { FormTextField(uiState.historicalPeriod, viewModel::updateHistoricalPeriod, "Historical period") }
            item { FormTextField(uiState.material, viewModel::updateMaterial, "Material") }
            item { FormTextField(uiState.dimensions, viewModel::updateDimensions, "Dimensions") }
            item { FormTextField(uiState.condition, viewModel::updateCondition, "Condition") }
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
                    error = uiState.fieldErrors["images"],
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
            if (uiState.errorMessage != null) {
                item { Text(uiState.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
            item {
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSubmitting
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save Artifact")
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
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
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        isError = error != null,
        supportingText = {
            if (error != null) Text(error)
        }
    )
}

@Composable
private fun ImageSectionHeader(
    error: String?,
    onPickImages: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Images", style = MaterialTheme.typography.titleMedium)
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
        label = if (image.markedForRemoval) "Removed" else "Server",
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
        modifier = Modifier.size(width = 128.dp, height = 172.dp),
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
            TextButton(onClick = onSelectPrimary, enabled = !removed) {
                Icon(
                    imageVector = if (isPrimary) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null
                )
                Text("Primary")
            }
        }
    }
}
