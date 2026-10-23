package agape.app.ui.prayer

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.data.content.PrayerSection
import agape.app.notifications.IntentionCategory
import agape.app.notifications.PrayerIntention
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.ProgressLine
import agape.app.ui.components.SpeakerButton
import agape.app.ui.components.SpeakerPill
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun HourScreen(hourId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val tradition = tradition
    val hour = remember(hourId, tradition) { container.content.prayerEntry(tradition, hourId) } ?: return
    // Sections bound to a weekday (Compline troparia, Rosary mysteries) show only on that day.
    val today = remember { LocalDate.now().dayOfWeek.value }
    val sections = remember(hour, today) { hour.sections.filter { it.weekday == null || it.weekday == today } }
    var prayed by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val progress by remember { derivedStateOf { list.firstVisibleItemIndex / sections.size.coerceAtLeast(1).toFloat() } }
    val c = AgapeTheme.colors
    val nl = lang == AppLanguage.DUTCH
    val untranslated = lang == AppLanguage.DUTCH && sections.any { s -> s.kind in listOf(PrayerSection.Kind.text, PrayerSection.Kind.rubric) && s.body != null && s.body.nl.isEmpty() && s.body.en.isNotEmpty() }

    val intentions = remember(settings.prayerIntentions) {
        settings.prayerIntentions.mapNotNull { PrayerIntention.decode(it) }
    }

    val spokenHourText = remember(hour, sections, lang) {
        buildString {
            append(hour.name[lang]).append(". ")
            if (hour.subtitle[lang].isNotBlank()) append(hour.subtitle[lang]).append(". ")
            sections.forEach { sec ->
                when (sec.kind) {
                    PrayerSection.Kind.text, PrayerSection.Kind.heading -> {
                        sec.title?.get(lang)?.takeIf { it.isNotBlank() }?.let { append(it).append(". ") }
                        sec.body?.get(lang)?.forEach { append(it).append(" ") }
                    }
                    PrayerSection.Kind.prayer -> {
                        val p = sec.prayerId?.let { container.content.prayer(it) }
                        if (p != null) {
                            append(sec.title?.get(lang) ?: p.title[lang]).append(". ")
                            p.body[lang].forEach { append(it).append(" ") }
                        }
                    }
                    PrayerSection.Kind.scripture -> {
                        sec.title?.get(lang)?.let { append(it).append(". ") }
                        sec.body?.get(lang)?.forEach { append(it).append(" ") }
                    }
                    else -> {}
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(hour.name[lang], onBack) {
            SpeakerButton(spokenHourText)
            FavoriteButton("hour:${hour.id}")
        }
        ProgressLine(progress, Modifier.padding(horizontal = 20.dp))
        LazyColumn(state = list, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 48.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hour.time.isNotBlank()) Kicker(hour.time)
                    Spacer(Modifier.height(6.dp))
                    Text(hour.name[lang], style = MaterialTheme.typography.displaySmall, color = c.primary, textAlign = TextAlign.Center)
                    Text(hour.subtitle[lang], style = MaterialTheme.typography.titleMedium, color = c.accent, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    SpeakerPill(spokenHourText)
                    Spacer(Modifier.height(14.dp))
                    Ornament()
                    if (untranslated) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.english_only_note), style = MaterialTheme.typography.bodySmall, color = c.inkFaint, textAlign = TextAlign.Center)
                    }
                }
            }
            items(sections.size) { i -> PrayerSectionView(sections[i]) }

            // Personal prayer intentions & names (Diptycha)
            if (intentions.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    AgapeCard(tonal = true) {
                        Kicker(if (nl) "Persoonlijke gebedsintenties & namen" else "Personal Prayer Intentions & Names")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (nl) "Gedenk, o Heer, uw dienaren en verhear onze gebeden:"
                            else "Remember, O Lord, Your servants and hear our prayers:",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = c.inkMuted,
                        )
                        Spacer(Modifier.height(10.dp))
                        IntentionCategory.entries.forEach { cat ->
                            val listForCat = intentions.filter { it.category == cat }
                            if (listForCat.isNotEmpty()) {
                                Text(
                                    cat.title[lang],
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = c.primary,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                )
                                listForCat.forEach { item ->
                                    Text(
                                        "• ${item.text}",
                                        style = AgapeTheme.type.scripture,
                                        color = c.ink,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ornament()
                    Spacer(Modifier.height(18.dp))
                    if (!prayed) PrimaryButton(stringResource(R.string.prayer_amen), {
                        prayed = true
                        scope.launch { container.user.markHourPrayed(hour.id, hour.name[lang]) }
                    }) else Text(stringResource(R.string.prayer_prayed), style = MaterialTheme.typography.titleMedium, color = c.primary)
                }
            }
        }
    }
}

@Composable
private fun PrayerSectionView(section: PrayerSection) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val textStyle = AgapeTheme.type.scripture
    when (section.kind) {
        PrayerSection.Kind.text -> {
            section.title?.get(lang)?.takeIf { it.isNotBlank() }?.let { SectionTitle(it) }
            section.body?.get(lang)?.forEach { Text(it, style = textStyle, color = c.ink, modifier = Modifier.padding(bottom = 12.dp)) }
        }
        PrayerSection.Kind.rubric -> {
            section.body?.get(lang)?.forEach {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = c.accent,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                )
            }
        }
        PrayerSection.Kind.heading -> {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Ornament(width = 90.dp)
                Spacer(Modifier.height(10.dp))
                Text(section.title?.get(lang).orEmpty(), style = MaterialTheme.typography.headlineSmall, color = c.primary, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() })
                section.body?.get(lang)?.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center) }
            }
        }
        PrayerSection.Kind.scripture -> {
            // English inline text (e.g. the Agpeya's Septuagint psalter) is used when present; otherwise the Bible of the app language.
            val inline = section.body?.let { if (lang == AppLanguage.ENGLISH) it.en else it.nl }.orEmpty()
            val passage = remember(section.ref) { section.ref?.let { Passage.parse(it) } }
            val verses by produceState(inline, passage, lang) {
                if (inline.isEmpty() && passage != null) value = container.bible.passage(passage, lang).map { it.text }
            }
            section.title?.let { SectionTitle(it[lang]) }
            Text(verses.joinToString(" "), style = textStyle, color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
        }
        PrayerSection.Kind.prayer -> {
            val prayer = remember(section.prayerId) { section.prayerId?.let { container.content.prayer(it) } } ?: return
            SectionTitle(section.title?.get(lang) ?: prayer.title[lang])
            prayer.body[lang].forEach { Text(it, style = textStyle, color = c.ink, modifier = Modifier.padding(bottom = 12.dp)) }
        }
        PrayerSection.Kind.counter -> PrayerCounter(section)
    }
}

/** Tap the bead for each Κύριε ἐλέησον; the ring fills as you go. */
@Composable
private fun PrayerCounter(section: PrayerSection) {
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val haptics = LocalHapticFeedback.current
    val total = section.count.coerceAtLeast(1)
    var done by rememberSaveable(section.hashCode()) { mutableIntStateOf(0) }
    val sweep by animateFloatAsState(done / total.toFloat(), label = "counter")
    val phrase = section.body?.get(lang)?.firstOrNull().orEmpty()
    val desc = stringResource(R.string.counter_a11y, phrase, done, total)
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        section.title?.get(lang)?.let { SectionTitle(it) }
        Box(
            Modifier.size(176.dp).clip(CircleShape).background(c.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (done < total) {
                        done++
                        haptics.performHapticFeedback(if (done == total) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                    } else done = 0
                }
                .semantics { contentDescription = desc; liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val stroke = 6.dp.toPx()
                drawArc(c.line, 0f, 360f, false, style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke))
                drawArc(c.accent, -90f, 360f * sweep, false, style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$done", style = MaterialTheme.typography.displayMedium.copy(fontFamily = AgapeTheme.type.ui), color = c.primary)
                Text("/ $total", style = MaterialTheme.typography.labelMedium, color = c.inkFaint)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(phrase, style = AgapeTheme.type.scripture, color = c.ink, textAlign = TextAlign.Center)
        // The Kyrie is prayed in Greek; its meaning is given once, quietly, beneath it.
        if ("Κύριε" in phrase) {
            Text(stringResource(R.string.kyrie_translation), style = MaterialTheme.typography.bodySmall, color = c.inkMuted, fontStyle = FontStyle.Italic)
        }
        Text(
            stringResource(if (done < total) R.string.counter_hint else R.string.counter_done),
            style = MaterialTheme.typography.bodySmall, color = c.inkFaint, textAlign = TextAlign.Center,
        )
    }
}

private val greek = Regex("[Ͱ-Ͽἀ-῿]")
private val accents = Regex("\\p{Mn}")

/** Upper case for section titles; Greek capitals are written without accents (Κύριε → ΚΥΡΙΕ). */
private fun String.headingCase(): String {
    val upper = uppercase()
    return if (greek.containsMatchIn(this)) accents.replace(java.text.Normalizer.normalize(upper, java.text.Normalizer.Form.NFD), "") else upper
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.headingCase(), style = AgapeTheme.type.kicker, color = AgapeTheme.colors.accent,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp).semantics { heading() },
    )
}

@Composable
fun PrayerTextScreen(prayerId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val prayer = remember(prayerId) { container.content.prayer(prayerId) } ?: return
    var prayed by remember { mutableStateOf(false) }
    val c = AgapeTheme.colors
    val fullSpeech = remember(prayer, lang) {
        buildString {
            append(prayer.title[lang]).append(". ")
            prayer.body[lang].forEach { append(it).append(" ") }
        }
    }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(prayer.title[lang], onBack) {
            SpeakerButton(fullSpeech)
            FavoriteButton("prayer:${prayer.id}")
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(prayer.title[lang], style = MaterialTheme.typography.displaySmall, color = c.primary, textAlign = TextAlign.Center)
                if (prayer.subtitle[lang].isNotBlank()) Text(prayer.subtitle[lang], style = MaterialTheme.typography.titleMedium, color = c.accent)
                Spacer(Modifier.height(12.dp))
                SpeakerPill(fullSpeech)
                Spacer(Modifier.height(14.dp))
                Ornament()
            }
            prayer.body[lang].forEach { Text(it, style = AgapeTheme.type.scripture, color = c.ink, modifier = Modifier.padding(bottom = 14.dp)) }
            Spacer(Modifier.height(16.dp))
            if (!prayed) PrimaryButton(stringResource(R.string.prayer_amen), {
                prayed = true
                scope.launch { container.user.markPrayed(prayer.id, prayer.title[lang]) }
            }) else Text(stringResource(R.string.prayer_prayed), style = MaterialTheme.typography.titleMedium, color = c.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(40.dp))
        }
    }
}
