package com.example.museumapp.ui.admin.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.museumapp.data.model.DashboardRecentArtifactDto
import com.example.museumapp.data.model.DashboardSummaryResponse
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.ui.admin.components.ArtifactAiStatusChip
import com.example.museumapp.ui.admin.components.HealthStatusChip

@Composable
fun DashboardScreen(
    repository: AdminRepositoryContract,
    padding: PaddingValues,
    onAddArtifact: () -> Unit,
    onTestRecognition: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Loading Dashboard...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DashboardHeader(
                adminName = uiState.adminName,
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh
            )
        }
        uiState.errorMessage?.let { message ->
            item { ErrorCard(message = message, onRetry = viewModel::refresh) }
        }
        uiState.summary?.let { summary ->
            item { MetricsSection(summary) }
            item {
                AiLibrarySection(
                    summary = summary,
                    isFeeding = uiState.feedingAiLibrary,
                    onFeed = viewModel::requestFeedAiLibrary
                )
            }
            item {
                HealthSection(summary)
            }
            item {
                QuickActions(
                    onAddArtifact = onAddArtifact,
                    onTestRecognition = onTestRecognition
                )
            }
            if (summary.recentArtifacts.isNotEmpty()) {
                item {
                    Text("Recent artifacts", style = MaterialTheme.typography.titleLarge)
                }
                items(summary.recentArtifacts, key = { it.id }) { artifact ->
                    RecentArtifactRow(artifact)
                }
            }
        }
    }

    if (uiState.feedConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::cancelFeedAiLibrary,
            title = { Text("Feed artifacts to AI Library?") },
            text = {
                Text(
                    "This will process all published artifacts that have not yet been added to the AI recognition library.\n\nArtifact recognition may take several minutes while images are processed."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmFeedAiLibrary, enabled = !uiState.feedingAiLibrary) {
                    Text("Feed Now", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelFeedAiLibrary, enabled = !uiState.feedingAiLibrary) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }

    uiState.feedResult?.let { result ->
        val hasFailures = result.failedArtifacts > 0
        AlertDialog(
            onDismissRequest = viewModel::dismissFeedResult,
            title = { Text(if (hasFailures) "AI Library Update Completed" else "AI Library Updated") },
            text = {
                Text(
                    "Artifacts processed: ${result.artifactsProcessed}\nImages processed: ${result.imagesProcessed}\nSuccessful: ${result.successfulArtifacts}\nFailed: ${result.failedArtifacts}"
                )
            },
            confirmButton = {
                Button(onClick = viewModel::dismissFeedResult) {
                    Text("Done", maxLines = 1)
                }
            },
            dismissButton = {
                if (hasFailures) {
                    TextButton(onClick = viewModel::retryFailedFeed) {
                        Text("Retry Failed", maxLines = 1)
                    }
                }
            }
        )
    }

    uiState.feedError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissFeedResult,
            title = { Text("AI Library Update Failed") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = viewModel::dismissFeedResult) {
                    Text("Done", maxLines = 1)
                }
            }
        )
    }
}

@Composable
private fun DashboardHeader(adminName: String, isRefreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Welcome, ${adminName.ifBlank { "Administrator" }}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh dashboard")
            }
        }
    }
}

@Composable
private fun MetricsSection(summary: DashboardSummaryResponse) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Total Artifacts", summary.totalArtifacts.toString(), Modifier.weight(1f))
            MetricCard("Total Images", summary.totalImages.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("In AI Library", summary.aiLibraryReadyCount().toString(), Modifier.weight(1f))
            MetricCard("Not in AI Library", summary.aiLibraryPendingCount().toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HealthSection(summary: DashboardSummaryResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("System health", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthStatusChip(summary.aiStatus)
                HealthStatusChip(summary.databaseStatus)
                HealthStatusChip(summary.uploadsStatus)
            }
        }
    }
}

@Composable
private fun AiLibrarySection(
    summary: DashboardSummaryResponse,
    isFeeding: Boolean,
    onFeed: () -> Unit
) {
    val pending = summary.aiLibraryPendingCount()
    val stale = summary.aiLibraryStaleArtifacts
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Text("AI Library", style = MaterialTheme.typography.titleLarge)
            }
            if (pending > 0) {
                Text(
                    text = if (stale > 0) {
                        "$pending published artifact(s) need AI Library attention, including $stale update(s)."
                    } else {
                        "$pending published artifact(s) have not been added to the AI recognition library."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onFeed, enabled = !isFeeding, modifier = Modifier.fillMaxWidth()) {
                    if (isFeeding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    }
                    Text(if (isFeeding) "Feeding to AI Library" else "Feed to AI Library", maxLines = 1)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("All published artifacts are available for artifact recognition.", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Up to date", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun QuickActions(
    onAddArtifact: () -> Unit,
    onTestRecognition: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Quick actions", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onAddArtifact, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("Add Artifact", maxLines = 1)
        }
        FilledTonalButton(onClick = onTestRecognition, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Text("Test Recognition", maxLines = 1)
        }
    }
}

@Composable
private fun RecentArtifactRow(artifact: DashboardRecentArtifactDto) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (artifact.primaryImageUrl.isNullOrBlank()) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    AsyncImage(
                        model = artifact.primaryImageUrl,
                        contentDescription = artifact.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(artifact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artifact.artifactCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(artifact.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ArtifactAiStatusChip(artifact.aiIndexStatus)
        }
    }
}

private fun DashboardSummaryResponse.aiLibraryReadyCount(): Int {
    return if (hasAiLibraryCounts()) aiLibraryReadyArtifacts else indexedArtifacts
}

private fun DashboardSummaryResponse.aiLibraryPendingCount(): Int {
    return if (hasAiLibraryCounts()) aiLibraryPendingArtifacts else pendingArtifacts + failedArtifacts
}

private fun DashboardSummaryResponse.hasAiLibraryCounts(): Boolean {
    return publishedArtifacts > 0 ||
        draftArtifacts > 0 ||
        aiLibraryReadyArtifacts > 0 ||
        aiLibraryPendingArtifacts > 0 ||
        aiLibraryStaleArtifacts > 0
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
