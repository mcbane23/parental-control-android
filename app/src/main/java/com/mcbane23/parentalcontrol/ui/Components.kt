package com.mcbane23.parentalcontrol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The mood of a small coloured element: fine, brand blue, needs a look, or plain. */
enum class Tone { Good, Accent, Attention, Neutral }

@Composable
private fun Tone.container(): Color = when (this) {
    Tone.Good -> MaterialTheme.colorScheme.tertiaryContainer
    Tone.Accent -> MaterialTheme.colorScheme.primaryContainer
    Tone.Attention -> MaterialTheme.colorScheme.errorContainer
    Tone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun Tone.content(): Color = when (this) {
    Tone.Good -> MaterialTheme.colorScheme.onTertiaryContainer
    Tone.Accent -> MaterialTheme.colorScheme.onPrimaryContainer
    Tone.Attention -> MaterialTheme.colorScheme.onErrorContainer
    Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** An icon on a round tinted background. */
@Composable
fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Accent,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier.size(size).background(tone.container(), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.content(), modifier = Modifier.size(size * 0.55f))
    }
}

/** A person's or app's first letter on a tinted background. */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = MaterialTheme.shapes.small,
    tone: Tone = Tone.Accent,
) {
    Box(
        modifier = modifier.size(size).background(tone.container(), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = tone.content(),
            style = if (size >= 44.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
        )
    }
}

/** A short status word in a pill. */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = tone.container(), contentColor = tone.content(), shape = CircleShape) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The icon and title that open a card, with an optional element at the end of the row. */
@Composable
fun CardHeader(
    icon: ImageVector,
    title: String,
    tone: Tone = Tone.Accent,
    trailing: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBubble(icon, tone = tone, size = 36.dp)
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        trailing()
    }
}

/** What a list shows while it has nothing in it. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBubble(icon, tone = Tone.Neutral, size = 48.dp)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A ring that fills as time is used up, and turns red when the limit is reached. */
@Composable
fun UsageRing(fraction: Float, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val shown = fraction.coerceIn(0f, 1f)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { shown },
            modifier = Modifier.fillMaxSize(),
            color = if (shown >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            strokeWidth = 7.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        Text("${(shown * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
    }
}

/** A thin bar for the same purpose as [UsageRing]. */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier) {
    val shown = fraction.coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { shown },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = if (shown >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** A white (or dark) card with a thin outline. Pass [onClick] to make the whole card a button. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            colors = colors,
            border = border,
            content = content,
        )
    } else {
        OutlinedCard(modifier = modifier.fillMaxWidth(), colors = colors, border = border, content = content)
    }
}

/**
 * The navy band at the top of a main screen. It reaches up behind the status bar, so the
 * screen below must not add status bar padding of its own.
 */
@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(color = AppColors.header, contentColor = AppColors.onHeader, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = if (onBack != null) 4.dp else 20.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.onHeader.copy(alpha = 0.78f),
                    )
                }
            }
            trailing()
        }
    }
}

/** From this width a screen shows two columns. It covers the Galaxy Tab in both orientations. */
private val TwoPaneMinWidth = 720.dp

/**
 * A screen made of a fixed header and two groups of scrolling items. Phones show the groups
 * in one column; tablets and wide windows show them side by side.
 */
@Composable
fun AdaptivePanes(
    header: @Composable () -> Unit,
    first: LazyListScope.() -> Unit,
    second: LazyListScope.() -> Unit,
) {
    val gap = Arrangement.spacedBy(10.dp)
    val edges = PaddingValues(top = 12.dp, bottom = 24.dp)
    Column(Modifier.fillMaxSize()) {
        header()
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            if (maxWidth >= TwoPaneMinWidth) {
                Row(
                    modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = gap,
                        contentPadding = edges,
                        content = first,
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = gap,
                        contentPadding = edges,
                        content = second,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = gap,
                    contentPadding = edges,
                ) {
                    first()
                    second()
                }
            }
        }
    }
}
