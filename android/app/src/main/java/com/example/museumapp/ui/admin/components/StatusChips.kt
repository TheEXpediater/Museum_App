package com.example.museumapp.ui.admin.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class StatusTone {
    Good,
    Progress,
    Warning,
    Error,
    Neutral
}

fun artifactAiStatusLabel(status: String?): String {
    val normalized = status.orEmpty().lowercase()
    return when (normalized) {
        "indexed" -> "In AI Library"
        "stale", "partial" -> "Needs AI Update"
        "failed" -> "AI Library Failed"
        "pending" -> "Feeding to AI Library"
        "not_indexed", "" -> "Not in AI Library"
        else -> normalized.replace('_', ' ').ifBlank { "Not in AI Library" }
    }
}

fun matchLevelLabel(level: String): String {
    val normalized = level.lowercase()
    return when (normalized) {
        "strong" -> "Strong"
        "possible" -> "Possible"
        "weak" -> "Weak"
        "no_match" -> "No match"
        else -> normalized.replace('_', ' ')
    }
}

@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    when (tone) {
        StatusTone.Good -> {
            container = colors.primaryContainer
            content = colors.onPrimaryContainer
        }
        StatusTone.Progress -> {
            container = colors.secondaryContainer
            content = colors.onSecondaryContainer
        }
        StatusTone.Warning -> {
            container = colors.tertiaryContainer
            content = colors.onTertiaryContainer
        }
        StatusTone.Error -> {
            container = colors.errorContainer
            content = colors.onErrorContainer
        }
        StatusTone.Neutral -> {
            container = colors.surfaceVariant
            content = colors.onSurfaceVariant
        }
    }
    Surface(
        modifier = modifier.heightIn(min = 32.dp),
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ArtifactAiStatusChip(status: String?, modifier: Modifier = Modifier) {
    val normalized = status.orEmpty().lowercase()
    val label = artifactAiStatusLabel(status)
    val tone = when (normalized) {
        "indexed" -> StatusTone.Good
        "pending" -> StatusTone.Progress
        "partial", "stale" -> StatusTone.Warning
        "failed" -> StatusTone.Error
        else -> StatusTone.Neutral
    }
    val icon = when (normalized) {
        "indexed" -> Icons.Outlined.CheckCircle
        "partial", "stale" -> Icons.Outlined.Sync
        "failed" -> Icons.Outlined.ErrorOutline
        "pending" -> Icons.Outlined.HourglassEmpty
        else -> Icons.Outlined.HelpOutline
    }
    StatusChip(label = label, tone = tone, icon = icon, modifier = modifier)
}

@Composable
fun MatchLevelChip(level: String, modifier: Modifier = Modifier) {
    val normalized = level.lowercase()
    val label = matchLevelLabel(level)
    val tone = when (normalized) {
        "strong" -> StatusTone.Good
        "possible", "weak" -> StatusTone.Warning
        "no_match" -> StatusTone.Neutral
        else -> StatusTone.Neutral
    }
    val icon = when (normalized) {
        "strong" -> Icons.Outlined.CheckCircle
        "possible", "weak" -> Icons.Outlined.WarningAmber
        else -> Icons.Outlined.HelpOutline
    }
    StatusChip(label = label, tone = tone, icon = icon, modifier = modifier)
}

@Composable
fun HealthStatusChip(status: String, modifier: Modifier = Modifier) {
    StatusChip(
        label = healthStatusLabel(status),
        tone = healthStatusTone(status),
        icon = healthStatusIcon(status),
        modifier = modifier
    )
}

fun healthStatusLabel(status: String?): String {
    val normalized = status.orEmpty().lowercase()
    return when (normalized) {
        "ai_enabled", "enabled" -> "Enabled"
        "disabled" -> "Disabled"
        "not_installed" -> "Not installed"
        "idle", "not_loaded" -> "Ready to load"
        "loading" -> "Loading"
        "loaded" -> "Loaded"
        "failed" -> "Load failed"
        "healthy" -> "Healthy"
        "connected" -> "Connected"
        "available" -> "Available"
        "ready" -> "Ready"
        "indexed" -> "Indexed"
        "degraded" -> "Degraded"
        "pending" -> "Pending"
        "partial" -> "Partial"
        "unknown", "" -> "Unknown"
        "unavailable" -> "Unavailable"
        "incompatible" -> "Incompatible"
        "missing" -> "Missing"
        else -> normalized.replace('_', ' ').ifBlank { "Unknown" }
    }
}

fun healthStatusTone(status: String?): StatusTone {
    val normalized = status.orEmpty().lowercase()
    return when (normalized) {
        "healthy", "connected", "available", "ready", "loaded", "indexed", "enabled", "ai_enabled" -> StatusTone.Good
        "loading" -> StatusTone.Progress
        "degraded", "pending", "partial", "unknown" -> StatusTone.Warning
        "unavailable", "failed", "not_installed", "incompatible" -> StatusTone.Error
        "disabled", "missing", "idle", "not_loaded", "" -> StatusTone.Neutral
        else -> StatusTone.Neutral
    }
}

fun healthStatusIcon(status: String?): ImageVector {
    return when (healthStatusTone(status)) {
        StatusTone.Good -> Icons.Outlined.CheckCircle
        StatusTone.Progress -> Icons.Outlined.HourglassEmpty
        StatusTone.Warning -> Icons.Outlined.WarningAmber
        StatusTone.Error -> Icons.Outlined.ErrorOutline
        StatusTone.Neutral -> Icons.Outlined.HelpOutline
    }
}
