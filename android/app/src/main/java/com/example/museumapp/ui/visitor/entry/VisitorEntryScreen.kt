package com.example.museumapp.ui.visitor.entry

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.museumapp.ui.visitor.components.VisitorAssetImage
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorSpacing

private const val RoleSectionTopSpacerWeight = 0.34f
private const val RoleSectionBottomSpacerWeight = 0.66f

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
    const val AdminLogin = "visitor_entry_admin_login"
    const val GuestCharacter = "visitor_entry_guest_character"
    const val StudentCharacter = "visitor_entry_student_character"
    const val StudentLogin = "visitor_entry_student_login"
    const val StudentRegister = "visitor_entry_student_register"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorEntryScreen(
    onGuest: () -> Unit,
    onStudentLogin: () -> Unit,
    onStudentRegister: () -> Unit,
    onAdminLogin: () -> Unit
) {
    var showStudentAccess by rememberSaveable { mutableStateOf(false) }

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
                onStudent = { showStudentAccess = true },
                onAdminLogin = onAdminLogin
            )
        } else {
            VisitorEntryAnchoredContent(
                horizontalPadding = horizontalPadding,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                cardWidth = cardWidth,
                selectionGap = selectionGap,
                onGuest = onGuest,
                onStudent = { showStudentAccess = true },
                onAdminLogin = onAdminLogin
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
}

@Composable
private fun VisitorEntryAnchoredContent(
    horizontalPadding: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
    cardWidth: Dp,
    selectionGap: Dp,
    onGuest: () -> Unit,
    onStudent: () -> Unit,
    onAdminLogin: () -> Unit
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
        Spacer(modifier = Modifier.weight(RoleSectionTopSpacerWeight))
        VisitorRoleSelectionSection(
            modifier = Modifier.fillMaxWidth(),
            cardWidth = cardWidth,
            selectionGap = selectionGap,
            onGuest = onGuest,
            onStudent = onStudent
        )
        Spacer(modifier = Modifier.weight(RoleSectionBottomSpacerWeight))
        AdministratorLoginAction(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            onClick = onAdminLogin
        )
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
    onStudent: () -> Unit,
    onAdminLogin: () -> Unit
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
        AdministratorLoginAction(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            onClick = onAdminLogin
        )
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

@Composable
private fun AdministratorLoginAction(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.99f
            isHovered -> 1.015f
            else -> 1f
        },
        label = "adminLoginScale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = when {
            isPressed -> 1.dp
            isHovered -> 8.dp
            else -> 4.dp
        },
        label = "adminLoginShadow"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isPressed -> MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            isHovered -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        },
        label = "adminLoginContainer"
    )

    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(RoundedCornerShape(32.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Administrator Login",
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "Administrator Login"
                role = Role.Button
            }
            .testTag(VisitorEntryTestTags.AdminLogin),
        shape = RoundedCornerShape(32.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = shadowElevation
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VisitorSpacing.Lg, vertical = VisitorSpacing.Md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisitorAssetImage(
                model = VisitorAssets.VisitorAdminIcon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(VisitorSpacing.Md))
            Text(
                text = "Administrator Login",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = VisitorSpacing.Xl, end = VisitorSpacing.Xl, bottom = VisitorSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)
        ) {
            Text("Student Access", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "Sign in or create your student account.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onStudentLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VisitorEntryTestTags.StudentLogin)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null)
                Spacer(modifier = Modifier.width(VisitorSpacing.Sm))
                Text("Sign In")
            }
            OutlinedButton(
                onClick = onStudentRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VisitorEntryTestTags.StudentRegister)
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(VisitorSpacing.Sm))
                Text("Create Student Account")
            }
        }
    }
}
