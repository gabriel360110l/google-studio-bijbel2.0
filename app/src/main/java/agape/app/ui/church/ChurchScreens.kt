package agape.app.ui.church

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.data.bible.FatherQuote
import agape.app.data.content.ChurchFather
import agape.app.data.content.Council
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Monogram
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.QuietButton
import agape.app.ui.components.SegmentedTabs
import agape.app.ui.components.cardShape
import agape.app.ui.onboarding.traditionName
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ArticleScreen(id: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val article = remember(id) { container.content.article(id) } ?: return
    val c = AgapeTheme.colors
    Column(Modifier.fillMaxSize()) {
        DetailTopBar("", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(article.title[lang], style = MaterialTheme.typography.displaySmall, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text(article.summary[lang], style = MaterialTheme.typography.titleMedium, color = c.inkMuted)
            Spacer(Modifier.height(18.dp))
            Ornament()
            Spacer(Modifier.height(18.dp))
            article.body[lang].forEach { Paragraph(it) }
        }
    }
}

@Composable
private fun Paragraph(text: String, italic: Boolean = false) {
    Text(
        text,
        style = AgapeTheme.type.scripture.copy(fontSize = AgapeTheme.type.scripture.fontSize * 0.9f, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal),
        color = AgapeTheme.colors.ink, modifier = Modifier.padding(bottom = 14.dp),
    )
}

// ------------------------------------------------------------------ Councils

/** All councils as a timeline; those the user's Church does not receive are shown, quieter, for context. */
@Composable
fun CouncilsScreen(onBack: () -> Unit, openCouncil: (String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val c = AgapeTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { DetailTopBar("", onBack) }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Kicker(stringResource(traditionName(tradition)), color = c.inkMuted)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.church_councils), style = MaterialTheme.typography.displaySmall, color = c.ink)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.councils_intro), style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
                Spacer(Modifier.height(20.dp))
            }
        }
        items(container.content.councils, key = { it.id }) { council ->
            val received = tradition in council.recognizedBy
            Row(Modifier.fillMaxWidth().clickable { openCouncil(council.id) }.padding(horizontal = 24.dp, vertical = 4.dp)) {
                // Timeline rail
                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(council.year.substringBefore("–"), style = MaterialTheme.typography.titleMedium, color = if (received) c.primary else c.inkFaint)
                    Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(if (received) c.accent else c.line))
                    Box(Modifier.width(1.dp).height(96.dp).background(c.line))
                }
                AgapeCard(Modifier.padding(start = 8.dp, bottom = 8.dp), tonal = !received, contentPadding = PaddingValues(16.dp)) {
                    Text(council.name[lang], style = MaterialTheme.typography.titleLarge, color = if (received) c.ink else c.inkMuted)
                    if (council.place[lang].isNotBlank()) Text(council.place[lang], style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
                    Spacer(Modifier.height(6.dp))
                    Text(council.heresy[lang], style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, maxLines = 3)
                    if (council.afterSchism) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.council_after_schism), style = MaterialTheme.typography.labelSmall, color = c.accent)
                    }
                    if (!received) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.council_not_received), style = MaterialTheme.typography.labelSmall, color = c.accent)
                    }
                }
            }
        }
    }
}

private enum class CouncilTab(val label: Int) { STORY(R.string.council_tab_story), DECISIONS(R.string.council_tab_decisions), CANONS(R.string.council_tab_canons), TEXTS(R.string.council_tab_texts) }

@Composable
fun CouncilScreen(id: String, onBack: () -> Unit, openPassage: (Passage) -> Unit, openQuiz: (String, String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val council = remember(id) { container.content.council(id) } ?: return
    val texts = remember(id) { container.content.councilTexts(id) }
    var tab by rememberSaveable { mutableStateOf(CouncilTab.STORY) }
    val c = AgapeTheme.colors

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { DetailTopBar("", onBack) }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Kicker(stringResource(R.string.council_number, council.number, council.year), color = c.accent)
                Spacer(Modifier.height(6.dp))
                Text(council.name[lang], style = MaterialTheme.typography.displaySmall, color = c.ink)
                Spacer(Modifier.height(10.dp))
                Fact(stringResource(R.string.council_place), council.place[lang])
                Fact(stringResource(R.string.council_convoked_label), council.convokedBy[lang])
                if (council.participants.isNotBlank()) Fact(stringResource(R.string.council_bishops), council.participants)
                if (council.keyFigures.isNotEmpty()) Fact(stringResource(R.string.council_figures), council.keyFigures.joinToString(", "))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    SegmentedTabs(CouncilTab.entries.map { stringResource(it.label) }, tab.ordinal, { tab = CouncilTab.entries[it] })
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (tab) {
                    CouncilTab.STORY -> {
                        Card(R.string.council_heresy) { Paragraph(council.heresy[lang], italic = true) }
                        Card(R.string.council_why) { council.background[lang].forEach { Paragraph(it) } }
                        Card(R.string.council_events) { council.events[lang].forEach { Paragraph(it) } }
                        Card(R.string.council_outcome) { council.outcome[lang].forEach { Paragraph(it) } }
                        council.traditionNotes[tradition]?.let { note ->
                            Card(R.string.council_your_church, tonal = true) { Paragraph(note[lang]) }
                        }
                        council.traditionNotes.filterKeys { it != tradition }.forEach { (t, note) ->
                            AgapeCard(tonal = true) {
                                Kicker(stringResource(traditionName(t)), color = c.inkMuted)
                                Spacer(Modifier.height(6.dp))
                                Text(note[lang], style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                            }
                        }
                        Text(
                            stringResource(R.string.council_recognized_by, council.recognizedBy.map { stringResource(traditionName(it)) }.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall, color = c.inkFaint,
                        )
                    }
                    CouncilTab.DECISIONS -> {
                        Card(R.string.council_decided) {
                            council.decisions.forEachIndexed { i, d -> Numbered(i + 1, d[lang]) }
                        }
                        Card(R.string.council_scripture) {
                            council.passages.mapNotNull { Passage.parse(it) }.forEach { p ->
                                QuietButton(container.passageLabel(p, lang), { openPassage(p) })
                            }
                        }
                        if (council.quiz.isNotEmpty()) PrimaryButton(stringResource(R.string.reader_take_quiz), { openQuiz("council:${council.id}", council.name[lang]) })
                    }
                    CouncilTab.CANONS -> {
                        Card(R.string.council_canons_summary) { council.canons.forEach { Numbered(null, it[lang]) } }
                        if (texts.canons.isEmpty()) {
                            // The bundled full canons cover the first councils; for the later ones the key decrees are under Texts.
                            val note = when {
                                council.number <= 8 -> R.string.council_no_canons
                                texts.documents.isNotEmpty() -> R.string.council_canons_in_texts
                                else -> R.string.council_texts_later
                            }
                            Text(stringResource(note), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                        } else {
                            Kicker(stringResource(R.string.council_canons_full, texts.canons.size), color = c.inkMuted)
                            if (texts.canons.any { it.textNl.isBlank() }) EnglishOnlyNote(lang)
                            texts.canons.forEach { canon -> ExpandableCanon(canon.number, canon.text(lang)) }
                        }
                    }
                    CouncilTab.TEXTS -> {
                        if (texts.documents.isEmpty()) {
                            Text(stringResource(R.string.council_texts_later), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                        }
                        if (texts.provisional) {
                            Text(stringResource(R.string.council_texts_provisional), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                            Spacer(Modifier.height(12.dp))
                        }
                        texts.documents.forEach { doc ->
                            AgapeCard {
                                Kicker(doc.title(lang))
                                Spacer(Modifier.height(10.dp))
                                if (!doc.hasDutch()) EnglishOnlyNote(lang)
                                doc.paragraphs(lang).forEach { Paragraph(it) }
                            }
                        }
                        if (texts.source.isNotBlank()) Text(texts.source, style = MaterialTheme.typography.labelSmall, color = c.inkFaint)
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AgapeTheme.colors.inkFaint, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AgapeTheme.colors.ink)
    }
}

@Composable
private fun Card(title: Int, tonal: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    AgapeCard(tonal = tonal) {
        Kicker(stringResource(title))
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Numbered(n: Int?, text: String) {
    val c = AgapeTheme.colors
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(n?.toString() ?: "✦", style = MaterialTheme.typography.titleMedium, color = c.accent, modifier = Modifier.width(28.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.ink)
    }
}

@Composable
private fun EnglishOnlyNote(lang: AppLanguage) {
    if (lang == AppLanguage.DUTCH) {
        Text(stringResource(R.string.english_only_note), style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.inkFaint)
    }
}

@Composable
private fun ExpandableCanon(number: Int, text: String) {
    var open by rememberSaveable(number) { mutableStateOf(false) }
    val c = AgapeTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(cardShape).background(c.surface).clickable { open = !open }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.council_canon_n, number), style = MaterialTheme.typography.titleMedium, color = c.primary, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = c.inkFaint)
        }
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = if (open) Int.MAX_VALUE else 3)
    }
}

/** The compact council summary used inside the council reading plan. */
@Composable
fun CouncilBody(council: Council, openPassage: (Passage) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker(stringResource(R.string.council_number, council.number, council.year))
        Text(council.name[lang], style = MaterialTheme.typography.headlineMedium, color = c.ink)
        Card(R.string.council_scripture) {
            council.passages.mapNotNull { Passage.parse(it) }.forEach { p -> QuietButton(container.passageLabel(p, lang), { openPassage(p) }) }
        }
        Card(R.string.council_why) { council.background[lang].forEach { Paragraph(it) } }
        Card(R.string.council_decided) { council.decisions.forEachIndexed { i, d -> Numbered(i + 1, d[lang]) } }
        Card(R.string.council_canons_summary) { council.canons.forEach { Numbered(null, it[lang]) } }
        Card(R.string.council_outcome) { council.outcome[lang].forEach { Paragraph(it) } }
    }
}

// ------------------------------------------------------------------ Church Fathers

private val groupOrder = listOf("APOSTOLIC", "EARLY", "NICENE", "DESERT", "CAPPADOCIAN", "ALEXANDRIAN", "ANTIOCHENE", "SYRIAC", "LATIN", "BYZANTINE")

private fun groupName(group: String) = when (group) {
    "APOSTOLIC" -> R.string.fathers_apostolic
    "EARLY" -> R.string.fathers_early
    "NICENE" -> R.string.fathers_nicene
    "DESERT" -> R.string.fathers_desert
    "CAPPADOCIAN" -> R.string.fathers_cappadocian
    "ALEXANDRIAN" -> R.string.fathers_alexandrian
    "ANTIOCHENE" -> R.string.fathers_antiochene
    "SYRIAC" -> R.string.fathers_syriac
    "LATIN" -> R.string.fathers_latin
    else -> R.string.fathers_byzantine
}

@Composable
fun FathersScreen(onBack: () -> Unit, openFather: (String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val fathers = remember(tradition) { container.content.fathers(tradition).groupBy { it.group } }
    val c = AgapeTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { DetailTopBar("", onBack) }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Kicker(stringResource(traditionName(tradition)), color = c.inkMuted)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.church_fathers), style = MaterialTheme.typography.displaySmall, color = c.ink)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.fathers_intro), style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
            }
        }
        groupOrder.filter { it in fathers }.forEach { group ->
            item(key = "g$group") { Kicker(stringResource(groupName(group)), Modifier.padding(start = 24.dp, top = 26.dp, bottom = 8.dp)) }
            items(fathers.getValue(group), key = { it.id }) { f ->
                AgapeCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), onClick = { openFather(f.id) }, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Monogram(f.name[lang])
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name[lang], style = MaterialTheme.typography.titleLarge, color = c.ink)
                            Text(f.era, style = MaterialTheme.typography.labelMedium, color = c.accent)
                            Text(f.summary[lang], style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FatherScreen(id: String, onBack: () -> Unit, openVerse: (String, Int, Int) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val father: ChurchFather = remember(id) { container.content.father(id) } ?: return
    val quotes by produceState(emptyList<FatherQuote>(), father.dbName) {
        value = father.dbName?.let { container.bible.quotesBy(it) }.orEmpty()
    }
    var shown by remember { mutableIntStateOf(6) }
    val c = AgapeTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { DetailTopBar("", onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Monogram(father.name[lang], size = 84.dp)
                Spacer(Modifier.height(14.dp))
                Text(father.name[lang], style = MaterialTheme.typography.displaySmall, color = c.ink, textAlign = TextAlign.Center)
                Text(father.era, style = MaterialTheme.typography.titleMedium, color = c.accent)
                Spacer(Modifier.height(4.dp))
                Text(father.place[lang], style = MaterialTheme.typography.bodySmall, color = c.inkMuted, textAlign = TextAlign.Center)
                if (father.feast[lang].isNotBlank()) Text(stringResource(R.string.father_feast, father.feast[lang]), style = MaterialTheme.typography.bodySmall, color = c.inkFaint, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                father.quote?.let { q ->
                    AgapeCard(tonal = true) {
                        Text("“${q[lang]}”", style = AgapeTheme.type.scripture, color = c.ink)
                        father.quoteSource?.let { Text("— $it", style = MaterialTheme.typography.labelMedium, color = c.inkMuted, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
                Card(R.string.father_life) { father.life[lang].forEach { Paragraph(it) } }
                Card(R.string.father_teaching) { father.teaching[lang].forEach { Paragraph(it) } }
                if (father.works.isNotEmpty()) Card(R.string.father_works) { father.works.forEach { Numbered(null, it[lang]) } }
                Text(
                    stringResource(R.string.council_recognized_by, father.traditions.map { stringResource(traditionName(it)) }.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall, color = c.inkFaint,
                )
                if (quotes.isNotEmpty()) {
                    Kicker(stringResource(R.string.father_on_scripture), Modifier.padding(top = 10.dp))
                    EnglishOnlyNote(lang)
                }
            }
        }
        items(quotes.take(shown), key = { "${it.book}${it.chapter}.${it.verse}${it.text.hashCode()}" }) { q ->
            AgapeCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), onClick = { openVerse(q.book, q.chapter, q.verse) }) {
                Text(container.passageLabel(Passage(q.book, q.chapter, q.verse, q.chapter, q.verseEnd), lang), style = MaterialTheme.typography.labelLarge, color = c.primary)
                Spacer(Modifier.height(6.dp))
                Text(q.text, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 8)
                if (q.source.isNotBlank()) Text(q.source, style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (quotes.size > shown) item {
            QuietButton(stringResource(R.string.show_more), { shown += 10 }, Modifier.padding(horizontal = 20.dp))
        }
    }
}
