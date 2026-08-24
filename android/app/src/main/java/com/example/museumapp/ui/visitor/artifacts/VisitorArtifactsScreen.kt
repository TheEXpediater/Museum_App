package com.example.museumapp.ui.visitor.artifacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.model.MuseumInformationDto
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.EmptyState
import com.example.museumapp.ui.visitor.components.MuseumInfoRow
import com.example.museumapp.ui.visitor.components.MuseumSectionTitle
import com.example.museumapp.ui.visitor.components.NewsCard
import com.example.museumapp.ui.visitor.components.VisitorArtifactCard
import com.example.museumapp.ui.visitor.components.VisitorAssetImage
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorErrorCard
import com.example.museumapp.ui.visitor.components.VisitorIllustration
import com.example.museumapp.ui.visitor.components.VisitorLoading
import com.example.museumapp.ui.visitor.components.VisitorSpacing
import com.example.museumapp.ui.visitor.components.hasMuseumContent

private val CatalogueMaxWidth = 980.dp

@Composable
fun VisitorArtifactsScreen(
    repository: VisitorRepositoryContract,
    padding: PaddingValues,
    onArtifactDetails: (String) -> Unit
) {
    val viewModel: VisitorArtifactsViewModel = viewModel(factory = VisitorArtifactsViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 176.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding),
        contentPadding = PaddingValues(
            start = VisitorSpacing.Lg,
            top = VisitorSpacing.Xl,
            end = VisitorSpacing.Lg,
            bottom = 112.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogueContent {
                CatalogueHeader(uiState)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogueContent {
                TabRow(selectedTabIndex = uiState.selectedTab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                    VisitorArtifactsTab.entries.forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }
        when (uiState.selectedTab) {
            VisitorArtifactsTab.Artifacts -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CatalogueContent {
                        ArtifactSearchAndFilters(uiState, viewModel)
                    }
                }
                when {
                    uiState.isLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                        CatalogueContent { VisitorLoading(modifier = Modifier.height(220.dp)) }
                    }
                    uiState.errorMessage != null && uiState.artifacts.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        CatalogueContent { VisitorErrorCard(uiState.errorMessage.orEmpty(), viewModel::refreshAll) }
                    }
                    uiState.artifacts.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        CatalogueContent { EmptyState("Artifacts will appear here once the museum collection is configured.") }
                    }
                    else -> {
                        items(uiState.artifacts, key = { it.id }) { artifact ->
                            VisitorArtifactCard(
                                artifact = artifact,
                                onClick = { onArtifactDetails(artifact.id) }
                            )
                        }
                        if (uiState.page < uiState.totalPages) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CatalogueContent {
                                    Button(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoadingMore) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                                        Text(if (uiState.isLoadingMore) "Loading" else "Load More")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            VisitorArtifactsTab.Articles -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CatalogueContent {
                        ArticleIntroAndSearch(uiState, viewModel)
                    }
                }
                if (uiState.articles.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CatalogueContent { EmptyState("Published museum facts and articles will appear here.") }
                    }
                } else {
                    items(uiState.articles, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { article ->
                        CatalogueContent { NewsCard(article) }
                    }
                }
            }
            VisitorArtifactsTab.MuseumInfo -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CatalogueContent {
                        MuseumInfoCard(uiState.museumInformation)
                    }
                }
            }
        }
    }
}

private val VisitorArtifactsTab.label: String
    get() = when (this) {
        VisitorArtifactsTab.Artifacts -> "Artifacts"
        VisitorArtifactsTab.Articles -> "Facts"
        VisitorArtifactsTab.MuseumInfo -> "Museum"
    }

@Composable
private fun CatalogueContent(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.widthIn(max = CatalogueMaxWidth).fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun CatalogueHeader(uiState: VisitorArtifactsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm)) {
        Text("Museum Catalogue", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            when (uiState.selectedTab) {
                VisitorArtifactsTab.Artifacts -> "${uiState.totalArtifacts} artifact record(s)"
                VisitorArtifactsTab.Articles -> "${uiState.articles.size} published fact(s) and article(s)"
                VisitorArtifactsTab.MuseumInfo -> "Visitor information"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtifactSearchAndFilters(
    uiState: VisitorArtifactsUiState,
    viewModel: VisitorArtifactsViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
        OutlinedTextField(
            value = uiState.search,
            onValueChange = viewModel::updateSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search artifacts") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
        )
        if (uiState.categories.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm)
            ) {
                CategoryChip("All", selected = uiState.selectedCategory.isBlank(), onClick = { viewModel.selectCategory("") })
                uiState.categories.forEach { category ->
                    CategoryChip(
                        label = category,
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun ArticleIntroAndSearch(
    uiState: VisitorArtifactsUiState,
    viewModel: VisitorArtifactsViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
        VisitorIllustration(
            model = VisitorAssets.ArtifactsFactsArticles,
            contentDescription = "Museum facts and articles illustration",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
        )
        OutlinedTextField(
            value = uiState.search,
            onValueChange = viewModel::updateSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search facts and articles") },
            singleLine = true,
            leadingIcon = {
                VisitorAssetImage(
                    model = VisitorAssets.SearchIcon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        )
    }
}

@Composable
private fun MuseumInfoCard(info: MuseumInformationDto) {
    val context = LocalContext.current
    val hasCoordinates = info.latitude != null && info.longitude != null
    val hasAnyInfo = info.museumName.hasMuseumContent() ||
        info.description.hasMuseumContent() ||
        info.campusLocation.hasMuseumContent() ||
        info.openingHours.hasMuseumContent() ||
        info.contactEmail.hasMuseumContent() ||
        info.contactPhone.hasMuseumContent() ||
        info.visitorGuidelines.hasMuseumContent() ||
        info.accessibilityInformation.hasMuseumContent()

    Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Lg)) {
        VisitorIllustration(
            model = VisitorAssets.MuseumLocation,
            contentDescription = "Museum location illustration",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        )
        MuseumSectionTitle(
            title = info.museumName.takeIf { it.hasMuseumContent() } ?: "Museum Information",
            subtitle = "Visitor details appear here when configured by the museum team."
        )
        if (!hasAnyInfo) {
            EmptyState("Museum information will appear here once configured.")
        } else {
            MuseumInfoRow("Description", info.description)
            MuseumInfoRow("Campus Location", info.campusLocation)
            MuseumInfoRow("Opening Hours", info.openingHours)
            MuseumInfoRow("Contact Email", info.contactEmail)
            MuseumInfoRow("Contact Phone", info.contactPhone)
            MuseumInfoRow("Visitor Guidelines", info.visitorGuidelines)
            MuseumInfoRow("Accessibility", info.accessibilityInformation)
            if (hasCoordinates) {
                Button(
                    onClick = {
                        val uri = Uri.parse("geo:${info.latitude},${info.longitude}?q=${info.latitude},${info.longitude}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null)
                    Text("Open Map")
                }
            }
        }
    }
}
