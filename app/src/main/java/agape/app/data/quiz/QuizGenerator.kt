package agape.app.data.quiz

import agape.app.core.model.L10n
import agape.app.data.bible.Verse
import agape.app.data.content.QuizQuestion
import kotlin.random.Random

/**
 * Builds quizzes from the Bible text itself, so every chapter and every book has one even before questions are
 * written by hand. Questions are fill-in-the-blank (preferring names of people and places) and, for books,
 * "in which chapter is this written?". Pure and deterministic per seed, so it can be unit-tested.
 */
object QuizGenerator {

    /** Localised wording, so the generator stays free of Android resources. */
    data class Templates(
        /** "Fill in the missing word (%1$s):" with the verse reference. */
        val cloze: String,
        /** "In which chapter of %1$s is this written?" with the book name. */
        val whichChapter: String,
        /** "Chapter %1$d" */
        val chapterOption: String,
    )

    private val stopwords = setOf(
        // English
        "god", "lord", "the", "and", "for", "that", "with", "this", "they", "them", "their", "there", "which", "when", "then",
        "have", "will", "shall", "said", "unto", "from", "your", "you", "his", "him", "her", "she", "who", "whom", "what",
        "because", "behold", "therefore", "against", "before", "after", "about", "among", "upon", "would", "should", "could",
        "every", "where", "these", "those", "into", "were", "been", "being", "also", "over", "under", "again", "even",
        // Dutch
        "heere", "heer", "here", "gods", "zijn", "hij", "haar", "hem", "die", "dat", "het", "een", "van", "voor", "met",
        "want", "maar", "niet", "ook", "zal", "zullen", "hebben", "heeft", "werd", "worden", "zeide", "tot", "uit", "door",
        "over", "naar", "alzo", "ende", "daarom", "wanneer", "zijne", "hare", "deze", "dezen", "welke", "welken", "zich",
    )

    private val word = Regex("""[\p{L}][\p{L}'’-]*""")

    private data class Candidate(val word: String, val isName: Boolean)

    private fun candidates(text: String): List<Candidate> {
        val words = word.findAll(text).map { it.value.trim('\'', '’', '-') }.toList()
        return words.mapIndexedNotNull { i, w ->
            val lower = w.lowercase()
            if (lower in stopwords || w.length < 4) return@mapIndexedNotNull null
            // A capitalised word that does not open the verse is very likely a name of a person or place.
            val isName = i > 0 && w[0].isUpperCase() && w.drop(1).any { it.isLowerCase() }
            when {
                isName -> Candidate(w, true)
                w.length >= 7 && w[0].isLowerCase() -> Candidate(w, false)
                else -> null
            }
        }
    }

    private fun cloze(
        verse: Verse, pool: Map<Boolean, List<String>>, label: (Verse) -> String, t: Templates, random: Random,
    ): QuizQuestion? {
        val options = candidates(verse.text)
        val pick = options.filter { it.isName }.ifEmpty { options }.randomOrNull(random) ?: return null
        val distractors = pool[pick.isName].orEmpty().distinctBy { it.lowercase() }
            .filter { !it.equals(pick.word, ignoreCase = true) }
            .shuffled(random).take(3)
        if (distractors.size < 3) return null
        val blanked = verse.text.replaceFirst(pick.word, "_____")
        val shown = if (blanked.length > 260) shorten(blanked) else blanked
        val all = (distractors + pick.word).shuffled(random)
        val q = "${t.cloze.format(label(verse))}\n“$shown”"
        return QuizQuestion(
            q = L10n(q, q),
            options = all.map { L10n(it, it) },
            answer = all.indexOf(pick.word),
            explanation = L10n("${label(verse)}: “${verse.text}”", "${label(verse)}: “${verse.text}”"),
        )
    }

    /** Keeps the part of a long verse around the blank. */
    private fun shorten(text: String): String {
        val at = text.indexOf("_____").coerceAtLeast(0)
        val start = (at - 120).coerceAtLeast(0)
        val end = (at + 140).coerceAtMost(text.length)
        return (if (start > 0) "… " else "") + text.substring(start, end).trim() + (if (end < text.length) " …" else "")
    }

    private fun pool(verses: List<Verse>): Map<Boolean, List<String>> =
        verses.flatMap { candidates(it.text) }.groupBy({ it.isName }, { it.word })

    /** Up to [need] fill-in-the-blank questions, spread over [verses]: one verse from each slice, then topped up. */
    private fun clozes(
        verses: List<Verse>, need: Int, pool: Map<Boolean, List<String>>, label: (Verse) -> String, t: Templates, random: Random,
    ): List<QuizQuestion> {
        if (need <= 0) return emptyList()
        val used = mutableSetOf<Verse>()
        val out = verses.chunked((verses.size + need - 1) / need).mapNotNull { slice ->
            slice.shuffled(random).firstNotNullOfOrNull { v -> cloze(v, pool, label, t, random)?.also { used += v } }
        }.toMutableList()
        // Short books and chapters have fewer usable slices than questions: fill up from the verses not used yet.
        for (v in verses.filter { it !in used }.shuffled(random)) {
            if (out.size >= need) break
            cloze(v, pool, label, t, random)?.let { out += it }
        }
        return out.take(need)
    }

    fun chapterQuiz(verses: List<Verse>, label: (Verse) -> String, t: Templates, count: Int = 5, seed: Long): List<QuizQuestion> {
        if (verses.isEmpty()) return emptyList()
        val random = Random(seed)
        return clozes(verses, count, pool(verses), label, t, random)
    }

    fun bookQuiz(verses: List<Verse>, bookName: String, label: (Verse) -> String, t: Templates, count: Int = 20, seed: Long): List<QuizQuestion> {
        if (verses.isEmpty()) return emptyList()
        val random = Random(seed)
        val pool = pool(verses)
        val chapters = verses.map { it.chapter }.distinct()
        val whichCount = if (chapters.size >= 4) count * 2 / 5 else 0
        // One verse per chapter first, so the questions cover the whole book; more from the same chapters if needed.
        val fitting = verses.filter { it.text.length in 60..240 }.shuffled(random)
        val which = (fitting.distinctBy { it.chapter } + fitting).distinct().take(whichCount).map { v ->
            val others = chapters.filter { it != v.chapter }.shuffled(random).take(3)
            val all = (others + v.chapter).shuffled(random)
            val q = "${t.whichChapter.format(bookName)}\n“${v.text}”"
            QuizQuestion(
                q = L10n(q, q),
                options = all.map { val s = t.chapterOption.format(it); L10n(s, s) },
                answer = all.indexOf(v.chapter),
                explanation = L10n(label(v), label(v)),
            )
        }
        return (clozes(verses, count - which.size, pool, label, t, random) + which).shuffled(random)
    }
}
