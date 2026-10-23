package agape.app

import agape.app.core.model.Tradition
import agape.app.data.calendar.CopticDate
import agape.app.data.calendar.LiturgicalCalendar
import agape.app.data.quiz.QuizGenerator
import agape.app.data.bible.Verse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LiturgicalCalendarTest {
    @Test fun copticDatesMatchTheSynaxarium() {
        // api.coptic.io lists 21 October 2026 as 11 Paopi (Baba).
        assertEquals(CopticDate(1743, 2, 11), LiturgicalCalendar.coptic(LocalDate.of(2026, 10, 21)))
        // Coptic Christmas, 29 Koiak, falls on 7 January.
        val nativity = LiturgicalCalendar.coptic(LocalDate.of(2027, 1, 7))
        assertEquals(4, nativity.month); assertEquals(29, nativity.day)
        // Nayrouz: 11 September, or 12 September before a Gregorian leap year.
        assertEquals(CopticDate(1743, 1, 1), LiturgicalCalendar.coptic(LocalDate.of(2026, 9, 11)))
        assertEquals(CopticDate(1740, 1, 1), LiturgicalCalendar.coptic(LocalDate.of(2023, 9, 12)))
    }

    @Test fun feastsFollowEachTradition() {
        assertTrue(LiturgicalCalendar.feasts(Tradition.CATHOLIC, LocalDate.of(2026, 4, 5)).any { it.name.en.startsWith("Easter") })
        assertTrue(LiturgicalCalendar.feasts(Tradition.EASTERN_ORTHODOX, LocalDate.of(2026, 4, 12)).any { it.name.en.startsWith("Pascha") })
        assertTrue(LiturgicalCalendar.feasts(Tradition.ORIENTAL_ORTHODOX, LocalDate.of(2027, 1, 7)).any { it.name.en == "Nativity of Christ" })
        assertEquals(LocalDate.of(2026, 11, 29), LiturgicalCalendar.advent(2026))
    }

    @Test fun generatedQuizzesHaveFourOptionsAndTheRightAnswer() {
        val verses = listOf(
            "In the beginning, God created the heavens and the earth.",
            "Abraham went up from Egypt with Sarah his wife and Lot.",
            "Isaac dwelt in Gerar near the land of Canaan.",
            "Jacob loved Rachel more than Leah, and served Laban seven years.",
            "Joseph was brought down to Egypt, and Potiphar bought him.",
            "Moses fled from Pharaoh and lived in the land of Midian.",
        ).mapIndexed { i, t -> Verse("GEN", 1 + i / 2, i + 1, t) }
        val t = QuizGenerator.Templates("Fill in (%1\$s):", "Which chapter of %1\$s?", "Chapter %1\$d")
        val quiz = QuizGenerator.chapterQuiz(verses, { "${it.book} ${it.chapter}:${it.verse}" }, t, count = 4, seed = 7)
        assertTrue(quiz.isNotEmpty())
        quiz.forEach { q ->
            assertEquals(4, q.options.size)
            val answer = q.options[q.answer].en
            val verse = q.explanation!!.en
            assertTrue("$answer should appear in $verse", verse.contains(answer))
        }
    }

    @Test fun shortBooksStillGetAFullQuiz() {
        val names = listOf("Abraham", "Isaac", "Jacob", "Joseph", "Moses", "Aaron", "Miriam", "Joshua", "Caleb", "Gideon", "Samuel", "David")
        // 25 verses, like Jude: fewer verses than twice the question count.
        val verses = (0 until 25).map { i ->
            Verse("JUD", 1, i + 1, "And then ${names[i % names.size]} spoke with ${names[(i + 5) % names.size]} near the river.")
        }
        val t = QuizGenerator.Templates("Fill in (%1\$s):", "Which chapter of %1\$s?", "Chapter %1\$d")
        val quiz = QuizGenerator.bookQuiz(verses, "Jude", { "${it.book} ${it.chapter}:${it.verse}" }, t, count = 20, seed = 3)
        assertEquals(20, quiz.size)
    }
}
