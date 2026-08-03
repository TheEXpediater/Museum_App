package com.example.museumapp.ui.admin.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.ui.admin.components.HealthStatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatusScreen(
    repository: AdminRepositoryContract,
    onBack: () -> Unit
) {
    val viewModel: SystemStatusViewModel = viewModel(factory = SystemStatusViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Status", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !uiState.isRefreshing && !uiState.isLoading) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh status")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                item { CircularProgressIndicator() }
            }
            uiState.errorMessage?.let { message ->
                item { MessageCard(message = message, error = true) }
            }
            uiState.actionMessage?.let { message ->
                item { MessageCard(message = message, error = false) }
            }
            if (shouldShowSetupChecklist(uiState)) {
                item { SetupChecklist(uiState) }
            }
            item {
                StatusActions(
                    uiState = uiState,
                    onWarmup = viewModel::loadAiModel,
                    onIndexAll = viewModel::requestIndexAll,
                    onRetryFailed = viewModel::requestRetryFailed,
                    onRebuild = viewModel::requestRebuildIndex
                )
            }
            item {
                StatusSection("Backend") {
                    StatusRow("Backend health", uiState.backendHealth?.status ?: "unknown")
                    StatusRow("MongoDB", uiState.backendHealth?.database ?: "unknown")
                    StatusRow("Upload directory", uiState.backendHealth?.uploadsDirectory ?: "unknown")
                }
            }
            item {
                StatusSection("OpenCLIP") {
                    val ai = uiState.aiHealth
                    val openclipStatus = openclipStatus(uiState)
                    StatusRow("AI enabled", if (ai?.aiEnabled == true) "enabled" else "disabled")
                    StatusRow("OpenCLIP", openclipStatus)
                    InfoRow("Model", ai?.modelName ?: "unknown")
                    InfoRow("Pretrained", ai?.pretrained ?: "unknown", valueMaxLines = 2)
                    InfoRow(
                        "Active device",
                        if (openclipStatus == "loaded") ai?.device ?: "unknown" else "Auto, selected during load",
                        valueMaxLines = 2
                    )
                    if (openclipStatus == "loaded") {
                        InfoRow("Model embedding dimension", ai?.embeddingDimension?.toString() ?: "unknown")
                    } else {
                        InfoRow(
                            "Expected dimension",
                            ai?.collectionVectorSize?.toString()
                                ?: uiState.indexStatus?.collectionVectorSize?.toString()
                                ?: "unknown"
                        )
                    }
                }
            }
            item {
                StatusSection("Qdrant") {
                    val ai = uiState.aiHealth
                    val index = uiState.indexStatus
                    StatusRow("Qdrant", ai?.qdrant ?: index?.qdrant ?: "unknown")
                    StatusRow("Collection status", ai?.collectionStatus ?: index?.collectionStatus ?: "unknown")
                    InfoRow("Collection", ai?.collection ?: index?.collection ?: "unknown", valueMaxLines = 2)
                    InfoRow("Distance", ai?.collectionDistance ?: index?.collectionDistance ?: "unknown")
                    InfoRow("Collection vector size", ai?.collectionVectorSize?.toString() ?: index?.collectionVectorSize?.toString() ?: "unknown")
                    InfoRow("Indexed vector points", indexedVectors(uiState).toString())
                }
            }
            item {
                StatusSection("Artifact Index") {
                    val index = uiState.indexStatus
                    InfoRow("Artifacts", index?.totalArtifacts?.toString() ?: "0")
                    InfoRow("Images", index?.totalImages?.toString() ?: "0")
                    InfoRow("Indexed artifacts", index?.indexedArtifacts?.toString() ?: "0")
                    InfoRow("Pending artifacts", index?.pendingArtifacts?.toString() ?: "0")
                    InfoRow("Partial artifacts", index?.partialArtifacts?.toString() ?: "0")
                    InfoRow("Failed artifacts", index?.failedArtifacts?.toString() ?: "0")
                    InfoRow("Not indexed", index?.notIndexedArtifacts?.toString() ?: "0")
                }
            }
        }
    }

    if (uiState.confirmIndexAll) {
        ConfirmationDialog(
            title = "Index Artifact Images",
            message = "The first run loads OpenCLIP and may take several minutes. Existing artifact images will be indexed for recognition.",
            confirm = "Index",
            onConfirm = viewModel::confirmIndexAll,
            onDismiss = viewModel::dismissConfirmation
        )
    }
    if (uiState.confirmRetryFailed) {
        ConfirmationDialog(
            title = "Retry Failed Indexes",
            message = "Retry artifacts with failed or partial AI indexing?",
            confirm = "Retry",
            onConfirm = viewModel::confirmRetryFailed,
            onDismiss = viewModel::dismissConfirmation
        )
    }
    if (uiState.confirmRebuildIndex) {
        ConfirmationDialog(
            title = "Rebuild Vector Collection",
            message = "This deletes and recreates only the configured Qdrant artifact vector collection. MongoDB artifacts and uploaded images are not deleted.",
            confirm = "Rebuild",
            onConfirm = viewModel::confirmRebuildIndex,
            onDismiss = viewModel::dismissConfirmation,
            destructive = true
        )
    }
}

@Composable
private fun SetupChecklist(uiState: SystemStatusUiState) {
    StatusSection("Setup Checklist") {
        ChecklistRow("Step 1", "Load AI Model", openclipStatus(uiState) == "loaded")
        ChecklistRow("Step 2", "Index Artifact Images", indexedVectors(uiState) > 0)
        ChecklistRow("Step 3", "Test Recognition", openclipStatus(uiState) == "loaded" && indexedVectors(uiState) > 0)
    }
}

@Composable
private fun ChecklistRow(step: String, label: String, complete: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Text(step, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        HealthStatusChip(if (complete) "ready" else "idle", modifier = Modifier.widthIn(max = 144.dp))
    }
}

@Composable
private fun StatusActions(
    uiState: SystemStatusUiState,
    onWarmup: () -> Unit,
    onIndexAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onRebuild: () -> Unit
) {
    val openclip = openclipStatus(uiState)
    val warmupBusy = uiState.isWarmingUp || uiState.isPollingWarmup || openclip == "loading"
    val indexBusy = uiState.isIndexingAll || uiState.isRetryingFailed || uiState.isRebuildingIndex
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onWarmup,
            enabled = !warmupBusy && openclip != "loaded",
            modifier = Modifier.fillMaxWidth()
        ) {
            if (warmupBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.SmartToy, contentDescription = null)
            }
            Text("Load AI Model")
        }
        Button(
            onClick = onIndexAll,
            enabled = !indexBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isIndexingAll) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Sync, contentDescription = null)
            }
            Text("Index Artifact Images")
        }
        OutlinedButton(
            onClick = onRetryFailed,
            enabled = !indexBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isRetryingFailed) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Sync, contentDescription = null)
            }
            Text("Retry Failed Indexes")
        }
        OutlinedButton(
            onClick = onRebuild,
            enabled = !indexBusy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            if (uiState.isRebuildingIndex) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Sync, contentDescription = null)
            }
            Text("Rebuild Vector Collection")
        }
    }
}

@Composable
private fun StatusSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(modifier = Modifier.widthIn(max = 168.dp), contentAlignment = Alignment.CenterEnd) {
            HealthStatusChip(status)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueMaxLines: Int = 1) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = valueMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    value,
                    modifier = Modifier.weight(1.1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = valueMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, error: Boolean) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun shouldShowSetupChecklist(uiState: SystemStatusUiState): Boolean {
    val openclip = openclipStatus(uiState)
    return openclip in setOf("idle", "not_loaded", "loading") || indexedVectors(uiState) == 0
}

private fun openclipStatus(uiState: SystemStatusUiState): String {
    return uiState.warmupStatus?.state
        ?: uiState.aiHealth?.openclip
        ?: uiState.indexStatus?.openclip
        ?: "unknown"
}

private fun indexedVectors(uiState: SystemStatusUiState): Int {
    return uiState.aiHealth?.indexedVectors ?: uiState.indexStatus?.indexedVectors ?: 0
}
