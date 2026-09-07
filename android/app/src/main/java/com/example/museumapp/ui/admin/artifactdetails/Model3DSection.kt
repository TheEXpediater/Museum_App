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
import com.example.museumapp.data.model.Model3DImageOrigin
import com.example.museumapp.data.model.Model3DJobDto
import com.example.museumapp.data.model.Model3DStateDto
import com.example.museumapp.data.model.Model3DStatus
import kotlin.math.roundToInt

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
    onDeleteReconstructionClick: () -> Unit
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
                    "This artifact does not have a 3D model yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onAddPhotosClick, enabled = !isBusy) {
                    Text("Create 3D Model")
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
                    Button(onClick = onBuildModel, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                        Text("Build 3D Model")
                    }
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

            model.status == Model3DStatus.Ready -> {
                ModelReadyBanner(model)
                if (!model.failureMessage.isNullOrBlank()) {
                    LastRebuildFailedNotice(model.failureMessage)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddPhotosClick, enabled = !isBusy) {
                        Text("Add Photos")
                    }
                    OutlinedButton(onClick = onRebuildClick, enabled = !isBusy) {
                        Text("Rebuild")
                    }
                }
            }

            model.status == Model3DStatus.Failed || model.status == Model3DStatus.Interrupted -> {
                Text(
                    text = model.failureMessage?.takeIf { it.isNotBlank() }
                        ?: if (model.status == Model3DStatus.Interrupted) {
                            "The reconstruction was interrupted, for example by a server restart."
                        } else {
                            "The last reconstruction attempt failed."
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
                        Text("Build Again")
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
private fun ModelReadyBanner(model: Model3DStateDto) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("3D model available", fontWeight = FontWeight.SemiBold)
            Text("Version ${model.version}", style = MaterialTheme.typography.bodySmall)
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

private fun humanReadableBytes(bytes: Long): String {
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
