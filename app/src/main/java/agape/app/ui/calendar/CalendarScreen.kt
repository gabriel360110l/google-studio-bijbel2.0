package agape.app.ui.calendar

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.core.model.Testament
import agape.app.core.model.Tradition
import agape.app.data.calendar.FeastRank
import agape.app.data.calendar.LiturgicalCalendar
import agape.app.data.saints.SaintDayView
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.SaintIcon
import agape.app.ui.components.TabHeader
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** The secondary date shown under the civil one: Coptic for the Oriental Orthodox, Julian (old style) for the Orthodox. */
fun secondaryDate(tradition: Tradition, date: LocalDate, lang: AppLanguage): String? = when (tradition) {
    Tradition.ORIENTAL_ORTHODOX -> LiturgicalCalendar.coptic(date).let { "${it.day} ${it.monthName(lang == AppLanguage.DUTCH)} ${it.year}" }
    Tradition.EASTERN_ORTHODOX -> LiturgicalCalendar.julian(date).let {
        "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag(lang.tag))} " + if (lang == AppLanguage.DUTCH) "(oude stijl)" else "(old style)"
    }
    Tradition.CATHOLIC -> null
}

@Composable
fun CalendarScreen(onBack: (() -> Unit)? = null, openPassage: (Passage) -> Unit = {}) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val locale = remember(lang) { Locale.forLanguageTag(lang.tag) }
    val today = remember { LocalDate.now() }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val ym = YearMonth.parse(month)
    val day = LocalDate.parse(selected)
    val activity by container.user.activity.collectAsStateWithLifecycle(emptyList())
    val activeDays = remember(activity) { activity.map { it.epochDay }.toSet() }
    val c = AgapeTheme.colors

    Column(Modifier.fillMaxSize()) {
        // A tab of its own; with onBack it is opened from elsewhere as a detail screen.
        if (onBack != null) DetailTopBar(stringResource(R.string.calendar_title), onBack)
        else TabHeader(kicker = secondaryDate(tradition, today, lang).orEmpty(), title = stringResource(R.string.calendar_title))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_previous), tint = c.ink) }
                Text(
                    ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " ${ym.year}",
                    style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                IconButton({ month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next), tint = c.ink) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                DayOfWeek.entries.forEach {
                    Text(it.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
            val first = ym.atDay(1)
            val cells = (0 until 42).map { first.minusDays((first.dayOfWeek.value - 1).toLong()).plusDays(it.toLong()) }
            cells.chunked(7).forEach { week ->
                if (week.first().monthValue != ym.monthValue && week.last().monthValue != ym.monthValue) return@forEach
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val inMonth = d.monthValue == ym.monthValue
                        val feasts = remember(d, tradition) { LiturgicalCalendar.feasts(tradition, d) }
                        val isSel = d == day
                        val isToday = d == today
                        Column(
                            Modifier.weight(1f).aspectRatio(0.82f).padding(2.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (isSel) c.primary else Color.Transparent)
                                .then(if (isToday && !isSel) Modifier.border(1.dp, c.accent, RoundedCornerShape(14.dp)) else Modifier)
                                .clickable { selected = d.toString() }
                                .semantics { contentDescription = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                d.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium.copy(fontFamily = AgapeTheme.type.ui),
                                color = when { isSel -> c.onPrimary; inMonth -> c.ink; else -> c.inkFaint.copy(alpha = 0.5f) },
                            )
                            if (tradition == Tradition.ORIENTAL_ORTHODOX) {
                                Text(LiturgicalCalendar.coptic(d).day.toString(), style = MaterialTheme.typography.labelSmall, color = if (isSel) c.onPrimary.copy(alpha = 0.8f) else c.inkFaint)
                            }
                            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (feasts.any { it.rank != FeastRank.SEASON }) Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSel) c.onPrimary else c.accent))
                                if (d.toEpochDay() in activeDays) Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSel) c.onPrimary else c.primary.copy(alpha = 0.6f)))
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent)); Text("  " + stringResource(R.string.calendar_legend_feast) + "     ", style = MaterialTheme.typography.labelSmall, color = c.inkMuted)
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.6f))); Text("  " + stringResource(R.string.calendar_legend_active), style = MaterialTheme.typography.labelSmall, color = c.inkMuted)
            }
            Spacer(Modifier.height(8.dp))
            DayDetails(day, tradition, lang, activity.filter { it.epochDay == day.toEpochDay() }.map { it.title }, openPassage)
        }
    }
}

@Composable
private fun DayDetails(day: LocalDate, tradition: Tradition, lang: AppLanguage, done: List<String>, openPassage: (Passage) -> Unit) {
    val container = LocalContainer.current
    val c = AgapeTheme.colors
    val locale = Locale.forLanguageTag(lang.tag)
    val view = remember(day, tradition) { container.saints.day(tradition, day) }
    val principal = view.commemorations.firstOrNull()
    // A moveable or fixed great feast from the liturgical calendar, unless the menologion already names it.
    val feasts = remember(day, tradition, lang) {
        LiturgicalCalendar.feasts(tradition, day).filter { f -> principal?.entry?.name?.get(lang)?.contains(f.name[lang], ignoreCase = true) != true }
    }
    AgapeCard {
        Kicker(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)))
        Spacer(Modifier.height(8.dp))
        ThreeDates(view, tradition, lang)
        Spacer(Modifier.height(12.dp))
        feasts.forEach { f ->
            Text(f.name[lang], style = if (f.rank == FeastRank.SEASON) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, color = if (f.rank == FeastRank.SEASON) c.accent else c.primary)
        }
        if (principal != null) {
            if (feasts.isNotEmpty()) Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SaintIcon(principal.icon, principal.entry.name[lang], width = 84.dp)
                Column(Modifier.weight(1f)) {
                    Text(principal.entry.name[lang], style = MaterialTheme.typography.titleLarge, color = c.ink)
                    principal.entry.life?.let { LifeText(it.text[lang], collapsedLines = 6) }
                }
            }
            principal.icon?.let {
                Text(
                    stringResource(R.string.icon_credit, it.title, it.licence),
                    style = MaterialTheme.typography.labelSmall, color = c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        val others = view.commemorations.drop(1)
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Kicker(stringResource(R.string.saint_also), color = c.inkMuted)
            others.forEach { o ->
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // A small icon only where the saint has one of their own; the general icons would repeat on every row.
                    if (o.ownIcon) SaintIcon(o.icon, o.entry.name[lang], width = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(o.entry.name[lang], style = MaterialTheme.typography.titleMedium, color = c.ink)
                        o.entry.life?.let { LifeText(it.text[lang], collapsedLines = 3) }
                    }
                }
            }
        }
        if (done.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Kicker(stringResource(R.string.calendar_your_day), color = c.inkMuted)
            done.distinct().take(6).forEach { Text("✓ $it", style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.padding(top = 3.dp)) }
        }
    }
    Spacer(Modifier.height(12.dp))
    DayReadings(view, tradition, lang, openPassage)
}

/** The day in the Gregorian, Julian and Coptic calendars; the tradition's own calendar stands out. */
@Composable
private fun ThreeDates(view: SaintDayView, tradition: Tradition, lang: AppLanguage) {
    val c = AgapeTheme.colors
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.forLanguageTag(lang.tag))
    val coptic = view.coptic
    listOf(
        Triple(R.string.calendar_gregorian, view.gregorian.format(format), tradition == Tradition.CATHOLIC),
        Triple(R.string.calendar_julian, view.julian.format(format), tradition == Tradition.EASTERN_ORTHODOX),
        Triple(R.string.calendar_coptic, "${coptic.day} ${coptic.monthName(lang == AppLanguage.DUTCH)} ${coptic.year}", tradition == Tradition.ORIENTAL_ORTHODOX),
    ).forEach { (label, date, own) ->
        Row(Modifier.padding(top = 2.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = c.inkFaint, modifier = Modifier.width(132.dp))
            Text(
                date, style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (own) FontWeight.SemiBold else FontWeight.Normal),
                color = if (own) c.ink else c.inkMuted,
            )
        }
    }
}

/** A life or summary that folds open when it is longer than a few lines. */
@Composable
private fun LifeText(text: String, collapsedLines: Int) {
    if (text.isBlank()) return
    val c = AgapeTheme.colors
    var open by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
        maxLines = if (open) Int.MAX_VALUE else collapsedLines, overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!open) overflows = it.hasVisualOverflow },
        modifier = Modifier.padding(top = 4.dp).animateContentSize(),
    )
    if (open || overflows) {
        Text(
            stringResource(if (open) R.string.calendar_less else R.string.calendar_more),
            style = MaterialTheme.typography.labelMedium, color = c.primary,
            modifier = Modifier.clickable { open = !open }.padding(vertical = 4.dp),
        )
    }
}

/** One reading as shown: its place in the service, the passage to open (if it could be read) and the citation. */
private data class ShownReading(val role: String, val passage: Passage?, val display: String)

/**
 * The readings of the selected day: from the bundled dataset where it has them (the Roman lectionary, 2025-2027),
 * otherwise, for the Byzantine calendar, from orthocal.info when online.
 */
@Composable
private fun DayReadings(view: SaintDayView, tradition: Tradition, lang: AppLanguage, openPassage: (Passage) -> Unit) {
    val container = LocalContainer.current
    val c = AgapeTheme.colors
    val bundled = remember(view) { view.readings.map { ShownReading(it.role, it.ref?.let { ref -> Passage.parse(ref) }, it.display) } }
    val online by produceState<List<ShownReading>?>(null, view, lang) {
        value = if (bundled.isEmpty() && tradition == Tradition.EASTERN_ORTHODOX) {
            container.daily.forDate(tradition, lang, view.gregorian)?.readings.orEmpty().mapNotNull { Passage.parse(it) }.map { p ->
                val testament = container.content.book(p.book)?.testament
                val role = when {
                    p.book == "PSA" -> "psalm"
                    p.book in setOf("MAT", "MRK", "LUK", "JHN") -> "gospel"
                    testament == Testament.NT -> "apostle"
                    else -> "old_testament"
                }
                ShownReading(role, p, p.toString())
            }
        } else emptyList()
    }
    val readings = bundled.ifEmpty { online.orEmpty() }
    AgapeCard(contentPadding = PaddingValues(0.dp)) {
        Kicker(stringResource(R.string.calendar_readings), Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp))
        if (readings.isEmpty()) {
            Text(
                stringResource(if (online == null) R.string.calendar_readings_loading else R.string.calendar_readings_none),
                style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
                modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp),
            )
        }
        readings.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(0.5.dp).background(c.line))
            val p = r.passage
            Row(
                Modifier.fillMaxWidth().then(if (p != null) Modifier.clickable { openPassage(p) } else Modifier).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(readingRole(r.role)), style = MaterialTheme.typography.labelMedium, color = c.accent)
                    Text(p?.let { container.passageLabel(it, lang) } ?: r.display, style = MaterialTheme.typography.titleMedium, color = c.ink)
                    // The full citation when the passage opened is only its first piece (the Psalm verses, a reading in parts).
                    if (p != null && Regex("""[,;]|\band\b|\d[a-eA-E]""").containsMatchIn(r.display)) {
                        Text(r.display, style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
                    }
                }
                if (p != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
            }
        }
        if (readings.isNotEmpty()) Spacer(Modifier.height(6.dp))
    }
}

private fun readingRole(role: String): Int = when (role) {
    "first_reading" -> R.string.reading_first
    "second_reading" -> R.string.reading_second
    "psalm", "psalm_gospel" -> R.string.reading_psalm
    "acclamation" -> R.string.reading_acclamation
    "gospel", "matins_gospel" -> R.string.reading_gospel
    "epistle", "apostle" -> R.string.reading_apostle
    "old_testament", "vespers" -> R.string.reading_old_testament
    "pauline" -> R.string.reading_pauline
    "catholic_epistle" -> R.string.reading_catholic_epistle
    "acts" -> R.string.reading_acts
    else -> R.string.reading_other
}

/** This week at a glance, for Home; tap to open the full calendar. */
@Composable
fun WeekStrip(onOpen: () -> Unit) {
    val lang = LocalLanguage.current
    val tradition = tradition
    val c = AgapeTheme.colors
    val locale = Locale.forLanguageTag(lang.tag)
    val today = remember { LocalDate.now() }
    val week = remember(today) { (0L..6L).map { today.minusDays((today.dayOfWeek.value - 1).toLong()).plusDays(it) } }
    val todayFeast = remember(tradition) { LiturgicalCalendar.feasts(tradition, today).firstOrNull() }
    AgapeCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(stringResource(R.string.calendar_title), Modifier.weight(1f))
            secondaryDate(tradition, today, lang)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.inkMuted) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                val isToday = d == today
                val feast = LiturgicalCalendar.feasts(tradition, d).any { it.rank != FeastRank.SEASON }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2), style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
                    Box(
                        Modifier.padding(top = 4.dp).size(36.dp).clip(CircleShape).background(if (isToday) c.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, color = if (isToday) c.onPrimary else c.ink) }
                    Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (feast) c.accent else Color.Transparent))
                }
            }
        }
        todayFeast?.let {
            Spacer(Modifier.height(10.dp))
            Text(it.name[lang], style = MaterialTheme.typography.titleMedium, color = c.primary)
        }
    }
}
