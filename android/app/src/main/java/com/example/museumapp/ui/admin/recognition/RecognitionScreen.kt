package com.example.museumapp.ui.admin.recognition

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import com.example.museumapp.data.model.ArtifactMatchDto
import com.example.museumapp.data.model.RecognitionResponseDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.ui.admin.components.MatchLevelChip

@Composable
fun RecognitionScreen(
    repository: AdminRepositoryContract,
    padding: PaddingValues
) {
    val viewModel: RecognitionViewModel = viewModel(factory = RecognitionViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = viewModel::selectImage
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AI Recognition", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Admin test",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            ReadinessCard(indexedVectors = uiState.indexedVectors, aiStatus = uiState.aiStatus)
        }
        item {
            ImageSelectionCard(
                image = uiState.selectedImage,
                isRecognizing = uiState.isRecognizing,
                onPick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRecognize = viewModel::recognize
            )
        }
        uiState.errorMessage?.let { message ->
            item { AiUnavailableCard(message, onTryAgain = viewModel::recognize) }
        }
        uiState.response?.let { response ->
            item {
                RecognitionResult(response = response, onTryAnother = viewModel::tryAnotherImage)
            }
        }
    }
}

@Composable
private fun ReadinessCard(indexedVectors: Int, aiStatus: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Indexed artifact images", style = MaterialTheme.typography.titleMedium)
            Text(
                if (indexedVectors > 0) {
                    "$indexedVectors indexed vector point(s) ready"
                } else {
                    "No indexed artifact images are ready yet"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "OpenCLIP ${aiStatus.replace('_', ' ')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImageSelectionCard(
    image: android.net.Uri?,
    isRecognizing: Boolean,
    onPick: () -> Unit,
    onRecognize: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (image == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No image selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    AsyncImage(
                        model = image,
                        contentDescription = "Selected recognition image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f), enabled = !isRecognizing) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                    Text(if (image == null) "Select" else "Replace")
                }
                Button(onClick = onRecognize, modifier = Modifier.weight(1f), enabled = image != null && !isRecognizing) {
                    if (isRecognizing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    }
                    Text("Recognize")
                }
            }
        }
    }
}

@Composable
private fun RecognitionResult(response: RecognitionResponseDto, onTryAnother: () -> Unit) {
    if (!response.matched || response.bestMatch == null) {
        NoMatchCard(response.message, onTryAnother)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BestMatchCard(match = response.bestMatch, level = response.matchLevel)
        if (response.otherMatches.isNotEmpty()) {
            Text("Alternative matches", style = MaterialTheme.typography.titleLarge)
            response.otherMatches.forEach { match ->
                AlternativeMatchCard(match)
            }
        }
        OutlinedButton(onClick = onTryAnother, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("Try Another Image")
        }
    }
}

@Composable
private fun BestMatchCard(match: ArtifactMatchDto, level: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Best match", style = MaterialTheme.typography.titleLarge)
                MatchLevelChip(level)
            }
            MatchArtifactContent(match, large = true)
        }
    }
}

@Composable
private fun AlternativeMatchCard(match: ArtifactMatchDto) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        MatchArtifactContent(match, large = false, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun MatchArtifactContent(match: ArtifactMatchDto, large: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (large) 92.dp else 64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (match.artifact.primaryImageUrl.isNullOrBlank()) {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AsyncImage(
                    model = match.artifact.primaryImageUrl,
                    contentDescription = match.artifact.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(match.artifact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(match.artifact.artifactCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(match.artifact.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (large) {
                Text(match.artifact.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Similarity score ${match.similarityScore}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${match.supportingImageHits} supporting image hit(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoMatchCard(message: String, onTryAnother: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MatchLevelChip("no_match")
            Text(message, style = MaterialTheme.typography.bodyLarge)
            FilledTonalButton(onClick = onTryAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Try Another Image")
            }
        }
    }
}

@Composable
private fun AiUnavailableCard(message: String, onTryAgain: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onTryAgain) {
                Text("Retry")
            }
        }
    }
}
