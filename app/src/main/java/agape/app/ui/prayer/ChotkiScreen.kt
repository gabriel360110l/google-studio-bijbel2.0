package agape.app.ui.prayer

import agape.app.core.model.AppLanguage
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.Pill
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.QuietButton
import agape.app.ui.components.SpeakerPill
import agape.app.ui.components.rememberAgapeSpeaker
import agape.app.ui.theme.AgapeTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChotkiScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val nl = lang == AppLanguage.DUTCH
    val c = AgapeTheme.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val speaker = rememberAgapeSpeaker()

    var totalKnots by rememberSaveable { mutableIntStateOf(33) }
    var currentKnot by rememberSaveable { mutableIntStateOf(0) }
    var roundsCompleted by rememberSaveable { mutableIntStateOf(0) }
    var speakEachKnot by rememberSaveable { mutableStateOf(false) }

    val jesusPrayer = if (nl) {
        "Heer Jezus Christus, Zoon van God, ontferm U over mij, zondaar."
    } else {
        "Lord Jesus Christ, Son of God, have mercy on me, a sinner."
    }
    val dividerPrayer = if (nl) {
        "Allerheiligste Moeder Gods, red ons."
    } else {
        "Most Holy Theotokos, save us."
    }

    val isDivider = remember(currentKnot, totalKnots) {
        if (currentKnot == 0) false
        else if (totalKnots == 33) currentKnot % 11 == 0
        else currentKnot % 10 == 0
    }

    fun advanceKnot() {
        if (currentKnot < totalKnots) {
            currentKnot++
            val hitDivider = if (totalKnots == 33) currentKnot % 11 == 0 else currentKnot % 10 == 0
            if (currentKnot == totalKnots || hitDivider) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            if (speakEachKnot) {
                speaker.speak(if (hitDivider && currentKnot < totalKnots) "$jesusPrayer $dividerPrayer" else jesusPrayer)
            }
            if (currentKnot == totalKnots) {
                roundsCompleted++
                scope.launch {
                    container.user.markPrayed(
                        "chotki_$totalKnots",
                        if (nl) "Gebedssnoer ($totalKnots knopen)" else "Prayer Rope ($totalKnots knots)",
                    )
                }
            }
        } else {
            currentKnot = 1
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (speakEachKnot) speaker.speak(jesusPrayer)
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(if (nl) "Gebedssnoer (Chotki)" else "Prayer Rope (Chotki)", onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Kicker(if (nl) "Het Jezusgebed · Komvoschoinion" else "The Jesus Prayer · Komvoschoinion")
            Spacer(Modifier.height(6.dp))
            Text(
                if (nl) "Interactief Gebedssnoer" else "Interactive Prayer Rope",
                style = MaterialTheme.typography.headlineMedium,
                color = c.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))

            // Knot count selector (33 / 50 / 100)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(33, 50, 100).forEach { count ->
                    Pill(
                        text = if (nl) "$count knopen" else "$count knots",
                        selected = totalKnots == count,
                        onClick = {
                            totalKnots = count
                            currentKnot = 0
                        },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // Interactive Circular Chotki Canvas
            Box(
                Modifier
                    .size(286.dp)
                    .clip(CircleShape)
                    .background(c.surface)
                    .border(1.dp, c.line, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::advanceKnot,
                    )
                    .testTag("chotki_rope_ring"),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = size.minDimension / 2f - 14.dp.toPx()

                    // Cord circle
                    drawCircle(
                        color = c.line,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx()),
                    )

                    // Draw knots around the circle starting from the bottom cross (90 degrees)
                    for (i in 1..totalKnots) {
                        val fraction = i.toFloat() / totalKnots.toFloat()
                        val angle = (PI / 2.0) + fraction * 2.0 * PI
                        val kx = cx + (radius * cos(angle)).toFloat()
                        val ky = cy + (radius * sin(angle)).toFloat()

                        val divider = if (totalKnots == 33) i % 11 == 0 else i % 10 == 0
                        val prayed = i <= currentKnot
                        val isCurrent = i == currentKnot

                        val knotRadius = when {
                            isCurrent -> 7.5.dp.toPx()
                            divider -> 5.8.dp.toPx()
                            totalKnots == 100 -> 2.8.dp.toPx()
                            else -> 4.2.dp.toPx()
                        }
                        val knotColor = when {
                            isCurrent -> c.accent
                            prayed && divider -> c.accent
                            prayed -> c.primary
                            divider -> c.accent.copy(alpha = 0.45f)
                            else -> c.inkFaint.copy(alpha = 0.35f)
                        }
                        drawCircle(
                            color = knotColor,
                            radius = knotRadius,
                            center = Offset(kx, ky),
                        )
                    }

                    // Draw woven cross at the bottom of the prayer rope
                    val crossY = cy + radius + 4.dp.toPx()
                    drawLine(
                        color = c.accent,
                        start = Offset(cx, crossY - 8.dp.toPx()),
                        end = Offset(cx, crossY + 12.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = c.accent,
                        start = Offset(cx - 7.dp.toPx(), crossY),
                        end = Offset(cx + 7.dp.toPx(), crossY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 36.dp),
                ) {
                    Text(
                        "☦",
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.accent,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$currentKnot",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = AgapeTheme.type.ui,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = c.primary,
                    )
                    Text(
                        if (nl) "van $totalKnots knopen" else "of $totalKnots knots",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.inkMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (currentKnot == 0) {
                            if (nl) "Tik om te bidden" else "Tap to pray"
                        } else if (currentKnot == totalKnots) {
                            if (nl) "Snoer voltooid · Tik voor nieuwe ronde" else "Rope completed · Tap for new round"
                        } else {
                            if (nl) "Tik voor volgende knoop" else "Tap for next knot"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = c.accent,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Prayer card
            AgapeCard {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Kicker(if (nl) "Het Jezusgebed" else "The Jesus Prayer")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        jesusPrayer,
                        style = AgapeTheme.type.scripture.copy(
                            fontSize = AgapeTheme.type.scripture.fontSize * 1.15f,
                        ),
                        color = c.ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Κύριε Ἰησοῦ Χριστέ, Υἱὲ τοῦ Θεοῦ, ἐλέησόν με τὸν ἁμαρτωλόν.",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = c.inkMuted,
                        textAlign = TextAlign.Center,
                    )
                    if (isDivider) {
                        Spacer(Modifier.height(12.dp))
                        Ornament(width = 72.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            dividerPrayer,
                            style = MaterialTheme.typography.titleSmall.copy(fontStyle = FontStyle.Italic),
                            color = c.accent,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpeakerPill(jesusPrayer)
                        Pill(
                            text = if (speakEachKnot) {
                                if (nl) "Spreek bij elke knoop: Aan" else "Speak per knot: On"
                            } else {
                                if (nl) "Spreek bij elke knoop: Uit" else "Speak per knot: Off"
                            },
                            selected = speakEachKnot,
                            onClick = { speakEachKnot = !speakEachKnot },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (currentKnot < totalKnots) {
                    if (nl) "Bid volgende knoop (${currentKnot + 1}/$totalKnots)" else "Pray next knot (${currentKnot + 1}/$totalKnots)"
                } else {
                    if (nl) "Opnieuw beginnen" else "Start new round"
                },
                onClick = ::advanceKnot,
            )
            if (currentKnot > 0) {
                QuietButton(
                    text = if (nl) "Teller terugzetten naar 0" else "Reset counter to 0",
                    onClick = { currentKnot = 0 },
                )
            }
            if (roundsCompleted > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (nl) "Vandaag gebeden rondes: $roundsCompleted" else "Rounds prayed today: $roundsCompleted",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                )
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

/** A small circular Chotki emblem for cards. */
@Composable
fun ChotkiMark(modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    Canvas(modifier) {
        val r = size.minDimension * 0.36f
        val centre = Offset(size.width / 2f, size.height * 0.44f)
        for (i in 0 until 12) {
            val a = i * (2 * PI / 12) - PI / 2
            val pt = Offset(centre.x + (r * cos(a)).toFloat(), centre.y + (r * sin(a)).toFloat())
            drawCircle(if (i % 4 == 0) c.accent else c.primary, radius = if (i % 4 == 0) 3.2.dp.toPx() else 2.2.dp.toPx(), center = pt)
        }
        val stemTop = Offset(centre.x, centre.y + r + 2.dp.toPx())
        val stemBot = Offset(centre.x, size.height * 0.94f)
        drawLine(c.accent, stemTop, stemBot, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val crossY = stemTop.y + (stemBot.y - stemTop.y) * 0.45f
        drawLine(c.accent, Offset(centre.x - 4.5.dp.toPx(), crossY), Offset(centre.x + 4.5.dp.toPx(), crossY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}
