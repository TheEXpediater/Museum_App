package com.example.museumapp.ui.admin.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        if (uiState.isLoading) {
            item { LoadingCard() }
        }
        uiState.errorMessage?.let { message ->
            item { ErrorCard(message = message, onRetry = viewModel::refresh) }
        }
        uiState.summary?.let { summary ->
            item { MetricsSection(summary) }
            item {
                HealthSection(summary)
            }
            item {
                QuickActions(
                    isIndexing = uiState.isIndexing,
                    onAddArtifact = onAddArtifact,
                    onTestRecognition = onTestRecognition,
                    onReindex = viewModel::reindexAll
                )
            }
            uiState.actionMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    BoxWithConstraints {
        val horizontal = maxWidth >= 560.dp
        if (horizontal) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Total Artifacts", summary.totalArtifacts.toString(), Modifier.weight(1f))
                MetricCard("Total Images", summary.totalImages.toString(), Modifier.weight(1f))
                MetricCard("Indexed", summary.indexedArtifacts.toString(), Modifier.weight(1f))
                MetricCard("Needs AI Review", (summary.pendingArtifacts + summary.failedArtifacts).toString(), Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Total Artifacts", summary.totalArtifacts.toString(), Modifier.weight(1f))
                    MetricCard("Total Images", summary.totalImages.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Indexed", summary.indexedArtifacts.toString(), Modifier.weight(1f))
                    MetricCard("Needs AI Review", (summary.pendingArtifacts + summary.failedArtifacts).toString(), Modifier.weight(1f))
                }
            }
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
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HealthSection(summary: DashboardSummaryResponse) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("System health", style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthStatusChip(summary.aiStatus)
                HealthStatusChip(summary.databaseStatus)
                HealthStatusChip(summary.uploadsStatus)
            }
            Text(
                text = "${summary.indexedVectors} indexed vector(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActions(
    isIndexing: Boolean,
    onAddArtifact: () -> Unit,
    onTestRecognition: () -> Unit,
    onReindex: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Quick actions", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAddArtifact, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add")
            }
            FilledTonalButton(onClick = onTestRecognition, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Text("Test")
            }
        }
        OutlinedButton(onClick = onReindex, enabled = !isIndexing, modifier = Modifier.fillMaxWidth()) {
            if (isIndexing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Sync, contentDescription = null)
            }
            Text("Reindex Artifacts")
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

@Composable
private fun LoadingCard() {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Loading dashboard", style = MaterialTheme.typography.bodyMedium)
        }
    }
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
