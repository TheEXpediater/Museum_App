package com.example.museumapp.ui.visitor.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.data.session.VisitorSession
import com.example.museumapp.ui.visitor.components.InfoRow
import com.example.museumapp.ui.visitor.components.InitialsAvatar

@Composable
fun VisitorSettingsScreen(
    repository: VisitorRepositoryContract,
    padding: PaddingValues,
    onLoggedOut: () -> Unit,
    onAdminLogin: () -> Unit,
    onMuseumInfo: () -> Unit
) {
    val viewModel: VisitorSettingsViewModel = viewModel(factory = VisitorSettingsViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ProfileCard(uiState.session) }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onMuseumInfo, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                        Text("Museum Information")
                    }
                    InfoRow("App Version", uiState.appVersion)
                }
            }
        }
        item {
            OutlinedButton(onClick = { confirmAction = "switch" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.SwitchAccount, contentDescription = null)
                Text("Switch Account")
            }
        }
        item {
            OutlinedButton(onClick = { confirmAction = "logout" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Logout, contentDescription = null)
                Text("Log Out")
            }
        }
        item {
            TextButton(onClick = onAdminLogin, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
                Text("Administrator Login")
            }
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(if (action == "switch") "Switch Account?" else "Log Out?") },
            text = { Text("Your visitor session on this device will be cleared.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmAction = null
                        viewModel.logout()
                    }
                ) {
                    Text(if (action == "switch") "Switch" else "Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(session: VisitorSession) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(initialsFor(session))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(session.displayName.ifBlank { "Visitor" }, style = MaterialTheme.typography.titleLarge)
                    Text(if (session.isStudent) "Student" else "Guest Visitor", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (session.isStudent) {
                InfoRow("Student ID", session.studentId)
                InfoRow("Year Level", session.yearLevel)
                InfoRow("Course", session.course)
                InfoRow("Email", session.email)
            } else {
                InfoRow("Relationship to PSAU", session.relationshipType)
                InfoRow("Relationship Details", guestDetail(session))
            }
        }
    }
}

private fun initialsFor(session: VisitorSession): String {
    val first = session.firstName.firstOrNull()?.uppercaseChar()
    val last = session.lastName.firstOrNull()?.uppercaseChar()
    return listOfNotNull(first, last).joinToString("").ifBlank { "MG" }
}

private fun guestDetail(session: VisitorSession): String {
    return when {
        session.relationshipDetail.isNotBlank() -> session.relationshipDetail
        session.batchOrGraduationYear.isNotBlank() -> session.batchOrGraduationYear
        session.officeOrDepartment.isNotBlank() -> session.officeOrDepartment
        else -> "Not provided"
    }
}
