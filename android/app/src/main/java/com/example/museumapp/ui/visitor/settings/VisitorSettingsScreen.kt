package com.example.museumapp.ui.visitor.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.example.museumapp.ui.visitor.components.MuseumSectionTitle
import com.example.museumapp.ui.visitor.components.VisitorAssetImage
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorCorners
import com.example.museumapp.ui.visitor.components.VisitorSpacing

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
            .padding(padding),
        contentPadding = PaddingValues(
            start = VisitorSpacing.Lg,
            top = VisitorSpacing.Xl,
            end = VisitorSpacing.Lg,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xl)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm)) {
                Text("Visitor Settings", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("Manage your visit profile and account access on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { ProfileCard(uiState.session) }
        item {
            SettingsGroup(title = "Museum Guide") {
                Button(onClick = onMuseumInfo, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Text("Museum Information")
                }
                InfoRow("App Version", uiState.appVersion)
            }
        }
        item {
            SettingsGroup(title = "Account") {
                OutlinedButton(onClick = { confirmAction = "switch" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.SwitchAccount, contentDescription = null)
                    Text("Switch Account")
                }
                OutlinedButton(onClick = { confirmAction = "logout" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Text("Log Out")
                }
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
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
        MuseumSectionTitle(title = title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(VisitorCorners.Lg),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(VisitorSpacing.Lg), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
                content()
            }
        }
    }
}

@Composable
private fun ProfileCard(session: VisitorSession) {
    Surface(
        shape = RoundedCornerShape(VisitorCorners.Xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(VisitorSpacing.Lg), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(initialsFor(session))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xs)) {
                    Text(session.displayName.ifBlank { "Visitor" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(if (session.isStudent) "Student visitor" else "Guest visitor", color = MaterialTheme.colorScheme.secondary)
                }
                VisitorAssetImage(VisitorAssets.ProfileIcon, contentDescription = null, modifier = Modifier.size(34.dp))
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
