package agape.app.ui.components

import agape.app.R
import agape.app.core.model.VisualStyle
import agape.app.ui.theme.AgapeTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radius per style: crisp for Classic Sacred, softer for the others. */
val cardShape: Shape
    @Composable get() = when (AgapeTheme.style) {
        VisualStyle.NAVY_CREAM -> RoundedCornerShape(24.dp)
        VisualStyle.CLASSIC_SACRED -> RoundedCornerShape(14.dp)
        VisualStyle.WARM_TERRACOTTA -> RoundedCornerShape(28.dp)
    }

val buttonShape: Shape
    @Composable get() = when (AgapeTheme.style) {
        VisualStyle.NAVY_CREAM -> CircleShape
        VisualStyle.CLASSIC_SACRED -> RoundedCornerShape(12.dp)
        VisualStyle.WARM_TERRACOTTA -> CircleShape
    }

/** A small uppercase label above a title. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = AgapeTheme.colors.accent) {
    Text(text.uppercase(), style = AgapeTheme.type.kicker, color = color, modifier = modifier)
}

/** The style's decorative divider: a gold rule with a diamond, a Byzantine cross, or three soft dots. */
@Composable
fun Ornament(modifier: Modifier = Modifier, width: Dp = 120.dp) {
    val c = AgapeTheme.colors
    val style = AgapeTheme.style
    Canvas(modifier.width(width).height(14.dp)) {
        val mid = Offset(size.width / 2, size.height / 2)
        val stroke = 1.dp.toPx()
        when (style) {
            VisualStyle.NAVY_CREAM -> {
                val gap = 10.dp.toPx()
                drawLine(c.accent, Offset(0f, mid.y), Offset(mid.x - gap, mid.y), stroke)
                drawLine(c.accent, Offset(mid.x + gap, mid.y), Offset(size.width, mid.y), stroke)
                val r = 4.dp.toPx()
                drawPath(Path().apply {
                    moveTo(mid.x, mid.y - r); lineTo(mid.x + r, mid.y); lineTo(mid.x, mid.y + r); lineTo(mid.x - r, mid.y); close()
                }, c.accent)
            }
            VisualStyle.CLASSIC_SACRED -> {
                val gap = 12.dp.toPx()
                drawLine(c.accent, Offset(0f, mid.y - 2.dp.toPx()), Offset(mid.x - gap, mid.y - 2.dp.toPx()), stroke)
                drawLine(c.accent, Offset(0f, mid.y + 2.dp.toPx()), Offset(mid.x - gap, mid.y + 2.dp.toPx()), stroke)
                drawLine(c.accent, Offset(mid.x + gap, mid.y - 2.dp.toPx()), Offset(size.width, mid.y - 2.dp.toPx()), stroke)
                drawLine(c.accent, Offset(mid.x + gap, mid.y + 2.dp.toPx()), Offset(size.width, mid.y + 2.dp.toPx()), stroke)
                val arm = 6.dp.toPx()
                val w = 2.dp.toPx()
                drawRect(c.accent, Offset(mid.x - w / 2, mid.y - arm), Size(w, arm * 2))
                drawRect(c.accent, Offset(mid.x - arm, mid.y - w / 2), Size(arm * 2, w))
                drawCircle(c.accent, 2.dp.toPx(), mid, style = Stroke(stroke))
            }
            VisualStyle.WARM_TERRACOTTA -> {
                val r = 2.5.dp.toPx()
                val step = 10.dp.toPx()
                drawCircle(c.accent.copy(alpha = 0.6f), r, mid.copy(x = mid.x - step))
                drawCircle(c.primary, r * 1.2f, mid)
                drawCircle(c.accent.copy(alpha = 0.6f), r, mid.copy(x = mid.x + step))
            }
        }
    }
}

@Composable
fun AgapeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tonal: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AgapeTheme.colors
    val shape = cardShape
    // Cards are edged with a warm gold-and-parchment gradient border, inspired by gilded Bible pages.
    val goldAlpha = when {
        AgapeTheme.style == VisualStyle.CLASSIC_SACRED -> if (c.isDark) 0.44f else 0.52f
        c.isDark -> 0.30f
        else -> 0.38f
    }
    val border = if (!tonal) {
        BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    c.accent.copy(alpha = goldAlpha),
                    c.line.copy(alpha = 0.65f),
                    c.accent.copy(alpha = goldAlpha * 0.85f),
                ),
            ),
        )
    } else {
        BorderStroke(0.8.dp, c.line.copy(alpha = 0.55f))
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (tonal) Modifier
                else Modifier.shadow(
                    elevation = if (c.isDark) 10.dp else 8.dp,
                    shape = shape,
                    ambientColor = c.primary.copy(alpha = if (c.isDark) 0.18f else 0.06f),
                    spotColor = c.primary.copy(alpha = if (c.isDark) 0.25f else 0.09f),
                )
            )
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = if (tonal) c.surfaceSunken.copy(alpha = 0.76f) else c.surface,
        border = border,
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (!tonal) {
                        Modifier.background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    c.surfaceRaised.copy(alpha = if (c.isDark) 0.35f else 0.65f),
                                    c.surface.copy(alpha = 0f),
                                ),
                            ),
                        )
                    } else Modifier
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val c = AgapeTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(if (enabled) 8.dp else 0.dp, buttonShape, spotColor = c.primary.copy(alpha = 0.35f)),
        shape = buttonShape,
        border = if (enabled) BorderStroke(0.8.dp, c.accent.copy(alpha = 0.55f)) else null,
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = c.onPrimary, strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = buttonShape,
        border = BorderStroke(1.dp, AgapeTheme.colors.accent.copy(alpha = 0.38f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AgapeTheme.colors.ink),
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = AgapeTheme.colors.primary)
    }
}

/** Large title area at the top of each tab. */
@Composable
fun TabHeader(kicker: String, title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 24.dp, end = 12.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (kicker.isNotBlank()) Kicker(kicker, color = AgapeTheme.colors.inkMuted)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.displayMedium, color = AgapeTheme.colors.ink, modifier = Modifier.semantics { heading() })
            // A gilded rule under the title, as on an illuminated title page.
            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(AgapeTheme.colors.accent))
                Box(
                    Modifier
                        .width(48.dp)
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(AgapeTheme.colors.accent, AgapeTheme.colors.accent.copy(alpha = 0.1f)),
                            ),
                        ),
                )
            }
        }
        trailing()
    }
}

/** Top bar for detail screens. */
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = AgapeTheme.colors.ink)
        }
        Text(
            title, style = MaterialTheme.typography.titleLarge, color = AgapeTheme.colors.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = AgapeTheme.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) QuietButton(action, onAction)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) c.primary else c.surfaceSunken.copy(alpha = 0.68f))
            .border(
                width = 0.8.dp,
                color = if (selected) c.accent.copy(alpha = 0.65f) else c.line.copy(alpha = 0.75f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onPrimary else c.inkMuted)
    }
}

/** Thin progress line used for plans and quizzes. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.surfaceSunken)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(c.accent))
    }
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Ornament()
        Text(title, style = MaterialTheme.typography.titleLarge, color = AgapeTheme.colors.ink)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AgapeTheme.colors.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** A tappable row with title, optional subtitle and trailing content. */
@Composable
fun ListRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onClick: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AgapeTheme.colors.ink)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(10.dp)); trailing() }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp).height(0.5.dp).background(AgapeTheme.colors.line.copy(alpha = 0.7f)))
}
