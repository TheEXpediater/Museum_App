package com.example.museumapp.ui.visitor.artifacts

import android.content.Intent
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.museumapp.data.model.PublicArtifactDto
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.ArtifactImage
import com.example.museumapp.ui.visitor.components.InfoRow
import com.example.museumapp.ui.visitor.components.VisitorErrorCard
import com.example.museumapp.ui.visitor.components.VisitorLoading

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
                modifier = Modifier.padding(padding).padding(16.dp)
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
    relatedArticles: List<com.example.museumapp.data.model.ArticleDto>,
    openedFromScan: Boolean,
    onScanAgain: () -> Unit,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                ArtifactImage(
                    artifact = artifact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                )
            }
        }
        if (artifact.imageUrls.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(artifact.imageUrls, key = { it }) { image ->
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth(0.34f)
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(artifact.name, style = MaterialTheme.typography.headlineSmall)
                Text(artifact.artifactCode, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(artifact.category, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { DetailSection("Description") { Text(artifact.description, style = MaterialTheme.typography.bodyLarge) } }
        item {
            DetailSection("Artifact Information") {
                InfoRow("Origin", artifact.origin)
                InfoRow("Historical Period", artifact.historicalPeriod)
                InfoRow("Material", artifact.material)
                InfoRow("Dimensions", artifact.dimensions)
                InfoRow("Condition", artifact.condition)
            }
        }
        item {
            DetailSection("Related Facts") {
                Text("More facts will appear when published museum articles are linked to this artifact.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DetailSection("Related Articles") {
                if (relatedArticles.isEmpty()) {
                    Text("No related articles yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    relatedArticles.forEach { article ->
                        Text(article.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(article.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (openedFromScan) {
            item {
                Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Text("Scan Again")
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
