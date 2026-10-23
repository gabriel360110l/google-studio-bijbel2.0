package agape.app

import agape.app.data.user.Streaks
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTest {
    private val today = 20_000L

    @Test fun streakCountsBackFromToday() {
        assertEquals(3, Streaks.current(setOf(today, today - 1, today - 2, today - 5), today))
    }

    @Test fun streakSurvivesUntilTheDayIsOver() {
        // Nothing yet today, but yesterday was active: the streak is not broken.
        assertEquals(2, Streaks.current(setOf(today - 1, today - 2), today))
    }

    @Test fun streakBreaksAfterAMissedDay() {
        assertEquals(0, Streaks.current(setOf(today - 2, today - 3), today))
    }

    @Test fun longestStreakFindsTheLongestRun() {
        assertEquals(4, Streaks.longest(setOf(1, 2, 3, 4, 10, 11, 20)))
        assertEquals(0, Streaks.longest(emptySet()))
    }
}
