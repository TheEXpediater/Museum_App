package com.example.museumapp.ui.visitor.artifacts

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.museumapp.data.model.ArticleDto
import com.example.museumapp.data.model.PublicArtifactDto
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.ArtifactImage
import com.example.museumapp.ui.visitor.components.InfoRow
import com.example.museumapp.ui.visitor.components.MetadataRow
import com.example.museumapp.ui.visitor.components.NewsCard
import com.example.museumapp.ui.visitor.components.ScanButton
import com.example.museumapp.ui.visitor.components.VisitorAssetImage
import com.example.museumapp.ui.visitor.components.VisitorCorners
import com.example.museumapp.ui.visitor.components.VisitorErrorCard
import com.example.museumapp.ui.visitor.components.VisitorLoading
import com.example.museumapp.ui.visitor.components.VisitorSpacing
import com.example.museumapp.ui.visitor.components.hasMuseumContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorArtifactDetailsScreen(
    repository: VisitorRepositoryContract,
    artifactId: String?,
    openedFromScan: Boolean,
    onBack: () -> Unit,
    onScanAgain: () -> Unit
) {
    val viewModel: VisitorArtifactDetailsViewModel = viewModel(
        key = "visitor_artifact_$artifactId",
        factory = VisitorArtifactDetailsViewModel.factory(repository, artifactId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artifact Details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    uiState.artifact?.let { artifact ->
                        IconButton(
                            onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${artifact.name}\n${artifact.artifactCode}\n${artifact.description}")
                                }
                                context.startActivity(Intent.createChooser(share, "Share Artifact"))
                            }
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> VisitorLoading(modifier = Modifier.padding(padding))
            uiState.errorMessage != null -> VisitorErrorCard(
                message = uiState.errorMessage.orEmpty(),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .padding(padding)
                    .padding(VisitorSpacing.Lg)
            )
            uiState.artifact != null -> ArtifactDetailsContent(
                artifact = uiState.artifact!!,
                relatedArticles = uiState.relatedArticles,
                openedFromScan = openedFromScan,
                onScanAgain = onScanAgain,
                padding = padding
            )
        }
    }
}

@Composable
private fun ArtifactDetailsContent(
    artifact: PublicArtifactDto,
    relatedArticles: List<ArticleDto>,
    openedFromScan: Boolean,
    onScanAgain: () -> Unit,
    padding: PaddingValues
) {
    val metadata = listOf(
        "Period" to artifact.historicalPeriod,
        "Category" to artifact.category,
        "Origin" to artifact.origin
    )
    val informationValues = listOf(
        artifact.origin,
        artifact.historicalPeriod,
        artifact.material,
        artifact.dimensions,
        artifact.condition
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = VisitorSpacing.Lg,
            top = VisitorSpacing.Lg,
            end = VisitorSpacing.Lg,
            bottom = VisitorSpacing.Xxl
        ),
        verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xl)
    ) {
        item {
            ArtifactImage(
                artifact = artifact,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.08f)
            )
        }
        if (artifact.imageUrls.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
                    items(artifact.imageUrls, key = { it }) { image ->
                        Surface(
                            modifier = Modifier
                                .fillParentMaxWidth(0.32f)
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(VisitorCorners.Md),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(modifier = Modifier.padding(VisitorSpacing.Sm), contentAlignment = Alignment.Center) {
                                VisitorAssetImage(
                                    model = image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm)) {
                if (openedFromScan) {
                    Text("Recognized Artifact", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
                Text(artifact.name, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text(artifact.artifactCode, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                if (metadata.any { it.second.hasMuseumContent() }) {
                    MetadataRow(metadata, modifier = Modifier.padding(top = VisitorSpacing.Sm))
                }
            }
        }
        if (artifact.description.hasMuseumContent()) {
            item {
                DetailSection("Historical Description") {
                    Text(artifact.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (informationValues.any { it.hasMuseumContent() }) {
            item {
                DetailSection("Artifact Information") {
                    InfoRow("Origin", artifact.origin)
                    InfoRow("Historical Period", artifact.historicalPeriod)
                    InfoRow("Material", artifact.material)
                    InfoRow("Dimensions", artifact.dimensions)
                    InfoRow("Condition", artifact.condition)
                }
            }
        }
        if (relatedArticles.isNotEmpty()) {
            item {
                DetailSection("Related Articles") {
                    relatedArticles.forEach { article ->
                        NewsCard(article)
                    }
                }
            }
        }
        if (openedFromScan) {
            item {
                ScanButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth(), label = "Scan Again")
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md), content = content)
    }
}
