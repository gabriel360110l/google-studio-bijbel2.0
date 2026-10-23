package agape.app.data.saints

import agape.app.core.model.AppLanguage
import agape.app.core.model.Tradition
import agape.app.data.calendar.FeastRank
import agape.app.data.calendar.LiturgicalCalendar
import agape.app.data.content.ContentRepository
import agape.app.data.content.IconEntry
import java.time.LocalDate

/** The saint or feast of a day, with an icon when one is bundled. */
data class SaintOfDay(
    val name: String,
    val others: List<String>,
    val summary: String,
    val icon: IconEntry?,
    /** A great feast of the Lord or the Mother of God rather than a saint. */
    val isFeast: Boolean,
)

/**
 * Picks the commemoration of a day from the bundled uniform saints dataset (`saints_unified.json`), so it works offline
 * in all three traditions: the Byzantine menologion, the Coptic Synaxarium (by Coptic date) and the General Roman
 * Calendar. A great feast from the liturgical calendar (Pascha, Pentecost, …) takes the place of the saint on its day.
 */
class SaintsRepository(private val content: ContentRepository) {
    private val calendar by lazy { SaintCalendar(content.saintsUnified) }

    /** Parses the dataset (about a megabyte) ahead of time, off the main thread, so Home and the calendar need not wait. */
    fun warmUp() {
        calendar
    }

    /** Everything the calendar shows for a date: the three calendar dates, all commemorations with lives and icons, readings. */
    fun day(tradition: Tradition, date: LocalDate): SaintDayView = calendar.day(tradition, date)

    fun forDate(tradition: Tradition, language: AppLanguage, date: LocalDate): SaintOfDay? {
        val greatFeast = LiturgicalCalendar.feasts(tradition, date).firstOrNull { it.rank == FeastRank.GREAT }
        val day = day(tradition, date)
        val names = day.commemorations.map { it.entry.name[language] }
        val summary = day.commemorations.firstOrNull()?.entry?.life?.text?.get(language).orEmpty()
        if (greatFeast != null) {
            val icon = content.iconFor(listOf(greatFeast.name.en))
                ?: day.icon
                ?: content.icons.firstOrNull { it.key == "pantocrator" || it.key == "all_saints" }
            // The Coptic and Roman summaries describe the day's saint, not a moveable feast that takes its place.
            val feastSummary = if (tradition == Tradition.EASTERN_ORTHODOX) summary else ""
            return SaintOfDay(greatFeast.name[language], names, feastSummary, icon, isFeast = true)
        }
        if (names.isEmpty()) return null
        val resolvedIcon = day.icon
            ?: content.iconFor(day.commemorations.map { it.entry.name.en })
            ?: content.icons.firstOrNull { it.key == "all_saints" }
            ?: content.icons.firstOrNull()
        return SaintOfDay(
            name = names.first(),
            others = names.drop(1),
            summary = summary,
            icon = resolvedIcon,
            isFeast = false,
        )
    }
}
