package agape.app.ui.plans

import agape.app.R
import agape.app.core.model.Passage
import agape.app.data.content.PlanCategory
import agape.app.data.content.PlanDefinition
import agape.app.data.plans.PlanEngine
import agape.app.data.plans.planDays
import agape.app.data.plans.quizKeyFor
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.church.CouncilBody
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Divider
import agape.app.ui.components.Kicker
import agape.app.ui.components.ListRow
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.ProgressLine
import agape.app.ui.components.QuietButton
import agape.app.ui.components.SecondaryButton
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private fun categoryName(c: PlanCategory) = when (c) {
    PlanCategory.BEGINNER -> R.string.plans_beginner
    PlanCategory.OT_NT -> R.string.plans_ot_nt
    PlanCategory.FULL -> R.string.plans_full
    PlanCategory.COUNCILS -> R.string.plans_councils
}

@Composable
fun PlansScreen(onBack: () -> Unit, openPlan: (String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val progress by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val grouped = remember { container.content.plans.groupBy { it.category } }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.plans_title), onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            PlanCategory.entries.forEach { cat ->
                val list = grouped[cat].orEmpty()
                if (list.isEmpty()) return@forEach
                item { Kicker(stringResource(categoryName(cat)), Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)) }
                items(list, key = { it.id }) { plan ->
                    val p = progress.firstOrNull { it.planId == plan.id }
                    ListRow(
                        title = plan.title[lang],
                        subtitle = plan.description[lang],
                        onClick = { openPlan(plan.id) },
                        trailing = {
                            when {
                                p?.active == true -> Text(stringResource(R.string.plans_active), style = MaterialTheme.typography.labelMedium, color = AgapeTheme.colors.primary)
                                p != null -> Icon(Icons.Outlined.CheckCircle, stringResource(R.string.plan_finished), tint = AgapeTheme.colors.accent)
                                plan.locked -> Icon(Icons.Outlined.Lock, stringResource(R.string.plans_with_quizzes), tint = AgapeTheme.colors.inkFaint)
                            }
                        },
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun PlanScreen(planId: String, onBack: () -> Unit, openDay: (String, Int) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    val plan = remember(planId) { container.content.plan(planId) } ?: return
    val days = remember(plan, tradition, lang) { container.content.planDays(plan, tradition, lang) }
    val all by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val progress = all.firstOrNull { it.planId == planId }
    val done = progress?.completedDays.orEmpty()
    var confirmStop by remember { mutableStateOf(false) }
    val c = AgapeTheme.colors

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.plan_stop_title)) },
            text = { Text(stringResource(R.string.plan_stop_text)) },
            confirmButton = { TextButton({ confirmStop = false; scope.launch { container.user.stopPlan(planId) } }) { Text(stringResource(R.string.plan_stop), color = c.danger) } },
            dismissButton = { TextButton({ confirmStop = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(plan.title[lang], onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(plan.description[lang], style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
                    Text(
                        stringResource(R.string.plan_length, days.size) + if (plan.locked) " · " + stringResource(R.string.plans_with_quizzes) else "",
                        style = MaterialTheme.typography.labelMedium, color = c.accent,
                    )
                    if (progress != null) {
                        ProgressLine(done.size / days.size.coerceAtLeast(1).toFloat())
                        val next = PlanEngine.nextDay(days.size, done)
                        if (next != null) PrimaryButton(stringResource(R.string.plan_continue_day, next), { openDay(planId, next) })
                        else Text(stringResource(R.string.plan_finished), style = MaterialTheme.typography.titleMedium, color = c.primary)
                        QuietButton(stringResource(R.string.plan_stop), { confirmStop = true })
                    } else {
                        PrimaryButton(stringResource(R.string.plan_start), {
                            scope.launch { container.user.startPlan(planId) }
                            openDay(planId, 1)
                        })
                    }
                    Ornament(Modifier.padding(vertical = 6.dp))
                }
            }
            items(days, key = { it.number }) { day ->
                val unlocked = progress != null && PlanEngine.isUnlocked(plan, day.number, done)
                ListRow(
                    title = day.title?.get(lang) ?: stringResource(R.string.plan_day, day.number),
                    subtitle = day.readings.joinToString(" · ") { container.passageLabel(it, lang) },
                    onClick = if (unlocked) ({ openDay(planId, day.number) }) else null,
                    leading = {
                        Icon(
                            when {
                                day.number in done -> Icons.Outlined.CheckCircle
                                !unlocked -> Icons.Outlined.Lock
                                else -> Icons.Outlined.RadioButtonUnchecked
                            },
                            null,
                            tint = if (day.number in done) c.accent else c.inkFaint,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun PlanDayScreen(planId: String, day: Int, onBack: () -> Unit, openPassage: (Passage) -> Unit, openQuiz: (String, String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    val plan: PlanDefinition = remember(planId) { container.content.plan(planId) } ?: return
    val days = remember(plan, tradition, lang) { container.content.planDays(plan, tradition, lang) }
    val planDay = days.firstOrNull { it.number == day } ?: return
    val all by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val progress = all.firstOrNull { it.planId == planId }
    val quizKey = remember(planDay) { container.content.quizKeyFor(plan, planDay) }
    val quizzes by container.user.quizResults.collectAsStateWithLifecycle(emptyList())
    val quizPassed = quizKey != null && quizzes.any { it.key == quizKey && it.passed }
    val needsQuiz = plan.locked && quizKey != null && !quizPassed
    val done = progress?.completedDays?.contains(day) == true
    val title = planDay.title?.get(lang) ?: stringResource(R.string.plan_day, day)
    val council = planDay.councilId?.let { container.content.council(it) }
    val c = AgapeTheme.colors

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.plan_day_of, day, days.size), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(plan.title[lang], style = MaterialTheme.typography.labelLarge, color = c.accent)
            if (council == null) Text(title, style = MaterialTheme.typography.headlineMedium, color = c.ink)
            if (council != null) CouncilBody(council, openPassage)
            else AgapeCard {
                Kicker(stringResource(R.string.plan_readings))
                Spacer(Modifier.height(4.dp))
                planDay.readings.forEach { p -> QuietButton(container.passageLabel(p, lang), { openPassage(p) }) }
            }
            Spacer(Modifier.height(4.dp))
            when {
                done -> Text(stringResource(R.string.plan_day_done), style = MaterialTheme.typography.titleMedium, color = c.primary)
                progress == null -> Text(stringResource(R.string.plan_not_started), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                needsQuiz -> {
                    Text(stringResource(R.string.plan_quiz_required), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                    PrimaryButton(stringResource(R.string.reader_take_quiz), { openQuiz(quizKey!!, title) })
                }
                else -> {
                    PrimaryButton(stringResource(R.string.plan_complete_day), {
                        scope.launch { container.user.completePlanDay(progress, day, days.size, "${plan.title[lang]} · $title") }
                        onBack()
                    })
                    if (quizKey != null && !quizPassed) SecondaryButton(stringResource(R.string.plan_optional_quiz), { openQuiz(quizKey, title) })
                }
            }
        }
    }
}
