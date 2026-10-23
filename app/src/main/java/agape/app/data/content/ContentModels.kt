package agape.app.data.content

import agape.app.core.model.AppLanguage
import agape.app.core.model.L10n
import agape.app.core.model.L10nLines
import agape.app.core.model.Testament
import agape.app.core.model.Tradition
import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: String,
    val order: Int,
    val testament: Testament,
    val deuterocanonical: Boolean,
    val traditions: List<Tradition>,
    val name: L10n,
    val intro: L10n? = null,
    /** Chapter count per language code ("en" = WEB, "nl" = Statenvertaling); 0 when the translation lacks the book. */
    val chapters: Map<String, Int>,
)

@Serializable
data class PrayerBook(
    val tradition: Tradition,
    val book: L10n,
    val intro: L10n,
    val hours: List<PrayerHour>,
    /** Services and prayers that are not tied to an hour (canons, communion prayers, the Rosary…). */
    val extras: List<PrayerHour> = emptyList(),
)

@Serializable
data class PrayerHour(
    val id: String,
    val name: L10n,
    val subtitle: L10n = L10n(),
    /** Local time "HH:mm" at which this hour is traditionally prayed. */
    val time: String,
    val theme: L10n = L10n(),
    val sections: List<PrayerSection>,
)

@Serializable
data class PrayerSection(
    val kind: Kind,
    val title: L10n? = null,
    val body: L10nLines? = null,
    /** For [Kind.scripture]: a passage such as "PSA 51" or "LUK 2:29-32", used when [body] has no text in the language. */
    val ref: String? = null,
    /** For [Kind.prayer]: id of a prayer in prayers_extra.json. */
    val prayerId: String? = null,
    /** For [Kind.counter]: how many times the phrase is prayed (Lord have mercy ×41, ×12, ×40). */
    val count: Int = 0,
    /** ISO day of week (1 = Monday) on which this section is prayed; null for every day. */
    val weekday: Int? = null,
) {
    @Suppress("EnumEntryName")
    enum class Kind { text, scripture, prayer, rubric, heading, counter }
}

@Serializable
data class Prayer(
    val id: String,
    val traditions: List<Tradition>,
    val title: L10n,
    val subtitle: L10n = L10n(),
    val body: L10nLines,
    val listed: Boolean = true,
)

@Serializable
data class QuizQuestion(val q: L10n, val options: List<L10n>, val answer: Int, val explanation: L10n? = null)

@Serializable
data class QuizFile(val passMark: Double, val quizzes: Map<String, List<QuizQuestion>>)

@Serializable
data class Council(
    val id: String,
    val number: Int,
    val year: String,
    val recognizedBy: List<Tradition>,
    val name: L10n,
    val convokedBy: L10n,
    val place: L10n = L10n(),
    val participants: String = "",
    val keyFigures: List<String> = emptyList(),
    /** The teaching the council answered. */
    val heresy: L10n = L10n(),
    /** One-paragraph summary of why the council met. */
    val summary: L10n,
    val background: L10nLines = L10nLines(),
    val events: L10nLines = L10nLines(),
    val decisions: List<L10n> = emptyList(),
    /** Short summaries of the most important canons. */
    val canons: List<L10n>,
    val outcome: L10nLines,
    val traditionNotes: Map<Tradition, L10n> = emptyMap(),
    val passages: List<String>,
    val quiz: List<QuizQuestion> = emptyList(),
    /** Catholic councils after the Great Schism, marked as such in the council timeline. */
    val afterSchism: Boolean = false,
)

/** Full texts from NPNF II/14 (English only). */
@Serializable
data class CouncilTexts(
    val documents: List<CouncilDocument> = emptyList(),
    val canons: List<CanonText> = emptyList(),
    val source: String = "",
    /** The documents are AGAPE's own provisional renderings of key passages, not an official translation. */
    val provisional: Boolean = false,
)

@Serializable
data class CouncilDocument(
    val title: String,
    val paragraphs: List<String>,
    val titleNl: String = "",
    val paragraphsNl: List<String> = emptyList(),
) {
    fun hasDutch() = paragraphsNl.isNotEmpty()
    fun title(lang: AppLanguage) = if (lang == AppLanguage.DUTCH && hasDutch()) titleNl else title
    fun paragraphs(lang: AppLanguage) = if (lang == AppLanguage.DUTCH && hasDutch()) paragraphsNl else paragraphs
}

@Serializable
data class CanonText(val number: Int, val text: String, val textNl: String = "") {
    fun text(lang: AppLanguage) = if (lang == AppLanguage.DUTCH && textNl.isNotBlank()) textNl else text
}

/** Hand-written commentary on one chapter, in both languages (`assets/content/commentary/<BOOK>.json`). */
@Serializable
data class ChapterCommentary(
    val chapter: Int,
    val title: L10n,
    val summary: L10n,
    /** Why the chapter matters: its place in the book and in the faith of the Church. */
    val meaning: L10n = L10n(),
    /** How the Fathers read the chapter, in our own words. */
    val fathers: L10n = L10n(),
    val themes: L10nLines = L10nLines(),
)

@Serializable
data class BookCommentary(val book: String, val chapters: List<ChapterCommentary>)

enum class PlanCategory { BEGINNER, OT_NT, FULL, COUNCILS }

@Serializable
data class PlanDefinition(
    val id: String,
    val category: PlanCategory,
    /** A locked plan only opens the next day after the previous day's quiz is passed. */
    val locked: Boolean,
    val title: L10n,
    val description: L10n,
    val generator: PlanGenerator,
)

@Serializable
data class PlanGenerator(
    val type: Type,
    val books: List<String> = emptyList(),
    val testament: Testament? = null,
    val days: Int = 0,
    val fixedDays: List<FixedDay> = emptyList(),
) {
    enum class Type { BOOKS, TESTAMENT, CANON, ORDERED, FIXED, COUNCILS }
}

@Serializable
data class FixedDay(val title: L10n, val readings: List<String>)

enum class ChurchSection { HISTORY, TRADITION }

@Serializable
data class ChurchArticle(
    val id: String,
    val section: ChurchSection,
    val traditions: List<Tradition>,
    val title: L10n,
    val summary: L10n,
    val body: L10nLines,
)

@Serializable
data class ChurchFather(
    val id: String,
    val name: L10n,
    val era: String,
    val place: L10n = L10n(),
    val feast: L10n = L10n(),
    val traditions: List<Tradition>,
    val group: String = "",
    val summary: L10n,
    val life: L10nLines = L10nLines(),
    val works: List<L10n> = emptyList(),
    val teaching: L10nLines = L10nLines(),
    val quote: L10n? = null,
    val quoteSource: String? = null,
    /** Name used in the verse-commentary table, when this Father has commentary there. */
    val dbName: String? = null,
)

@Serializable
data class ChurchFile(val articles: List<ChurchArticle>, val fathers: List<ChurchFather>, val leaders: List<ChurchLeader> = emptyList())

@Serializable
data class ChurchLeader(
    val tradition: Tradition,
    val church: L10n,
    val title: L10n,
    val current: String,
    val since: String,
    val wiki: String,
    val notable: List<NotableLeader> = emptyList(),
)

@Serializable
data class NotableLeader(val name: String, val years: String, val note: L10n)

@Serializable
data class SaintEntry(val month: Int, val day: Int, val rank: String, val title: L10n, val summary: L10n)

/** A bundled icon (public domain or CC0, from Wikimedia Commons) and the saint and feast names it belongs to. */
@Serializable
data class IconEntry(
    val key: String,
    val file: String,
    val match: List<String>,
    val title: String,
    val artist: String = "",
    val licence: String,
    val source: String,
    /** A general icon (Christ, the Mother of God), used only when no name of the day has an icon of its own. */
    val fallback: Boolean = false,
)

@Serializable
data class VerseOfDayFile(
    val common: List<String>,
    @kotlinx.serialization.SerialName("EASTERN_ORTHODOX") val easternOrthodox: List<String>,
    @kotlinx.serialization.SerialName("ORIENTAL_ORTHODOX") val orientalOrthodox: List<String>,
    @kotlinx.serialization.SerialName("CATHOLIC") val catholic: List<String>,
)
