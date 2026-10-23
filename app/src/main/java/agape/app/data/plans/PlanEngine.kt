package agape.app.data.plans

import agape.app.core.model.L10n
import agape.app.core.model.Passage
import agape.app.data.content.Council
import agape.app.data.content.PlanDefinition
import agape.app.data.content.PlanGenerator
import agape.app.core.model.Testament

/** A book of the user's canon, as far as plan generation needs it. */
data class CanonBook(val id: String, val testament: Testament, val chapters: Int)

data class PlanDay(
    val number: Int,
    val title: L10n? = null,
    val readings: List<Passage>,
    /** Set on council-plan days. */
    val councilId: String? = null,
)

/** Turns plan definitions into concrete days for one tradition's canon. Pure, so it can be unit-tested. */
object PlanEngine {

    fun days(plan: PlanDefinition, canon: List<CanonBook>, councils: List<Council>): List<PlanDay> {
        val g = plan.generator
        return when (g.type) {
            PlanGenerator.Type.FIXED -> g.fixedDays.mapIndexed { i, d ->
                PlanDay(i + 1, d.title, d.readings.mapNotNull { Passage.parse(it) })
            }
            PlanGenerator.Type.COUNCILS -> councils.mapIndexed { i, c ->
                PlanDay(i + 1, c.name, c.passages.mapNotNull { Passage.parse(it) }, councilId = c.id)
            }
            PlanGenerator.Type.BOOKS -> spread(g.books.mapNotNull { id -> canon.firstOrNull { it.id == id } }, g.days)
            PlanGenerator.Type.TESTAMENT -> spread(canon.filter { it.testament == g.testament }, g.days)
            PlanGenerator.Type.CANON -> spread(canon, g.days)
            PlanGenerator.Type.ORDERED -> {
                val byId = canon.associateBy { it.id }
                val ordered = g.books.mapNotNull { byId[it] }
                // Books of the canon the ordering list does not mention still belong in a whole-Bible plan.
                spread(ordered + canon.filter { it.id !in g.books }, g.days)
            }
        }
    }

    /** Splits the chapters of [books] over [dayCount] days as evenly as possible, merging adjacent chapters. */
    fun spread(books: List<CanonBook>, dayCount: Int): List<PlanDay> {
        val chapters = books.flatMap { b -> (1..b.chapters).map { b.id to it } }
        if (chapters.isEmpty()) return emptyList()
        val days = dayCount.coerceIn(1, chapters.size)
        return (0 until days).map { d ->
            val from = (d.toLong() * chapters.size / days).toInt()
            val to = ((d + 1).toLong() * chapters.size / days).toInt()
            PlanDay(d + 1, readings = merge(chapters.subList(from, to)))
        }
    }

    private fun merge(chapters: List<Pair<String, Int>>): List<Passage> {
        val out = mutableListOf<Passage>()
        for ((book, ch) in chapters) {
            val last = out.lastOrNull()
            if (last != null && last.book == book && last.endChapter == ch - 1) {
                out[out.lastIndex] = last.copy(endChapter = ch)
            } else {
                out += Passage(book, ch)
            }
        }
        return out
    }

    /** The quiz that guards a plan day, if any content exists for it. */
    fun quizKey(plan: PlanDefinition, day: PlanDay, hasQuiz: (String) -> Boolean): String? {
        val candidates = buildList {
            add("plan:${plan.id}:${day.number}")
            day.councilId?.let { add("council:$it") }
            day.readings.singleOrNull()?.takeIf { it.isWholeChapter }?.let { add("chapter:${it.book}.${it.chapter}") }
        }
        return candidates.firstOrNull(hasQuiz)
    }

    /** In a locked plan a day opens only once every earlier day is complete. */
    fun isUnlocked(plan: PlanDefinition, dayNumber: Int, completed: Set<Int>): Boolean =
        !plan.locked || (1 until dayNumber).all { it in completed }

    /** First day that is not complete yet, or null when the plan is finished. */
    fun nextDay(totalDays: Int, completed: Set<Int>): Int? = (1..totalDays).firstOrNull { it !in completed }
}
