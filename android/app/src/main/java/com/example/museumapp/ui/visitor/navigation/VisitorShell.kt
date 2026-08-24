package com.example.museumapp.ui.visitor.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home as FilledHome
import androidx.compose.material.icons.filled.Inventory2 as FilledInventory2
import androidx.compose.material.icons.filled.Settings as FilledSettings
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.Inventory2 as OutlinedInventory2
import androidx.compose.material.icons.outlined.Settings as OutlinedSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.museumapp.data.repository.VisitorRepositoryContract
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorAssetImage
import com.example.museumapp.ui.visitor.scan.VisitorScanSheet
import com.example.museumapp.ui.visitor.theme.VisitorMuseumTokens

data class VisitorDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null,
    val assetIcon: String? = null,
    val opensScanSheet: Boolean = false
)

val VisitorTopLevelDestinations = listOf(
    VisitorDestination(VisitorRoutes.Home, "Home", Icons.Filled.FilledHome, Icons.Outlined.OutlinedHome),
    VisitorDestination(VisitorRoutes.Artifacts, "Artifacts", Icons.Filled.FilledInventory2, Icons.Outlined.OutlinedInventory2),
    VisitorDestination(VisitorRoutes.Scan, "Scan", assetIcon = VisitorAssets.ScanIcon, opensScanSheet = true),
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
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                VisitorTopLevelDestinations.forEach { destination ->
                    VisitorNavigationItem(
                        destination = destination,
                        currentRoute = currentRoute,
                        onNavigate = onNavigate,
                        onScan = { showScanSheet = true }
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
    onNavigate: (String) -> Unit,
    onScan: () -> Unit
) {
    val selected = currentRoute == destination.route || (destination.opensScanSheet && currentRoute == VisitorRoutes.Camera)
    NavigationBarItem(
        selected = selected,
        onClick = {
            if (destination.opensScanSheet) onScan() else onNavigate(destination.route)
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        icon = {
            if (destination.assetIcon != null) {
                Surface(
                    shape = CircleShape,
                    color = VisitorMuseumTokens.AntiqueGold.copy(alpha = if (selected) 0.28f else 0.16f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        VisitorAssetImage(
                            model = destination.assetIcon,
                            contentDescription = destination.label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            } else {
                Icon(
                    if (selected) destination.selectedIcon!! else destination.unselectedIcon!!,
                    contentDescription = destination.label
                )
            }
        },
        label = {
            Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    )
}
