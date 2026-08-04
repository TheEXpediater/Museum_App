package com.example.museumapp.ui.visitor.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home as FilledHome
import androidx.compose.material.icons.filled.Inventory2 as FilledInventory2
import androidx.compose.material.icons.filled.Settings as FilledSettings
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.Inventory2 as OutlinedInventory2
import androidx.compose.material.icons.outlined.Settings as OutlinedSettings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.scan.VisitorScanSheet

data class VisitorDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val VisitorTopLevelDestinations = listOf(
    VisitorDestination(VisitorRoutes.Home, "Home", Icons.Filled.FilledHome, Icons.Outlined.OutlinedHome),
    VisitorDestination(VisitorRoutes.Artifacts, "Artifacts", Icons.Filled.FilledInventory2, Icons.Outlined.OutlinedInventory2),
    VisitorDestination(VisitorRoutes.Settings, "Settings", Icons.Filled.FilledSettings, Icons.Outlined.OutlinedSettings)
)

@Composable
fun VisitorShell(
    currentRoute: String?,
    repository: VisitorRepositoryContract,
    onNavigate: (String) -> Unit,
    onOpenCamera: () -> Unit,
    content: @Composable (PaddingValues, () -> Unit) -> Unit
) {
    var showScanSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Box {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.navigationBarsPadding()) {
                    VisitorNavigationItem(VisitorTopLevelDestinations[0], currentRoute, onNavigate)
                    VisitorNavigationItem(VisitorTopLevelDestinations[1], currentRoute, onNavigate)
                    Spacer(Modifier.weight(1f))
                    VisitorNavigationItem(VisitorTopLevelDestinations[2], currentRoute, onNavigate)
                }
                FloatingActionButton(
                    onClick = { showScanSheet = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(72.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    AsyncImage(
                        model = VisitorAssets.AiScanIcon,
                        contentDescription = "AI Scan for Artifact",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding) { showScanSheet = true }
        }
    }

    if (showScanSheet) {
        VisitorScanSheet(
            repository = repository,
            onDismiss = { showScanSheet = false },
            onOpenCamera = {
                showScanSheet = false
                onOpenCamera()
            },
            onContinueBrowsing = {
                showScanSheet = false
                onNavigate(VisitorRoutes.Artifacts)
            }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.VisitorNavigationItem(
    destination: VisitorDestination,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
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
        label = {
            Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    )
}
