package agape.app.ui

import agape.app.AppContainer
import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.core.model.Tradition
import agape.app.core.prefs.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }
val LocalLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }
val LocalSettings = staticCompositionLocalOf { Settings() }

/** The chosen tradition; screens after onboarding can rely on it being set. */
val tradition: Tradition
    @Composable @ReadOnlyComposable get() = LocalSettings.current.tradition ?: Tradition.EASTERN_ORTHODOX

fun AppContainer.bookName(id: String, language: AppLanguage): String = content.book(id)?.name?.get(language) ?: id

/** "John 3:16-21", "Genesis 1–3", "Isaiah 52:13–53:12". */
fun AppContainer.passageLabel(p: Passage, language: AppLanguage): String {
    val name = bookName(p.book, language)
    return when {
        p.startVerse == null && p.endChapter == p.chapter -> "$name ${p.chapter}"
        p.startVerse == null -> "$name ${p.chapter}–${p.endChapter}"
        p.endChapter != p.chapter -> "$name ${p.chapter}:${p.startVerse}–${p.endChapter}:${p.endVerse}"
        p.endVerse != null && p.endVerse != p.startVerse -> "$name ${p.chapter}:${p.startVerse}–${p.endVerse}"
        else -> "$name ${p.chapter}:${p.startVerse}"
    }
}
