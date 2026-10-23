package agape.app.data.content

import agape.app.core.model.AppLanguage
import agape.app.core.model.Tradition
import agape.app.data.saints.UnifiedSaints
import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** Bundled, offline content from `assets/content`. Everything is parsed once and kept in memory. */
class ContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> load(name: String): T =
        context.assets.open("content/$name").bufferedReader().use { json.decodeFromString<T>(it.readText()) }

    val books: List<Book> by lazy { load<List<Book>>("books.json").sortedBy { it.order } }
    private val booksById by lazy { books.associateBy { it.id } }

    private val prayerBooks: Map<Tradition, PrayerBook> by lazy {
        listOf("prayers_eo.json", "prayers_oo.json", "prayers_ca.json").map { load<PrayerBook>(it) }.associateBy { it.tradition }
    }
    private val prayers: List<Prayer> by lazy { load("prayers_extra.json") }
    val councils: List<Council> by lazy { load<List<Council>>("councils.json").sortedBy { it.number } }
    val plans: List<PlanDefinition> by lazy { load("plans.json") }
    private val quizFile: QuizFile by lazy { load("quizzes.json") }
    private val church: ChurchFile by lazy { load("church.json") }
    private val councilTexts: Map<String, CouncilTexts> by lazy { load("council_texts.json") }
    private val saintsEo: List<SaintEntry> by lazy { load("saints_eo.json") }
    private val verseOfDay: VerseOfDayFile by lazy { load("verse_of_day.json") }
    private val verseNotes: JsonObject by lazy {
        context.assets.open("content/verse_notes.json").bufferedReader().use { json.parseToJsonElement(it.readText()).jsonObject }
    }

    fun book(id: String): Book? = booksById[id]

    private val commentaries = java.util.concurrent.ConcurrentHashMap<String, Map<Int, ChapterCommentary>>()

    /** Written commentary for a chapter, or null where only the provisional guide in the Bible database exists yet. */
    fun commentary(book: String, chapter: Int): ChapterCommentary? =
        commentaries.getOrPut(book) {
            runCatching { load<BookCommentary>("commentary/$book.json").chapters.associateBy { it.chapter } }.getOrDefault(emptyMap())
        }[chapter]

    /** Books of [tradition]'s canon that exist in the translation used for [language]. */
    fun canon(tradition: Tradition, language: AppLanguage): List<Book> =
        books.filter { tradition in it.traditions && it.chapterCount(language) > 0 }

    fun prayerBook(tradition: Tradition): PrayerBook = prayerBooks.getValue(tradition)
    fun prayerBookHourIds(): Set<String> = prayerBooks.values.flatMap { b -> b.hours.map { it.id } }.toSet()

    /** An hour or an extra service of [tradition]'s prayer book. */
    fun prayerEntry(tradition: Tradition, id: String): PrayerHour? =
        prayerBook(tradition).let { b -> b.hours.firstOrNull { it.id == id } ?: b.extras.firstOrNull { it.id == id } }
    fun prayer(id: String): Prayer? = prayers.firstOrNull { it.id == id }
    fun listedPrayers(tradition: Tradition): List<Prayer> = prayers.filter { it.listed && tradition in it.traditions }

    val quizPassMark: Double get() = quizFile.passMark
    fun quiz(key: String): List<QuizQuestion>? =
        quizFile.quizzes[key] ?: key.removePrefix("council:").takeIf { key.startsWith("council:") }
            ?.let { id -> councils.firstOrNull { it.id == id }?.quiz?.takeIf { q -> q.isNotEmpty() } }

    fun councils(tradition: Tradition): List<Council> = councils.filter { tradition in it.recognizedBy }
    fun council(id: String): Council? = councils.firstOrNull { it.id == id }
    fun councilTexts(id: String): CouncilTexts = councilTexts[id] ?: CouncilTexts()
    fun plan(id: String): PlanDefinition? = plans.firstOrNull { it.id == id }

    fun articles(tradition: Tradition, section: ChurchSection): List<ChurchArticle> =
        church.articles.filter { it.section == section && tradition in it.traditions }
    fun article(id: String): ChurchArticle? = church.articles.firstOrNull { it.id == id }
    fun fathers(tradition: Tradition): List<ChurchFather> = church.fathers.filter { tradition in it.traditions }
    fun father(id: String): ChurchFather? = church.fathers.firstOrNull { it.id == id }
    fun leaders(tradition: Tradition): List<ChurchLeader> = church.leaders.filter { it.tradition == tradition }

    /** The Byzantine menologion entry for a civil date (new calendar). */
    fun saintEo(date: LocalDate): SaintEntry? = saintsEo.firstOrNull { it.month == date.monthValue && it.day == date.dayOfMonth }

    /** All commemorations of the three traditions in one schema (tools/schema/README.md), for the calendar and saint screens. */
    val saintsUnified: UnifiedSaints by lazy { load("saints_unified.json") }

    val icons: List<IconEntry> by lazy { load("icons.json") }
    private val iconPatterns by lazy { icons.map { icon -> icon to icon.match.map { Regex(it, RegexOption.IGNORE_CASE) } } }

    /**
     * The icon for a day's commemorations, given their English names in order: the first name with an icon of its
     * own wins; the general icons of Christ and the Mother of God are tried only after that.
     */
    fun iconFor(names: List<String>): IconEntry? {
        fun match(fallback: Boolean) = names.firstNotNullOfOrNull { name ->
            iconPatterns.firstOrNull { (icon, patterns) -> icon.fallback == fallback && patterns.any { it.containsMatchIn(name) } }?.first
        }
        return match(fallback = false) ?: match(fallback = true)
    }

    /** A stable verse for the day, mixing shared and tradition-specific verses. */
    fun verseOfDay(tradition: Tradition, date: LocalDate): String {
        val own = when (tradition) {
            Tradition.EASTERN_ORTHODOX -> verseOfDay.easternOrthodox
            Tradition.ORIENTAL_ORTHODOX -> verseOfDay.orientalOrthodox
            Tradition.CATHOLIC -> verseOfDay.catholic
        }
        val pool = verseOfDay.common + own
        return pool[Math.floorMod(date.toEpochDay(), pool.size.toLong()).toInt()]
    }

    /**
     * Per-verse notes in [language]: the shared note and, if present, a tradition-specific one. There is no fallback to
     * the other language, because where WEB and the Statenvertaling number a verse differently (the Psalm titles, a few
     * chapter breaks) each language has its own key and a note must not appear on the wrong verse.
     */
    fun verseNotes(key: String, tradition: Tradition, language: AppLanguage): List<String> {
        val entry = verseNotes[key] as? JsonObject ?: return emptyList()
        return listOf("all", tradition.name).mapNotNull { k ->
            (entry[k] as? JsonObject)?.get(language.tag)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }
    }
}

fun Book.chapterCount(language: AppLanguage): Int = chapters[language.tag] ?: 0
