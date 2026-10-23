package agape.app.ui.bible

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.data.bible.ChapterGuide
import agape.app.data.bible.FatherNote
import agape.app.data.bible.FatherQuote
import agape.app.data.content.Book
import agape.app.data.content.ChapterCommentary
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.bookName
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.SegmentedTabs
import agape.app.ui.components.cardShape
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private data class RefPreview(val passage: Passage, val text: String)

private enum class InfoTab(val label: Int) {
    EXPLANATION(R.string.info_tab_explanation), FATHERS(R.string.info_tab_fathers), REFERENCES(R.string.info_tab_refs),
}

/**
 * Everything about a chapter that is not the text itself, opened from the reader's Info button so the Bible text
 * stays clean: the explanation, the Fathers and where else to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterInfoSheet(book: String, chapter: Int, onDismiss: () -> Unit, openReference: (String, Int, Int) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val commentary = remember(book, chapter) { container.content.commentary(book, chapter) }
    val bookInfo = remember(book) { container.content.book(book) }
    val guide by produceState<ChapterGuide?>(null, book, chapter) {
        if (commentary == null) value = container.bible.chapterGuide(book, chapter)
    }
    val quotes by produceState(emptyList<FatherQuote>(), book, chapter) { value = container.bible.chapterQuotes(book, chapter) }
    val notes by produceState(emptyList<FatherNote>(), book, chapter) { value = container.bible.fatherNotes(book, chapter) }
    val refs by produceState(emptyList<RefPreview>(), book, chapter, lang) {
        value = container.bible.chapterCrossRefs(book, chapter, limit = 10).map { r ->
            val p = Passage(r.book, r.chapter, r.verse, r.chapter, r.verseEnd)
            RefPreview(p, container.bible.passage(p, lang).joinToString(" ") { it.text })
        }.filter { it.text.isNotBlank() }
    }
    var tab by rememberSaveable { mutableStateOf(InfoTab.EXPLANATION) }
    val go: (String, Int, Int) -> Unit = { b, ch, v -> onDismiss(); openReference(b, ch, v) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Kicker(stringResource(R.string.chapter_explanation))
                Spacer(Modifier.height(4.dp))
                Text("${container.bookName(book, lang)} $chapter", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                Ornament(Modifier.padding(vertical = 6.dp))
                SegmentedTabs(InfoTab.entries.map { stringResource(it.label) }, tab.ordinal, { tab = InfoTab.entries[it] })
            }
            when (tab) {
                InfoTab.EXPLANATION -> Explanation(commentary, guide, bookInfo, lang)
                InfoTab.FATHERS -> Fathers(commentary, notes, quotes.distinctBy { it.father }.take(6), lang, go)
                InfoTab.REFERENCES -> References(refs, lang, go)
            }
        }
    }
}

@Composable
private fun ColumnScope.Explanation(commentary: ChapterCommentary?, guide: ChapterGuide?, book: Book?, lang: AppLanguage) {
    val c = AgapeTheme.colors
    when {
        commentary != null -> {
            Text(commentary.title[lang], style = MaterialTheme.typography.titleLarge, color = c.primary)
            Body(commentary.summary[lang])
            commentary.meaning[lang].takeIf { it.isNotBlank() }?.let { Section(stringResource(R.string.info_meaning)) { Body(it) } }
            Themes(commentary.themes[lang])
        }
        guide != null -> {
            Provisional()
            Text(guide.title, style = MaterialTheme.typography.titleLarge, color = c.primary)
            Body(guide.summary)
            Themes(guide.themes)
        }
        else -> Text(stringResource(R.string.info_none), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    }
    book?.intro?.get(lang)?.takeIf { it.isNotBlank() }?.let { intro ->
        Section(stringResource(R.string.info_about_book, book.name[lang])) { Body(intro) }
    }
}

@Composable
private fun ColumnScope.Fathers(
    commentary: ChapterCommentary?,
    notes: List<FatherNote>,
    quotes: List<FatherQuote>,
    lang: AppLanguage,
    go: (String, Int, Int) -> Unit,
) {
    val c = AgapeTheme.colors
    val container = LocalContainer.current
    val reading = commentary?.fathers?.get(lang).orEmpty()
    if (reading.isBlank() && notes.isEmpty() && quotes.isEmpty()) {
        Text(stringResource(R.string.info_no_fathers), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        return
    }
    if (reading.isNotBlank()) Section(stringResource(R.string.info_fathers_reading)) { Body(reading) }
    if (lang == AppLanguage.DUTCH && notes.isNotEmpty()) {
        Text(stringResource(R.string.english_only_note), style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
    }
    notes.forEach { n ->
        Column {
            Text("${n.father} · ${n.work}", style = MaterialTheme.typography.titleSmall, color = c.primary)
            Body(n.note, Modifier.padding(top = 4.dp))
        }
    }
    if (quotes.isNotEmpty()) {
        Section(stringResource(R.string.chapter_fathers)) {
            if (lang == AppLanguage.DUTCH) {
                Text(stringResource(R.string.info_quotes_english), style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
            }
            quotes.forEach { q ->
                Column(Modifier.fillMaxWidth().clip(cardShape).clickable { go(q.book, q.chapter, q.verse) }.padding(vertical = 8.dp)) {
                    Text(
                        "${q.father} · ${container.passageLabel(Passage(q.book, q.chapter, q.verse), lang)}",
                        style = MaterialTheme.typography.titleSmall, color = c.primary,
                    )
                    Text(q.text, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 8, modifier = Modifier.padding(top = 4.dp))
                    if (q.source.isNotBlank()) Text(q.source, style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.References(refs: List<RefPreview>, lang: AppLanguage, go: (String, Int, Int) -> Unit) {
    val c = AgapeTheme.colors
    val container = LocalContainer.current
    if (refs.isEmpty()) {
        Text(stringResource(R.string.info_no_refs), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        return
    }
    refs.forEach { r ->
        Column(
            Modifier.fillMaxWidth().clip(cardShape).background(c.surfaceSunken.copy(alpha = 0.5f))
                .clickable { go(r.passage.book, r.passage.chapter, r.passage.startVerse ?: 0) }.padding(14.dp),
        ) {
            Text(container.passageLabel(r.passage, lang), style = MaterialTheme.typography.labelLarge, color = c.primary)
            Text(r.text, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Kicker(title)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = AgapeTheme.colors.ink, modifier = modifier)
}

@Composable
private fun Themes(themes: List<String>) {
    if (themes.isEmpty()) return
    Text(themes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = AgapeTheme.colors.accent)
}

/** Marks text that is still a first draft, so readers know a fuller version is coming. */
@Composable
private fun Provisional() {
    val c = AgapeTheme.colors
    Text(
        stringResource(R.string.info_provisional),
        style = MaterialTheme.typography.labelMedium, color = c.inkMuted,
        modifier = Modifier.clip(CircleShape).border(1.dp, c.line, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** The quiet close of a chapter: only the quiz, and the book quiz after the last chapter. */
@Composable
fun ChapterQuizRow(book: String, chapter: Int, lastOfBook: Book?, openQuiz: (String, String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val title = "${container.bookName(book, lang)} $chapter"
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        QuizChip(stringResource(R.string.chapter_quiz)) { openQuiz("chapter:$book.$chapter", title) }
        lastOfBook?.let { b -> QuizChip(stringResource(R.string.book_end_quiz)) { openQuiz("book:${b.id}", b.name[lang]) } }
    }
}

@Composable
private fun QuizChip(text: String, onClick: () -> Unit) {
    val c = AgapeTheme.colors
    Row(
        Modifier.clip(CircleShape).border(1.dp, c.line, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Quiz, null, tint = c.accent, modifier = Modifier.height(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.ink)
    }
}
