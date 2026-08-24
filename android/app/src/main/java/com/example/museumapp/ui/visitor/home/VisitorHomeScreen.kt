package com.example.museumapp.ui.visitor.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.model.MuseumInformationDto
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.AnnouncementCard
import com.example.museumapp.ui.visitor.components.CollectionCard
import com.example.museumapp.ui.visitor.components.EmptyState
import com.example.museumapp.ui.visitor.components.MuseumInfoRow
import com.example.museumapp.ui.visitor.components.MuseumSectionTitle
import com.example.museumapp.ui.visitor.components.MuseumTopBar
import com.example.museumapp.ui.visitor.components.NewsCard
import com.example.museumapp.ui.visitor.components.ScanButton
import com.example.museumapp.ui.visitor.components.VisitorArtifactCard
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorChip
import com.example.museumapp.ui.visitor.components.VisitorErrorCard
import com.example.museumapp.ui.visitor.components.VisitorHero
import com.example.museumapp.ui.visitor.components.VisitorIllustration
import com.example.museumapp.ui.visitor.components.VisitorLoading
import com.example.museumapp.ui.visitor.components.VisitorSpacing
import com.example.museumapp.ui.visitor.components.hasMuseumContent

private val HomeMaxWidth = 920.dp

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
            modifier = Modifier
                .padding(padding)
                .padding(VisitorSpacing.Lg)
        )
        else -> {
            val home = uiState.home
            LazyColumn(
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
                verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    MuseumContent {
                        MuseumTopBar(
                            title = "PSAU Museum Guide",
                            subtitle = "Welcome, ${uiState.session.displayName.takeIf { it.isNotBlank() } ?: "Visitor"}",
                            onSearch = onExploreArtifacts
                        )
                    }
                }
                item {
                    MuseumContent {
                        VisitorChip(if (uiState.session.isStudent) "Student visitor" else "Guest visitor")
                    }
                }
                item {
                    MuseumContent {
                        VisitorHero(
                            title = "Discover Heritage",
                            body = "Explore artifact records, museum stories, and visitor information prepared for the PSAU Museum Guide.",
                            image = VisitorAssets.HomeMuseumHero,
                            contentDescription = "Museum guide hero illustration",
                            primaryActionLabel = "Explore Artifacts",
                            onPrimaryAction = onExploreArtifacts
                        )
                    }
                }
                item {
                    MuseumContent {
                        ScanButton(onClick = onScanArtifact, modifier = Modifier.fillMaxWidth())
                    }
                }
                home?.featuredArtifacts?.firstOrNull()?.let { featured ->
                    item {
                        MuseumContent {
                            Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
                                MuseumSectionTitle(
                                    title = "Featured Artifact",
                                    subtitle = "A selected object from the current museum collection.",
                                    actionLabel = "See all",
                                    onAction = onExploreArtifacts
                                )
                                VisitorArtifactCard(
                                    artifact = featured,
                                    onClick = { onArtifactDetails(featured.id) }
                                )
                            }
                        }
                    }
                }
                if (home?.featuredArtifacts.orEmpty().drop(1).isNotEmpty()) {
                    item {
                        MuseumContent {
                            Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
                                MuseumSectionTitle(title = "Recent Discoveries")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
                                    items(home!!.featuredArtifacts.drop(1), key = { it.id }) { artifact ->
                                        VisitorArtifactCard(
                                            artifact = artifact,
                                            onClick = { onArtifactDetails(artifact.id) },
                                            modifier = Modifier.fillParentMaxWidth(0.74f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    MuseumContent {
                        ExploreCollections(onExploreArtifacts, onScanArtifact)
                    }
                }
                item {
                    MuseumContent {
                        MuseumInformationPreview(home?.museumInformation)
                    }
                }
                if (!home?.latestNews.isNullOrEmpty()) {
                    item {
                        MuseumContent {
                            Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
                                VisitorIllustration(
                                    model = VisitorAssets.NewsAnnouncements,
                                    contentDescription = "Museum news and announcements illustration",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                )
                                MuseumSectionTitle(title = "Latest News")
                                home!!.latestNews.forEach { news -> NewsCard(news) }
                            }
                        }
                    }
                }
                if (!home?.announcements.isNullOrEmpty()) {
                    item {
                        MuseumContent {
                            Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
                                if (home?.latestNews.isNullOrEmpty()) {
                                    VisitorIllustration(
                                        model = VisitorAssets.NewsAnnouncements,
                                        contentDescription = "Museum news and announcements illustration",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                    )
                                }
                                MuseumSectionTitle(title = "Announcements")
                                home!!.announcements.forEach { announcement -> AnnouncementCard(announcement) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MuseumContent(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.widthIn(max = HomeMaxWidth).fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun ExploreCollections(
    onExploreArtifacts: () -> Unit,
    onScanArtifact: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
        MuseumSectionTitle(
            title = "Explore Collections",
            subtitle = "Move through catalogued objects, learning material, and guided scanning."
        )
        CollectionCard(
            title = "Artifact Catalogue",
            body = "Browse objects with real museum records and artifact photographs.",
            iconAsset = VisitorAssets.ArtifactIcon,
            onClick = onExploreArtifacts
        )
        CollectionCard(
            title = "Facts and Articles",
            body = "Read published educational content when available.",
            iconAsset = VisitorAssets.LearnIcon,
            onClick = onExploreArtifacts
        )
        CollectionCard(
            title = "Artifact Scanner",
            body = "Identify recognized artifacts through the visitor camera guide.",
            iconAsset = VisitorAssets.ScanIcon,
            onClick = onScanArtifact
        )
    }
}

@Composable
private fun MuseumInformationPreview(info: MuseumInformationDto?) {
    val hasName = info?.museumName.hasMuseumContent()
    val hasDescription = info?.description.hasMuseumContent()
    val hasLocation = info?.campusLocation.hasMuseumContent()
    val hasHours = info?.openingHours.hasMuseumContent()

    Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
        MuseumSectionTitle(
            title = "Museum Information",
            subtitle = "Practical visitor details from the configured museum record."
        )
        if (!hasName && !hasDescription && !hasLocation && !hasHours) {
            EmptyState("Museum information will appear here once configured.")
        } else {
            if (hasName) {
                Text(info!!.museumName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (hasDescription) {
                Text(info!!.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MuseumInfoRow("Location", info?.campusLocation, Icons.Outlined.Place)
            MuseumInfoRow("Opening Hours", info?.openingHours, Icons.Outlined.AccessTime)
        }
    }
}
