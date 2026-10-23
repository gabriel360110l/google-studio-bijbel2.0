package agape.app

import agape.app.core.model.L10n
import agape.app.core.model.L10nLines
import agape.app.core.model.Testament
import agape.app.core.model.Tradition
import agape.app.data.content.Council
import agape.app.data.content.FixedDay
import agape.app.data.content.PlanCategory
import agape.app.data.content.PlanDefinition
import agape.app.data.content.PlanGenerator
import agape.app.data.content.PrayerHour
import agape.app.data.plans.CanonBook
import agape.app.data.plans.PlanEngine
import agape.app.data.plans.PrayerClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class PlanEngineTest {
    private val canon = listOf(
        CanonBook("GEN", Testament.OT, 50),
        CanonBook("TOB", Testament.OT, 14),
        CanonBook("MRK", Testament.NT, 16),
        CanonBook("JHN", Testament.NT, 21),
    )

    private fun plan(generator: PlanGenerator, locked: Boolean = false) =
        PlanDefinition("p", PlanCategory.FULL, locked, L10n("p"), L10n("p"), generator)

    @Test fun spreadsEveryChapterExactlyOnce() {
        val days = PlanEngine.days(plan(PlanGenerator(PlanGenerator.Type.CANON, days = 30)), canon, emptyList())
        assertEquals(30, days.size)
        val chapters = days.flatMap { d -> d.readings.flatMap { p -> (p.chapter..p.endChapter).map { p.book to it } } }
        assertEquals(101, chapters.size)
        assertEquals(chapters.distinct(), chapters)
    }

    @Test fun oneChapterPerDayForBookPlans() {
        val days = PlanEngine.days(plan(PlanGenerator(PlanGenerator.Type.BOOKS, books = listOf("MRK"), days = 16)), canon, emptyList())
        assertEquals(16, days.size)
        assertEquals("MRK 7", days[6].readings.single().toString())
    }

    @Test fun testamentPlansSkipTheOtherTestament() {
        val days = PlanEngine.days(plan(PlanGenerator(PlanGenerator.Type.TESTAMENT, testament = Testament.NT, days = 10)), canon, emptyList())
        assertTrue(days.all { d -> d.readings.all { it.book in setOf("MRK", "JHN") } })
    }

    @Test fun orderedPlansSkipBooksOutsideTheCanonAndAppendUnlistedOnes() {
        // 4MA is not in this canon; GEN is in the canon but missing from the order list, so it is appended.
        val g = PlanGenerator(PlanGenerator.Type.ORDERED, books = listOf("JHN", "4MA", "MRK", "TOB"), days = 5)
        val books = PlanEngine.days(plan(g), canon, emptyList()).flatMap { d -> d.readings.map { it.book } }.distinct()
        assertEquals(listOf("JHN", "MRK", "TOB", "GEN"), books)
    }

    @Test fun councilPlansFollowTheCouncilsGiven() {
        val council = Council(
            id = "nicaea-1", number = 1, year = "325", recognizedBy = listOf(Tradition.CATHOLIC), name = L10n("N"),
            convokedBy = L10n(), summary = L10n(), canons = emptyList(), outcome = L10nLines(), passages = listOf("JHN 1:1-18"),
        )
        val councilsPlan = plan(PlanGenerator(PlanGenerator.Type.COUNCILS))
        val day = PlanEngine.days(councilsPlan, canon, listOf(council)).single()
        assertEquals("nicaea-1", day.councilId)
        assertEquals("council:nicaea-1", PlanEngine.quizKey(councilsPlan, day) { it.startsWith("council:") })
    }

    @Test fun quizKeyPrefersPlanQuizThenChapterQuiz() {
        val g = PlanGenerator(PlanGenerator.Type.FIXED, fixedDays = listOf(FixedDay(L10n("x"), listOf("GEN 3"))))
        val fixed = plan(g)
        val day = PlanEngine.days(fixed, canon, emptyList()).single()
        assertEquals("plan:p:1", PlanEngine.quizKey(fixed, day) { true })
        assertEquals("chapter:GEN.3", PlanEngine.quizKey(fixed, day) { it.startsWith("chapter:") })
        assertNull(PlanEngine.quizKey(fixed, day) { false })
    }

    @Test fun lockedPlansOpenOneDayAtATime() {
        val locked = plan(PlanGenerator(PlanGenerator.Type.CANON, days = 5), locked = true)
        assertTrue(PlanEngine.isUnlocked(locked, 1, emptySet()))
        assertFalse(PlanEngine.isUnlocked(locked, 2, emptySet()))
        assertTrue(PlanEngine.isUnlocked(locked, 3, setOf(1, 2)))
        assertTrue(PlanEngine.isUnlocked(plan(PlanGenerator(PlanGenerator.Type.CANON, days = 5)), 4, emptySet()))
        assertEquals(3, PlanEngine.nextDay(5, setOf(1, 2, 4)))
        assertNull(PlanEngine.nextDay(2, setOf(1, 2)))
    }

    @Test fun currentHourWrapsAroundMidnight() {
        fun hour(id: String, t: String) = PrayerHour(id, L10n(id), time = t, sections = emptyList())
        val hours = listOf(hour("vespers", "18:00"), hour("first", "07:00"), hour("compline", "21:00"))
        assertEquals("first", PrayerClock.current(hours, LocalTime.of(9, 30))!!.id)
        assertEquals("compline", PrayerClock.current(hours, LocalTime.of(2, 0))!!.id)
        assertEquals("first", PrayerClock.next(hours, LocalTime.of(22, 0))!!.id)
    }
}
