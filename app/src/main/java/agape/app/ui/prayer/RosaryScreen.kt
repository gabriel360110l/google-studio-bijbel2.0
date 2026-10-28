package agape.app.ui.prayer

import agape.app.R
import agape.app.core.model.Passage
import agape.app.data.rosary.BeadKind
import agape.app.data.rosary.MysterySet
import agape.app.data.rosary.Rosary
import agape.app.data.rosary.RosaryStep
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.Pill
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.QuietButton
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/** The Rosary's entry in the Catholic prayer book; it opens this screen instead of the plain text. */
const val ROSARY_ID = "rosary"

/**
 * The Rosary, bead by bead. Tap the glowing bead (or Next) to pray on; tap any other bead to go there. The card
 * below shows the mystery and the prayer of the bead, so the beads can be followed without knowing the prayers.
 */
@Composable
fun RosaryScreen(onBack: () -> Unit, openPassage: (Passage) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val c = AgapeTheme.colors
    var setIndex by rememberSaveable { mutableIntStateOf(MysterySet.forDay(LocalDate.now().dayOfWeek).ordinal) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    val set = MysterySet.entries[setIndex]
    val mysteries = Rosary.mysteries.getValue(set)
    val steps = Rosary.steps
    val current = steps[step]
    val title = stringResource(R.string.rosary_title)

    fun go(to: Int) {
        if (to in steps.indices) { step = to; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    fun next() {
        if (step < steps.lastIndex) go(step + 1)
        else if (!finished) {
            finished = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch { container.user.markPrayed("rosary", title) }
        }
    }

    val currentStepSpeech = remember(current, mysteries, set, lang) {
        buildString {
            current.decade?.let { d ->
                mysteries.getOrNull(d)?.let { m ->
                    append(m.title[lang]).append(". ")
                }
            }
            current.intention?.let { append(it[lang]).append(". ") }
            current.prayers.forEach { p ->
                append(p.title[lang]).append(". ")
                append(p.text[lang]).append(" ")
            }
        }.trim()
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title, onBack) {
            agape.app.ui.components.SpeakerButton(text = currentStepSpeech)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MysterySet.entries.forEach { s ->
                    Pill(shortSetName(s), s == set, { setIndex = s.ordinal })
                }
            }
            Text(
                set.title[lang] + if (set == MysterySet.forDay(LocalDate.now().dayOfWeek)) " · " + stringResource(R.string.rosary_today) else "",
                style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            RosaryBeads(
                step = step,
                finished = finished,
                onTapBead = { bead ->
                    if (bead == current.bead) next()
                    else { finished = false; go(Rosary.firstStepOn(bead)) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )

            AnimatedContent(
                targetState = step to finished,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "step",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) { (s, done) ->
                if (done) FinishedCard(set.title[lang]) { finished = false; step = 0 }
                else StepCard(steps[s], mysteries, set, openPassage)
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietButton(stringResource(R.string.rosary_previous), { finished = false; go(step - 1) })
            Spacer(Modifier.width(8.dp))
            PrimaryButton(
                stringResource(if (step == steps.lastIndex) R.string.rosary_amen else R.string.rosary_next),
                ::next, modifier = Modifier.weight(1f), enabled = !finished,
            )
        }
    }
}

@Composable
private fun shortSetName(s: MysterySet): String = stringResource(
    when (s) {
        MysterySet.JOYFUL -> R.string.rosary_joyful
        MysterySet.LUMINOUS -> R.string.rosary_luminous
        MysterySet.SORROWFUL -> R.string.rosary_sorrowful
        MysterySet.GLORIOUS -> R.string.rosary_glorious
    },
)

@Composable
private fun StepCard(s: RosaryStep, mysteries: List<agape.app.data.rosary.Mystery>, set: MysterySet, openPassage: (Passage) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val ordinals = listOf(R.string.rosary_first, R.string.rosary_second, R.string.rosary_third, R.string.rosary_fourth, R.string.rosary_fifth)
    val stepSpeech = remember(s, mysteries, lang) {
        buildString {
            s.decade?.let { d -> mysteries.getOrNull(d)?.let { m -> append(m.title[lang]).append(". ") } }
            s.intention?.let { append(it[lang]).append(". ") }
            s.prayers.forEach { p ->
                append(p.title[lang]).append(". ")
                append(p.text[lang]).append(" ")
            }
        }.trim()
    }
    AgapeCard {
        val kicker = when {
            s.decade != null && s.count > 0 -> stringResource(R.string.rosary_decade_count, stringResource(ordinals[s.decade]), s.count)
            s.decade != null -> stringResource(R.string.rosary_mystery_n, stringResource(ordinals[s.decade]))
            s.bead == Rosary.MEDAL -> stringResource(R.string.rosary_closing)
            else -> stringResource(R.string.rosary_opening)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(kicker, Modifier.weight(1f))
            // Five dots: the decades, filled as they are prayed.
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(5) { d ->
                    val on = (s.decade != null && d <= s.decade) || s.bead == Rosary.MEDAL
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (on) c.accent else c.line))
                }
            }
        }
        s.decade?.let { d ->
            val m = mysteries[d]
            Spacer(Modifier.height(8.dp))
            Text(m.title[lang], style = MaterialTheme.typography.headlineSmall, color = c.ink)
            if (s.announce) {
                m.passage?.let { Passage.parse(it) }?.let { p ->
                    QuietButton(stringResource(R.string.rosary_read, container.passageLabel(p, lang)), { openPassage(p) })
                }
            }
        }
        s.intention?.let {
            Spacer(Modifier.height(6.dp))
            Text(it[lang], style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        }
        if (stepSpeech.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            agape.app.ui.components.SpeakerPill(text = stepSpeech)
        }
        s.prayers.forEach { p ->
            Spacer(Modifier.height(14.dp))
            Text(p.title[lang], style = MaterialTheme.typography.titleMedium, color = c.primary)
            Spacer(Modifier.height(6.dp))
            Text(p.text[lang], style = AgapeTheme.type.scripture, color = c.ink)
        }
        if (set == MysterySet.GLORIOUS && s.decade == 3 && s.announce) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.rosary_assumption_note), style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
        }
    }
}

@Composable
private fun FinishedCard(setTitle: String, onAgain: () -> Unit) {
    val c = AgapeTheme.colors
    AgapeCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Ornament()
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.rosary_done_title), style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.rosary_done_body, setTitle), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
            QuietButton(stringResource(R.string.rosary_again), onAgain)
        }
    }
}

/** Where each bead sits, in a box of width [w]: the loop above, the pendant and crucifix below. */
private class BeadLayout(w: Float) {
    val small = w * 0.017f
    val large = w * 0.028f
    val cx = w / 2
    val radius = w * 0.40f
    val cy = radius + large * 2
    val centers = arrayOfNulls<Offset>(Rosary.BEAD_COUNT)
    val height: Float

    init {
        // The loop: the medal at the bottom, then the five decades going round clockwise on screen (to the left).
        val loop = (7 until Rosary.BEAD_COUNT).toList()
        fun weight(b: Int) = if (Rosary.kind(b) == BeadKind.LARGE) 1.7f else 1f
        val medalWeight = 2.6f
        val total = loop.sumOf { weight(it).toDouble() }.toFloat() + medalWeight
        var acc = medalWeight / 2
        val start = Math.PI.toFloat() / 2
        centers[Rosary.MEDAL] = Offset(cx, cy + radius)
        loop.forEach { b ->
            acc += weight(b) / 2
            val a = start + acc / total * 2 * Math.PI.toFloat()
            centers[b] = Offset(cx + cos(a) * radius, cy + sin(a) * radius)
            acc += weight(b) / 2
        }
        // The pendant hangs from the medal: Our Father bead 5, Hail Marys 4–2, Our Father bead 1, crucifix.
        var y = cy + radius + large * 2.6f
        for (b in listOf(5, 4, 3, 2, 1)) {
            val r = if (Rosary.kind(b) == BeadKind.LARGE) large else small
            y += r
            centers[b] = Offset(cx, y)
            y += r + small * 1.6f
        }
        y += w * 0.07f
        centers[Rosary.CRUCIFIX] = Offset(cx, y)
        height = y + w * 0.11f
    }

    fun radiusOf(b: Int) = when (Rosary.kind(b)) {
        BeadKind.SMALL -> small
        BeadKind.LARGE -> large
        BeadKind.MEDAL -> large * 1.2f
        BeadKind.CRUCIFIX -> large * 2f
    }

    fun nearest(p: Offset, slop: Float): Int? = centers.indices
        .minByOrNull { (centers[it]!! - p).getDistanceSquared() }
        ?.takeIf { (centers[it]!! - p).getDistance() <= radiusOf(it) + slop }
}

@Composable
private fun RosaryBeads(step: Int, finished: Boolean, onTapBead: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    val currentBead = Rosary.steps[step].bead
    // Beads already prayed: every bead of an earlier step, plus the current one once it is done.
    val prayed = remember(step, finished) {
        val beads = Rosary.steps.take(if (finished) Rosary.steps.size else step).map { it.bead }.toMutableSet()
        if (!finished) beads.remove(currentBead)
        beads
    }
    val pulse by rememberInfiniteTransition(label = "bead").animateFloat(0.55f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val label = stringResource(R.string.rosary_beads_description)
    BoxWithConstraints(modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val layout = remember(wPx) { BeadLayout(wPx) }
        val slop = with(density) { 14.dp.toPx() }
        val heightDp = with(density) { layout.height.toDp() }
        Canvas(
            Modifier.fillMaxWidth().height(heightDp)
                .semantics { contentDescription = label }
                .pointerInput(layout) { detectTapGestures { p -> layout.nearest(p, slop)?.let(onTapBead) } },
        ) {
            val chain = c.accent.copy(alpha = 0.45f)
            val stroke = 1.2.dp.toPx()
            // The chain: round the loop, and down the pendant.
            val loopOrder = listOf(Rosary.MEDAL) + (7 until Rosary.BEAD_COUNT) + Rosary.MEDAL
            loopOrder.zipWithNext().forEach { (a, b) -> drawLine(chain, layout.centers[a]!!, layout.centers[b]!!, stroke) }
            listOf(Rosary.MEDAL, 5, 4, 3, 2, 1, Rosary.CRUCIFIX).zipWithNext().forEach { (a, b) ->
                drawLine(chain, layout.centers[a]!!, layout.centers[b]!!, stroke)
            }
            for (b in 0 until Rosary.BEAD_COUNT) {
                val center = layout.centers[b]!!
                val r = layout.radiusOf(b)
                val isCurrent = b == currentBead && !finished
                val done = b in prayed
                if (isCurrent) {
                    drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.55f * pulse), Color.Transparent), center, r * 4.5f), r * 4.5f, center)
                }
                when (Rosary.kind(b)) {
                    BeadKind.CRUCIFIX -> crucifix(center, r, if (done || isCurrent) c.accent else c.inkMuted)
                    BeadKind.MEDAL -> medal(center, r, if (done || isCurrent) c.accent else c.inkMuted, c.background)
                    else -> bead(center, if (isCurrent) r * 1.35f else r, done, isCurrent, c.accent, c.inkFaint)
                }
            }
        }
    }
}

private fun DrawScope.bead(center: Offset, r: Float, done: Boolean, current: Boolean, gold: Color, plain: Color) {
    val base = when {
        current -> Color(0xFFFFE6A8)
        done -> gold
        else -> plain.copy(alpha = 0.55f)
    }
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (done || current) 0.85f else 0.35f), base, base.copy(alpha = 0.8f)), center.copy(x = center.x - r * 0.35f, y = center.y - r * 0.35f), r * 1.6f), r, center)
    drawCircle(Color.Black.copy(alpha = 0.25f), r, center, style = Stroke(0.6f))
}

private fun DrawScope.medal(center: Offset, r: Float, gold: Color, hole: Color) {
    val size = Size(r * 1.9f, r * 2.4f)
    val topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2)
    drawOval(Brush.linearGradient(listOf(gold, gold.copy(alpha = 0.6f)), topLeft, topLeft + Offset(size.width, size.height)), topLeft, size)
    drawOval(hole.copy(alpha = 0.55f), topLeft + Offset(r * 0.3f, r * 0.3f), Size(size.width - r * 0.6f, size.height - r * 0.6f))
    val arm = r * 0.55f
    val w = r * 0.18f
    drawRect(gold, Offset(center.x - w / 2, center.y - arm), Size(w, arm * 2))
    drawRect(gold, Offset(center.x - arm * 0.7f, center.y - arm * 0.35f - w / 2), Size(arm * 1.4f, w))
}

private fun DrawScope.crucifix(center: Offset, r: Float, gold: Color) {
    // A Latin cross with flared ends, hanging from its top.
    val h = r * 2.6f
    val w = r * 0.32f
    val top = center.y - h * 0.38f
    val arm = r * 0.95f
    val armY = top + h * 0.3f
    val path = Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(center.x - w / 2, top, center.x + w / 2, top + h))
        addRect(androidx.compose.ui.geometry.Rect(center.x - arm, armY - w / 2, center.x + arm, armY + w / 2))
    }
    drawPath(path, Brush.verticalGradient(listOf(gold, gold.copy(alpha = 0.7f)), top, top + h))
    listOf(Offset(center.x, top), Offset(center.x, top + h), Offset(center.x - arm, armY), Offset(center.x + arm, armY)).forEach {
        drawCircle(gold, w * 0.8f, it)
    }
    drawCircle(gold, w * 1.1f, Offset(center.x, armY), style = Stroke(w * 0.35f))
}

/** A small ring of beads with a cross, as a mark for the Rosary on cards. */
@Composable
fun RosaryMark(modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    Canvas(modifier) {
        val r = size.minDimension * 0.30f
        val center = Offset(size.width / 2, size.height * 0.40f)
        val bead = size.minDimension * 0.035f
        for (i in 0 until 20) {
            val a = Math.PI.toFloat() / 2 + (i + 0.5f) / 21f * 2 * Math.PI.toFloat()
            drawCircle(c.accent.copy(alpha = 0.85f), if (i % 5 == 4) bead * 1.5f else bead, Offset(center.x + cos(a) * r, center.y + sin(a) * r))
        }
        val bottom = Offset(center.x, center.y + r)
        drawLine(c.accent, bottom, bottom.copy(y = size.height * 0.92f), 1.5.dp.toPx())
        drawLine(c.accent, Offset(center.x - bead * 2.4f, size.height * 0.80f), Offset(center.x + bead * 2.4f, size.height * 0.80f), 1.5.dp.toPx())
    }
}
