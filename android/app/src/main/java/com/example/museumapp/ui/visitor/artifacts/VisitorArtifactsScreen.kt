package com.example.museumapp.ui.visitor.artifacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.model.ArticleDto
import com.example.museumapp.data.model.MuseumInformationDto
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.EmptyState
import com.example.museumapp.ui.visitor.components.InfoRow
import com.example.museumapp.ui.visitor.components.VisitorArtifactCard
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorErrorCard
import com.example.museumapp.ui.visitor.components.VisitorIllustration
import com.example.museumapp.ui.visitor.components.VisitorLoading

@Composable
fun VisitorArtifactsScreen(
    repository: VisitorRepositoryContract,
    padding: PaddingValues,
    onArtifactDetails: (String) -> Unit
) {
    val viewModel: VisitorArtifactsViewModel = viewModel(factory = VisitorArtifactsViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            VisitorIllustration(
                model = if (uiState.selectedTab == VisitorArtifactsTab.MuseumInfo) VisitorAssets.MuseumLocation else VisitorAssets.ArtifactsFactsArticles,
                contentDescription = "Artifacts, facts, articles, and museum information",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )
        }
        item {
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                VisitorArtifactsTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
        when (uiState.selectedTab) {
            VisitorArtifactsTab.Artifacts -> {
                item {
                    OutlinedTextField(
                        value = uiState.search,
                        onValueChange = viewModel::updateSearch,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search artifacts") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
                    )
                }
                if (uiState.categories.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(onClick = { viewModel.selectCategory("") }, label = { Text("All") })
                            uiState.categories.forEach { category ->
                                AssistChip(onClick = { viewModel.selectCategory(category) }, label = { Text(category) })
                            }
                        }
                    }
                }
                when {
                    uiState.isLoading -> item { VisitorLoading(modifier = Modifier.height(220.dp)) }
                    uiState.errorMessage != null && uiState.artifacts.isEmpty() -> item { VisitorErrorCard(uiState.errorMessage.orEmpty(), viewModel::refreshAll) }
                    uiState.artifacts.isEmpty() -> item { EmptyState("Artifacts will appear here once the museum collection is configured.") }
                    else -> {
                        items(uiState.artifacts, key = { it.id }) { artifact ->
                            VisitorArtifactCard(artifact = artifact, onClick = { onArtifactDetails(artifact.id) })
                        }
                        if (uiState.page < uiState.totalPages) {
                            item {
                                Button(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoadingMore) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                                    Text(if (uiState.isLoadingMore) "Loading" else "Load More")
                                }
                            }
                        }
                    }
                }
            }
            VisitorArtifactsTab.Articles -> {
                item {
                    OutlinedTextField(
                        value = uiState.search,
                        onValueChange = viewModel::updateSearch,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search facts and articles") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
                    )
                }
                if (uiState.articles.isEmpty()) {
                    item { EmptyState("Published museum facts and articles will appear here.") }
                } else {
                    items(uiState.articles, key = { it.id }) { article -> ArticleCard(article) }
                }
            }
            VisitorArtifactsTab.MuseumInfo -> {
                item { MuseumInfoCard(uiState.museumInformation) }
            }
        }
    }
}

private val VisitorArtifactsTab.label: String
    get() = when (this) {
        VisitorArtifactsTab.Artifacts -> "Artifacts"
        VisitorArtifactsTab.Articles -> "Facts and Articles"
        VisitorArtifactsTab.MuseumInfo -> "Museum Information"
    }

@Composable
private fun ArticleCard(article: ArticleDto) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(article.title, style = MaterialTheme.typography.titleMedium)
            Text(article.category ?: "Museum Article", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(article.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("Read Article", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MuseumInfoCard(info: MuseumInformationDto) {
    val context = LocalContext.current
    val hasCoordinates = info.latitude != null && info.longitude != null
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(info.museumName, style = MaterialTheme.typography.headlineSmall)
            InfoRow("Description", info.description)
            InfoRow("Campus Location", info.campusLocation)
            InfoRow("Opening Hours", info.openingHours)
            InfoRow("Contact Email", info.contactEmail)
            InfoRow("Contact Phone", info.contactPhone)
            InfoRow("Visitor Guidelines", info.visitorGuidelines)
            InfoRow("Accessibility", info.accessibilityInformation)
            Button(
                onClick = {
                    if (hasCoordinates) {
                        val uri = Uri.parse("geo:${info.latitude},${info.longitude}?q=${info.latitude},${info.longitude}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasCoordinates
            ) {
                Icon(Icons.Outlined.Map, contentDescription = null)
                Text("Open Map")
            }
        }
    }
}
