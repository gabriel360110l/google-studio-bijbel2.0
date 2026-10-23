package agape.app.core.model

import kotlinx.serialization.Serializable

enum class Tradition {
    EASTERN_ORTHODOX, ORIENTAL_ORTHODOX, CATHOLIC
}

enum class VisualStyle {
    NAVY_CREAM, CLASSIC_SACRED, WARM_TERRACOTTA
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class AppLanguage(val tag: String) {
    ENGLISH("en"), DUTCH("nl");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { tag?.startsWith(it.tag) == true } ?: ENGLISH
    }
}

enum class Testament { OT, NT }

/** A piece of text in both app languages. */
@Serializable
data class L10n(val en: String = "", val nl: String = "") {
    operator fun get(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> en.ifEmpty { nl }
        AppLanguage.DUTCH -> nl.ifEmpty { en }
    }
}

/** Paragraphs or lines of text in both app languages. */
@Serializable
data class L10nLines(val en: List<String> = emptyList(), val nl: List<String> = emptyList()) {
    operator fun get(language: AppLanguage): List<String> = when (language) {
        AppLanguage.ENGLISH -> en.ifEmpty { nl }
        AppLanguage.DUTCH -> nl.ifEmpty { en }
    }
}

data class VerseRef(val book: String, val chapter: Int, val verse: Int) {
    val key: String get() = "$book.$chapter.$verse"
}

/**
 * A reading such as `JHN 3`, `JHN 3:16`, `JHN 3:16-21` or `ISA 52:13-53:12`.
 * [endChapter]/[endVerse] are inclusive; a null [startVerse] means the whole chapter.
 */
data class Passage(
    val book: String,
    val chapter: Int,
    val startVerse: Int? = null,
    val endChapter: Int = chapter,
    val endVerse: Int? = startVerse,
) {
    val isWholeChapter: Boolean get() = startVerse == null && endChapter == chapter

    override fun toString(): String = buildString {
        append(book).append(' ').append(chapter)
        if (startVerse != null) {
            append(':').append(startVerse)
            if (endChapter != chapter) append('-').append(endChapter).append(':').append(endVerse)
            else if (endVerse != null && endVerse != startVerse) append('-').append(endVerse)
        } else if (endChapter != chapter) {
            append('-').append(endChapter)
        }
    }

    companion object {
        private val pattern = Regex("""^([1-4A-Z][A-Z0-9]{2})\s+(\d+)(?::(\d+))?(?:-(\d+)(?::(\d+))?)?$""")

        fun parse(text: String): Passage? {
            val m = pattern.matchEntire(text.trim()) ?: return null
            val (book, ch, v1, a, b) = m.destructured
            val chapter = ch.toInt()
            val start = v1.toIntOrNull()
            return when {
                a.isEmpty() -> Passage(book, chapter, start, chapter, start)
                b.isNotEmpty() -> Passage(book, chapter, start ?: 1, a.toInt(), b.toInt())
                start != null -> Passage(book, chapter, start, chapter, a.toInt())
                else -> Passage(book, chapter, null, a.toInt(), null)
            }
        }
    }
}
