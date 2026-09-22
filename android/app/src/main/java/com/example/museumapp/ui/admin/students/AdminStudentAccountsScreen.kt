package com.example.museumapp.ui.admin.students

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.model.AdminStudentDetailDto
import com.example.museumapp.data.model.AdminStudentListItemDto
import com.example.museumapp.data.repository.AdminRepositoryContract
import com.example.museumapp.ui.admin.components.StudentAccountStatusChip
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStudentAccountsScreen(
    repository: AdminRepositoryContract,
    padding: PaddingValues
) {
    val viewModel: AdminStudentAccountsViewModel = viewModel(factory = AdminStudentAccountsViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbarMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Student Accounts", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Review and manage registered student access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by Student ID, name or email") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudentAccountFilters.Options.forEach { filter ->
                        FilterChip(
                            selected = uiState.selectedFilter == filter,
                            onClick = { viewModel.selectFilter(filter) },
                            label = { Text(filter.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
            if (uiState.selectedFilter == StudentAccountFilters.Pending && uiState.students.isNotEmpty()) {
                item {
                    Text(
                        "${uiState.students.size} pending approval${if (uiState.students.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            when {
                uiState.isLoading -> item { LoadingState() }
                uiState.errorMessage != null -> item {
                    ErrorState(message = uiState.errorMessage.orEmpty(), onRetry = viewModel::refresh)
                }
                uiState.students.isEmpty() -> item {
                    EmptyState(searchActive = uiState.searchQuery.isNotBlank(), filter = uiState.selectedFilter)
                }
                else -> items(uiState.students, key = { it.id }) { student ->
                    StudentAccountRow(student = student, onView = { viewModel.openDetails(student.id) })
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(padding)
        ) { Snackbar(it) }
    }

    if (uiState.isDetailLoading || uiState.selectedStudent != null) {
        StudentAccountDetailSheet(
            detail = uiState.selectedStudent,
            isLoading = uiState.isDetailLoading,
            isUpdatingStatus = uiState.isUpdatingStatus,
            pendingStatusChange = uiState.pendingStatusChange,
            onDismiss = viewModel::closeDetails,
            onRequestStatusChange = viewModel::requestStatusChange,
            onDismissStatusChange = viewModel::dismissStatusChange,
            onConfirmStatusChange = viewModel::confirmStatusChange
        )
    }
}

@Composable
private fun StudentAccountRow(student: AdminStudentListItemDto, onView: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(student.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    student.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StudentAccountStatusChip(status = student.accountStatus)
            TextButton(onClick = onView) { Text("View") }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyState(searchActive: Boolean, filter: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Outlined.PeopleOutline,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val message = when {
            searchActive -> "No student accounts match your search."
            filter == StudentAccountFilters.Pending -> "No student accounts are waiting for approval."
            filter == StudentAccountFilters.Active -> "No active student accounts yet."
            filter == StudentAccountFilters.Inactive -> "No inactive student accounts."
            else -> "No student accounts yet."
        }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentAccountDetailSheet(
    detail: AdminStudentDetailDto?,
    isLoading: Boolean,
    isUpdatingStatus: Boolean,
    pendingStatusChange: String?,
    onDismiss: () -> Unit,
    onRequestStatusChange: (String) -> Unit,
    onDismissStatusChange: () -> Unit,
    onConfirmStatusChange: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading || detail == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(detail.displayName, style = MaterialTheme.typography.headlineSmall)
                        StudentAccountStatusChip(status = detail.accountStatus)
                    }
                }

                DetailSection(title = "Account") {
                    DetailRow("Full Name", detail.displayName)
                    DetailRow("Email", detail.email)
                    DetailRow("Student ID", detail.studentId)
                    DetailRow("Account Status", detail.accountStatus.replaceFirstChar { it.uppercase() })
                }

                DetailSection(title = "Academic Information") {
                    DetailRow("Course / Program", detail.course)
                    DetailRow("Year Level", detail.yearLevel)
                }

                DetailSection(title = "Activity") {
                    DetailRow("Registered Date", formatInstant(detail.createdAt))
                    detail.approvedAt?.let { DetailRow("Approved Date", formatInstant(it)) }
                    DetailRow("Last Login", detail.lastLoginAt?.let { formatInstant(it) } ?: "Never")
                }

                Spacer(modifier = Modifier.height(4.dp))

                when (detail.accountStatus) {
                    StudentAccountFilters.Pending -> StatusActionButton(
                        label = "Activate Account",
                        enabled = !isUpdatingStatus,
                        onClick = { onRequestStatusChange(StudentAccountFilters.Active) }
                    )
                    StudentAccountFilters.Active -> StatusActionButton(
                        label = "Deactivate Account",
                        enabled = !isUpdatingStatus,
                        onClick = { onRequestStatusChange(StudentAccountFilters.Inactive) }
                    )
                    StudentAccountFilters.Inactive -> StatusActionButton(
                        label = "Reactivate Account",
                        enabled = !isUpdatingStatus,
                        onClick = { onRequestStatusChange(StudentAccountFilters.Active) }
                    )
                }
            }
        }
    }

    pendingStatusChange?.let { targetStatus ->
        StatusChangeConfirmationDialog(
            targetStatus = targetStatus,
            isUpdating = isUpdatingStatus,
            onConfirm = onConfirmStatusChange,
            onDismiss = onDismissStatusChange
        )
    }
}

@Composable
private fun StatusActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!enabled) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(label)
    }
}

@Composable
private fun StatusChangeConfirmationDialog(
    targetStatus: String,
    isUpdating: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, body, confirmLabel) = when (targetStatus) {
        StudentAccountFilters.Active -> Triple(
            "Activate student account?",
            "This student will be able to sign in after activation.",
            "Activate"
        )
        else -> Triple(
            "Deactivate student account?",
            "The student will no longer be able to use authenticated student access.",
            "Deactivate"
        )
    }
    AlertDialog(
        onDismissRequest = { if (!isUpdating) onDismiss() },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isUpdating) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUpdating) { Text("Cancel") }
        }
    )
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatInstant(raw: String): String {
    return try {
        val parsed = OffsetDateTime.parse(raw)
        parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
    } catch (_: DateTimeParseException) {
        raw
    }
}
