package com.example.museumapp.ui.admin.artifactdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.ArtifactMetadataSectionIds
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.ui.admin.components.ArtifactAiStatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactDetailsScreen(
    repository: AdminRepositoryContract,
    artifactId: String?,
    onBack: () -> Unit,
    onEditArtifact: (String) -> Unit,
    onPreviewDraftModel: (artifactId: String, version: Int, sha256: String, url: String) -> Unit = { _, _, _, _ -> }
) {
    val viewModel: ArtifactDetailsViewModel = viewModel(
        key = "artifact_details_$artifactId",
        factory = ArtifactDetailsViewModel.factory(repository, artifactId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddPhotosSheet by remember { mutableStateOf(false) }
    var showRebuildConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artifact Details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    uiState.artifact?.let { artifact ->
                        Box {
                            IconButton(onClick = { menuExpanded = true }, enabled = !uiState.feedingAi && !uiState.deleting) {
                                if (uiState.feedingAi || uiState.deleting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "Artifact actions")
                                }
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEditArtifact(artifact.id)
                                    }
                                )
                                val isIndexed = artifact.aiIndexStatus.equals("indexed", ignoreCase = true)
                                DropdownMenuItem(
                                    text = { Text(if (isIndexed) "Already in AI Library" else "Feed to AI Library") },
                                    leadingIcon = {
                                        Icon(
                                            if (isIndexed) Icons.Outlined.CheckCircle else Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (isIndexed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    enabled = !isIndexed,
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.feedToAiLibrary()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.requestDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            uiState.errorMessage != null -> ArtifactDetailsError(
                message = uiState.errorMessage.orEmpty(),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            )
            uiState.artifact != null -> ArtifactDetailsContent(
                artifact = uiState.artifact!!,
                uiState = uiState,
                padding = padding,
                onAddPhotosClick = { showAddPhotosSheet = true },
                onRemoveModel3DImage = viewModel::remove3DImage,
                onRunPreflight = viewModel::runPreflight,
                onBuildModel = viewModel::buildModel,
                onRebuildClick = { showRebuildConfirm = true },
                onDeleteReconstructionClick = viewModel::requestDeleteReconstruction,
                onPreviewDraft = { version, sha256, url ->
                    onPreviewDraftModel(uiState.artifact!!.id, version, sha256, url)
                },
                onAcceptModel = viewModel::acceptModel,
                onRejectModel = viewModel::rejectModel
            )
        }
    }

    if (showAddPhotosSheet && uiState.artifact != null) {
        AddReconstructionPhotosDialog(
            artifact = uiState.artifact!!,
            onDismiss = { showAddPhotosSheet = false },
            onConfirm = { reusePaths, newImages ->
                viewModel.add3DImages(reusePaths, newImages)
                showAddPhotosSheet = false
            }
        )
    }

    if (showRebuildConfirm) {
        AlertDialog(
            onDismissRequest = { showRebuildConfirm = false },
            title = { Text("Rebuild 3D model?") },
            text = { Text("This starts a new reconstruction. The current 3D model stays available to visitors until the rebuild finishes successfully.") },
            confirmButton = {
                Button(onClick = {
                    showRebuildConfirm = false
                    viewModel.buildModel()
                }) {
                    Text("Rebuild")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (uiState.pendingDeleteReconstruction) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteReconstruction,
            title = { Text("Delete 3D reconstruction data?") },
            text = { Text("This removes all reconstruction photos, work files, and any published 3D model for this artifact. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDeleteReconstruction,
                    enabled = !uiState.deletingReconstruction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteReconstruction, enabled = !uiState.deletingReconstruction) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.model3DError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissModel3DError,
            title = { Text("3D Model Action Failed") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = viewModel::dismissModel3DError) { Text("Done") }
            }
        )
    }

    if (uiState.pendingDelete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete artifact?") },
            text = { Text("This will permanently remove this artifact record and its stored images. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    enabled = !uiState.deleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Artifact", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete, enabled = !uiState.deleting) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }

    uiState.deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteError,
            title = { Text("Delete Failed") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = viewModel::dismissDeleteError) { Text("Done") }
            }
        )
    }

    uiState.feedError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissFeedError,
            title = { Text("AI Library Update Failed") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = viewModel::dismissFeedError) { Text("Done") }
            }
        )
    }
}

@Composable
private fun ArtifactDetailsContent(
    artifact: ArtifactDto,
    uiState: ArtifactDetailsUiState,
    padding: PaddingValues,
    onAddPhotosClick: () -> Unit,
    onRemoveModel3DImage: (String) -> Unit,
    onRunPreflight: () -> Unit,
    onBuildModel: () -> Unit,
    onRebuildClick: () -> Unit,
    onDeleteReconstructionClick: () -> Unit,
    onPreviewDraft: (version: Int, sha256: String, url: String) -> Unit,
    onAcceptModel: () -> Unit,
    onRejectModel: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PrimaryImageCard(artifact)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(artifact.name, style = MaterialTheme.typography.headlineSmall)
                Text(artifact.artifactCode, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(artifact.status)
                    Text(artifact.category, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (artifact.primaryImageNeedsReview) {
            item { PrimaryReviewWarning() }
        }
        item {
            DetailSection("Description") {
                Text(artifact.description, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            DetailSection("Historical Information") {
                DetailRow("Origin", artifact.origin)
                DetailRow("Historical period", artifact.historicalPeriod)
                artifact.metadataSectionFields(ArtifactMetadataSectionIds.HistoricalDetails).forEach { field ->
                    DetailRow(field.label, listOf(field.value, field.unit).filter { !it.isNullOrBlank() }.joinToString(" "))
                }
            }
        }
        item {
            DetailSection("Physical Details") {
                DetailRow("Material", artifact.material)
                DetailRow("Dimensions", artifact.dimensions)
                DetailRow("Condition", artifact.condition)
                artifact.metadataSectionFields(ArtifactMetadataSectionIds.PhysicalDetails).forEach { field ->
                    DetailRow(field.label, listOf(field.value, field.unit).filter { !it.isNullOrBlank() }.joinToString(" "))
                }
            }
        }
        artifact.metadataSections
            .filterNot { it.id in ArtifactMetadataSectionIds.SystemSections }
            .filter { section -> section.fields.any { it.label.isNotBlank() || it.value.isNotBlank() } }
            .forEach { section ->
                item {
                    DetailSection(section.title) {
                        section.fields.sortedBy { it.order }.forEach { field ->
                            DetailRow(field.label.ifBlank { "Untitled field" }, listOf(field.value, field.unit).filter { !it.isNullOrBlank() }.joinToString(" "))
                        }
                    }
                }
            }
        if (artifact.customFields.any { it.value.isNotBlank() }) {
            item {
                DetailSection("Additional Information") {
                    artifact.customFields.filter { it.value.isNotBlank() }.forEach { field ->
                        DetailRow(field.label, listOf(field.value, field.unit).filter { !it.isNullOrBlank() }.joinToString(" "))
                    }
                }
            }
        }
        item {
            AdditionalImagesSection(artifact)
        }
        item {
            AiIndexSection(artifact)
        }
        item {
            Model3DSection(
                state = uiState.model3D,
                job = uiState.model3DJob,
                isLoading = uiState.model3DLoading,
                isBusy = uiState.model3DBusy || uiState.deletingReconstruction,
                onAddPhotosClick = onAddPhotosClick,
                onRemoveImage = onRemoveModel3DImage,
                onRunPreflight = onRunPreflight,
                onBuildModel = onBuildModel,
                onRebuildClick = onRebuildClick,
                onDeleteReconstructionClick = onDeleteReconstructionClick,
                onPreviewDraft = onPreviewDraft,
                onAcceptModel = onAcceptModel,
                onRejectModel = onRejectModel
            )
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val draft = status.equals("draft", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (draft) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (draft) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            text = if (draft) "Draft" else "Published",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PrimaryReviewWarning() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Text("Main image was selected automatically during bulk import. Review the image before publishing.")
        }
    }
}

@Composable
private fun PrimaryImageCard(artifact: ArtifactDto) {
    val primaryImage = artifact.primaryImageUrl ?: artifact.imageUrls.firstOrNull()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (primaryImage.isNullOrBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No primary image", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                AsyncImage(
                    model = primaryImage,
                    contentDescription = artifact.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun AdditionalImagesSection(artifact: ArtifactDto) {
    val images = artifact.imageUrls.mapIndexedNotNull { index, url ->
        val path = artifact.imagePaths.getOrNull(index)
        if (url.isBlank() || path == null) null else path to url
    }.primaryFirst(artifact.primaryImagePath)
    DetailSection("Images (${images.size})") {
        if (images.isEmpty()) {
            Text("No images", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(images, key = { it.first }) { image ->
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = image.second,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (image.first == artifact.primaryImagePath) {
                            GalleryBadge("MAIN", modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                        } else if (image.first in artifact.visitorGalleryImagePaths) {
                            GalleryBadge("SELECTED", modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun AiIndexSection(artifact: ArtifactDto) {
    DetailSection("AI Index Status") {
        ArtifactAiStatusChip(artifact.aiIndexStatus)
        DetailRow("Indexed images", artifact.aiIndexedImageCount?.toString() ?: "0")
        DetailRow("Indexed at", artifact.aiIndexedAt)
        if (!artifact.aiIndexError.isNullOrBlank()) {
            Text(artifact.aiIndexError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun DetailRow(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "Not provided",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun ArtifactDto.metadataSectionFields(sectionId: String) = metadataSections
    .firstOrNull { it.id == sectionId }
    ?.fields
    ?.sortedBy { it.order }
    ?.filter { it.label.isNotBlank() || it.value.isNotBlank() }
    .orEmpty()

private fun List<Pair<String, String>>.primaryFirst(primaryPath: String?): List<Pair<String, String>> {
    val primary = firstOrNull { it.first == primaryPath }
    return if (primary == null) this else listOf(primary) + filterNot { it.first == primaryPath }
}

@Composable
private fun ArtifactDetailsError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                Button(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Retry")
                }
            }
        }
    }
}
