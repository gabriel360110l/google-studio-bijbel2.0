package agape.app.data.calendar

import agape.app.core.model.Tradition
import java.time.LocalDate

/** Date rules carried over from the legacy app: Pascha for East and West, and the Julian offset. */
object ChurchCalendar {
    /** Days between the Julian and Gregorian calendars for 1900–2099. */
    const val JULIAN_OFFSET = 13L

    /** Orthodox Pascha (Julian computus), as a Gregorian date. Also used by the Coptic Church. */
    fun orthodoxPascha(year: Int): LocalDate {
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = ((d + e + 114) % 31) + 1
        return LocalDate.of(year, month, day).plusDays(JULIAN_OFFSET)
    }

    /** Western Easter (anonymous Gregorian algorithm). */
    fun westernEaster(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    fun pascha(tradition: Tradition, year: Int): LocalDate =
        if (tradition == Tradition.CATHOLIC) westernEaster(year) else orthodoxPascha(year)

    /** The forty days from Pascha to the eve of the Ascension. */
    fun isPaschalSeason(tradition: Tradition, date: LocalDate): Boolean {
        val p = pascha(tradition, date.year)
        return !date.isBefore(p) && date.isBefore(p.plusDays(39))
    }
}
