package dev.ignotus.sonyrebuild.ui

import android.content.Context
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.textureBlur

// ── Reusable composables shared across all screens ─────────────────────────

@Composable
internal fun ModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Layout primitives ──────────────────────────────────────────────────────

@Composable
internal fun PageColumn(
    bottomInnerPadding: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 18.dp,
            end = 16.dp,
            bottom = bottomInnerPadding + 12.dp,
        ),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

// ── Animation ──────────────────────────────────────────────────────────────

internal fun reareyeHorizontalTransform(forward: Boolean): ContentTransform =
    fadeIn(
        animationSpec = tween(
            durationMillis = 210,
            delayMillis = 50,
            easing = LinearOutSlowInEasing,
        )
    ) + slideInHorizontally(
        animationSpec = tween(
            durationMillis = 280,
            easing = FastOutSlowInEasing,
        )
    ) { fullWidth ->
        if (forward) fullWidth / 9 else -fullWidth / 9
    } togetherWith fadeOut(
        animationSpec = tween(
            durationMillis = 110,
            easing = FastOutLinearInEasing,
        )
    ) + slideOutHorizontally(
        animationSpec = tween(
            durationMillis = 190,
            easing = FastOutLinearInEasing,
        )
    ) { fullWidth ->
        if (forward) -fullWidth / 12 else fullWidth / 12
    }

// ── Page header ────────────────────────────────────────────────────────────

@Composable
internal fun PageHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Cards ──────────────────────────────────────────────────────────────────

@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(8.dp),
    content: @Composable () -> Unit,
) {
    val backdrop = LocalTextureBackdrop.current
    val renderCapabilities = LocalUiRenderCapabilities.current
    val glassEnabled = renderCapabilities.glassCardsEnabled && backdrop != null
    val isLight = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val fallbackContainerColor = MaterialTheme.colorScheme.surface.copy(
        alpha = if (isLight) 0.94f else 0.98f,
    )
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (glassEnabled) 0.16f else 0.14f)
    val blurColors = BlurColors(
        blendColors = rememberGlassVisualTokens().cardBlendColors,
    )
    Card(
        shape = shape,
        border = if (glassEnabled) null else BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.then(
            if (glassEnabled) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = shape,
                    blurRadius = 60f,
                    noiseCoefficient = 0.001f,
                    colors = blurColors,
                    enabled = true,
                )
            } else {
                Modifier
                    .shadow(
                        elevation = 1.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = if (isLight) 0.06f else 0.20f),
                        spotColor = Color.Black.copy(alpha = if (isLight) 0.08f else 0.22f),
                    )
                    .clip(shape)
                    .background(fallbackContainerColor)
            }
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

@Composable
internal fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

@Composable
internal fun ManagerCard(
    title: String,
    summary: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Settings & form controls ───────────────────────────────────────────────

@Composable
internal fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun <T> SegmentedChoice(
    selected: T,
    values: Iterable<T>,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 2,
        modifier = Modifier.widthIn(max = 220.dp),
    ) {
        values.forEach { value ->
            ModeButton(
                text = label(value),
                selected = selected == value,
                enabled = true,
                modifier = Modifier.heightIn(min = 36.dp),
                onClick = { onSelected(value) },
            )
        }
    }
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.62f),
        )
    }
}

// ── Indicators & placeholders ──────────────────────────────────────────────

@Composable
internal fun StatusPill(text: String, active: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
    )
}

@Composable
internal fun EmptyHint(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Utility ────────────────────────────────────────────────────────────────

internal fun appVersionName(context: Context): String =
    runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "Unknown"
    }.getOrDefault("Unknown")
