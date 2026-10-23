package agape.app.ui.home

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.core.model.Testament
import agape.app.core.model.Tradition
import agape.app.data.calendar.ChurchCalendar
import agape.app.data.calendar.DayObservanceKind
import agape.app.data.calendar.LiturgicalCalendar
import agape.app.data.content.chapterCount
import agape.app.data.daily.DailyInfo
import agape.app.data.plans.PlanEngine
import agape.app.data.plans.planDays
import agape.app.data.rosary.MysterySet
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.bookName
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.Backdrop
import agape.app.ui.components.Kicker
import agape.app.ui.components.ProgressLine
import agape.app.ui.components.SaintOfDayCard
import agape.app.ui.main.BibleRoute
import agape.app.ui.passageLabel
import agape.app.ui.prayer.ChotkiMark
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Cream on the dark backdrop, whatever the style: the backdrop is always dark. */
private val OnBackdrop = Color(0xFFF4EEE3)

@Composable
fun HomeScreen(
    openReader: (String, Int, Int) -> Unit,
    openPlans: () -> Unit,
    openPlan: (String) -> Unit,
    openTab: (Any) -> Unit,
    openHelp: () -> Unit,
    openProfile: () -> Unit,
    openRosary: () -> Unit,
    openCalendar: () -> Unit,
    openChotki: () -> Unit = {},
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val nl = lang == AppLanguage.DUTCH
    val settings = LocalSettings.current
    val tradition = tradition
    val today = remember { LocalDate.now() }
    val c = AgapeTheme.colors
    val context = LocalContext.current

    val verseRef = remember(tradition, today) { Passage.parse(container.content.verseOfDay(tradition, today)) }
    val verseText by produceState<String?>(null, verseRef, lang) {
        value = verseRef?.let { container.bible.passage(it, lang).joinToString(" ") { v -> v.text } }
    }
    val daily by produceState<DailyInfo?>(null, tradition, lang) { value = container.daily.today(tradition, lang) }
    val saint = remember(tradition, lang, today) { container.saints.forDate(tradition, lang, today) }
    val dayStatus = remember(tradition, today) { LiturgicalCalendar.dayStatus(tradition, today) }
    var showDayStatusDialog by remember { mutableStateOf(false) }
    val plans by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val stats by container.user.stats.collectAsStateWithLifecycle(null)
    val totalChapters = remember(tradition, lang) { container.content.canon(tradition, lang).sumOf { it.chapterCount(lang) } }
    val background = remember(today, tradition) { container.backgrounds.forDate(today, tradition) }
    val verseLabel = verseRef?.let { container.passageLabel(it, lang) }.orEmpty()
    // The verse may open or close a quotation of its own; the page supplies the outer quotes.
    val quoted = verseText?.let { "“${it.trim(' ', '“', '”', '"')}”" }
    val traditionCross = when (tradition) {
        Tradition.EASTERN_ORTHODOX -> "☦"
        Tradition.ORIENTAL_ORTHODOX -> "☩"
        Tradition.CATHOLIC -> "✝"
    }
    val traditionLabel = when (tradition) {
        Tradition.EASTERN_ORTHODOX -> stringResource(R.string.tradition_eo)
        Tradition.ORIENTAL_ORTHODOX -> stringResource(R.string.tradition_oo)
        Tradition.CATHOLIC -> stringResource(R.string.tradition_ca)
    }

    if (showDayStatusDialog) {
        AlertDialog(
            onDismissRequest = { showDayStatusDialog = false },
            containerColor = c.surface,
            title = {
                Text(
                    dayStatus.title[lang],
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.primary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        dayStatus.badge[lang],
                        style = MaterialTheme.typography.labelLarge,
                        color = c.accent,
                    )
                    Text(
                        dayStatus.description[lang],
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                    )
                }
            },
            confirmButton = {
                TextButton({
                    showDayStatusDialog = false
                    openCalendar()
                }) {
                    Text(if (nl) "Open Kerkelijke Kalender" else "Open Church Calendar", color = c.primary)
                }
            },
            dismissButton = {
                TextButton({ showDayStatusDialog = false }) {
                    Text(if (nl) "Sluiten" else "Close", color = c.inkMuted)
                }
            },
        )
    }

    // The picture behind the status bar is dark in every style, so the clock and icons turn light while Home shows.
    val view = LocalView.current
    DisposableEffect(view, c.isDark) {
        val window = (view.context as? Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.isAppearanceLightStatusBars = false
        onDispose { insets?.isAppearanceLightStatusBars = !c.isDark }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Verse of the day, over the picture of the day.
        item {
            Backdrop(background, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 24.dp)) {
                    Spacer(Modifier.height(12.dp))
                    // Clean top bar: tradition badge on the left, Info & Settings buttons high at the top right
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.36f))
                                .border(0.8.dp, OnBackdrop.copy(alpha = 0.28f), CircleShape)
                                .clickable(onClick = openProfile)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(traditionCross, style = MaterialTheme.typography.labelLarge, color = c.accent)
                            Text(traditionLabel, style = MaterialTheme.typography.labelMedium, color = OnBackdrop.copy(alpha = 0.92f))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RoundIcon(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.home_help), openHelp)
                            RoundIcon(Icons.Outlined.Settings, stringResource(R.string.home_profile), openProfile)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    // Fasting & Feast Day indicator pill at the top of Home
                    val statusSymbol = when (dayStatus.kind) {
                        DayObservanceKind.FEAST -> "✦"
                        DayObservanceKind.FAST -> "🕯"
                        DayObservanceKind.FAST_FREE -> "☦"
                        DayObservanceKind.ORDINARY -> "•"
                    }
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f))
                            .border(
                                0.8.dp,
                                if (dayStatus.kind == DayObservanceKind.FEAST) c.accent.copy(alpha = 0.7f) else OnBackdrop.copy(alpha = 0.30f),
                                CircleShape,
                            )
                            .clickable { showDayStatusDialog = true }
                            .padding(horizontal = 13.dp, vertical = 6.dp)
                            .testTag("home_fast_feast_badge"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(statusSymbol, style = MaterialTheme.typography.labelMedium, color = c.accent)
                        Text(
                            dayStatus.badge[lang],
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = OnBackdrop.copy(alpha = 0.95f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(92.dp))

                    Kicker(stringResource(R.string.home_today), color = OnBackdrop.copy(alpha = 0.78f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag(lang.tag)))
                            .replaceFirstChar { it.titlecase(Locale.forLanguageTag(lang.tag)) },
                        style = MaterialTheme.typography.bodyLarge, color = OnBackdrop.copy(alpha = 0.92f),
                    )
                    Box(Modifier.padding(top = 12.dp).width(32.dp).height(2.dp).background(c.accent))
                    if (ChurchCalendar.isPaschalSeason(tradition, today)) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(if (tradition == Tradition.CATHOLIC) R.string.home_paschal_catholic else R.string.home_paschal_orthodox),
                            style = MaterialTheme.typography.titleLarge, color = c.accent,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        quoted ?: "…",
                        style = AgapeTheme.type.scripture.copy(
                            fontFamily = AgapeTheme.type.display,
                            fontSize = AgapeTheme.type.scripture.fontSize * 1.42f,
                            lineHeight = AgapeTheme.type.scripture.fontSize * 1.42f * 1.28f,
                        ),
                        color = OnBackdrop,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(verseLabel, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = OnBackdrop)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BackdropButton(stringResource(R.string.home_read), filled = true) {
                            verseRef?.let { r -> openReader(r.book, r.chapter, r.startVerse ?: 0) }
                        }
                        BackdropButton(stringResource(R.string.home_share), filled = false) {
                            if (quoted != null) {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$quoted\n— $verseLabel")
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.home_share_via)))
                            }
                        }
                    }
                    Spacer(Modifier.height(44.dp))
                }
            }
        }

        // Reading
        item {
            val pos = settings.lastPosition
            Section(stringResource(R.string.home_reading)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { if (pos != null) openReader(pos.book, pos.chapter, pos.verse) else openTab(BibleRoute) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_open_bible), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium), color = c.ink)
                        Text(
                            if (pos != null) "${container.bookName(pos.book, lang)} ${pos.chapter}" else stringResource(R.string.home_start_reading),
                            style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
                }
            }
        }

        // Progress
        item {
            val s = stats
            Section(stringResource(R.string.home_progress)) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Stat("${s?.currentStreak ?: 0}", stringResource(R.string.home_stat_streak))
                    Stat("${s?.chaptersRead ?: 0}/$totalChapters", stringResource(R.string.home_stat_read))
                    Stat("${s?.hoursPrayed ?: 0}", stringResource(R.string.home_stat_prayed))
                }
            }
        }

        // Readings of the day
        item {
            val readings = daily?.readings.orEmpty().mapNotNull { Passage.parse(it) }
            ReadingsCard(readings, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), onOpen = { p -> openReader(p.book, p.chapter, p.startVerse ?: 0) })
        }

        // Interactive Chotki (Prayer Rope) on Home
        item {
            AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), onClick = openChotki) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Kicker(if (nl) "Gebedssnoer · Chotki" else "Prayer Rope · Chotki")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (nl) "Bid het Jezusgebed" else "Pray the Jesus Prayer",
                            style = MaterialTheme.typography.headlineSmall,
                            color = c.ink,
                        )
                        Text(
                            if (nl) "Heer Jezus Christus, Zoon van God, ontferm U over mij, zondaar."
                            else "Lord Jesus Christ, Son of God, have mercy on me, a sinner.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.inkMuted,
                        )
                    }
                    ChotkiMark(Modifier.size(48.dp))
                }
            }
        }

        // The Rosary, for Catholics.
        if (tradition == Tradition.CATHOLIC) {
            item {
                AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), onClick = openRosary) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Kicker(stringResource(R.string.rosary_title))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.home_rosary_today, MysterySet.forDay(today.dayOfWeek).title[lang]),
                                style = MaterialTheme.typography.headlineSmall, color = c.ink,
                            )
                            Text(stringResource(R.string.rosary_card_body), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
                    }
                }
            }
        }

        // Reading plan
        item {
            val active = plans.firstOrNull { it.active }
            val def = active?.let { container.content.plan(it.planId) }
            AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), onClick = { if (def != null) openPlan(def.id) else openPlans() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Kicker(stringResource(R.string.home_plan), color = c.inkMuted)
                        Spacer(Modifier.height(6.dp))
                        if (def != null) {
                            val days = remember(def, tradition, lang) { container.content.planDays(def, tradition, lang) }
                            val next = PlanEngine.nextDay(days.size, active.completedDays)
                            Text(def.title[lang], style = MaterialTheme.typography.headlineSmall, color = c.ink)
                            Text(
                                if (next != null) stringResource(R.string.plan_day_of, next, days.size) else stringResource(R.string.plan_finished),
                                style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            ProgressLine(active.completedDays.size / days.size.coerceAtLeast(1).toFloat())
                        } else {
                            Text(stringResource(R.string.home_plan_start), style = MaterialTheme.typography.headlineSmall, color = c.ink)
                            Text(stringResource(R.string.home_plan_start_hint), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                        }
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
                }
            }
        }

        // Saint of the day: further down, so it greets the reader on the way through the page.
        saint?.let { s ->
            item {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                AnimatedVisibility(shown, enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 5 }) {
                    SaintOfDayCard(
                        s, stringResource(if (s.isFeast) R.string.saint_feast else R.string.home_saint),
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }

        background?.let { b ->
            item {
                Text(
                    stringResource(R.string.home_background_credit, b.title, b.licence),
                    style = MaterialTheme.typography.labelSmall, color = c.inkFaint, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** A small uppercase title over a group, as in a printed book. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Kicker(title, color = AgapeTheme.colors.inkMuted)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val c = AgapeTheme.colors
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, OnBackdrop.copy(alpha = 0.35f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = OnBackdrop, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun BackdropButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (filled) OnBackdrop else Color.Black.copy(alpha = 0.25f),
        border = if (filled) null else BorderStroke(1.dp, OnBackdrop.copy(alpha = 0.45f)),
    ) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = if (filled) Color(0xFF1A1512) else OnBackdrop,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
        )
    }
}

/** The day's readings, labelled as in the lectionary: Law and Prophets, Psalm, Gospel, Apostle. Folds away. */
@Composable
private fun ReadingsCard(readings: List<Passage>, modifier: Modifier = Modifier, onOpen: (Passage) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    var open by rememberSaveable { mutableStateOf(true) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    AgapeCard(modifier, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(22.dp).clip(CircleShape).background(c.primary))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.home_readings), style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.inkMuted, modifier = Modifier.rotate(turn))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically()) {
            Column(Modifier.padding(bottom = 8.dp)) {
                if (readings.isEmpty()) {
                    Text(
                        stringResource(R.string.home_readings_offline), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp),
                    )
                }
                readings.forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(0.5.dp).background(c.line))
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(p) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Kicker(stringResource(readingLabel(container.content.book(p.book)?.testament, p.book)), color = c.inkMuted)
                            Spacer(Modifier.height(4.dp))
                            Text(container.passageLabel(p, lang), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
                    }
                }
            }
        }
    }
}

private val gospels = setOf("MAT", "MRK", "LUK", "JHN")

private fun readingLabel(testament: Testament?, book: String): Int = when {
    book == "PSA" -> R.string.home_reading_psalm
    book in gospels -> R.string.home_reading_gospel
    testament == Testament.NT -> R.string.home_reading_apostle
    else -> R.string.home_reading_law
}
