package agape.app.data.rosary

import agape.app.core.model.L10n
import java.time.DayOfWeek

/** The four sets of mysteries, and the weekday on which each is usually prayed (Rosarium Virginis Mariae, 2002). */
enum class MysterySet(val title: L10n) {
    JOYFUL(L10n("The Joyful Mysteries", "De blijde geheimen")),
    LUMINOUS(L10n("The Luminous Mysteries", "De geheimen van het licht")),
    SORROWFUL(L10n("The Sorrowful Mysteries", "De droevige geheimen")),
    GLORIOUS(L10n("The Glorious Mysteries", "De glorievolle geheimen")),
    ;

    companion object {
        fun forDay(day: DayOfWeek): MysterySet = when (day) {
            DayOfWeek.MONDAY, DayOfWeek.SATURDAY -> JOYFUL
            DayOfWeek.THURSDAY -> LUMINOUS
            DayOfWeek.TUESDAY, DayOfWeek.FRIDAY -> SORROWFUL
            DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY -> GLORIOUS
        }
    }
}

/** One mystery, with the passage to read before its decade (null where the mystery has no single passage). */
data class Mystery(val title: L10n, val passage: String?)

enum class RosaryPrayer(val title: L10n, val text: L10n) {
    SIGN_OF_THE_CROSS(
        L10n("The Sign of the Cross", "Het kruisteken"),
        L10n(
            "In the name of the Father, and of the Son, and of the Holy Spirit. Amen.",
            "In de naam van de Vader en de Zoon en de heilige Geest. Amen.",
        ),
    ),
    APOSTLES_CREED(
        L10n("The Apostles' Creed", "De apostolische geloofsbelijdenis"),
        L10n(
            "I believe in God, the Father almighty, Creator of heaven and earth, and in Jesus Christ, his only Son, our Lord, " +
                "who was conceived by the Holy Spirit, born of the Virgin Mary, suffered under Pontius Pilate, was crucified, " +
                "died and was buried; he descended into hell; on the third day he rose again from the dead; he ascended into " +
                "heaven, and is seated at the right hand of God the Father almighty; from there he will come to judge the living " +
                "and the dead. I believe in the Holy Spirit, the holy catholic Church, the communion of saints, the forgiveness " +
                "of sins, the resurrection of the body, and life everlasting. Amen.",
            "Ik geloof in God, de almachtige Vader, Schepper van hemel en aarde. En in Jezus Christus, zijn enig Zoon, onze Heer, " +
                "die ontvangen is van de heilige Geest, geboren uit de maagd Maria; die geleden heeft onder Pontius Pilatus, is " +
                "gekruisigd, gestorven en begraven; die nedergedaald is ter helle, de derde dag verrezen uit de doden; die " +
                "opgestegen is ten hemel, zit aan de rechterhand van God, de almachtige Vader; vandaar zal Hij komen oordelen de " +
                "levenden en de doden. Ik geloof in de heilige Geest; de heilige katholieke Kerk, de gemeenschap van de heiligen; " +
                "de vergeving van de zonden; de verrijzenis van het lichaam; en het eeuwig leven. Amen.",
        ),
    ),
    OUR_FATHER(
        L10n("Our Father", "Onze Vader"),
        L10n(
            "Our Father, who art in heaven, hallowed be thy name; thy kingdom come, thy will be done on earth as it is in heaven. " +
                "Give us this day our daily bread, and forgive us our trespasses, as we forgive those who trespass against us; " +
                "and lead us not into temptation, but deliver us from evil. Amen.",
            "Onze Vader, die in de hemel zijt, uw naam worde geheiligd, uw rijk kome, uw wil geschiede op aarde zoals in de " +
                "hemel. Geef ons heden ons dagelijks brood, en vergeef ons onze schuld, zoals ook wij aan anderen hun schuld " +
                "vergeven, en breng ons niet in beproeving, maar verlos ons van het kwade. Amen.",
        ),
    ),
    HAIL_MARY(
        L10n("Hail Mary", "Weesgegroet"),
        L10n(
            "Hail Mary, full of grace, the Lord is with thee. Blessed art thou among women, and blessed is the fruit of thy " +
                "womb, Jesus. Holy Mary, Mother of God, pray for us sinners, now and at the hour of our death. Amen.",
            "Wees gegroet, Maria, vol van genade, de Heer is met U. Gezegend zijt Gij boven alle vrouwen, en gezegend is " +
                "Jezus, de vrucht van uw schoot. Heilige Maria, Moeder van God, bid voor ons, zondaars, nu en in het uur van " +
                "onze dood. Amen.",
        ),
    ),
    GLORY_BE(
        L10n("Glory Be", "Eer aan de Vader"),
        L10n(
            "Glory be to the Father, and to the Son, and to the Holy Spirit, as it was in the beginning, is now, and ever " +
                "shall be, world without end. Amen.",
            "Eer aan de Vader en de Zoon en de heilige Geest, zoals het was in het begin, en nu en altijd, en in de eeuwen " +
                "der eeuwen. Amen.",
        ),
    ),
    FATIMA(
        L10n("O my Jesus", "O mijn Jezus"),
        L10n(
            "O my Jesus, forgive us our sins, save us from the fires of hell, lead all souls to heaven, especially those in " +
                "most need of thy mercy.",
            "O mijn Jezus, vergeef ons onze zonden, behoed ons voor het vuur van de hel, breng alle zielen naar de hemel, " +
                "vooral die uw barmhartigheid het meest nodig hebben.",
        ),
    ),
    HAIL_HOLY_QUEEN(
        L10n("Hail, Holy Queen", "Salve Regina"),
        L10n(
            "Hail, holy Queen, Mother of mercy, our life, our sweetness and our hope. To thee do we cry, poor banished " +
                "children of Eve; to thee do we send up our sighs, mourning and weeping in this valley of tears. Turn then, most " +
                "gracious advocate, thine eyes of mercy toward us, and after this our exile show unto us the blessed fruit of " +
                "thy womb, Jesus. O clement, O loving, O sweet Virgin Mary.\n" +
                "V. Pray for us, O holy Mother of God.\nR. That we may be made worthy of the promises of Christ.",
            "Wees gegroet, Koningin, Moeder van barmhartigheid, ons leven, onze vreugde en onze hoop, wees gegroet. Tot u " +
                "roepen wij, ballingen, kinderen van Eva; tot u smeken wij, zuchtend en wenend in dit dal van tranen. Daarom " +
                "dan, onze voorspreekster, sla op ons uw barmhartige ogen, en toon ons na deze ballingschap Jezus, de " +
                "gezegende vrucht van uw schoot. O goedertieren, o liefdevolle, o zoete Maagd Maria.\n" +
                "V. Bid voor ons, heilige Moeder van God.\nA. Opdat wij de beloften van Christus waardig worden.",
        ),
    ),
    CLOSING_PRAYER(
        L10n("Closing prayer", "Slotgebed"),
        L10n(
            "Let us pray. O God, whose only-begotten Son, by his life, death and resurrection, has purchased for us the " +
                "rewards of eternal life, grant, we beseech thee, that, meditating upon these mysteries of the most holy Rosary " +
                "of the Blessed Virgin Mary, we may imitate what they contain and obtain what they promise. Through the same " +
                "Christ our Lord. Amen.",
            "Laat ons bidden. God, uw eniggeboren Zoon heeft door zijn leven, dood en verrijzenis voor ons het eeuwig leven " +
                "verworven. Wij bidden U: geef dat wij, terwijl wij deze geheimen van de heilige rozenkrans van de heilige Maagd " +
                "Maria overwegen, navolgen wat zij bevatten en verkrijgen wat zij beloven. Door Christus onze Heer. Amen.",
        ),
    ),
}

enum class BeadKind { CRUCIFIX, LARGE, SMALL, MEDAL }

/**
 * One step of the Rosary: the bead it is prayed on and its prayers.
 *
 * [decade] is the mystery the step belongs to (0–4), or null before the first and after the last. A step with
 * [announce] set opens a decade: the mystery is named before its Our Father. [count] is the Hail Mary's place in
 * its group (1–10, or 1–3 on the pendant).
 */
data class RosaryStep(
    val bead: Int,
    val prayers: List<RosaryPrayer>,
    val decade: Int? = null,
    val announce: Boolean = false,
    val count: Int = 0,
    val intention: L10n? = null,
)

/**
 * The Rosary as beads and steps. Beads are numbered from the crucifix up the pendant to the medal, then round the
 * loop: crucifix 0, Our Father bead 1, three Hail Mary beads 2–4, Our Father bead 5, medal 6, and then for each
 * decade ten small beads followed (between decades) by one large bead.
 */
object Rosary {
    const val CRUCIFIX = 0
    const val MEDAL = 6
    private const val LOOP_START = 7
    const val BEAD_COUNT = LOOP_START + 5 * 10 + 4

    /** The bead of the [i]-th Hail Mary (0–9) of decade [d] (0–4). */
    fun decadeBead(d: Int, i: Int): Int = LOOP_START + d * 11 + i

    /** The large bead after decade [d] (0–3); after the last decade the medal closes the loop. */
    fun largeBeadAfter(d: Int): Int = if (d < 4) LOOP_START + d * 11 + 10 else MEDAL

    fun kind(bead: Int): BeadKind = when {
        bead == CRUCIFIX -> BeadKind.CRUCIFIX
        bead == MEDAL -> BeadKind.MEDAL
        bead == 1 || bead == 5 -> BeadKind.LARGE
        bead < LOOP_START -> BeadKind.SMALL
        (bead - LOOP_START) % 11 == 10 -> BeadKind.LARGE
        else -> BeadKind.SMALL
    }

    private val virtues = listOf(
        L10n("For an increase of faith", "Om een groter geloof"),
        L10n("For an increase of hope", "Om een sterkere hoop"),
        L10n("For an increase of love", "Om een vuriger liefde"),
    )

    val steps: List<RosaryStep> = buildList {
        add(RosaryStep(CRUCIFIX, listOf(RosaryPrayer.SIGN_OF_THE_CROSS)))
        add(RosaryStep(CRUCIFIX, listOf(RosaryPrayer.APOSTLES_CREED)))
        add(RosaryStep(1, listOf(RosaryPrayer.OUR_FATHER)))
        for (i in 0 until 3) add(RosaryStep(2 + i, listOf(RosaryPrayer.HAIL_MARY), count = i + 1, intention = virtues[i]))
        add(RosaryStep(5, listOf(RosaryPrayer.GLORY_BE)))
        add(RosaryStep(5, listOf(RosaryPrayer.OUR_FATHER), decade = 0, announce = true))
        for (d in 0 until 5) {
            for (i in 0 until 10) add(RosaryStep(decadeBead(d, i), listOf(RosaryPrayer.HAIL_MARY), decade = d, count = i + 1))
            add(RosaryStep(largeBeadAfter(d), listOf(RosaryPrayer.GLORY_BE, RosaryPrayer.FATIMA), decade = d))
            if (d < 4) add(RosaryStep(largeBeadAfter(d), listOf(RosaryPrayer.OUR_FATHER), decade = d + 1, announce = true))
        }
        add(RosaryStep(MEDAL, listOf(RosaryPrayer.HAIL_HOLY_QUEEN)))
        add(RosaryStep(MEDAL, listOf(RosaryPrayer.CLOSING_PRAYER)))
        add(RosaryStep(MEDAL, listOf(RosaryPrayer.SIGN_OF_THE_CROSS)))
    }

    /** The first step prayed on [bead]. */
    fun firstStepOn(bead: Int): Int = steps.indexOfFirst { it.bead == bead }

    val mysteries: Map<MysterySet, List<Mystery>> = mapOf(
        MysterySet.JOYFUL to listOf(
            Mystery(L10n("The Annunciation", "De boodschap van de engel aan Maria"), "LUK 1:26-38"),
            Mystery(L10n("The Visitation", "Maria bezoekt haar nicht Elisabet"), "LUK 1:39-56"),
            Mystery(L10n("The Nativity", "De geboorte van Jezus in Betlehem"), "LUK 2:1-20"),
            Mystery(L10n("The Presentation in the Temple", "Jezus wordt in de tempel opgedragen"), "LUK 2:22-38"),
            Mystery(L10n("The Finding in the Temple", "Jezus wordt teruggevonden in de tempel"), "LUK 2:41-52"),
        ),
        MysterySet.LUMINOUS to listOf(
            Mystery(L10n("The Baptism in the Jordan", "Het doopsel van Jezus in de Jordaan"), "MAT 3:13-17"),
            Mystery(L10n("The Wedding at Cana", "De bruiloft te Kana"), "JHN 2:1-11"),
            Mystery(L10n("The Proclamation of the Kingdom", "De verkondiging van het Rijk Gods"), "MRK 1:14-15"),
            Mystery(L10n("The Transfiguration", "De gedaanteverandering op de berg"), "LUK 9:28-36"),
            Mystery(L10n("The Institution of the Eucharist", "De instelling van de eucharistie"), "LUK 22:14-20"),
        ),
        MysterySet.SORROWFUL to listOf(
            Mystery(L10n("The Agony in the Garden", "De doodsangst van Jezus in de hof"), "LUK 22:39-46"),
            Mystery(L10n("The Scourging at the Pillar", "Jezus wordt gegeseld"), "JHN 19:1"),
            Mystery(L10n("The Crowning with Thorns", "Jezus wordt met doornen gekroond"), "MAT 27:27-31"),
            Mystery(L10n("The Carrying of the Cross", "Jezus draagt het kruis"), "JHN 19:16-17"),
            Mystery(L10n("The Crucifixion", "Jezus sterft aan het kruis"), "LUK 23:33-46"),
        ),
        MysterySet.GLORIOUS to listOf(
            Mystery(L10n("The Resurrection", "De verrijzenis van Jezus"), "MAT 28:1-10"),
            Mystery(L10n("The Ascension", "De hemelvaart van Jezus"), "ACT 1:6-11"),
            Mystery(L10n("The Descent of the Holy Spirit", "De nederdaling van de heilige Geest"), "ACT 2:1-4"),
            Mystery(L10n("The Assumption of Mary", "De tenhemelopneming van Maria"), null),
            Mystery(L10n("The Coronation of Mary", "De kroning van Maria"), "REV 12:1"),
        ),
    )
}
