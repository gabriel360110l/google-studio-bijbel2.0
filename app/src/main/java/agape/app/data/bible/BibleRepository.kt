package agape.app.data.bible

import agape.app.core.model.AppLanguage
import agape.app.core.model.Passage
import agape.app.core.model.VerseRef
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.GZIPInputStream

data class Verse(val book: String, val chapter: Int, val verse: Int, val text: String) {
    val ref: VerseRef get() = VerseRef(book, chapter, verse)
}

data class CrossRef(val book: String, val chapter: Int, val verse: Int, val verseEnd: Int)

data class ChapterGuide(val title: String, val summary: String, val context: String, val themes: List<String>, val related: List<Passage>)

data class FatherQuote(val book: String, val chapter: Int, val verse: Int, val verseEnd: Int, val father: String, val year: Int, val source: String, val text: String)

data class FatherNote(val father: String, val work: String, val date: String, val verseFrom: Int, val verseTo: Int, val note: String, val source: String)

private val footnoteRegex = Regex("""\\f\b.*?\\f\*|\\fe\b.*?\\fe\*|\\x\b.*?\\x\*""")
private val attrRegex = Regex("""\|[^\\]*?(?=\\\+?[a-zA-Z0-9*])|\|[^\\*\s]+(?:\s+[^\\*]+)?""")
private val tagRegex = Regex("""\\[+]?[a-zA-Z0-9]+(?:\*|\\\*)?|\\[*+]""")
private val punctRegex = Regex("""\s+([,.:;?!'\"\)\]])""")
private val openPunctRegex = Regex("""([(\[])\s+""")
private val whitespaceRegex = Regex("""\s+""")

fun cleanVerseText(raw: String): String {
    if (!raw.contains('\\') && !raw.contains('|')) return raw
    var s = raw
    s = footnoteRegex.replace(s, "")
    s = attrRegex.replace(s, "")
    s = tagRegex.replace(s, " ")
    s = punctRegex.replace(s, "$1")
    s = openPunctRegex.replace(s, "$1")
    return whitespaceRegex.replace(s, " ").trim()
}

/** The bundled, read-only Bible database. Translation follows the app language: WEB for English, Statenvertaling for Dutch. */
class BibleRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var db: SQLiteDatabase? = null

    private fun database(): SQLiteDatabase = db ?: synchronized(this) {
        db ?: open().also { db = it }
    }

    /** SQLite cannot open files inside the APK, so the database is extracted once per content version. */
    private fun open(): SQLiteDatabase {
        val target = File(context.noBackupFilesDir, "bible-v$DB_VERSION.db")
        if (!isValidDatabase(target)) {
            val tmp = File(target.path + ".tmp")
            try {
                val assetFiles = context.assets.list("bible").orEmpty()
                if ("bible.db.gz" in assetFiles) {
                    GZIPInputStream(context.assets.open("bible/bible.db.gz")).use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                } else {
                    context.assets.open("bible/bible.db").use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                }
                if (tmp.length() > 1_000_000L) {
                    context.noBackupFilesDir.listFiles { f -> f.name.startsWith("bible-v") && f != tmp }?.forEach { it.delete() }
                    tmp.renameTo(target)
                } else {
                    tmp.delete()
                }
            } catch (e: Exception) {
                tmp.delete()
                if (!target.exists()) throw e
            }
        }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun isValidDatabase(file: File): Boolean {
        if (!file.exists() || file.length() < 5_000_000L) return false
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { checkDb ->
                checkDb.rawQuery("SELECT COUNT(*) FROM verses", null).use { c ->
                    c.moveToFirst() && c.getInt(0) > 50_000
                }
            }
        }.getOrDefault(false)
    }

    private fun translation(language: AppLanguage) = if (language == AppLanguage.DUTCH) "sv" else "web"

    suspend fun chapter(book: String, chapter: Int, language: AppLanguage): List<Verse> = withContext(Dispatchers.IO) {
        runCatching {
            database().rawQuery(
                "SELECT verse, text FROM verses WHERE translation = ? AND book = ? AND chapter = ? ORDER BY verse",
                arrayOf(translation(language), book, chapter.toString()),
            ).use { c -> buildList { while (c.moveToNext()) add(Verse(book, chapter, c.getInt(0), cleanVerseText(c.getString(1)))) } }
        }.getOrDefault(emptyList())
    }

    suspend fun passage(passage: Passage, language: AppLanguage): List<Verse> = withContext(Dispatchers.IO) {
        runCatching {
            val start = passage.startVerse ?: 1
            val end = passage.endVerse ?: Int.MAX_VALUE
            database().rawQuery(
                """SELECT chapter, verse, text FROM verses WHERE translation = ? AND book = ?
                   AND (chapter * 1000 + verse) BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER) ORDER BY chapter, verse""",
                arrayOf(
                    translation(language), passage.book,
                    (passage.chapter * 1000L + start).toString(),
                    (passage.endChapter * 1000L + minOf(end, 999)).toString(),
                ),
            ).use { c -> buildList { while (c.moveToNext()) add(Verse(passage.book, c.getInt(0), c.getInt(1), cleanVerseText(c.getString(2)))) } }
        }.getOrDefault(emptyList())
    }

    suspend fun verse(ref: VerseRef, language: AppLanguage): Verse? =
        passage(Passage(ref.book, ref.chapter, ref.verse), language).firstOrNull()

    suspend fun crossRefs(ref: VerseRef, limit: Int = 12): List<CrossRef> = withContext(Dispatchers.IO) {
        database().rawQuery(
            "SELECT to_book, to_chapter, to_verse, to_verse_end FROM cross_refs WHERE book = ? AND chapter = ? AND verse = ? ORDER BY votes DESC LIMIT ?",
            arrayOf(ref.book, ref.chapter.toString(), ref.verse.toString(), limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(CrossRef(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3))) } }
    }

    /** The English chapter guide. The legacy Dutch guides are machine-made and are not shown until they are rewritten. */
    suspend fun chapterGuide(book: String, chapter: Int): ChapterGuide? = withContext(Dispatchers.IO) {
        database().rawQuery(
            "SELECT title, summary, context, themes, related FROM chapter_guides WHERE book = ? AND chapter = ? AND lang = 'en' LIMIT 1",
            arrayOf(book, chapter.toString()),
        ).use { c ->
            if (!c.moveToFirst()) null else ChapterGuide(
                title = c.getString(0),
                summary = c.getString(1),
                context = c.getString(2),
                themes = runCatching { json.decodeFromString<List<String>>(c.getString(3)) }.getOrDefault(emptyList()),
                related = runCatching { json.decodeFromString<List<RelatedRow>>(c.getString(4)) }.getOrDefault(emptyList())
                    .mapNotNull { r -> r.chapter?.let { Passage(r.book, it, r.verse) } },
            )
        }
    }

    /**
     * The legacy Father notes that are real summaries of a Father's reading. Most legacy notes are generated filler
     * ("the note that follows is a paraphrase…"), and all Dutch ones are; those are left out, and the English note
     * is shown in both languages.
     */
    suspend fun fatherNotes(book: String, chapter: Int): List<FatherNote> = withContext(Dispatchers.IO) {
        database().rawQuery(
            "SELECT father, work, date, verse_from, verse_to, note_en, source FROM father_notes WHERE book = ? AND chapter = ? " +
                "AND note_en NOT LIKE '%fabricated%' AND note_en NOT LIKE '%named above%' AND note_en NOT LIKE '%burden is%' " +
                "AND note_en NOT LIKE '%invented for the app%' AND note_en NOT LIKE '%names that surface%' ORDER BY sort",
            arrayOf(book, chapter.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(FatherNote(c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4), c.getString(5), c.getString(6)))
                }
            }
        }
    }

    /** Every verse of a book, for the book quiz. */
    suspend fun book(book: String, language: AppLanguage): List<Verse> = withContext(Dispatchers.IO) {
        database().rawQuery(
            "SELECT chapter, verse, text FROM verses WHERE translation = ? AND book = ? ORDER BY chapter, verse",
            arrayOf(translation(language), book),
        ).use { c -> buildList { while (c.moveToNext()) add(Verse(book, c.getInt(0), c.getInt(1), cleanVerseText(c.getString(2)))) } }
    }

    /** The passages most often linked from anywhere in a chapter, outside the chapter itself. */
    suspend fun chapterCrossRefs(book: String, chapter: Int, limit: Int = 10): List<CrossRef> = withContext(Dispatchers.IO) {
        database().rawQuery(
            """SELECT to_book, to_chapter, to_verse, MAX(to_verse_end), SUM(votes) AS v FROM cross_refs
               WHERE book = ? AND chapter = ? AND NOT (to_book = ? AND to_chapter = ?)
               GROUP BY to_book, to_chapter, to_verse ORDER BY v DESC LIMIT ?""",
            arrayOf(book, chapter.toString(), book, chapter.toString(), limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(CrossRef(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3))) } }
    }

    /** Father commentary anywhere in a chapter. */
    suspend fun chapterQuotes(book: String, chapter: Int): List<FatherQuote> = quotes(
        "SELECT book, chapter, verse, verse_end, father, year, source, text FROM father_quotes WHERE book = ? AND chapter = ? ORDER BY verse",
        arrayOf(book, chapter.toString()),
    )

    /** One quote from each of the Fathers who commented most on a book. */
    suspend fun bookQuotes(book: String, limit: Int = 8): List<FatherQuote> = quotes(
        """SELECT book, chapter, verse, verse_end, father, year, source, text FROM father_quotes
           WHERE rowid IN (SELECT MIN(rowid) FROM father_quotes WHERE book = ? GROUP BY father ORDER BY COUNT(*) DESC LIMIT ?)""",
        arrayOf(book, limit.toString()),
    )

    /** Verses containing [query] in the translation of [language], ordered with the 4 Gospels first and then canonical priority. */
    suspend fun searchVerses(query: String, language: AppLanguage, limit: Int = 200): List<Verse> = withContext(Dispatchers.IO) {
        database().rawQuery(
            """SELECT book, chapter, verse, text FROM verses
               WHERE translation = ? AND text LIKE ? ESCAPE '\'
               ORDER BY CASE book
                   WHEN 'MAT' THEN 1
                   WHEN 'MRK' THEN 2
                   WHEN 'LUK' THEN 3
                   WHEN 'JHN' THEN 4
                   WHEN 'ACT' THEN 5
                   WHEN 'ROM' THEN 6
                   WHEN 'PSA' THEN 7
                   WHEN 'GEN' THEN 8
                   WHEN 'ISA' THEN 9
                   WHEN '1CO' THEN 10
                   WHEN '2CO' THEN 11
                   WHEN 'GAL' THEN 12
                   WHEN 'EPH' THEN 13
                   WHEN 'PHP' THEN 14
                   WHEN 'COL' THEN 15
                   WHEN 'HEB' THEN 16
                   WHEN 'JAS' THEN 17
                   WHEN '1PE' THEN 18
                   WHEN '1JN' THEN 19
                   WHEN 'REV' THEN 20
                   ELSE 30
               END, chapter, verse
               LIMIT ?""",
            arrayOf(translation(language), "%" + escapeLike(query) + "%", limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Verse(c.getString(0), c.getInt(1), c.getInt(2), cleanVerseText(c.getString(3)))) } }
    }

    /** Father commentary mentioning [query], or by a Father whose name matches. */
    suspend fun searchQuotes(query: String, limit: Int = 30): List<FatherQuote> = quotes(
        "SELECT book, chapter, verse, verse_end, father, year, source, text FROM father_quotes WHERE text LIKE ? ESCAPE '\\' OR father LIKE ? ESCAPE '\\' LIMIT ?",
        arrayOf("%" + escapeLike(query) + "%", "%" + escapeLike(query) + "%", limit.toString()),
    )

    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Church Father commentary on a verse, from the patristic commentary database. */
    suspend fun fatherQuotes(ref: VerseRef): List<FatherQuote> = quotes(
        "SELECT book, chapter, verse, verse_end, father, year, source, text FROM father_quotes WHERE book = ? AND chapter = ? AND verse = ?",
        arrayOf(ref.book, ref.chapter.toString(), ref.verse.toString()),
    )

    /** A sample of a Father's commentary, in canonical order. */
    suspend fun quotesBy(father: String, limit: Int = 40): List<FatherQuote> = quotes(
        "SELECT book, chapter, verse, verse_end, father, year, source, text FROM father_quotes WHERE father = ? ORDER BY rowid LIMIT ?",
        arrayOf(father, limit.toString()),
    )

    private suspend fun quotes(sql: String, args: Array<String>) = withContext(Dispatchers.IO) {
        database().rawQuery(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(FatherQuote(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getString(4), c.getInt(5), c.getString(6), c.getString(7))) }
        }
    }

    @Serializable
    private data class RelatedRow(val book: String, val chapter: Int? = null, val verse: Int? = null)

    companion object {
        const val DB_VERSION = 7
    }
}
