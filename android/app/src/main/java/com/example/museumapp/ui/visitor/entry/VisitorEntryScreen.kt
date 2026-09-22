package com.example.museumapp.ui.visitor.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.ui.admin.login.AdminLoginDialog
import com.example.museumapp.ui.visitor.components.VisitorAssetImage
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorSpacing

private const val RoleSectionSpacerWeight = 1f

data class VisitorEntrySelectionSpec(
    val target: String,
    val contentDescription: String,
    val icon: String,
    val illustration: String
)

val VisitorEntrySelections = listOf(
    VisitorEntrySelectionSpec(
        target = "Guest",
        contentDescription = "Sign in as Guest",
        icon = VisitorAssets.VisitorGuestIcon,
        illustration = VisitorAssets.VisitorGuestCharacter
    ),
    VisitorEntrySelectionSpec(
        target = "Student",
        contentDescription = "Sign in as Student",
        icon = VisitorAssets.VisitorStudentIcon,
        illustration = VisitorAssets.VisitorStudentCharacter
    )
)

object VisitorEntryTestTags {
    const val Root = "visitor_entry_root"
    const val GuestCard = "visitor_entry_guest_card"
    const val StudentCard = "visitor_entry_student_card"
    const val AdminAccess = "visitor_entry_admin_access"
    const val GuestCharacter = "visitor_entry_guest_character"
    const val StudentCharacter = "visitor_entry_student_character"
    const val StudentLogin = "visitor_entry_student_login"
    const val StudentRegister = "visitor_entry_student_register"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorEntryScreen(
    adminRepository: AdminRepository,
    onGuest: () -> Unit,
    onStudentLogin: () -> Unit,
    onStudentRegister: () -> Unit,
    onAdminLogin: () -> Unit
) {
    var showStudentAccess by rememberSaveable { mutableStateOf(false) }
    var showAdminLogin by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(VisitorEntryTestTags.Root)
    ) {
        val horizontalPadding = if (maxWidth >= 600.dp) {
            (maxWidth * 0.16f).coerceIn(48.dp, 180.dp)
        } else {
            (maxWidth * 0.06f).coerceIn(18.dp, 26.dp)
        }
        val selectionGap = (maxWidth * 0.04f).coerceIn(12.dp, 28.dp)
        val cardWidth = ((maxWidth - horizontalPadding * 2f - selectionGap) / 2f)
            .coerceIn(128.dp, if (maxWidth >= 600.dp) 252.dp else 188.dp)
        val topPadding = (maxHeight * 0.045f).coerceIn(22.dp, 48.dp)
        val bottomPadding = (maxHeight * 0.035f).coerceIn(18.dp, 36.dp)
        val compactHeight = maxHeight < 700.dp

        VisitorEntryBackground()

        if (compactHeight) {
            VisitorEntryScrollableContent(
                horizontalPadding = horizontalPadding,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                cardWidth = cardWidth,
                selectionGap = selectionGap,
                onGuest = onGuest,
                onStudent = { showStudentAccess = true }
            )
        } else {
            VisitorEntryAnchoredContent(
                horizontalPadding = horizontalPadding,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                cardWidth = cardWidth,
                selectionGap = selectionGap,
                onGuest = onGuest,
                onStudent = { showStudentAccess = true }
            )
        }

        IconButton(
            onClick = { showAdminLogin = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(VisitorSpacing.Md)
                .size(48.dp)
                .testTag(VisitorEntryTestTags.AdminAccess)
        ) {
            Icon(
                imageVector = Icons.Outlined.AdminPanelSettings,
                contentDescription = "Administrator access",
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showStudentAccess) {
        StudentAccessSheet(
            onDismiss = { showStudentAccess = false },
            onStudentLogin = {
                showStudentAccess = false
                onStudentLogin()
            },
            onStudentRegister = {
                showStudentAccess = false
                onStudentRegister()
            }
        )
    }

    if (showAdminLogin) {
        AdminLoginDialog(
            repository = adminRepository,
            onDismiss = { showAdminLogin = false },
            onLoginSuccess = {
                showAdminLogin = false
                onAdminLogin()
            }
        )
    }
}

@Composable
private fun VisitorEntryAnchoredContent(
    horizontalPadding: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
    cardWidth: Dp,
    selectionGap: Dp,
    onGuest: () -> Unit,
    onStudent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = horizontalPadding)
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SignInHeader()
        Spacer(modifier = Modifier.weight(RoleSectionSpacerWeight))
        VisitorRoleSelectionSection(
            modifier = Modifier.fillMaxWidth(),
            cardWidth = cardWidth,
            selectionGap = selectionGap,
            onGuest = onGuest,
            onStudent = onStudent
        )
        Spacer(modifier = Modifier.weight(RoleSectionSpacerWeight))
    }
}

@Composable
private fun VisitorEntryScrollableContent(
    horizontalPadding: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
    cardWidth: Dp,
    selectionGap: Dp,
    onGuest: () -> Unit,
    onStudent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SignInHeader()
        Spacer(modifier = Modifier.height(VisitorSpacing.Xl))
        VisitorRoleSelectionSection(
            modifier = Modifier.fillMaxWidth(),
            cardWidth = cardWidth,
            selectionGap = selectionGap,
            onGuest = onGuest,
            onStudent = onStudent
        )
        Spacer(modifier = Modifier.height(VisitorSpacing.Xl))
    }
}

@Composable
private fun VisitorEntryBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AsyncImage(
            model = VisitorAssets.VisitorEntryBackground,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.36f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun VisitorRoleSelectionSection(
    cardWidth: Dp,
    selectionGap: Dp,
    onGuest: () -> Unit,
    onStudent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(selectionGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VisitorRoleCard(
            title = VisitorEntrySelections[0].target,
            icon = VisitorEntrySelections[0].icon,
            image = VisitorEntrySelections[0].illustration,
            contentDescription = VisitorEntrySelections[0].contentDescription,
            cardTestTag = VisitorEntryTestTags.GuestCard,
            illustrationTestTag = VisitorEntryTestTags.GuestCharacter,
            modifier = Modifier
                .width(cardWidth)
                .aspectRatio(VisitorRoleCardAspectRatio),
            onPress = onGuest
        )
        VisitorRoleCard(
            title = VisitorEntrySelections[1].target,
            icon = VisitorEntrySelections[1].icon,
            image = VisitorEntrySelections[1].illustration,
            contentDescription = VisitorEntrySelections[1].contentDescription,
            cardTestTag = VisitorEntryTestTags.StudentCard,
            illustrationTestTag = VisitorEntryTestTags.StudentCharacter,
            modifier = Modifier
                .width(cardWidth)
                .aspectRatio(VisitorRoleCardAspectRatio),
            onPress = onStudent
        )
    }
}

@Composable
private fun SignInHeader() {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.32f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VisitorSpacing.Lg, vertical = VisitorSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisitorAssetImage(
                model = VisitorAssets.VisitorSignInIcon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Sign in as",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentAccessSheet(
    onDismiss: () -> Unit,
    onStudentLogin: () -> Unit,
    onStudentRegister: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = VisitorSpacing.Xl, end = VisitorSpacing.Xl, bottom = VisitorSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)
        ) {
            Text("Student Access", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "Create a student account to get started, or sign in if you already have one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(VisitorSpacing.Xs))
            Button(
                onClick = onStudentRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag(VisitorEntryTestTags.StudentRegister)
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(VisitorSpacing.Sm))
                Text("Create Student Account", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onStudentLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag(VisitorEntryTestTags.StudentLogin)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(VisitorSpacing.Sm))
                Text("Sign In")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Exit", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
