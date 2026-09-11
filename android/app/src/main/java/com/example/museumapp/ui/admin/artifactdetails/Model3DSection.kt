package com.example.museumapp.ui.admin.artifactdetails

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.model.Model3DCoverageRegion
import com.example.museumapp.data.model.Model3DGenerationMethod
import com.example.museumapp.data.model.Model3DImageDto
import com.example.museumapp.data.model.Model3DImageOrigin
import com.example.museumapp.data.model.Model3DJobDto
import com.example.museumapp.data.model.Model3DQuality
import com.example.museumapp.data.model.Model3DStateDto
import com.example.museumapp.data.model.Model3DStatus
import kotlin.math.roundToInt

/** Fallback used only if the backend reports AI availability without a max-image count (should
 * not happen in practice - detect_ai_availability always pairs the two). */
private const val DEFAULT_AI_MAX_IMAGES = 4

/**
 * Pure, I/O-free decision logic for [SelectAiPreviewPhotosDialog] - kept separate from the
 * composable so the provider image-cap enforcement is unit-testable on plain JVM, matching
 * [com.example.museumapp.model3d.Model3DCacheDecisions]'s split for the same reason.
 */
internal object AiPreviewPhotoSelection {
    /** Falls back to [default] only if the backend didn't report a cap at all - it always
     * should when ai_available is true (see detect_ai_availability), so this is defensive. */
    fun effectiveMax(reportedMaxImages: Int?, default: Int = DEFAULT_AI_MAX_IMAGES): Int =
        reportedMaxImages?.takeIf { it > 0 } ?: default

    fun isOverLimit(selectedCount: Int, maxImages: Int): Boolean = selectedCount > maxImages

    fun canSubmit(selectedCount: Int, maxImages: Int): Boolean = selectedCount in 1..maxImages
}

/**
 * Renders the "3D Model" section on the admin artifact details screen, following the state
 * machine driven by [Model3DStateDto.status]. Transient dialogs (add photos, rebuild
 * confirmation, delete confirmation, error) are owned by [ArtifactDetailsScreen] at the top
 * level, matching how the existing delete/feed dialogs are handled -- this keeps them alive
 * regardless of LazyColumn scroll position.
 */
@Composable
fun Model3DSection(
    state: Model3DStateDto?,
    job: Model3DJobDto?,
    isLoading: Boolean,
    isBusy: Boolean,
    onAddPhotosClick: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onRunPreflight: () -> Unit,
    onBuildModel: () -> Unit,
    onRebuildClick: () -> Unit,
    onDeleteReconstructionClick: () -> Unit,
    onPreviewDraft: (version: Int, sha256: String, url: String) -> Unit,
    onPreviewPublished: (version: Int, sha256: String, url: String) -> Unit,
    onAcceptModel: () -> Unit,
    onRejectModel: () -> Unit,
    onGenerateAiPreviewClick: () -> Unit
) {
    DetailSection("3D Model") {
        if (isLoading && state == null) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            return@DetailSection
        }
        val model = state ?: Model3DStateDto()

        when {
            model.status == Model3DStatus.None -> {
                Text(
                    "This artifact does not have a 3D preview yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Minimum: 3 overlapping photos. Additional angles may improve the preview.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onAddPhotosClick, enabled = !isBusy) {
                    Text("Create 3D Preview")
                }
            }

            model.status in setOf(Model3DStatus.NeedsImages, Model3DStatus.ReadyForBuild) -> {
                Model3DImageSummary(model, onRemoveImage, isBusy)
                GuidanceList(model.guidance)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddPhotosClick, enabled = !isBusy) {
                        Text("Add Photos")
                    }
                    OutlinedButton(onClick = onRunPreflight, enabled = !isBusy) {
                        Text("Run Check Again")
                    }
                }
                if (model.status == Model3DStatus.ReadyForBuild) {
                    if (model.qualityAssessment == Model3DQuality.Insufficient) {
                        WeakReconstructionBanner(
                            model = model,
                            aiAvailable = model.aiAvailable,
                            isBusy = isBusy,
                            onAddPhotosClick = onAddPhotosClick,
                            onGenerateAiPreviewClick = onGenerateAiPreviewClick
                        )
                    }
                    Button(onClick = onBuildModel, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                        Text("Generate Preview")
                    }
                    Text(
                        "The quality and completeness of the 3D preview depend on the photographs provided.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            model.status in Model3DStatus.ActiveJobStatuses || model.activeJobId != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        job?.stageMessage?.takeIf { it.isNotBlank() } ?: "Processing…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "A 3D reconstruction is running. This can take a while -- you can leave this screen and come back later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            model.status == Model3DStatus.PendingReview -> {
                PendingReviewBanner(model)
                if (model.qualityAssessment == Model3DQuality.Insufficient) {
                    WeakReconstructionBanner(
                        model = model,
                        aiAvailable = model.aiAvailable,
                        isBusy = isBusy,
                        onAddPhotosClick = onAddPhotosClick,
                        onGenerateAiPreviewClick = onGenerateAiPreviewClick
                    )
                }
                GuidanceList(model.guidance)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val version = model.draftVersion
                            val sha256 = model.draftSha256
                            val url = model.draftModelUrl
                            if (version != null && !sha256.isNullOrBlank() && !url.isNullOrBlank()) {
                                onPreviewDraft(version, sha256, url)
                            }
                        },
                        enabled = !isBusy && model.draftModelUrl != null
                    ) {
                        Text("Preview 3D")
                    }
                    Button(onClick = onAcceptModel, enabled = !isBusy) {
                        Text("Accept 3D Model")
                    }
                    OutlinedButton(onClick = onRejectModel, enabled = !isBusy) {
                        Text("Reject 3D Model")
                    }
                }
            }

            model.status == Model3DStatus.Ready -> {
                ModelReadyBanner(model)
                if (!model.failureMessage.isNullOrBlank()) {
                    LastRebuildFailedNotice(model.failureMessage)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sha256 = model.sha256
                    val url = model.modelUrl
                    Button(
                        onClick = { onPreviewPublished(model.version, sha256!!, url!!) },
                        enabled = !isBusy && !sha256.isNullOrBlank() && !url.isNullOrBlank()
                    ) {
                        Text("Preview 3D Model")
                    }
                    OutlinedButton(onClick = onAddPhotosClick, enabled = !isBusy) {
                        Text("Add Photos")
                    }
                    OutlinedButton(onClick = onRebuildClick, enabled = !isBusy) {
                        Text("Regenerate Preview")
                    }
                }
            }

            model.status == Model3DStatus.Failed || model.status == Model3DStatus.Interrupted -> {
                Text(
                    text = model.failureMessage?.takeIf { it.isNotBlank() }
                        ?: if (model.status == Model3DStatus.Interrupted) {
                            "The reconstruction was interrupted, for example by a server restart."
                        } else {
                            "Unable to create a usable 3D preview from these photographs. Add photos from overlapping angles and try again."
                        },
                    color = MaterialTheme.colorScheme.error
                )
                GuidanceList(model.guidance)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddPhotosClick, enabled = !isBusy) {
                        Text("Add Photos")
                    }
                    OutlinedButton(onClick = onRunPreflight, enabled = !isBusy) {
                        Text("Re-run Check")
                    }
                    Button(onClick = onBuildModel, enabled = !isBusy) {
                        Text("Generate Preview Again")
                    }
                }
            }
        }

        if (model.status != Model3DStatus.None) {
            TextButton(onClick = onDeleteReconstructionClick, enabled = !isBusy) {
                Text("Delete Reconstruction Data", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Model3DImageSummary(model: Model3DStateDto, onRemoveImage: (String) -> Unit, isBusy: Boolean) {
    val reused = model.images.count { it.origin == Model3DImageOrigin.Reused }
    val uploaded = model.images.count { it.origin == Model3DImageOrigin.Uploaded }
    Text(
        "${model.sourceImageCount} source photo(s) -- $reused reused from gallery, $uploaded uploaded",
        style = MaterialTheme.typography.bodyMedium
    )
    if (model.images.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            model.images.forEach { image ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        image.originalFilename?.takeIf { it.isNotBlank() } ?: "Photo",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                    Text(
                        if (image.origin == Model3DImageOrigin.Reused) "reused" else "uploaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { onRemoveImage(image.id) }, enabled = !isBusy) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidanceList(guidance: List<String>) {
    if (guidance.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        guidance.forEach { line ->
            Text("•  $line", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PendingReviewBanner(model: Model3DStateDto) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("3D preview ready for review", fontWeight = FontWeight.SemiBold)
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
                    Text(
                        "Estimated photo-supported coverage: ${model.draftEstimatedSupportedPercent}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Estimated AI-inferred coverage: ${model.draftEstimatedInferredPercent}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Coverage estimate unavailable", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Source photos: ${model.sourceImageCount}", style = MaterialTheme.typography.bodySmall)
            model.registeredImageCount?.let {
                Text("Reconstructed from: $it photo(s)", style = MaterialTheme.typography.bodySmall)
            }
            model.draftSizeBytes?.let { Text(humanReadableBytes(it), style = MaterialTheme.typography.bodySmall) }
            Text(
                "The quality and completeness of the 3D preview depend on the photographs provided.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ModelReadyBanner(model: Model3DStateDto) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("3D model available", fontWeight = FontWeight.SemiBold)
            Text("Version ${model.version}", style = MaterialTheme.typography.bodySmall)
            Text("Generation method: ${Model3DGenerationMethod.label(model.generationMethod)}", style = MaterialTheme.typography.bodySmall)
            model.createdAt?.take(10)?.let { Text("Created $it", style = MaterialTheme.typography.bodySmall) }
            model.sizeBytes?.let { Text(humanReadableBytes(it), style = MaterialTheme.typography.bodySmall) }
            model.sha256?.let { Text("Checksum ${it.take(12)}…", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LastRebuildFailedNotice(failureMessage: String) {
    var dismissed by remember(failureMessage) { mutableStateOf(false) }
    if (dismissed) return
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Last rebuild failed: $failureMessage", modifier = Modifier.fillMaxWidth(0.85f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { dismissed = true }) { Text("Dismiss") }
        }
    }
}

/**
 * Shown whenever the backend's COLMAP quality gate reports [Model3DQuality.Insufficient] (e.g.
 * only 3 of 30 photos registered) - offers the admin a deliberate choice between adding better
 * photos and falling back to the optional AI 3D preview. Never appears merely because a
 * reconstruction is still running or has too few images to attempt at all; those already have
 * their own states (see the ActiveJobStatuses and NeedsImages branches above).
 */
@Composable
private fun WeakReconstructionBanner(
    model: Model3DStateDto,
    aiAvailable: Boolean,
    isBusy: Boolean,
    onAddPhotosClick: () -> Unit,
    onGenerateAiPreviewClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("3D Reconstruction Incomplete", fontWeight = FontWeight.SemiBold)
            val registered = model.registeredImageCount
            Text(
                if (registered != null) {
                    "Only $registered of ${model.sourceImageCount} image(s) could be matched."
                } else {
                    "Only a portion of the supplied photos could be matched."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "The current reconstruction is unlikely to produce a complete artifact.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddPhotosClick, enabled = !isBusy) {
                    Text("Add Better Photos")
                }
                if (aiAvailable) {
                    Button(onClick = onGenerateAiPreviewClick, enabled = !isBusy) {
                        Text("Generate AI 3D Preview")
                    }
                }
            }
        }
    }
}

internal fun humanReadableBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex += 1
    }
    val rounded = (value * 10.0).roundToInt() / 10.0
    return "$rounded ${units[unitIndex]}"
}

/**
 * Lets the admin pick reconstruction source photos: a checklist of the artifact's own existing
 * images (posted as `reuse_image_paths`) plus a system photo picker for brand-new files, using
 * the same [ActivityResultContracts.PickMultipleVisualMedia] mechanism as the artifact form
 * screen's image picker.
 */
@Composable
fun AddReconstructionPhotosDialog(
    artifact: ArtifactDto,
    onDismiss: () -> Unit,
    onConfirm: (reuseImagePaths: List<String>, newImages: List<Uri>) -> Unit
) {
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var pickedUris by remember { mutableStateOf(listOf<Uri>()) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris -> pickedUris = (pickedUris + uris).distinct() }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Reconstruction Photos") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Choose photos with overlapping angles around the artifact. You can reuse this artifact's existing images or add new ones.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (artifact.imagePaths.isNotEmpty()) {
                    Text("Existing images", style = MaterialTheme.typography.labelLarge)
                    artifact.imagePaths.forEach { path ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = path in selectedPaths,
                                onCheckedChange = { checked ->
                                    selectedPaths = if (checked) selectedPaths + path else selectedPaths - path
                                }
                            )
                            Text(
                                path.substringAfterLast('/'),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (pickedUris.isEmpty()) "Choose New Photos" else "New Photos Selected: ${pickedUris.size}")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedPaths.toList(), pickedUris) },
                enabled = selectedPaths.isNotEmpty() || pickedUris.isNotEmpty()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Lets the admin pick which of the artifact's already-added reconstruction photos to submit to
 * the optional AI 3D fallback (see [Model3DGenerationMethod.AiMultiview]). Distinct from
 * [AddReconstructionPhotosDialog]: this only selects FROM existing reconstruction images (never
 * uploads new ones - AI generation reuses the same source set as COLMAP) and must respect
 * [maxImages], the real per-request cap of whichever provider the backend has configured
 * (AI_3D_PROVIDER) rather than assuming a fixed number.
 */
@Composable
fun SelectAiPreviewPhotosDialog(
    images: List<Model3DImageDto>,
    maxImages: Int?,
    onDismiss: () -> Unit,
    onConfirm: (imageIds: List<String>, visibleRegions: List<String>) -> Unit
) {
    val effectiveMax = AiPreviewPhotoSelection.effectiveMax(maxImages)
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var selectedRegions by remember { mutableStateOf(setOf<String>()) }
    val overLimit = AiPreviewPhotoSelection.isOverLimit(selectedIds.size, effectiveMax)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate AI 3D Preview") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (effectiveMax <= 1) {
                        "Choose one primary photo - prefer a sharp, three-quarter angle with the " +
                            "whole artifact visible against a plain background. Areas not visible in " +
                            "this photo may be estimated by AI."
                    } else {
                        "Choose up to $effectiveMax distinct views for the best result - for example front, " +
                            "left, back, and right. Avoid near-duplicate angles."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selectedIds = images.map { it.id }.toSet() }) {
                        Text("Select All")
                    }
                    TextButton(onClick = { selectedIds = emptySet() }, enabled = selectedIds.isNotEmpty()) {
                        Text("Clear")
                    }
                }
                Text(
                    "Selected: ${selectedIds.size} / ${images.size}",
                    style = MaterialTheme.typography.labelMedium
                )
                images.forEach { image ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = image.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + image.id else selectedIds - image.id
                            }
                        )
                        Text(
                            image.originalFilename?.takeIf { it.isNotBlank() } ?: "Photo",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (overLimit) {
                    Text(
                        "Select at most $effectiveMax photo(s) - the configured AI provider does not accept " +
                            "more (currently ${selectedIds.size} selected).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider()
                Text("Visible in generation input", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Mark which sides of the artifact are actually visible in the selected photo(s). " +
                        "Used only to show an honest estimate of how much of the preview came from a " +
                        "photo versus AI inference - never a claimed model confidence.",
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
                Text(
                    if (selectedRegions.isEmpty()) {
                        "Coverage estimate unavailable"
                    } else {
                        val supportedPercent = (100 * selectedRegions.size / Model3DCoverageRegion.ALL.size)
                        "${selectedRegions.size} / ${Model3DCoverageRegion.ALL.size} regions represented - " +
                            "estimated photo-supported coverage: $supportedPercent%"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedIds.toList(), selectedRegions.toList()) },
                enabled = AiPreviewPhotoSelection.canSubmit(selectedIds.size, effectiveMax)
            ) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
