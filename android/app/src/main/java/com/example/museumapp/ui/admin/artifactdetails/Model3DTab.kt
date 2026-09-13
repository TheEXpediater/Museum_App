package com.example.museumapp.ui.admin.artifactdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.Model3DCoverageRegion
import com.example.museumapp.data.model.Model3DGenerationMethod
import com.example.museumapp.data.model.Model3DJobDto
import com.example.museumapp.data.model.Model3DQuality
import com.example.museumapp.data.model.Model3DStateDto
import com.example.museumapp.data.model.Model3DStatus
import com.example.museumapp.data.model.isJobActive

/**
 * The dedicated "3D Model" tab: a summary-first layout (published model, create/update action,
 * current generation only when relevant) instead of dumping the full reconstruction/photo
 * management UI onto the normal Details page. See [ArtifactDetailsScreen] for the root-cause
 * notes behind why this flow never calls the synchronous COLMAP preflight endpoint.
 */
@Composable
fun Model3DTabContent(
    artifact: ArtifactDto,
    model: Model3DStateDto?,
    job: Model3DJobDto?,
    submitStage: Model3DSubmitStage?,
    isLoading: Boolean,
    isBusy: Boolean,
    padding: PaddingValues,
    onCreatePreviewClick: () -> Unit,
    onViewPublished: () -> Unit,
    onPreviewDraft: () -> Unit,
    onAcceptModel: () -> Unit,
    onRejectModel: () -> Unit,
    onRetryFailed: () -> Unit,
    onAdvancedClick: () -> Unit
) {
    val state = model ?: Model3DStateDto()
    val hasPublished = state.version > 0 && !state.sha256.isNullOrBlank() && !state.modelUrl.isNullOrBlank()
    val isActive = submitStage != null || state.isJobActive()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoading && model == null) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            return@Column
        }

        if (hasPublished) {
            PublishedModelCard(state, isBusy, onViewPublished)
        }

        CreateUpdatePreviewCard(
            imageCount = artifact.imagePaths.size,
            hasPublished = hasPublished,
            isBusy = isBusy,
            isActive = isActive,
            onCreatePreviewClick = onCreatePreviewClick
        )

        when {
            isActive -> ProcessingCard(state, job, submitStage)
            state.status == Model3DStatus.PendingReview -> PendingReviewCard(state, isBusy, onPreviewDraft, onAcceptModel, onRejectModel)
            state.status == Model3DStatus.Failed || state.status == Model3DStatus.Interrupted -> FailedCard(state, isBusy, onRetryFailed)
            state.status == Model3DStatus.None && !hasPublished -> {
                Text(
                    "This artifact does not have a 3D preview yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TextButton(onClick = onAdvancedClick) {
            Text("Advanced: Photogrammetry, source photos")
        }
    }
}

@Composable
private fun PublishedModelCard(model: Model3DStateDto, isBusy: Boolean, onViewPublished: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Published Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DetailRow("Status", "Published")
            DetailRow("Version", model.version.toString())
            DetailRow("Generation method", Model3DGenerationMethod.label(model.generationMethod))
            if (model.estimatedSupportedPercent != null && model.estimatedInferredPercent != null) {
                DetailRow("Photo-supported coverage", "${model.estimatedSupportedPercent}%")
                DetailRow("AI-estimated coverage", "${model.estimatedInferredPercent}%")
            }
            model.sizeBytes?.let { DetailRow("Size", humanReadableBytes(it)) }
            Button(onClick = onViewPublished, enabled = !isBusy, modifier = Modifier.padding(top = 8.dp)) {
                Text("View 3D Model")
            }
        }
    }
}

@Composable
private fun CreateUpdatePreviewCard(
    imageCount: Int,
    hasPublished: Boolean,
    isBusy: Boolean,
    isActive: Boolean,
    onCreatePreviewClick: () -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (hasPublished) "Create / Update 3D Preview" else "Create 3D Preview", style = MaterialTheme.typography.titleMedium)
            Text(
                "$imageCount artifact image(s) available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCreatePreviewClick,
                enabled = !isBusy && !isActive && imageCount > 0,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Create 3D Preview")
            }
            if (imageCount == 0) {
                Text(
                    "Add images to this artifact first (Images tab).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProcessingCard(model: Model3DStateDto, job: Model3DJobDto?, submitStage: Model3DSubmitStage?) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Current Generation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(processingStageText(model, job, submitStage), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "This can take a few minutes. You can leave this screen and come back later.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

internal fun processingStageText(model: Model3DStateDto, job: Model3DJobDto?, submitStage: Model3DSubmitStage?): String =
    when (submitStage) {
        Model3DSubmitStage.PreparingImages -> "Preparing selected images…"
        Model3DSubmitStage.StartingGeneration -> "Starting Local AI 3D generation…"
        null -> job?.stageMessage?.takeIf { it.isNotBlank() } ?: Model3DStatus.processingLabel(model.status)
    }

@Composable
private fun PendingReviewCard(
    model: Model3DStateDto,
    isBusy: Boolean,
    onPreviewDraft: () -> Unit,
    onAcceptModel: () -> Unit,
    onRejectModel: () -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("3D Preview Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Generation method: ${Model3DGenerationMethod.label(model.draftGenerationMethod)}", style = MaterialTheme.typography.bodySmall)
            if (model.draftGenerationMethod == Model3DGenerationMethod.AiMultiview ||
                model.draftGenerationMethod == Model3DGenerationMethod.AiLocal
            ) {
                Text(
                    "AI-generated 3D preview. Areas not visible in the generation input may have been " +
                        "estimated by AI and may not exactly match the physical artifact.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (model.draftEstimatedSupportedPercent != null && model.draftEstimatedInferredPercent != null) {
                    Text("Photo-supported coverage: ${model.draftEstimatedSupportedPercent}%", style = MaterialTheme.typography.bodySmall)
                    Text("AI-estimated coverage: ${model.draftEstimatedInferredPercent}%", style = MaterialTheme.typography.bodySmall)
                    if (model.draftVisibleRegions.isNotEmpty()) {
                        Text(
                            "Regions: " + model.draftVisibleRegions.joinToString(", ") { Model3DCoverageRegion.label(it) },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text("Coverage estimate unavailable", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (model.qualityAssessment == Model3DQuality.Insufficient) {
                Text(
                    "The photo overlap for this draft was limited - review carefully before accepting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = onPreviewDraft, enabled = !isBusy && model.draftModelUrl != null) {
                    Text("Preview 3D")
                }
                Button(onClick = onAcceptModel, enabled = !isBusy) { Text("Accept") }
                OutlinedButton(onClick = onRejectModel, enabled = !isBusy) { Text("Reject") }
            }
        }
    }
}

@Composable
private fun FailedCard(model: Model3DStateDto, isBusy: Boolean, onRetry: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("3D Preview Could Not Be Created", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                model.failureMessage?.takeIf { it.isNotBlank() }
                    ?: if (model.status == Model3DStatus.Interrupted) {
                        "The generation was interrupted, for example by a server restart."
                    } else {
                        "Unable to create a usable 3D preview from these photographs."
                    },
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onRetry, enabled = !isBusy) { Text("Retry") }
        }
    }
}

/** Non-dismissable-by-accident processing modal: shown immediately once the admin confirms a
 * photo selection, before any backend job may even exist yet. Explicitly hideable (the job keeps
 * running/polling regardless - see [ArtifactDetailsViewModel.createOrUpdate3DPreview] and
 * polling in the ViewModel) rather than blocking navigation entirely, matching the requirement
 * that leaving and returning to this screen must restore - not lose - an active job. */
@Composable
fun ProcessingModal(model: Model3DStateDto?, job: Model3DJobDto?, submitStage: Model3DSubmitStage?, onHide: () -> Unit) {
    val state = model ?: Model3DStateDto()
    Dialog(onDismissRequest = onHide, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Creating 3D Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    processingStageText(state, job, submitStage),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "This may take a few minutes. You can hide this and check back later - it keeps running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onHide) { Text("Hide") }
            }
        }
    }
}

/**
 * Shown automatically, exactly once, when [ArtifactDetailsViewModel] observes a generation job go
 * from active to [com.example.museumapp.data.model.Model3DStatus.PendingReview] (see
 * [PreviewReadyEvent] / [ArtifactDetailsViewModel.consumePreviewReadyEvent]) - the admin no longer
 * has to notice the Pending Review card appearing on their own. Deliberately does not Accept,
 * Reject, or open SceneView by itself: either button is a distinct, explicit admin action.
 */
@Composable
fun PreviewReadyDialog(
    event: PreviewReadyEvent,
    onViewPreview: () -> Unit,
    onReviewLater: () -> Unit
) {
    Dialog(onDismissRequest = onReviewLater) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("3D Preview Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Your 3D preview was generated successfully and is ready for review.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Generation: ${Model3DGenerationMethod.label(event.generationMethod)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onViewPreview, modifier = Modifier.fillMaxWidth()) {
                    Text("View 3D Preview")
                }
                TextButton(onClick = onReviewLater, modifier = Modifier.fillMaxWidth()) {
                    Text("Review Later")
                }
            }
        }
    }
}

private enum class CreatePreviewMode { Initial, Choosing }

/**
 * The "Create 3D Preview" flow entry point. All images are selected by default (capped to the
 * AI provider's per-request limit, primary image first). The one-tap path is
 * Create 3D Preview -> Use All Images -> automatic generation; "Choose Images" opens a
 * scrollable checklist with Select All/Clear inside the same dialog rather than a separate giant
 * inline list on the artifact page.
 */
@Composable
fun CreateThreeDPreviewDialog(
    images: List<Pair<String, String>>,
    primaryImagePath: String?,
    aiMaxImages: Int?,
    onDismiss: () -> Unit,
    onConfirm: (selectedPaths: List<String>, visibleRegions: List<String>) -> Unit
) {
    val effectiveMax = AiPreviewPhotoSelection.effectiveMax(aiMaxImages)
    val orderedPaths = remember(images, primaryImagePath) {
        val paths = images.map { it.first }
        if (primaryImagePath != null && primaryImagePath in paths) {
            listOf(primaryImagePath) + paths.filterNot { it == primaryImagePath }
        } else {
            paths
        }
    }
    val defaultSelection = remember(orderedPaths, effectiveMax) { orderedPaths.take(effectiveMax).toSet() }
    var mode by remember { mutableStateOf(CreatePreviewMode.Initial) }
    var selectedPaths by remember { mutableStateOf(defaultSelection) }
    var selectedRegions by remember { mutableStateOf(setOf<String>()) }
    val isCapped = images.size > effectiveMax

    when (mode) {
        CreatePreviewMode.Initial -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Create 3D Preview") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${images.size} image(s) are available for this artifact.")
                    Text(
                        if (isCapped) {
                            "This generator uses up to $effectiveMax photo(s) at a time. The best photo is used by default."
                        } else {
                            "Use all images?"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onConfirm(defaultSelection.toList(), emptyList()) },
                        enabled = defaultSelection.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCapped) "Use Recommended Photo" else "Use All Images")
                    }
                    OutlinedButton(onClick = { mode = CreatePreviewMode.Choosing }, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose Images")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )

        CreatePreviewMode.Choosing -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Choose Images") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedPaths = orderedPaths.toSet() }) { Text("Select All") }
                        TextButton(onClick = { selectedPaths = emptySet() }, enabled = selectedPaths.isNotEmpty()) { Text("Clear All") }
                    }
                    Text(
                        "${selectedPaths.size} of ${images.size} selected",
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (AiPreviewPhotoSelection.isOverLimit(selectedPaths.size, effectiveMax)) {
                        Text(
                            "This generator accepts at most $effectiveMax photo(s). Only the first $effectiveMax " +
                                "(in the order shown) will be used.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(images, key = { it.first }) { (path, url) ->
                            ImageSelectTile(
                                url = url,
                                isPrimary = path == primaryImagePath,
                                checked = path in selectedPaths,
                                onCheckedChange = { checked ->
                                    selectedPaths = if (checked) selectedPaths + path else selectedPaths - path
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                    Text("Visible regions (optional)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Mark which sides of the artifact are visible in the selected photo(s), for an honest " +
                            "coverage estimate - never a claimed model confidence.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Model3DCoverageRegion.ALL.forEach { region ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = region in selectedRegions,
                                onCheckedChange = { checked ->
                                    selectedRegions = if (checked) selectedRegions + region else selectedRegions - region
                                }
                            )
                            Text(Model3DCoverageRegion.label(region))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConfirm(selectedPaths.toList(), selectedRegions.toList()) },
                    enabled = selectedPaths.isNotEmpty()
                ) {
                    Text("Confirm Selection")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ImageSelectTile(url: String, isPrimary: Boolean, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(if (isPrimary) "Main" else " ", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
