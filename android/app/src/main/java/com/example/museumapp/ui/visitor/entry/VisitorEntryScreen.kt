package com.example.museumapp.ui.visitor.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.museumapp.ui.visitor.components.VisitorAssetImage
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorCorners
import com.example.museumapp.ui.visitor.components.VisitorIllustration
import com.example.museumapp.ui.visitor.components.VisitorSpacing

@Composable
fun VisitorEntryScreen(
    onGuest: () -> Unit,
    onStudentLogin: () -> Unit,
    onStudentRegister: () -> Unit,
    onAdminLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VisitorSpacing.Xl, vertical = VisitorSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VisitorAssetImage(
            model = VisitorAssets.AppLogo,
            contentDescription = "PSAU Museum Guide app logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(72.dp)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm)) {
            Text(
                "PSAU Museum Guide",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                "Choose the visit mode that fits your connection to the museum.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        VisitorIllustration(
            model = VisitorAssets.AuthGuestStudent,
            contentDescription = "Museum visitors choosing guest or student access",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp, max = 280.dp)
        )
        AccessOption(
            title = "Guest Visit",
            body = "Continue with a short visitor profile for browsing collections and museum information.",
            primaryLabel = "Continue as Guest",
            primaryIcon = Icons.Outlined.Person,
            onPrimary = onGuest
        )
        AccessOption(
            title = "Student Access",
            body = "Use your student account for a personalized visit record.",
            primaryLabel = "Student Login",
            primaryIcon = Icons.AutoMirrored.Outlined.Login,
            onPrimary = onStudentLogin,
            secondaryLabel = "Create Student Account",
            secondaryIcon = Icons.Outlined.PersonAdd,
            onSecondary = onStudentRegister
        )
        TextButton(onClick = onAdminLogin) {
            Row(horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
                Text("Administrator Login")
            }
        }
    }
}

@Composable
private fun AccessOption(
    title: String,
    body: String,
    primaryLabel: String,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onSecondary: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(VisitorCorners.Lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(VisitorSpacing.Lg), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Icon(primaryIcon, contentDescription = null)
                Text(primaryLabel)
            }
            if (secondaryLabel != null && secondaryIcon != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                    Icon(secondaryIcon, contentDescription = null)
                    Text(secondaryLabel)
                }
            }
        }
    }
}
