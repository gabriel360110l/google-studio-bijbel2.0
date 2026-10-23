package agape.app.ui.me

import agape.app.BuildConfig
import agape.app.R
import agape.app.core.model.Passage
import agape.app.data.user.Achievement
import agape.app.data.user.ActivityType
import agape.app.data.user.ProgressStats
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Divider
import agape.app.ui.components.EmptyState
import agape.app.ui.components.Kicker
import agape.app.ui.components.ListRow
import agape.app.ui.components.Pill
import agape.app.ui.components.ProgressLine
import agape.app.ui.components.QuietButton
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class SavedTab(val label: Int) { BOOKMARKS(R.string.saved_bookmarks), HIGHLIGHTS(R.string.saved_highlights), NOTES(R.string.saved_notes), PRAYERS(R.string.saved_prayers), PLANS(R.string.saved_plans) }

@Composable
fun SavedScreen(
    onBack: () -> Unit,
    openVerse: (String, Int, Int) -> Unit,
    openPlan: (String) -> Unit,
    openHour: (String) -> Unit,
    openPrayer: (String) -> Unit,
) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    var tab by rememberSaveable { mutableStateOf(SavedTab.BOOKMARKS) }
    val bookmarks by container.user.bookmarks.collectAsStateWithLifecycle(emptyList())
    val highlights by container.user.highlights.collectAsStateWithLifecycle(emptyList())
    val notes by container.user.notes.collectAsStateWithLifecycle(emptyList())
    val favorites by container.user.favorites.collectAsStateWithLifecycle(emptyList())
    val plans by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val hours = remember(tradition) { container.content.prayerBook(tradition).hours }
    fun label(b: String, c: Int, v: Int) = container.passageLabel(Passage(b, c, v), lang)

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.me_saved), onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SavedTab.entries.forEach { t -> Pill(stringResource(t.label), tab == t, { tab = t }) }
        }
        val empty = @Composable { EmptyState(stringResource(R.string.saved_empty_title), stringResource(R.string.saved_empty_text)) }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            when (tab) {
                SavedTab.BOOKMARKS -> {
                    if (bookmarks.isEmpty()) item { empty() }
                    items(bookmarks, key = { "${it.book}.${it.chapter}.${it.verse}" }) { b ->
                        ListRow(label(b.book, b.chapter, b.verse), onClick = { openVerse(b.book, b.chapter, b.verse) }); Divider()
                    }
                }
                SavedTab.HIGHLIGHTS -> {
                    if (highlights.isEmpty()) item { empty() }
                    items(highlights, key = { "${it.book}.${it.chapter}.${it.verse}" }) { h ->
                        ListRow(
                            label(h.book, h.chapter, h.verse), onClick = { openVerse(h.book, h.chapter, h.verse) },
                            leading = { Box(Modifier.size(14.dp).clip(CircleShape).background(AgapeTheme.colors.highlights.getOrElse(h.color) { AgapeTheme.colors.accent })) },
                        ); Divider()
                    }
                }
                SavedTab.NOTES -> {
                    if (notes.isEmpty()) item { empty() }
                    items(notes, key = { "${it.book}.${it.chapter}.${it.verse}" }) { n ->
                        ListRow(label(n.book, n.chapter, n.verse), subtitle = n.text, onClick = { openVerse(n.book, n.chapter, n.verse) }); Divider()
                    }
                }
                SavedTab.PRAYERS -> {
                    if (favorites.isEmpty()) item { empty() }
                    items(favorites, key = { it.id }) { f ->
                        val (kind, id) = f.id.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                        val title = if (kind == "hour") hours.firstOrNull { it.id == id }?.name?.get(lang) else container.content.prayer(id)?.title?.get(lang)
                        if (title != null) {
                            ListRow(title, onClick = { if (kind == "hour") openHour(id) else openPrayer(id) }); Divider()
                        }
                    }
                }
                SavedTab.PLANS -> {
                    if (plans.isEmpty()) item { empty() }
                    items(plans, key = { it.planId }) { p ->
                        val def = container.content.plan(p.planId) ?: return@items
                        ListRow(
                            def.title[lang],
                            subtitle = if (p.active) stringResource(R.string.plans_active) else stringResource(R.string.plan_finished),
                            onClick = { openPlan(p.planId) },
                        ); Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val activity by container.user.activity.collectAsStateWithLifecycle(emptyList())
    val grouped = remember(activity) { activity.groupBy { it.epochDay }.toSortedMap(compareByDescending { it }) }
    val fmt = remember(lang) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag(lang.tag)) }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.me_activity), onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (activity.isEmpty()) item { EmptyState(stringResource(R.string.activity_empty_title), stringResource(R.string.activity_empty_text)) }
            grouped.forEach { (day, items) ->
                item(key = "d$day") { Kicker(LocalDate.ofEpochDay(day).format(fmt), Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)) }
                items(items, key = { "${it.type}${it.ref}${it.epochDay}" }) { a ->
                    ListRow(a.title, subtitle = stringResource(activityLabel(a.type))); Divider()
                }
            }
        }
    }
}

private fun activityLabel(t: ActivityType) = when (t) {
    ActivityType.READ_CHAPTER -> R.string.activity_read
    ActivityType.PRAYED_HOUR -> R.string.activity_prayed_hour
    ActivityType.PRAYED -> R.string.activity_prayed
    ActivityType.QUIZ_PASSED -> R.string.activity_quiz
    ActivityType.PLAN_DAY -> R.string.activity_plan_day
    ActivityType.HIGHLIGHT -> R.string.activity_highlight
    ActivityType.BOOKMARK -> R.string.activity_bookmark
    ActivityType.NOTE -> R.string.activity_note
}

private fun achievementTitle(a: Achievement) = when (a) {
    Achievement.FIRST_CHAPTER -> R.string.ach_first_chapter
    Achievement.TEN_CHAPTERS -> R.string.ach_ten_chapters
    Achievement.HUNDRED_CHAPTERS -> R.string.ach_hundred_chapters
    Achievement.STREAK_7 -> R.string.ach_streak_7
    Achievement.STREAK_30 -> R.string.ach_streak_30
    Achievement.FIRST_HOUR -> R.string.ach_first_hour
    Achievement.FORTY_HOURS -> R.string.ach_forty_hours
    Achievement.FIRST_QUIZ -> R.string.ach_first_quiz
    Achievement.FIRST_PLAN -> R.string.ach_first_plan
    Achievement.COUNCILS -> R.string.ach_councils
    Achievement.TEN_SAVED -> R.string.ach_ten_saved
}

@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val stats by container.user.stats.collectAsStateWithLifecycle(ProgressStats())
    val c = AgapeTheme.colors
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.me_achievements), onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Achievement.entries) { a ->
                val earned = a.earned(stats)
                AgapeCard(tonal = !earned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.EmojiEvents, null, tint = if (earned) c.accent else c.inkFaint, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(achievementTitle(a)), style = MaterialTheme.typography.titleMedium, color = if (earned) c.ink else c.inkMuted)
                            Spacer(Modifier.height(6.dp))
                            ProgressLine(a.progress(stats) / a.target.toFloat())
                            Text("${a.progress(stats)} / ${a.target}", style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit, openLegal: (String) -> Unit) {
    val context = LocalContext.current
    val c = AgapeTheme.colors
    val faq = listOf(
        R.string.help_q_offline to R.string.help_a_offline,
        R.string.help_q_translation to R.string.help_a_translation,
        R.string.help_q_quiz to R.string.help_a_quiz,
        R.string.help_q_notifications to R.string.help_a_notifications,
        R.string.help_q_church to R.string.help_a_church,
    )
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.me_help), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            faq.forEach { (q, a) ->
                AgapeCard {
                    Text(stringResource(q), style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(a), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                }
            }
            QuietButton(stringResource(R.string.help_contact), {
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${BuildConfig.SUPPORT_EMAIL}"))) }
            })
            QuietButton(stringResource(R.string.legal_privacy), { openLegal("privacy") })
            QuietButton(stringResource(R.string.legal_terms), { openLegal("terms") })
        }
    }
}
