package agape.app.ui.search

import agape.app.R
import agape.app.core.model.AppLanguage
import agape.app.core.model.L10n
import agape.app.core.model.Passage
import agape.app.core.model.Tradition
import agape.app.data.content.chapterCount
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.Kicker
import agape.app.ui.components.ListRow
import agape.app.ui.components.Pill
import agape.app.ui.components.TabHeader
import agape.app.ui.components.cardShape
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a result leads. */
sealed interface SearchTarget {
    data class Verse(val book: String, val chapter: Int, val verse: Int) : SearchTarget
    data class Hour(val id: String) : SearchTarget
    data class Prayer(val id: String) : SearchTarget
    data class Council(val id: String) : SearchTarget
    data class Father(val id: String) : SearchTarget
    data class Article(val id: String) : SearchTarget
    data class Plan(val id: String) : SearchTarget
    data object Calendar : SearchTarget
}

private enum class Kind(val label: Int) {
    ALL(R.string.search_all), BIBLE(R.string.search_bible), PRAYER(R.string.search_prayers),
    CHURCH(R.string.search_church), FATHERS(R.string.search_fathers), SAINTS(R.string.search_saints), PLANS(R.string.search_plans),
}

private data class Hit(val kind: Kind, val title: String, val subtitle: String, val target: SearchTarget)

@Composable
fun SearchScreen(open: (SearchTarget) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(Kind.ALL) }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val recent by container.settings.recentSearches.collectAsStateWithLifecycle(emptyList())
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val c = AgapeTheme.colors

    LaunchedEffect(query, lang, tradition) {
        val q = query.trim()
        if (q.length < 2) { hits = emptyList(); return@LaunchedEffect }
        searching = true
        try {
            delay(250) // let the user finish typing
            hits = search(container, q, lang, tradition)
        } finally {
            searching = false // also when a newer query cancels this one
        }
    }

    fun go(target: SearchTarget) {
        if (query.isNotBlank()) scope.launch { container.settings.addRecentSearch(query.trim()) }
        keyboard?.hide()
        open(target)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { TabHeader(kicker = "", title = stringResource(R.string.tab_search)) }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = c.inkMuted) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.search_clear), tint = c.inkMuted) } },
                singleLine = true, shape = cardShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.primary, unfocusedBorderColor = c.line,
                    focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, cursorColor = c.primary,
                ),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kind.entries.forEach { k -> Pill(stringResource(k.label), kind == k, { kind = k }) }
            }
            if (searching) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = c.accent, trackColor = c.line,
                )
            }
        }
        if (query.trim().length < 2) {
            if (recent.isNotEmpty()) {
                item { Kicker(stringResource(R.string.search_recent), Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
                items(recent, key = { "r$it" }) { r ->
                    ListRow(r, onClick = { query = r }, leading = { Icon(Icons.Outlined.History, null, tint = c.inkFaint) })
                }
            } else item {
                Text(stringResource(R.string.search_empty_hint), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(24.dp))
            }
        } else {
            val shown = hits.filter { kind == Kind.ALL || it.kind == kind }
            if (shown.isEmpty() && !searching) item {
                Text(stringResource(R.string.search_none, query.trim()), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(24.dp))
            }
            shown.groupBy { it.kind }.forEach { (k, list) ->
                item(key = "h$k") { Kicker(stringResource(k.label), Modifier.padding(start = 20.dp, top = 16.dp, bottom = 2.dp)) }
                items(list.take(if (kind == Kind.ALL) 6 else 80), key = { "${k}${it.target}${it.title}${it.subtitle.hashCode()}" }) { h ->
                    ListRow(h.title, subtitle = h.subtitle, onClick = { go(h.target) })
                }
            }
        }
    }
}

private fun L10n.has(q: String, lang: AppLanguage) = this[lang].contains(q, ignoreCase = true)

/** Searches everything the app holds, offline. */
private suspend fun search(container: agape.app.AppContainer, q: String, lang: AppLanguage, tradition: Tradition): List<Hit> {
    val content = container.content
    val out = mutableListOf<Hit>()
    val canon = content.canon(tradition, lang)

    // "John 3:16", "joh 3", "Genesis 1" → straight to the passage.
    Regex("""^(\d?\s?\p{L}[\p{L} ]*?)\s*(\d+)(?:[:.,](\d+))?$""").matchEntire(q)?.let { m ->
        val name = m.groupValues[1].trim().lowercase()
        val book = canon.firstOrNull { b -> b.name[lang].lowercase().startsWith(name) || b.name.en.lowercase().startsWith(name) || b.id.lowercase() == name }
        val ch = m.groupValues[2].toInt()
        if (book != null && ch in 1..book.chapterCount(lang)) {
            val v = m.groupValues[3].toIntOrNull()
            out += Hit(Kind.BIBLE, container.passageLabel(Passage(book.id, ch, v), lang), "", SearchTarget.Verse(book.id, ch, v ?: 0))
        }
    }
    canon.filter { it.name[lang].contains(q, true) || it.name.en.contains(q, true) }.forEach {
        out += Hit(Kind.BIBLE, it.name[lang], it.intro?.get(lang)?.take(120).orEmpty(), SearchTarget.Verse(it.id, 1, 0))
    }
    if (q.length >= 3) container.bible.searchVerses(q, lang).forEach { v ->
        out += Hit(Kind.BIBLE, container.passageLabel(Passage(v.book, v.chapter, v.verse), lang), v.text, SearchTarget.Verse(v.book, v.chapter, v.verse))
    }

    val book = content.prayerBook(tradition)
    (book.hours + book.extras).forEach { h ->
        if (h.name.has(q, lang) || h.subtitle.has(q, lang) || h.theme.has(q, lang) || h.sections.any { s -> s.title?.has(q, lang) == true }) {
            out += Hit(Kind.PRAYER, h.name[lang], h.subtitle[lang], SearchTarget.Hour(h.id))
        }
    }
    content.listedPrayers(tradition).filter { p -> p.title.has(q, lang) || p.body[lang].any { it.contains(q, true) } }.forEach {
        out += Hit(Kind.PRAYER, it.title[lang], it.subtitle[lang], SearchTarget.Prayer(it.id))
    }

    content.councils.filter { c -> c.name.has(q, lang) || c.heresy.has(q, lang) || c.year.contains(q) || c.keyFigures.any { it.contains(q, true) } || c.summary.has(q, lang) }.forEach {
        out += Hit(Kind.CHURCH, it.name[lang], "${it.year} · ${it.heresy[lang]}", SearchTarget.Council(it.id))
    }
    content.fathers(tradition).filter { f -> f.name.has(q, lang) || f.summary.has(q, lang) || f.place.has(q, lang) }.forEach {
        out += Hit(Kind.CHURCH, it.name[lang], "${it.era} · ${it.summary[lang]}", SearchTarget.Father(it.id))
    }
    (content.articles(tradition, agape.app.data.content.ChurchSection.TRADITION) + content.articles(tradition, agape.app.data.content.ChurchSection.HISTORY))
        .filter { a -> a.title.has(q, lang) || a.body[lang].any { it.contains(q, true) } }
        .forEach { out += Hit(Kind.CHURCH, it.title[lang], it.summary[lang], SearchTarget.Article(it.id)) }

    if (q.length >= 3) container.bible.searchQuotes(q).forEach { f ->
        out += Hit(Kind.FATHERS, "${f.father} · ${container.passageLabel(Passage(f.book, f.chapter, f.verse), lang)}", f.text, SearchTarget.Verse(f.book, f.chapter, f.verse))
    }

    val traditionId = agape.app.data.saints.SaintCalendar.id(tradition)
    content.saintsUnified.commemorations.filter { it.tradition == traditionId && it.name.has(q, lang) }.forEach { s ->
        val date = if (s.date.calendar == "coptic") "${s.date.day} ${agape.app.data.calendar.CopticDate(0, s.date.month, 1).monthName(lang == AppLanguage.DUTCH)}"
            else "%02d-%02d".format(s.date.day, s.date.month)
        out += Hit(Kind.SAINTS, s.name[lang], date, SearchTarget.Calendar)
    }

    content.plans.filter { p -> p.title.has(q, lang) || p.description.has(q, lang) }.forEach {
        out += Hit(Kind.PLANS, it.title[lang], it.description[lang], SearchTarget.Plan(it.id))
    }
    return out
}
