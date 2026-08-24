package com.example.museumapp.ui.visitor.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.museumapp.ui.visitor.components.VisitorAssets
import com.example.museumapp.ui.visitor.components.VisitorCorners
import com.example.museumapp.ui.visitor.components.VisitorSpacing

private const val AuthHeroAspectRatio = 1122f / 1410f
private const val NarrowHeroFitThreshold = 0.56f

data class VisitorEntryHeroRegion(
    val label: String,
    val contentDescription: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

val VisitorEntryHeroRegions = listOf(
    VisitorEntryHeroRegion(
        label = "Continue as Guest",
        contentDescription = "Continue as Guest",
        left = 0.08f,
        top = 0.32f,
        right = 0.47f,
        bottom = 0.86f
    ),
    VisitorEntryHeroRegion(
        label = "Student Access",
        contentDescription = "Student Access",
        left = 0.53f,
        top = 0.32f,
        right = 0.92f,
        bottom = 0.86f
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorEntryScreen(
    onGuest: () -> Unit,
    onStudentLogin: () -> Unit,
    onStudentRegister: () -> Unit,
    onAdminLogin: () -> Unit
) {
    var showStudentAccess by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val contentScale = if (maxWidth / maxHeight < NarrowHeroFitThreshold) {
            ContentScale.Fit
        } else {
            ContentScale.Crop
        }
        val imageBounds = heroImageBounds(maxWidth, maxHeight, contentScale)
        val guestRegion = VisitorEntryHeroRegions[0]
        val studentRegion = VisitorEntryHeroRegions[1]
        val guestBounds = imageBounds.regionBounds(guestRegion, maxWidth, maxHeight)
        val studentBounds = imageBounds.regionBounds(studentRegion, maxWidth, maxHeight)

        AsyncImage(
            model = VisitorAssets.AuthGuestStudent,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )

        HeroChoiceOverlay(
            bounds = guestBounds,
            label = guestRegion.label,
            contentDescription = guestRegion.contentDescription,
            onClick = onGuest
        )
        HeroChoiceOverlay(
            bounds = studentBounds,
            label = studentRegion.label,
            contentDescription = studentRegion.contentDescription,
            onClick = { showStudentAccess = true }
        )

        TextButton(
            onClick = onAdminLogin,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = VisitorSpacing.Md)
                .semantics {
                    contentDescription = "Administrator Login"
                    role = Role.Button
                }
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = VisitorSpacing.Md, vertical = VisitorSpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Administrator Login", style = MaterialTheme.typography.labelLarge)
                }
            }
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
private fun HeroChoiceOverlay(
    bounds: HeroBounds,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(VisitorCorners.Xl)
    val highlightColor = if (pressed) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.03f)
    }
    val borderColor = if (pressed) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.82f)
    } else {
        Color.White.copy(alpha = 0.24f)
    }
    val labelBackground = if (pressed) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)
    }
    val labelBottomPadding = (bounds.height * 0.08f).coerceIn(VisitorSpacing.Sm, VisitorSpacing.Xl)

    Box(
        modifier = Modifier
            .offset(x = bounds.left, y = bounds.top)
            .size(width = bounds.width, height = bounds.height)
            .clip(shape)
            .background(highlightColor)
            .border(BorderStroke(if (pressed) 2.dp else 1.dp, borderColor), shape)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = VisitorSpacing.Sm)
                .padding(bottom = labelBottomPadding)
                .background(labelBackground, RoundedCornerShape(VisitorCorners.Sm))
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = if (pressed) 0.56f else 0.28f)),
                    RoundedCornerShape(VisitorCorners.Sm)
                )
                .padding(horizontal = VisitorSpacing.Sm, vertical = VisitorSpacing.Xs)
                .clearAndSetSemantics {},
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(
                    color = Color.White.copy(alpha = 0.72f),
                    offset = Offset(0f, 1f),
                    blurRadius = 2f
                )
            )
        )
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
            Button(onClick = onStudentLogin, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null)
                Text("Sign In")
            }
            OutlinedButton(onClick = onStudentRegister, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                Text("Create Student Account")
            }
        }
    }
}

private data class HeroImageBounds(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp
) {
    fun regionBounds(region: VisitorEntryHeroRegion, containerWidth: Dp, containerHeight: Dp): HeroBounds {
        val rawLeft = left + width * region.left
        val rawTop = top + height * region.top
        val rawRight = left + width * region.right
        val rawBottom = top + height * region.bottom
        val clampedLeft = rawLeft.coerceIn(0.dp, containerWidth)
        val clampedTop = rawTop.coerceIn(0.dp, containerHeight)
        val clampedRight = rawRight.coerceIn(0.dp, containerWidth)
        val clampedBottom = rawBottom.coerceIn(0.dp, containerHeight)
        return HeroBounds(
            left = clampedLeft,
            top = clampedTop,
            width = (clampedRight - clampedLeft).coerceAtLeast(48.dp),
            height = (clampedBottom - clampedTop).coerceAtLeast(48.dp)
        )
    }
}

private data class HeroBounds(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp
)

private fun heroImageBounds(containerWidth: Dp, containerHeight: Dp, contentScale: ContentScale): HeroImageBounds {
    val containerRatio = containerWidth / containerHeight
    val imageUsesFullWidth = when (contentScale) {
        ContentScale.Fit -> containerRatio <= AuthHeroAspectRatio
        else -> containerRatio >= AuthHeroAspectRatio
    }
    val width = if (imageUsesFullWidth) containerWidth else containerHeight * AuthHeroAspectRatio
    val height = if (imageUsesFullWidth) containerWidth / AuthHeroAspectRatio else containerHeight
    return HeroImageBounds(
        left = (containerWidth - width) / 2f,
        top = (containerHeight - height) / 2f,
        width = width,
        height = height
    )
}
