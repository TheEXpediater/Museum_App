package com.example.museumapp.ui.visitor.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.VisitorFormValidation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestInfoScreen(
    repository: VisitorRepositoryContract,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val viewModel: GuestInfoViewModel = viewModel(factory = GuestInfoViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var relationshipExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guest Information") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Tell us who is visiting today.", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Current students should use Student Login or create a student account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            VisitorTextField("First Name", uiState.firstName, viewModel::updateFirstName, uiState.errors["firstName"])
            VisitorTextField("Last Name", uiState.lastName, viewModel::updateLastName, uiState.errors["lastName"])
            Column {
                OutlinedTextField(
                    value = uiState.relationship,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PSAU Relationship") },
                    isError = uiState.errors["relationship"] != null,
                    supportingText = { uiState.errors["relationship"]?.let { Text(it) } },
                    trailingIcon = {
                        IconButton(onClick = { relationshipExpanded = true }) {
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Select relationship")
                        }
                    }
                )
                DropdownMenu(expanded = relationshipExpanded, onDismissRequest = { relationshipExpanded = false }) {
                    VisitorFormValidation.GuestRelationships.forEach { relationship ->
                        DropdownMenuItem(
                            text = { Text(relationship) },
                            onClick = {
                                viewModel.updateRelationship(relationship)
                                relationshipExpanded = false
                            }
                        )
                    }
                }
            }
            if (uiState.relationship == "Alumni or Former Student") {
                VisitorTextField(
                    label = "Batch or Graduation Year",
                    value = uiState.batchOrGraduationYear,
                    onValueChange = viewModel::updateBatchOrGraduationYear,
                    error = null
                )
            }
            if (uiState.relationship in setOf("Current Employee", "Former Employee")) {
                VisitorTextField(
                    label = "Office or Department",
                    value = uiState.officeOrDepartment,
                    onValueChange = viewModel::updateOfficeOrDepartment,
                    error = null
                )
            }
            if (uiState.relationship == "Other") {
                VisitorTextField(
                    label = "Please Specify",
                    value = uiState.otherDetail,
                    onValueChange = viewModel::updateOtherDetail,
                    error = uiState.errors["otherDetail"]
                )
            }
            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = viewModel::continueToMuseum,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 10.dp))
                }
                Text("Continue to Museum")
            }
        }
    }
}

@Composable
private fun VisitorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } }
    )
}
