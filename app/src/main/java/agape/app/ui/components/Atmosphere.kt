package agape.app.ui.components

import agape.app.ui.theme.AgapeTheme
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalTime

/** A soft glow of the style's accent behind the top of a screen, so pages feel lit rather than flat. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, glow: Color = AgapeTheme.colors.accent, content: @Composable BoxScope.() -> Unit) {
    val c = AgapeTheme.colors
    Box(
        modifier.fillMaxSize().background(c.background).drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = if (c.isDark) 0.16f else 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.85f, -size.width * 0.05f),
                    radius = size.width * 1.1f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(c.primary.copy(alpha = if (c.isDark) 0.10f else 0.05f), Color.Transparent),
                    center = Offset(-size.width * 0.1f, size.height * 0.35f),
                    radius = size.width * 0.9f,
                ),
            )
        },
        content = content,
    )
}

/** Sky colours for the hour of the day: dawn, day, evening and night. */
data class DaySky(val top: Color, val bottom: Color, val text: Color, val muted: Color)

fun daySky(time: LocalTime, dark: Boolean): DaySky {
    val h = time.hour
    val sky = when {
        h in 5..8 -> DaySky(Color(0xFFF4C9A8), Color(0xFFE8B8C0), Color(0xFF3B2430), Color(0xFF6E4E58))
        h in 9..15 -> DaySky(Color(0xFFF6E7C1), Color(0xFFE9D3A6), Color(0xFF3A2C14), Color(0xFF6E5A36))
        h in 16..19 -> DaySky(Color(0xFFE7A272), Color(0xFFB4566A), Color(0xFFFFF4EA), Color(0xFFF6DCCB))
        else -> DaySky(Color(0xFF1E2A4A), Color(0xFF0E1426), Color(0xFFEDE6D6), Color(0xFFB5BBCB))
    }
    return if (dark && h in 5..15) sky.copy(top = sky.top.copy(alpha = 0.85f), bottom = sky.bottom.copy(alpha = 0.85f)) else sky
}

/** A small candle flame that breathes gently. */
@Composable
fun Flame(modifier: Modifier = Modifier, size: Dp = 36.dp, color: Color = Color(0xFFF2B655)) {
    val transition = rememberInfiniteTransition(label = "flame")
    val sway by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Reverse), label = "sway")
    val breathe by transition.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "breathe")
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val tipX = w / 2 + sway * w * 0.06f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)), radius = w * 0.5f * breathe, center = Offset(w / 2, h * 0.58f))
        val flame = Path().apply {
            moveTo(tipX, h * (0.18f + (1 - breathe) * 0.4f))
            cubicTo(w * 0.78f, h * 0.45f, w * 0.70f, h * 0.86f, w / 2, h * 0.86f)
            cubicTo(w * 0.30f, h * 0.86f, w * 0.22f, h * 0.45f, tipX, h * (0.18f + (1 - breathe) * 0.4f))
            close()
        }
        drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFF3C4), color, Color(0xFFD9772E)), startY = h * 0.2f, endY = h * 0.86f))
        drawCircle(Color(0xFFFFFBEA).copy(alpha = 0.9f), radius = w * 0.07f, center = Offset(w / 2, h * 0.70f))
    }
}

/** A round initial for a person, used for the Church Fathers. */
@Composable
fun Monogram(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val c = AgapeTheme.colors
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.35f), c.primary.copy(alpha = 0.25f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
    }
}

/** Calm segmented tabs: text that underlines softly when selected. */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    Row(
        modifier.clip(CircleShape).background(c.surfaceSunken.copy(alpha = 0.6f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.clip(CircleShape).background(if (on) c.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .semantics { role = Role.Tab; this.selected = on }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) c.ink else c.inkMuted)
            }
        }
    }
}
