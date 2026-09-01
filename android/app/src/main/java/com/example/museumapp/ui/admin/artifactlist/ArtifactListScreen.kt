package com.example.museumapp.ui.admin.artifactlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.museumapp.data.model.ArtifactDto
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.ui.admin.artifactcategories.ArtifactCategoriesScreen
import com.example.museumapp.ui.admin.components.ArtifactAiStatusChip

@Composable
fun ArtifactListScreen(
    repository: AdminRepository,
    padding: PaddingValues,
    onAddArtifact: () -> Unit,
    onEditArtifact: (String) -> Unit,
    initialDestination: String = ArtifactListDestinations.All,
    onCategoryCreated: (String) -> Unit = {}
) {
    val viewModel: ArtifactListViewModel = viewModel(
        key = "artifact_list_$initialDestination",
        factory = ArtifactListViewModel.factory(repository, initialDestination)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.padding(padding),
        floatingActionButton = {
            if (uiState.selectedDestination != ArtifactListDestinations.Categories) {
                ExtendedFloatingActionButton(
                    onClick = onAddArtifact,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add Artifact", maxLines = 1) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            ListHeader(
                uiState = uiState,
                showRefresh = uiState.selectedDestination != ArtifactListDestinations.Categories,
                onRefresh = viewModel::refresh
            )
            ArtifactSectionTabs(uiState.selectedDestination, viewModel::selectDestination)
            if (uiState.selectedDestination == ArtifactListDestinations.Categories) {
                ArtifactCategoriesScreen(
                    repository = repository,
                    modifier = Modifier.weight(1f),
                    onCategoryCreated = { onCategoryCreated(it.name) }
                )
            } else {
                SearchAndFilterRow(uiState, viewModel)
                when {
                    uiState.isLoading -> LoadingState()
                    uiState.errorMessage != null && uiState.artifacts.isEmpty() -> ErrorState(uiState.errorMessage.orEmpty(), viewModel::refresh)
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
    }

    uiState.pendingDelete?.let { artifact ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete artifact?") },
            text = {
                Text(
                    "This will permanently remove this artifact record and its stored images.\n\nArtifact: ${artifact.name}\n\nThis action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    enabled = uiState.deletingId == null,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Artifact", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete, enabled = uiState.deletingId == null) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }
}

@Composable
private fun ListHeader(uiState: ArtifactListUiState, showRefresh: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Artifacts", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "${uiState.totalItems} record(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showRefresh) {
            IconButton(onClick = onRefresh, enabled = !uiState.isRefreshing) {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh artifacts")
                }
            }
        }
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
                leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.applyFilters() })
            )
            Box {
                OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
                    Text(sortLabel(uiState.sort))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    SortOption("Newest", "newest", uiState.sort, viewModel) { menuExpanded = false }
                    SortOption("Oldest", "oldest", uiState.sort, viewModel) { menuExpanded = false }
                    SortOption("Name A-Z", "name_asc", uiState.sort, viewModel) { menuExpanded = false }
                    SortOption("Name Z-A", "name_desc", uiState.sort, viewModel) { menuExpanded = false }
                }
            }
        }
        Button(onClick = viewModel::applyFilters, modifier = Modifier.fillMaxWidth()) {
            Text("Apply Filters")
        }
    }
}

@Composable
private fun ArtifactSectionTabs(selected: String, onSelected: (String) -> Unit) {
    val destinations = listOf(
        "All" to ArtifactListDestinations.All,
        "Published" to ArtifactListDestinations.Published,
        "Drafts" to ArtifactListDestinations.Drafts,
        "Categories" to ArtifactListDestinations.Categories
    )
    val selectedIndex = destinations.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        destinations.forEachIndexed { index, destination ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelected(destination.second) },
                text = { Text(destination.first, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
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
                text = label,
                fontWeight = if (selected == value) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        onClick = {
            viewModel.updateSort(value)
            closeMenu()
        }
    )
}

private fun sortLabel(value: String): String = when (value) {
    "oldest" -> "Oldest"
    "name_asc" -> "A-Z"
    "name_desc" -> "Z-A"
    else -> "Newest"
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
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        uiState.errorMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        items(uiState.artifacts, key = { it.id }) { artifact ->
            ArtifactCard(
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load More")
                }
            }
        }
    }
}

@Composable
private fun ArtifactCard(
    artifact: ArtifactDto,
    deleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtifactThumbnail(artifact.primaryImageUrl)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(artifact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ArtifactStatusBadges(artifact)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            artifact.artifactCode,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(
                        artifact.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ArtifactAiStatusChip(artifact.aiIndexStatus)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = !deleting) {
                    if (deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Artifact actions")
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtifactStatusBadges(artifact: ArtifactDto) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (artifact.status.equals("draft", ignoreCase = true)) {
            SmallBadge("Draft", warning = true)
        }
        if (artifact.primaryImageNeedsReview) {
            SmallBadge("Needs image review", warning = true)
        }
    }
}

@Composable
private fun SmallBadge(label: String, warning: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (warning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (warning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtifactThumbnail(imageUrl: String?) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(12.dp))
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text("Loading artifacts", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No artifacts found.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
