package agape.app.ui.plans

import agape.app.R
import agape.app.data.plans.PlanEngine
import agape.app.data.plans.planDays
import agape.app.data.quiz.QuizGenerator
import agape.app.data.content.QuizQuestion
import agape.app.ui.bookName
import agape.app.ui.passageLabel
import androidx.compose.runtime.produceState
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.ProgressLine
import agape.app.ui.components.SecondaryButton
import agape.app.ui.components.cardShape
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** A short quiz. Passing it can unlock the next day of a locked plan, and completes that plan day. */
@Composable
fun QuizScreen(quizKey: String, title: String, planId: String, day: Int, onDone: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    val templates = QuizGenerator.Templates(
        cloze = stringResource(R.string.quiz_gen_cloze),
        whichChapter = stringResource(R.string.quiz_gen_which_chapter),
        chapterOption = stringResource(R.string.quiz_gen_chapter_option),
    )
    val loaded by produceState<List<QuizQuestion>?>(null, quizKey, lang) { value = loadQuiz(container, quizKey, lang, templates) }
    val questions = loaded ?: return
    val answers = remember { mutableStateListOf<Int>() }
    var index by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var finished by remember { mutableStateOf(false) }
    val c = AgapeTheme.colors
    if (questions.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            DetailTopBar(stringResource(R.string.quiz_title, title), onDone)
            Text(stringResource(R.string.quiz_none), style = MaterialTheme.typography.bodyLarge, color = c.inkMuted, modifier = Modifier.padding(24.dp))
        }
        return
    }
    val needed = ceil(questions.size * container.content.quizPassMark).toInt()

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.quiz_title, title), onDone)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!finished) {
                val q = questions[index]
                ProgressLine((index) / questions.size.toFloat())
                Kicker(stringResource(R.string.quiz_question_of, index + 1, questions.size))
                Text(q.q[lang], style = MaterialTheme.typography.headlineSmall, color = c.ink)
                Spacer(Modifier.height(4.dp))
                q.options.forEachIndexed { i, option ->
                    val revealed = chosen != null
                    val isAnswer = i == q.answer
                    val border = when {
                        revealed && isAnswer -> c.primary
                        revealed && chosen == i -> c.danger
                        else -> c.line
                    }
                    Text(
                        option[lang],
                        style = MaterialTheme.typography.bodyLarge, color = c.ink,
                        modifier = Modifier.fillMaxWidth().clip(cardShape)
                            .background(if (revealed && isAnswer) c.surfaceSunken else c.surface)
                            .border(if (revealed && (isAnswer || chosen == i)) 2.dp else 1.dp, border, cardShape)
                            .selectable(selected = chosen == i, enabled = !revealed, role = Role.RadioButton) { chosen = i }
                            .padding(16.dp),
                    )
                }
                chosen?.let { pick ->
                    Text(
                        stringResource(if (pick == q.answer) R.string.quiz_correct else R.string.quiz_incorrect),
                        style = MaterialTheme.typography.titleMedium, color = if (pick == q.answer) c.primary else c.danger,
                    )
                    q.explanation?.let { Text(it[lang], style = MaterialTheme.typography.bodyMedium, color = c.inkMuted) }
                    PrimaryButton(stringResource(if (index + 1 < questions.size) R.string.quiz_next else R.string.quiz_finish), {
                        answers += pick
                        chosen = null
                        if (index + 1 < questions.size) index++ else {
                            finished = true
                            val score = answers.indices.count { answers[it] == questions[it].answer }
                            val passed = score >= needed
                            scope.launch {
                                container.user.saveQuizResult(quizKey, score, questions.size, passed, title)
                                if (passed && planId.isNotEmpty()) completePlanDay(container, planId, day, tradition, lang, title)
                            }
                        }
                    })
                }
            } else {
                val score = answers.indices.count { answers[it] == questions[it].answer }
                val passed = score >= needed
                Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ornament()
                    Text(stringResource(if (passed) R.string.quiz_passed else R.string.quiz_not_passed), style = MaterialTheme.typography.headlineMedium, color = c.ink)
                    Text(stringResource(R.string.quiz_score, score, questions.size), style = MaterialTheme.typography.titleMedium, color = c.inkMuted)
                    if (!passed) Text(stringResource(R.string.quiz_retry_hint, needed), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                    Spacer(Modifier.height(8.dp))
                    if (passed) PrimaryButton(stringResource(R.string.continue_label), onDone)
                    else {
                        PrimaryButton(stringResource(R.string.quiz_retry), { answers.clear(); index = 0; finished = false })
                        SecondaryButton(stringResource(R.string.quiz_reread), onDone)
                    }
                }
            }
        }
    }
}

/** Written questions where they exist; otherwise questions generated from the chapter or book text. */
private suspend fun loadQuiz(
    container: agape.app.AppContainer, key: String, lang: agape.app.core.model.AppLanguage, t: QuizGenerator.Templates,
): List<QuizQuestion> {
    container.content.quiz(key)?.let { return it }
    val label = { v: agape.app.data.bible.Verse -> container.passageLabel(agape.app.core.model.Passage(v.book, v.chapter, v.verse), lang) }
    return when {
        key.startsWith("chapter:") -> {
            val (book, ch) = key.removePrefix("chapter:").split('.').let { it[0] to it[1].toInt() }
            QuizGenerator.chapterQuiz(container.bible.chapter(book, ch, lang), label, t, count = 5, seed = key.hashCode().toLong())
        }
        key.startsWith("book:") -> {
            val book = key.removePrefix("book:")
            QuizGenerator.bookQuiz(container.bible.book(book, lang), container.bookName(book, lang), label, t, count = 20, seed = key.hashCode().toLong())
        }
        else -> emptyList()
    }
}

private suspend fun completePlanDay(
    container: agape.app.AppContainer, planId: String, day: Int,
    tradition: agape.app.core.model.Tradition, lang: agape.app.core.model.AppLanguage, title: String,
) {
    val plan = container.content.plan(planId) ?: return
    val progress = container.user.plans.first().firstOrNull { it.planId == planId } ?: return
    val days = container.content.planDays(plan, tradition, lang)
    if (day !in progress.completedDays && PlanEngine.isUnlocked(plan, day, progress.completedDays)) {
        container.user.completePlanDay(progress, day, days.size, "${plan.title[lang]} · $title")
    }
}
