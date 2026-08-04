package com.example.museumapp.ui.visitor.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.model.AnnouncementDto
import com.example.museumapp.data.model.NewsDto
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.EmptyState
import com.example.museumapp.ui.visitor.components.InfoRow
import com.example.museumapp.ui.visitor.components.VisitorArtifactCard
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorChip
import com.example.museumapp.ui.visitor.components.VisitorErrorCard
import com.example.museumapp.ui.visitor.components.VisitorIllustration
import com.example.museumapp.ui.visitor.components.VisitorLoading
import com.example.museumapp.ui.visitor.components.VisitorSectionHeader

@Composable
fun VisitorHomeScreen(
    repository: VisitorRepositoryContract,
    padding: PaddingValues,
    onExploreArtifacts: () -> Unit,
    onScanArtifact: () -> Unit,
    onArtifactDetails: (String) -> Unit
) {
    val viewModel: VisitorHomeViewModel = viewModel(factory = VisitorHomeViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> VisitorLoading(modifier = Modifier.padding(padding))
        uiState.errorMessage != null && uiState.home == null -> VisitorErrorCard(
            message = uiState.errorMessage.orEmpty(),
            onRetry = viewModel::refresh,
            modifier = Modifier.padding(padding).padding(16.dp)
        )
        else -> {
            val home = uiState.home
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Welcome, ${uiState.session.displayName.takeIf { it.isNotBlank() } ?: "Visitor"}", style = MaterialTheme.typography.headlineSmall)
                        VisitorChip(if (uiState.session.isStudent) "Student" else "Guest Visitor")
                    }
                }
                item {
                    MuseumHero(onExploreArtifacts, onScanArtifact)
                }
                item {
                    VisitorSectionHeader("Latest News")
                    if (home?.latestNews.isNullOrEmpty()) EmptyState("No published news yet.")
                }
                if (!home?.latestNews.isNullOrEmpty()) {
                    items(home!!.latestNews, key = { it.id }) { news ->
                        NewsCard(news)
                    }
                }
                item {
                    VisitorSectionHeader("Announcements")
                    if (home?.announcements.isNullOrEmpty()) EmptyState("No active announcements.")
                }
                if (!home?.announcements.isNullOrEmpty()) {
                    items(home!!.announcements, key = { it.id }) { announcement ->
                        AnnouncementCard(announcement)
                    }
                }
                item {
                    VisitorSectionHeader("Featured Artifacts", actionLabel = "See All", onAction = onExploreArtifacts)
                    if (home?.featuredArtifacts.isNullOrEmpty()) EmptyState("Featured artifacts will appear here once configured.")
                }
                if (!home?.featuredArtifacts.isNullOrEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(home!!.featuredArtifacts, key = { it.id }) { artifact ->
                                VisitorArtifactCard(
                                    artifact = artifact,
                                    onClick = { onArtifactDetails(artifact.id) },
                                    modifier = Modifier.fillParentMaxWidth(0.88f)
                                )
                            }
                        }
                    }
                }
                item {
                    VisitorSectionHeader("Quick Museum Information")
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(home?.museumInformation?.museumName ?: "PSAU Museum", style = MaterialTheme.typography.titleMedium)
                            }
                            InfoRow("Location", home?.museumInformation?.campusLocation)
                            InfoRow("Opening Hours", home?.museumInformation?.openingHours)
                            OutlinedButton(onClick = onExploreArtifacts, modifier = Modifier.fillMaxWidth()) {
                                Text("Visitor Guidelines")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MuseumHero(onExploreArtifacts: () -> Unit, onScanArtifact: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            VisitorIllustration(
                model = VisitorAssets.HomeMuseumHero,
                contentDescription = "Illustrated PSAU Museum visitor hero",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            )
            Text("PSAU Museum", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "Explore the heritage, collections, and stories of Pampanga State Agricultural University.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onExploreArtifacts, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null)
                    Text("Explore Artifacts")
                }
                OutlinedButton(onClick = onScanArtifact, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Text("Scan Artifact")
                }
            }
        }
    }
}

@Composable
private fun NewsCard(news: NewsDto) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(news.title, style = MaterialTheme.typography.titleMedium)
            Text(news.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(news.publishedAt?.take(10).orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Read More", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AnnouncementCard(announcement: AnnouncementDto) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(announcement.title, style = MaterialTheme.typography.titleMedium)
            Text(announcement.message, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${announcement.priority.replaceFirstChar { it.uppercase() }} priority", style = MaterialTheme.typography.labelMedium)
            announcement.expiresAt?.take(10)?.let { Text("Until $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
