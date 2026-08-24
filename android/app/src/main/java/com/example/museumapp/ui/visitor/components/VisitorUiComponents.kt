package com.example.museumapp.ui.visitor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.museumapp.data.model.AnnouncementDto
import com.example.museumapp.data.model.ArticleDto
import com.example.museumapp.data.model.NewsDto
import com.example.museumapp.data.model.PublicArtifactDto
import com.example.museumapp.ui.visitor.theme.VisitorMuseumTokens

object VisitorSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
}

object VisitorCorners {
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
}

private val MuseumFrameShape = RoundedCornerShape(VisitorCorners.Lg)

fun String?.hasMuseumContent(): Boolean {
    val value = this?.trim().orEmpty()
    return value.isNotEmpty() && !value.equals("To be configured.", ignoreCase = true)
}

fun compactDate(value: String?): String? = value?.take(10)?.takeIf { it.isNotBlank() }

@Composable
fun VisitorAssetImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    backgroundColor: Color = Color.Transparent,
    cornerRadius: Dp = 0.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Error -> Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is AsyncImagePainter.State.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
                else -> SubcomposeAsyncImageContent()
            }
        }
    }
}

@Composable
fun VisitorIllustration(
    model: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    VisitorAssetImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        cornerRadius = VisitorCorners.Lg,
        modifier = modifier
    )
}

@Composable
fun MuseumTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSearch: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            VisitorAssetImage(
                model = VisitorAssets.AppLogo,
                contentDescription = "PSAU Museum Guide app logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(42.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                VisitorAssetImage(
                    model = VisitorAssets.SearchIcon,
                    contentDescription = "Search",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MuseumSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(actionLabel)
                }
            }
        }
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VisitorSectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    MuseumSectionTitle(title = title, actionLabel = actionLabel, onAction = onAction)
}

@Composable
fun VisitorHero(
    title: String,
    body: String,
    image: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Lg)
    ) {
        VisitorIllustration(
            model = image,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp, max = 260.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm)) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Md), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPrimaryAction, modifier = Modifier.weight(1f)) {
                    Text(primaryActionLabel)
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.weight(1f)) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun VisitorLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun VisitorErrorCard(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VisitorCorners.Lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(VisitorSpacing.Lg), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (onRetry != null) {
                Button(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VisitorCorners.Lg),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(VisitorSpacing.Lg),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun VisitorChip(label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label, maxLines = 1) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
fun ArtifactImage(
    artifact: PublicArtifactDto,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val image = artifact.primaryImageUrl ?: artifact.imageUrls.firstOrNull()
    Box(
        modifier = modifier
            .clip(MuseumFrameShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MuseumFrameShape)
            .padding(VisitorSpacing.Sm),
        contentAlignment = Alignment.Center
    ) {
        if (image.isNullOrBlank()) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            VisitorAssetImage(
                model = image,
                contentDescription = artifact.name,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun VisitorArtifactCard(
    artifact: PublicArtifactDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "View Details", onClick = onClick),
        shape = RoundedCornerShape(VisitorCorners.Lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(VisitorSpacing.Md), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
            ArtifactImage(
                artifact = artifact,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xs)) {
                Text(
                    artifact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                MetadataLine(
                    listOf(
                        artifact.category,
                        artifact.historicalPeriod,
                        artifact.origin
                    )
                )
                Text(
                    artifact.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(artifact.artifactCode, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("View", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun CollectionCard(
    title: String,
    body: String,
    iconAsset: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(VisitorCorners.Lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(VisitorSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisitorAssetImage(iconAsset, contentDescription = null, modifier = Modifier.size(42.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun VisitorAccessCard(
    title: String,
    body: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    actionLabel: String? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val backgroundColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val supportingColor = if (primary) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (primary) {
        null
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp),
        shape = RoundedCornerShape(VisitorCorners.Lg),
        color = backgroundColor,
        contentColor = contentColor,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(VisitorSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = contentColor,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xs)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = contentColor)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = supportingColor)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(24.dp), tint = contentColor)
            }
            actionLabel?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = contentColor)
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(
                    onClick = onSecondaryAction,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = VisitorSpacing.Sm)
                ) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}

@Composable
fun ScanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Scan Artifact",
    enabled: Boolean = true
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = VisitorMuseumTokens.AntiqueGold,
            contentColor = VisitorMuseumTokens.MuseumNavy,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        VisitorAssetImage(VisitorAssets.ScanIcon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(VisitorSpacing.Sm))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MetadataRow(items: List<Pair<String, String?>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm)
    ) {
        items.filter { it.second.hasMuseumContent() }.forEach { (label, value) ->
            Surface(
                shape = RoundedCornerShape(VisitorCorners.Md),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = VisitorSpacing.Md, vertical = VisitorSpacing.Sm)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text(value.orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MetadataLine(values: List<String?>) {
    val text = values.filter { it.hasMuseumContent() }.joinToString(" / ")
    if (text.isNotBlank()) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MuseumInfoRow(
    label: String,
    value: String?,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    if (!value.hasMuseumContent()) return
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VisitorSpacing.Md)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Xs)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun NewsCard(
    title: String,
    excerpt: String,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    label: String? = null,
    coverImageUrl: String? = null,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick == null) modifier.fillMaxWidth() else modifier.fillMaxWidth().clickable(onClick = onClick)
    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(VisitorCorners.Lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(VisitorSpacing.Lg), verticalArrangement = Arrangement.spacedBy(VisitorSpacing.Sm)) {
            if (coverImageUrl.hasMuseumContent()) {
                VisitorAssetImage(
                    model = coverImageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    cornerRadius = VisitorCorners.Md,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }
            label?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(excerpt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            metadata?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun NewsCard(news: NewsDto, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    NewsCard(
        title = news.title,
        excerpt = news.summary,
        metadata = compactDate(news.publishedAt),
        label = "Museum News",
        coverImageUrl = news.coverImageUrl,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
fun NewsCard(article: ArticleDto, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    NewsCard(
        title = article.title,
        excerpt = article.summary,
        metadata = compactDate(article.publishedAt),
        label = article.category ?: "Museum Article",
        coverImageUrl = article.coverImageUrl,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
fun AnnouncementCard(announcement: AnnouncementDto, modifier: Modifier = Modifier) {
    val priority = announcement.priority.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
    NewsCard(
        title = announcement.title,
        excerpt = announcement.message,
        metadata = compactDate(announcement.expiresAt)?.let { "Until $it" },
        label = priority?.let { "$it announcement" },
        modifier = modifier
    )
}

@Composable
fun InitialsAvatar(initials: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier) {
    if (!value.hasMuseumContent()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.orEmpty(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Spacer12() {
    Spacer(Modifier.height(12.dp))
}
