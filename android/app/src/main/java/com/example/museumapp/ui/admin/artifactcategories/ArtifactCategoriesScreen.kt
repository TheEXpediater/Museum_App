package com.example.museumapp.ui.admin.artifactcategories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.model.ArtifactCategoryDto
import com.example.museumapp.data.repository.AdminRepositoryContract

@Composable
fun ArtifactCategoriesScreen(
    repository: AdminRepositoryContract,
    modifier: Modifier = Modifier,
    onCategoryCreated: (ArtifactCategoryDto) -> Unit = {}
) {
    val viewModel: ArtifactCategoriesViewModel = viewModel(factory = ArtifactCategoriesViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var renamingCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val renamingCategory = uiState.categories.firstOrNull { it.id == renamingCategoryId }

    LaunchedEffect(uiState.createdCategoryResult?.id) {
        uiState.createdCategoryResult?.let {
            onCategoryCreated(it)
            viewModel.clearCreatedCategoryResult()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Categories", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${uiState.categories.size} category record(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = viewModel::refresh, enabled = !uiState.isRefreshing && !uiState.isMutating) {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh categories")
                }
            }
        }

        OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("Add Category", maxLines = 1)
        }

        uiState.actionMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        uiState.errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        when {
            uiState.isLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.categories.isEmpty() -> Text("No categories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.categories, key = { it.id }) { category ->
                    CategoryCard(
                        category = category,
                        actionsEnabled = !uiState.isMutating,
                        onRename = { renamingCategoryId = category.id },
                        onActivate = { viewModel.activateCategory(category) },
                        onDeactivate = { viewModel.requestDeactivate(category) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryNameDialog(
            title = "Add Category",
            confirmLabel = "Add Category",
            initialValue = "",
            onDismiss = { showAddDialog = false },
            onConfirm = {
                showAddDialog = false
                viewModel.addCategory(it)
            }
        )
    }

    if (renamingCategory != null) {
        CategoryNameDialog(
            title = "Rename Category",
            confirmLabel = "Rename",
            initialValue = renamingCategory.name,
            helperText = "Current: ${renamingCategory.name}",
            onDismiss = { renamingCategoryId = null },
            onConfirm = {
                renamingCategoryId = null
                viewModel.renameCategory(renamingCategory, it)
            }
        )
    }

    uiState.pendingDeactivate?.let { category ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeactivate,
            title = { Text("Deactivate category?") },
            text = {
                Text(
                    "\"${category.name}\" will no longer be available for new selections. Existing artifacts using this category will not be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDeactivate,
                    enabled = !uiState.isMutating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Deactivate", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeactivate, enabled = !uiState.isMutating) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }
}

@Composable
private fun CategoryCard(
    category: ArtifactCategoryDto,
    actionsEnabled: Boolean,
    onRename: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${category.artifactCount} artifact(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CategoryStatusBadge(category.isActive)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = actionsEnabled) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Category actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    if (category.isActive) {
                        DropdownMenuItem(
                            text = { Text("Deactivate", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDeactivate()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Activate") },
                            leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onActivate()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryStatusBadge(active: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = if (active) "Active" else "Inactive",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    helperText: String? = null
) {
    var value by rememberSaveable(title, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                helperText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", maxLines = 1)
            }
        }
    )
}
