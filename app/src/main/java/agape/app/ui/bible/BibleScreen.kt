package agape.app.ui.bible

import agape.app.R
import agape.app.core.model.Testament
import agape.app.data.content.Book
import agape.app.data.content.ChurchSection
import agape.app.data.content.chapterCount
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.Divider
import agape.app.ui.components.Kicker
import agape.app.ui.components.ListRow
import agape.app.ui.components.Pill
import agape.app.ui.components.TabHeader
import agape.app.ui.components.cardShape
import agape.app.ui.onboarding.traditionName
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private enum class BibleTab { BOOKS, CHURCH }

@Composable
fun BibleScreen(
    openChapter: (String, Int) -> Unit,
    openArticle: (String) -> Unit,
    openCouncils: () -> Unit,
    openFathers: () -> Unit,
    openLeaders: () -> Unit,
    openPlans: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(BibleTab.BOOKS) }
    var testament by rememberSaveable { mutableStateOf(Testament.OT) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val books = remember(tradition, lang) { container.content.canon(tradition, lang) }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TabHeader(kicker = stringResource(traditionName(tradition)), title = stringResource(R.string.tab_bible))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.bible_books), tab == BibleTab.BOOKS, { tab = BibleTab.BOOKS })
                Pill(stringResource(R.string.bible_church), tab == BibleTab.CHURCH, { tab = BibleTab.CHURCH })
            }
        }
        when (tab) {
            BibleTab.BOOKS -> {
                item {
                    AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onClick = openPlans, tonal = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.plans_title), style = MaterialTheme.typography.titleMedium, color = AgapeTheme.colors.ink)
                                Text(stringResource(R.string.bible_plans_hint), style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.inkMuted)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AgapeTheme.colors.inkMuted)
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(R.string.bible_ot), testament == Testament.OT, { testament = Testament.OT })
                        Pill(stringResource(R.string.bible_nt), testament == Testament.NT, { testament = Testament.NT })
                    }
                }
                items(books.filter { it.testament == testament }, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        expanded = expanded == book.id,
                        onToggle = { expanded = if (expanded == book.id) null else book.id },
                        onChapter = { openChapter(book.id, it) },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.bible_translation_note),
                        style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.inkFaint,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            BibleTab.CHURCH -> churchSection(this, tradition, lang, container, openArticle, openCouncils, openFathers, openLeaders)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookRow(book: Book, expanded: Boolean, onToggle: () -> Unit, onChapter: (Int) -> Unit) {
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val chapters = book.chapterCount(lang)
    Column {
        ListRow(
            title = book.name[lang],
            subtitle = if (book.deuterocanonical) stringResource(R.string.bible_deutero) else null,
            onClick = onToggle,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chapters.toString(), style = MaterialTheme.typography.labelMedium, color = c.inkFaint)
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = c.inkFaint)
                }
            },
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                book.intro?.get(lang)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 4, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chapterLabel = stringResource(R.string.bible_chapter)
                    (1..chapters).forEach { ch ->
                        Box(
                            Modifier.size(48.dp).clip(cardShape).background(c.surface).border(1.dp, c.line, cardShape)
                                .clickable { onChapter(ch) }.semantics { contentDescription = "$chapterLabel $ch" },
                            contentAlignment = Alignment.Center,
                        ) { Text(ch.toString(), style = MaterialTheme.typography.labelLarge, color = c.ink) }
                    }
                }
            }
        }
        Divider()
    }
}

private fun churchSection(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    tradition: agape.app.core.model.Tradition,
    lang: agape.app.core.model.AppLanguage,
    container: agape.app.AppContainer,
    openArticle: (String) -> Unit,
    openCouncils: () -> Unit,
    openFathers: () -> Unit,
    openLeaders: () -> Unit,
) = with(scope) {
    val content = container.content
    item {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChurchTile(
                title = stringResource(R.string.church_councils),
                subtitle = stringResource(R.string.church_councils_count, content.councils(tradition).size),
                symbol = "☩", modifier = Modifier.weight(1f), onClick = openCouncils,
            )
            ChurchTile(
                title = stringResource(R.string.church_fathers),
                subtitle = stringResource(R.string.church_fathers_count, content.fathers(tradition).size),
                symbol = "✠", modifier = Modifier.weight(1f), onClick = openFathers,
            )
        }
    }
    item {
        AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onClick = openLeaders) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.leaders_title), style = MaterialTheme.typography.titleLarge, color = AgapeTheme.colors.ink)
                    Text(content.leaders(tradition).joinToString(" · ") { it.current }.take(80), style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.inkMuted)
                }
                Chevron()
            }
        }
    }
    item { ChurchHeading(R.string.church_your_tradition) }
    items(content.articles(tradition, ChurchSection.TRADITION), key = { it.id }) { a ->
        AgapeCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), onClick = { openArticle(a.id) }) {
            Text(a.title[lang], style = MaterialTheme.typography.titleLarge, color = AgapeTheme.colors.ink)
            Spacer(Modifier.height(4.dp))
            Text(a.summary[lang], style = MaterialTheme.typography.bodyMedium, color = AgapeTheme.colors.inkMuted)
        }
    }
    item { ChurchHeading(R.string.church_history) }
    items(content.articles(tradition, ChurchSection.HISTORY), key = { it.id }) { a ->
        ListRow(a.title[lang], subtitle = a.summary[lang], onClick = { openArticle(a.id) }, trailing = { Chevron() })
        Divider()
    }
}

@Composable
private fun ChurchTile(title: String, subtitle: String, symbol: String, modifier: Modifier, onClick: () -> Unit) {
    val c = AgapeTheme.colors
    Column(
        modifier.clip(cardShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.primary, c.primary.copy(alpha = 0.78f))))
            .clickable(onClick = onClick).padding(18.dp),
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineMedium, color = c.accent)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.onPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.onPrimary.copy(alpha = 0.75f))
    }
}

@Composable
private fun ChurchHeading(title: Int) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp)) {
        Kicker(stringResource(title))
    }
}

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AgapeTheme.colors.inkFaint)
