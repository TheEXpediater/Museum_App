package com.example.museumapp.ui.admin.artifactlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.repository.AdminRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactListScreen(
    repository: AdminRepository,
    onAddArtifact: () -> Unit,
    onEditArtifact: (String) -> Unit
) {
    val viewModel: ArtifactListViewModel = viewModel(factory = ArtifactListViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artifacts") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !uiState.isRefreshing) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddArtifact,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add Artifact") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            SearchAndFilterRow(uiState, viewModel)
            Spacer(Modifier.height(12.dp))
            if (uiState.errorMessage != null) {
                Text(uiState.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            when {
                uiState.isLoading -> LoadingState()
                uiState.artifacts.isEmpty() -> EmptyState()
                else -> ArtifactListContent(
                    uiState = uiState,
                    onEditArtifact = onEditArtifact,
                    onDeleteArtifact = viewModel::requestDelete,
                    onLoadMore = viewModel::loadNextPage
                )
            }
        }
    }

    uiState.pendingDelete?.let { artifact ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete artifact") },
            text = { Text("Delete ${artifact.name}?") },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    enabled = uiState.deletingId == null
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete, enabled = uiState.deletingId == null) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SearchAndFilterRow(uiState: ArtifactListUiState, viewModel: ArtifactListViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.search,
            onValueChange = viewModel::updateSearch,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search name or code") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.applyFilters() })
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = uiState.category,
                onValueChange = viewModel::updateCategory,
                modifier = Modifier.weight(1f),
                label = { Text("Category") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.applyFilters() })
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Sort")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    SortOption("Newest", "newest", uiState.sort, viewModel) { menuExpanded = false }
                    SortOption("Oldest", "oldest", uiState.sort, viewModel) { menuExpanded = false }
                    SortOption("Name A-Z", "name_asc", uiState.sort, viewModel) { menuExpanded = false }
                    SortOption("Name Z-A", "name_desc", uiState.sort, viewModel) { menuExpanded = false }
                }
            }
            Button(onClick = viewModel::applyFilters) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun SortOption(
    label: String,
    value: String,
    selected: String,
    viewModel: ArtifactListViewModel,
    closeMenu: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = if (selected == value) "$label selected" else label,
                fontWeight = if (selected == value) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        onClick = {
            viewModel.updateSort(value)
            closeMenu()
        }
    )
}

@Composable
private fun ArtifactListContent(
    uiState: ArtifactListUiState,
    onEditArtifact: (String) -> Unit,
    onDeleteArtifact: (ArtifactDto) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(uiState.artifacts, key = { it.id }) { artifact ->
            ArtifactRow(
                artifact = artifact,
                deleting = uiState.deletingId == artifact.id,
                onEdit = { onEditArtifact(artifact.id) },
                onDelete = { onDeleteArtifact(artifact) }
            )
        }
        if (uiState.page < uiState.totalPages) {
            item {
                Button(
                    onClick = onLoadMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 88.dp)
                ) {
                    Text("Load More")
                }
            }
        } else {
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun ArtifactRow(
    artifact: ArtifactDto,
    deleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtifactThumbnail(artifact.primaryImageUrl)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(artifact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artifact.artifactCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(artifact.category, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${artifact.name}")
            }
            IconButton(onClick = onDelete, enabled = !deleting) {
                if (deleting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${artifact.name}")
                }
            }
        }
    }
}

@Composable
private fun ArtifactThumbnail(imageUrl: String?) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No artifacts found.", style = MaterialTheme.typography.bodyLarge)
    }
}
