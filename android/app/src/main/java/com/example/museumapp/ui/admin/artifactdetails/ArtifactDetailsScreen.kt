package com.example.museumapp.ui.admin.artifactdetails

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.ui.admin.components.ArtifactAiStatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactDetailsScreen(
    repository: AdminRepositoryContract,
    artifactId: String?,
    onBack: () -> Unit,
    onEditArtifact: (String) -> Unit
) {
    val viewModel: ArtifactDetailsViewModel = viewModel(
        key = "artifact_details_$artifactId",
        factory = ArtifactDetailsViewModel.factory(repository, artifactId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                        IconButton(onClick = { onEditArtifact(artifact.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit artifact")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            uiState.errorMessage != null -> ArtifactDetailsError(
                message = uiState.errorMessage.orEmpty(),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            )
            uiState.artifact != null -> ArtifactDetailsContent(
                artifact = uiState.artifact!!,
                padding = padding
            )
        }
    }
}

@Composable
private fun ArtifactDetailsContent(artifact: ArtifactDto, padding: PaddingValues) {
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
            PrimaryImageCard(artifact)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(artifact.name, style = MaterialTheme.typography.headlineSmall)
                Text(artifact.artifactCode, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(artifact.category, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            DetailSection("Description") {
                Text(artifact.description, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            DetailSection("Historical Information") {
                DetailRow("Origin", artifact.origin)
                DetailRow("Historical period", artifact.historicalPeriod)
            }
        }
        item {
            DetailSection("Physical Details") {
                DetailRow("Material", artifact.material)
                DetailRow("Dimensions", artifact.dimensions)
                DetailRow("Condition", artifact.condition)
            }
        }
        item {
            AdditionalImagesSection(artifact)
        }
        item {
            AiIndexSection(artifact)
        }
    }
}

@Composable
private fun PrimaryImageCard(artifact: ArtifactDto) {
    val primaryImage = artifact.primaryImageUrl ?: artifact.imageUrls.firstOrNull()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (primaryImage.isNullOrBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No primary image", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                AsyncImage(
                    model = primaryImage,
                    contentDescription = artifact.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun AdditionalImagesSection(artifact: ArtifactDto) {
    val primaryImage = artifact.primaryImageUrl ?: artifact.imageUrls.firstOrNull()
    val additionalImages = artifact.imageUrls.filter { it.isNotBlank() && it != primaryImage }
    DetailSection("Additional Images") {
        if (additionalImages.isEmpty()) {
            Text("No additional images", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(additionalImages, key = { it }) { image ->
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiIndexSection(artifact: ArtifactDto) {
    DetailSection("AI Index Status") {
        ArtifactAiStatusChip(artifact.aiIndexStatus)
        DetailRow("Indexed images", artifact.aiIndexedImageCount?.toString() ?: "0")
        DetailRow("Indexed at", artifact.aiIndexedAt)
        if (!artifact.aiIndexError.isNullOrBlank()) {
            Text(artifact.aiIndexError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "Not provided",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ArtifactDetailsError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                Button(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Retry")
                }
            }
        }
    }
}
