package agape.app.data.calendar

import agape.app.core.model.L10n
import agape.app.core.model.Tradition
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.JulianFields

/** A date in the Coptic calendar (Anno Martyrum). */
data class CopticDate(val year: Int, val month: Int, val day: Int) {
    fun monthName(dutch: Boolean): String = (if (dutch) MONTHS_NL else MONTHS_EN)[month - 1]

    companion object {
        val MONTHS_EN = listOf("Thout", "Paopi", "Hathor", "Koiak", "Tobi", "Meshir", "Paremhat", "Parmouti", "Pashons", "Paoni", "Epip", "Mesori", "Nasie")
        val MONTHS_NL = listOf("Thoot", "Paopi", "Hathor", "Kiahk", "Tobi", "Amshir", "Baramhat", "Baramoeda", "Bashans", "Paoni", "Epip", "Mesra", "Nasie")
    }
}

enum class FeastRank { GREAT, MAJOR, SEASON }

data class Feast(val name: L10n, val rank: FeastRank)

enum class DayObservanceKind { FEAST, FAST, FAST_FREE, ORDINARY }

data class LiturgicalDayStatus(
    val kind: DayObservanceKind,
    val badge: L10n,
    val title: L10n,
    val description: L10n,
)

/** Calendar arithmetic and the principal feasts and fasts of each tradition, all offline. */
object LiturgicalCalendar {
    /** Julian Day Number of 1 Thout, year 1 of the Martyrs (29 August 284, Julian). */
    private const val COPTIC_EPOCH = 1825030L

    fun coptic(date: LocalDate): CopticDate {
        val jdn = date.getLong(JulianFields.JULIAN_DAY)
        val year = ((4 * (jdn - COPTIC_EPOCH) + 1463) / 1461).toInt()
        val startOfYear = COPTIC_EPOCH + 365L * (year - 1) + (year / 4)
        val dayOfYear = (jdn - startOfYear).toInt()
        return CopticDate(year, dayOfYear / 30 + 1, dayOfYear % 30 + 1)
    }

    /** The Julian ("old") calendar date shown alongside the civil date. */
    fun julian(date: LocalDate): LocalDate = date.minusDays(ChurchCalendar.JULIAN_OFFSET)

    private fun t(en: String, nl: String) = L10n(en, nl)

    fun feasts(tradition: Tradition, date: LocalDate): List<Feast> = buildList {
        val pascha = ChurchCalendar.pascha(tradition, date.year)
        val fromPascha = ChronoUnit.DAYS.between(pascha, date).toInt()
        when (tradition) {
            Tradition.EASTERN_ORTHODOX -> {
                when (fromPascha) {
                    -48 -> add(Feast(t("Clean Monday · Great Lent begins", "Schone Maandag · begin van de Grote Vasten"), FeastRank.SEASON))
                    -7 -> add(Feast(t("Palm Sunday", "Palmzondag"), FeastRank.GREAT))
                    -2 -> add(Feast(t("Great and Holy Friday", "Grote en Heilige Vrijdag"), FeastRank.GREAT))
                    0 -> add(Feast(t("Pascha · The Resurrection of the Lord", "Pascha · De Verrijzenis van de Heer"), FeastRank.GREAT))
                    39 -> add(Feast(t("Ascension", "Hemelvaart"), FeastRank.GREAT))
                    49 -> add(Feast(t("Pentecost", "Pinksteren"), FeastRank.GREAT))
                }
                fixed(date, listOf(
                    1 to 6 to t("Theophany", "Theofanie"), 2 to 2 to t("Meeting of the Lord", "Ontmoeting van de Heer"),
                    3 to 25 to t("Annunciation", "Annunciatie"), 8 to 6 to t("Transfiguration", "Gedaanteverandering"),
                    8 to 15 to t("Dormition of the Theotokos", "Ontslapen van de Moeder Gods"), 9 to 8 to t("Nativity of the Theotokos", "Geboorte van de Moeder Gods"),
                    9 to 14 to t("Exaltation of the Cross", "Kruisverheffing"), 11 to 21 to t("Entry of the Theotokos", "Intrede van de Moeder Gods"),
                    12 to 25 to t("Nativity of Christ", "Geboorte van Christus"),
                )).forEach { add(Feast(it, FeastRank.GREAT)) }
                if (date.monthValue == 11 && date.dayOfMonth == 15) add(Feast(t("Nativity Fast begins", "Begin van de Kerstvasten"), FeastRank.SEASON))
            }
            Tradition.ORIENTAL_ORTHODOX -> {
                when (fromPascha) {
                    -69 -> add(Feast(t("Fast of Jonah (Nineveh) begins", "Vasten van Jona (Nineve) begint"), FeastRank.SEASON))
                    -55 -> add(Feast(t("Great Lent begins", "De Grote Vasten begint"), FeastRank.SEASON))
                    -7 -> add(Feast(t("Palm Sunday", "Palmzondag"), FeastRank.GREAT))
                    -2 -> add(Feast(t("Good Friday", "Goede Vrijdag"), FeastRank.GREAT))
                    0 -> add(Feast(t("Feast of the Resurrection", "Feast van de Verrijzenis"), FeastRank.GREAT))
                    39 -> add(Feast(t("Ascension", "Hemelvaart"), FeastRank.GREAT))
                    49 -> add(Feast(t("Pentecost", "Pinksteren"), FeastRank.GREAT))
                }
                val c = coptic(date)
                listOf(
                    (1 to 1) to Feast(t("Nayrouz · Coptic New Year", "Nayrouz · Koptisch Nieuwjaar"), FeastRank.MAJOR),
                    (1 to 17) to Feast(t("Feast of the Cross", "Feest van het Kruis"), FeastRank.MAJOR),
                    (5 to 11) to Feast(t("Theophany", "Theofanie"), FeastRank.GREAT),
                    (7 to 29) to Feast(t("Annunciation", "Annunciatie"), FeastRank.GREAT),
                    (9 to 24) to Feast(t("Entry of the Lord into Egypt", "Intocht van de Heer in Egypte"), FeastRank.MAJOR),
                    (11 to 5) to Feast(t("Feast of the Apostles", "Feest van de Apostelen"), FeastRank.MAJOR),
                    (12 to 16) to Feast(t("Assumption of St Mary", "Opneming van de heilige Maria"), FeastRank.MAJOR),
                ).forEach { (md, feast) -> if (c.month == md.first && c.day == md.second) add(feast) }
                // 29 Kiahk, but kept on 7 January (28 Kiahk) in the years after a Coptic leap year.
                if (date.monthValue == 1 && date.dayOfMonth == 7) add(Feast(t("Nativity of Christ", "Geboorte van Christus"), FeastRank.GREAT))
            }
            Tradition.CATHOLIC -> {
                when (fromPascha) {
                    -46 -> add(Feast(t("Ash Wednesday", "Aswoensdag"), FeastRank.SEASON))
                    -7 -> add(Feast(t("Palm Sunday", "Palmzondag"), FeastRank.GREAT))
                    -3 -> add(Feast(t("Holy Thursday", "Witte Donderdag"), FeastRank.GREAT))
                    -2 -> add(Feast(t("Good Friday", "Goede Vrijdag"), FeastRank.GREAT))
                    0 -> add(Feast(t("Easter Sunday", "Pasen"), FeastRank.GREAT))
                    39 -> add(Feast(t("Ascension", "Hemelvaart"), FeastRank.GREAT))
                    49 -> add(Feast(t("Pentecost", "Pinksteren"), FeastRank.GREAT))
                    56 -> add(Feast(t("Holy Trinity", "Heilige Drie-eenheid"), FeastRank.MAJOR))
                    60 -> add(Feast(t("Corpus Christi", "Sacramentsdag"), FeastRank.MAJOR))
                    68 -> add(Feast(t("Sacred Heart", "Heilig Hart"), FeastRank.MAJOR))
                }
                val advent = advent(date.year)
                if (date == advent) add(Feast(t("First Sunday of Advent", "Eerste zondag van de Advent"), FeastRank.SEASON))
                if (date == advent.minusDays(7)) add(Feast(t("Christ the King", "Christus Koning"), FeastRank.MAJOR))
                fixed(date, listOf(
                    1 to 1 to t("Mary, Mother of God", "Maria, Moeder van God"), 1 to 6 to t("Epiphany", "Driekoningen"),
                    2 to 2 to t("Presentation of the Lord", "Opdracht van de Heer"), 3 to 19 to t("St Joseph", "Heilige Jozef"),
                    3 to 25 to t("Annunciation", "Aankondiging van de Heer"), 6 to 29 to t("Sts Peter and Paul", "Heiligen Petrus en Paulus"),
                    8 to 6 to t("Transfiguration", "Gedaanteverandering"), 8 to 15 to t("Assumption of Mary", "Maria-Tenhemelopneming"),
                    11 to 1 to t("All Saints", "Allerheiligen"), 11 to 2 to t("All Souls", "Allerzielen"),
                    12 to 8 to t("Immaculate Conception", "Onbevlekte Ontvangenis"), 12 to 25 to t("Christmas", "Kerstmis"),
                )).forEach { add(Feast(it, FeastRank.GREAT)) }
            }
        }
    }

    /** Computes today's Feast or Fasting status for the Home screen badge and modal dialog. */
    fun dayStatus(tradition: Tradition, date: LocalDate): LiturgicalDayStatus {
        val pascha = ChurchCalendar.pascha(tradition, date.year)
        val fromPascha = ChronoUnit.DAYS.between(pascha, date).toInt()
        val majorFeast = feasts(tradition, date).firstOrNull { it.rank == FeastRank.GREAT || it.rank == FeastRank.MAJOR }

        // Holy Friday / Good Friday is always a strict fast day even though it is ranked GREAT
        if (fromPascha == -2) {
            return LiturgicalDayStatus(
                kind = DayObservanceKind.FAST,
                badge = t("Strict Fast · Holy Friday", "Strenge Vasten · Goede Vrijdag"),
                title = t("Great and Holy Friday", "Grote en Heilige Vrijdag"),
                description = t(
                    "Today the Church commemorates the saving Passion and Crucifixion of our Lord Jesus Christ with strict fasting, silence, and prayer.",
                    "Vandaag herdenkt de Kerk het heilzame Lijden en de Kruisiging van onze Heer Jezus Christus met strenge vasten, stilte en gebed.",
                ),
            )
        }

        if (majorFeast != null) {
            return LiturgicalDayStatus(
                kind = DayObservanceKind.FEAST,
                badge = t("Feast · ${majorFeast.name.en}", "Feestdag · ${majorFeast.name.nl}"),
                title = majorFeast.name,
                description = t(
                    "Today is a Feast of the Church (${majorFeast.name.en}). The faithful celebrate with festal hymns, thanksgiving, and joy in the Lord.",
                    "Vandaag viert de Kerk het feest van ${majorFeast.name.nl}. Een dag van dankzegging, feestelijke hymnen en geestelijke vreugde.",
                ),
            )
        }

        when (tradition) {
            Tradition.EASTERN_ORTHODOX -> {
                if (fromPascha in 1..6) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST_FREE,
                        badge = t("Bright Week · Fast-free", "Lichte Week · Vastenvrij"),
                        title = t("Bright Week of Pascha", "Lichte Week van Pascha"),
                        description = t(
                            "During the week following Pascha, the whole week is kept as one continuous day of Resurrection joy without fasting.",
                            "In de week na Pascha viert de Kerk de hele week als één aaneengesloten dag van de Verrijzenis, zonder vasten.",
                        ),
                    )
                }
                if (fromPascha in -48..-1) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Fast · Great Lent", "Vasten · Grote Vasten"),
                        title = t("Great and Holy Lent", "De Grote en Heilige Vasten"),
                        description = t(
                            "The Forty Days of Great Lent prepare the heart for Pascha through prayer, almsgiving, and abstinence from meat, dairy, and eggs.",
                            "De Veertig Dagen van de Grote Vasten bereiden het hart voor op Pascha door gebed, aalmoezen en onthouding van vlees en zuivel.",
                        ),
                    )
                }
                if ((date.monthValue == 11 && date.dayOfMonth >= 15) || (date.monthValue == 12 && date.dayOfMonth <= 24)) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Fast · Nativity Fast", "Vasten · Kerstvasten"),
                        title = t("The Nativity Fast", "De Kerstvasten (Filippusvasten)"),
                        description = t(
                            "A forty-day season of fasting and watchful prayer preparing for the Nativity of our Lord Jesus Christ.",
                            "De veertigdaagse voorbereidingstijd van vasten en gebed op weg naar het feest van de Geboorte van Christus.",
                        ),
                    )
                }
                if (date.monthValue == 8 && date.dayOfMonth in 1..14) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Fast · Dormition Fast", "Vasten · Moeder Gods-vasten"),
                        title = t("Fast of the Dormition of the Theotokos", "Vasten van het Ontslapen van de Moeder Gods"),
                        description = t(
                            "Two weeks of fasting and supplication in honour of the Mother of God, leading up to 15 August.",
                            "Twee weken van vasten en smeekgebed ter ere van de Moeder Gods, in voorbereiding op 15 augustus.",
                        ),
                    )
                }
                if (date.dayOfWeek == DayOfWeek.WEDNESDAY || date.dayOfWeek == DayOfWeek.FRIDAY) {
                    val isWednesday = date.dayOfWeek == DayOfWeek.WEDNESDAY
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = if (isWednesday) t("Fast Day · Wednesday", "Vastendag · Woensdag") else t("Fast Day · Friday", "Vastendag · Vrijdag"),
                        title = if (isWednesday) t("Wednesday Fast", "Woensdagvasten") else t("Friday Fast", "Vrijdagvasten"),
                        description = if (isWednesday) {
                            t(
                                "Orthodox Christians fast on Wednesdays in remembrance of the betrayal of Christ, turning the heart toward repentance and prayer.",
                                "Op woensdag vast de Kerk ter herinnering aan het verraad van Christus, met extra aandacht voor waakzaamheid en gebed.",
                            )
                        } else {
                            t(
                                "Orthodox Christians fast on Fridays in remembrance of the Crucifixion of our Lord Jesus Christ.",
                                "Op vrijdag vast de Kerk ter gedachtenis aan het Kruis en het Lijden van onze Heer Jezus Christus.",
                            )
                        },
                    )
                }
            }

            Tradition.ORIENTAL_ORTHODOX -> {
                if (fromPascha in 1..49) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST_FREE,
                        badge = t("Holy Fifty Days · Joyful Season", "Heilige Vijftig Dagen · Paastijd"),
                        title = t("The Holy Fifty Days (Khamasin)", "De Heilige Vijftig Dagen (Khamasin)"),
                        description = t(
                            "From the Resurrection to Pentecost, the Oriental Orthodox Church celebrates fifty days of Paschal joy without fasting.",
                            "Vanaf het Verrijzenisfeest tot Pinksteren viert de Oriëntaals-Orthodoxe Kerk vijftig dagen van Paasvreugde zonder vasten.",
                        ),
                    )
                }
                if (fromPascha in -69..-67) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Fast · Fast of Nineveh", "Vasten · Vasten van Nineve"),
                        title = t("The Fast of Jonah (Nineveh)", "Het Vasten van Jona (Nineve)"),
                        description = t(
                            "Three days of strict fasting and repentance recalling the prophet Jonah and the repentance of Nineveh.",
                            "Drie dagen van vasten en bekering ter herinnering aan de profeet Jona en het berouw van Nineve.",
                        ),
                    )
                }
                if (fromPascha in -55..-1) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Fast · Great Lent", "Vasten · Grote Vasten"),
                        title = t("The Great Fast (55 Days)", "De Grote Vasten (55 dagen)"),
                        description = t(
                            "The fifty-five days of the Great Fast in the Oriental Orthodox tradition, combining prayer, fasting, and works of mercy.",
                            "De vijfenvijftig dagen van de Grote Vasten in de Oriëntaals-Orthodoxe traditie, gewijd aan gebed, vasten en barmhartigheid.",
                        ),
                    )
                }
                if ((date.monthValue == 11 && date.dayOfMonth >= 25) || date.monthValue == 12 || (date.monthValue == 1 && date.dayOfMonth <= 6)) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Fast · Nativity Fast (Kiahk)", "Vasten · Kerstvasten (Kiahk)"),
                        title = t("The Nativity Fast & Month of Kiahk", "De Kerstvasten & Maand Kiahk"),
                        description = t(
                            "A season of fasting and midnight praises honoring the Incarnation of Christ and the Mother of God.",
                            "Periode van vasten en lofprijzing ter voorbereiding op het geheim van de Menswording van Christus.",
                        ),
                    )
                }
                if (date.dayOfWeek == DayOfWeek.WEDNESDAY || date.dayOfWeek == DayOfWeek.FRIDAY) {
                    val isWednesday = date.dayOfWeek == DayOfWeek.WEDNESDAY
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = if (isWednesday) t("Fast Day · Wednesday", "Vastendag · Woensdag") else t("Fast Day · Friday", "Vastendag · Vrijdag"),
                        title = if (isWednesday) t("Wednesday Fast", "Woensdagvasten") else t("Friday Fast", "Vrijdagvasten"),
                        description = t(
                            "Throughout the year (outside the Holy Fifty Days), Wednesdays and Fridays are kept as days of fasting and prayer.",
                            "Door het hele jaar heen (buiten de Vijftig Paasdagen) worden woensdag en vrijdag gehouden als dagen van vasten en gebed.",
                        ),
                    )
                }
            }

            Tradition.CATHOLIC -> {
                if (fromPascha == -46) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Strict Fast · Ash Wednesday", "Vasten · Aswoensdag"),
                        title = t("Ash Wednesday", "Aswoensdag · Begin van de Veertigdagentijd"),
                        description = t(
                            "Ash Wednesday opens the season of Lent with fasting, abstinence from meat, and the blessing of ashes.",
                            "Aswoensdag opent de Veertigdagentijd met vasten, onthouding van vlees en het ontvangen van het askruisje.",
                        ),
                    )
                }
                if (fromPascha in -45..-1 && date.dayOfWeek != DayOfWeek.SUNDAY) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Lent · Day of Penance", "Veertigdagentijd · Vasten"),
                        title = t("Season of Lent", "De Veertigdagentijd"),
                        description = t(
                            "The forty days of Lent call the faithful to prayer, fasting, and almsgiving in preparation for Easter.",
                            "De Veertigdagentijd roept op tot gebed, soberheid, vasten en naastenliefde als voorbereiding op Pasen.",
                        ),
                    )
                }
                val adventStart = advent(date.year)
                if (!date.isBefore(adventStart) && date.monthValue == 12 && date.dayOfMonth < 25 || (!date.isBefore(adventStart) && date.monthValue == 11)) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Advent · Watchful Preparation", "Advent · Verwachting"),
                        title = t("Season of Advent", "De Adventstijd"),
                        description = t(
                            "A season of joyful and prayerful expectation preparing for the Nativity of the Lord.",
                            "Een tijd van hoopvolle verwachting, stilte en gebed op weg naar het Kerstfeest.",
                        ),
                    )
                }
                if (date.dayOfWeek == DayOfWeek.FRIDAY && fromPascha !in 0..7) {
                    return LiturgicalDayStatus(
                        kind = DayObservanceKind.FAST,
                        badge = t("Friday · Day of Abstinence", "Vrijdag · Onthoudingsdag"),
                        title = t("Friday Penance & Abstinence", "Vrijdagse Onthoudingsdag"),
                        description = t(
                            "In union with the Passion of Christ on the Cross, Fridays are kept as days of abstinence and devotion.",
                            "In verbondenheid met het Lijden van Christus op Goede Vrijdag houdt de Kerk de vrijdag als dag van onthouding en gebed.",
                        ),
                    )
                }
            }
        }

        if (date.dayOfWeek == DayOfWeek.SUNDAY) {
            return LiturgicalDayStatus(
                kind = DayObservanceKind.FEAST,
                badge = t("Lord's Day · Sunday", "Dag des Heren · Zondag"),
                title = t("The Day of the Lord", "De Dag van de Heer (Zondag)"),
                description = t(
                    "Every Sunday celebrates the Resurrection of Christ and the new creation.",
                    "Elke zondag is een klein Paasfeest waarop de Kerk de Verrijzenis van Christus viert.",
                ),
            )
        }

        return LiturgicalDayStatus(
            kind = DayObservanceKind.ORDINARY,
            badge = t("Liturgical Day · No Fast", "Liturgische dag · Geen vasten"),
            title = t("Daily Walk in Prayer", "Dagelijkse wandel in gebed"),
            description = t(
                "An ordinary liturgical weekday outside the fasting seasons. A day to give thanks and keep the hours of prayer.",
                "Een gewone liturgische weekdag zonder bijzonder vastenvoorschrift. Een dag om in dankbaarheid te bidden.",
            ),
        )
    }

    private fun fixed(date: LocalDate, list: List<Pair<Pair<Int, Int>, L10n>>): List<L10n> =
        list.filter { (md, _) -> md.first == date.monthValue && md.second == date.dayOfMonth }.map { it.second }

    /** First Sunday of Advent: the Sunday between 27 November and 3 December. */
    fun advent(year: Int): LocalDate {
        var d = LocalDate.of(year, 11, 27)
        while (d.dayOfWeek.value != 7) d = d.plusDays(1)
        return d
    }
}
