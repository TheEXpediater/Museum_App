package com.example.museumapp.ui.visitor.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.ScanButton
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorIllustration
import com.example.museumapp.ui.visitor.components.VisitorSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorScanSheet(
    repository: VisitorRepositoryContract,
    onDismiss: () -> Unit,
    onOpenCamera: () -> Unit,
    onContinueBrowsing: () -> Unit
) {
    val viewModel: VisitorScanViewModel = viewModel(factory = VisitorScanViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = VisitorSpacing.Xl, end = VisitorSpacing.Xl, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)
        ) {
            VisitorIllustration(
                model = VisitorAssets.OnboardingAiScan,
                contentDescription = "Visitor scanning an artifact",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 220.dp)
            )
            Text("Artifact Scanner", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "Point your camera at a museum artifact, keep the object inside the guide, and start the scan.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.isLoading) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Preparing scanner")
                }
            } else if (uiState.canOpenCamera) {
                Text("Scanner ready.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                uiState.indexedArtifacts?.let { count ->
                    Text("$count museum image(s) available for recognition.", style = MaterialTheme.typography.bodyMedium)
                }
                ScanButton(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth(), label = "Open Camera")
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            } else {
                Text(uiState.errorMessage ?: "Artifact scanning is temporarily unavailable.", color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Try Again")
                }
                OutlinedButton(onClick = onContinueBrowsing, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue Browsing")
                }
            }
        }
    }
}
