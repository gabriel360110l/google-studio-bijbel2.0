package agape.app

import agape.app.core.model.Tradition
import agape.app.data.calendar.ChurchCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChurchCalendarTest {
    @Test fun orthodoxPaschaMatchesPublishedDates() {
        listOf("2026-04-12", "2027-05-02", "2028-04-16", "2029-04-08", "2030-04-28").forEach {
            assertEquals(it, ChurchCalendar.orthodoxPascha(it.take(4).toInt()).toString())
        }
    }

    @Test fun westernEasterMatchesPublishedDates() {
        listOf("2026-04-05", "2027-03-28", "2028-04-16", "2029-04-01", "2030-04-21").forEach {
            assertEquals(it, ChurchCalendar.westernEaster(it.take(4).toInt()).toString())
        }
    }

    @Test fun paschalSeasonUsesTheDateOfEachTradition() {
        val day = LocalDate.of(2026, 4, 8) // after Western Easter, before Orthodox Pascha
        assertTrue(ChurchCalendar.isPaschalSeason(Tradition.CATHOLIC, day))
        assertFalse(ChurchCalendar.isPaschalSeason(Tradition.EASTERN_ORTHODOX, day))
        assertFalse(ChurchCalendar.isPaschalSeason(Tradition.CATHOLIC, LocalDate.of(2026, 5, 20)))
    }
}
