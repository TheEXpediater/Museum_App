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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import com.example.museumapp.ui.visitor.components.VisitorAssetImage
import com.example.museumapp.ui.visitor.components.VisitorCorners
import com.example.museumapp.ui.visitor.components.VisitorSpacing

const val VisitorRoleCardAspectRatio = 0.62f

@Composable
fun VisitorRoleCard(
    title: String,
    image: String,
    icon: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Sign in as $title",
    cardTestTag: String? = null,
    illustrationTestTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.985f
            isHovered -> 1.03f
            else -> 1f
        },
        label = "visitorRoleCardScale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = when {
            isPressed -> 3.dp
            isHovered -> 14.dp
            else -> 8.dp
        },
        label = "visitorRoleCardShadow"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isPressed -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.82f)
            isHovered -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.70f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "visitorRoleCardBorder"
    )
    val overlayColor by animateColorAsState(
        targetValue = when {
            isPressed -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
            isHovered -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        label = "visitorRoleCardOverlay"
    )
    val cardShape = RoundedCornerShape(VisitorCorners.Xl)

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onPress
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .then(if (cardTestTag == null) Modifier else Modifier.testTag(cardTestTag)),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = shadowElevation
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.44f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(VisitorSpacing.Md),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RoleIllustrationLayer(
                    image = image,
                    icon = icon,
                    illustrationTestTag = illustrationTestTag,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Spacer(modifier = Modifier.height(VisitorSpacing.Md))
                RoleLabelLayer(title = title)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor)
            )
        }
    }
}

@Composable
private fun RoleIllustrationLayer(
    image: String,
    icon: String,
    illustrationTestTag: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(VisitorCorners.Lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)),
        contentAlignment = Alignment.Center
    ) {
        VisitorAssetImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            backgroundColor = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .padding(VisitorSpacing.Sm)
                .then(if (illustrationTestTag == null) Modifier else Modifier.testTag(illustrationTestTag))
        )
    }
}

@Composable
private fun RoleLabelLayer(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(VisitorCorners.Lg))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
            .padding(horizontal = VisitorSpacing.Md, vertical = VisitorSpacing.Sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.width(VisitorSpacing.Xs))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}
