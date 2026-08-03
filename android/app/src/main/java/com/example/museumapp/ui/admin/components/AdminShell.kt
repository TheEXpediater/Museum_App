package com.example.museumapp.ui.admin.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome as FilledAutoAwesome
import androidx.compose.material.icons.filled.Dashboard as FilledDashboard
import androidx.compose.material.icons.filled.Inventory2 as FilledInventory2
import androidx.compose.material.icons.filled.Settings as FilledSettings
import androidx.compose.material.icons.outlined.AutoAwesome as OutlinedAutoAwesome
import androidx.compose.material.icons.outlined.Dashboard as OutlinedDashboard
import androidx.compose.material.icons.outlined.Inventory2 as OutlinedInventory2
import androidx.compose.material.icons.outlined.Settings as OutlinedSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class AdminDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val AdminTopLevelDestinations = listOf(
    AdminDestination("admin_dashboard", "Dashboard", Icons.Filled.FilledDashboard, Icons.Outlined.OutlinedDashboard),
    AdminDestination("admin_artifact_list", "Artifacts", Icons.Filled.FilledInventory2, Icons.Outlined.OutlinedInventory2),
    AdminDestination("admin_ai_recognition", "Recognize", Icons.Filled.FilledAutoAwesome, Icons.Outlined.OutlinedAutoAwesome),
    AdminDestination("admin_settings", "Settings", Icons.Filled.FilledSettings, Icons.Outlined.OutlinedSettings)
)

@Composable
fun AdminShell(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp
        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    AdminTopLevelDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onNavigate(destination.route) },
                            icon = {
                                Icon(
                                    if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { NavigationLabel(destination.label) }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp)
                ) {
                    content(PaddingValues())
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        AdminTopLevelDestinations.forEach { destination ->
                            val selected = currentRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onNavigate(destination.route) },
                                icon = {
                                    Icon(
                                        if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.label
                                    )
                                },
                                label = { NavigationLabel(destination.label) }
                            )
                        }
                    }
                }
            ) { padding -> content(padding) }
        }
    }
}

@Composable
private fun NavigationLabel(label: String) {
    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
