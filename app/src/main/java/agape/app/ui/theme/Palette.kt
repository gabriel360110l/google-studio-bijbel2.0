package agape.app.ui.theme

import agape.app.core.model.VisualStyle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The colour tokens every screen draws from. Material's ColorScheme is derived from these. */
@Immutable
data class AgapeColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val line: Color,
    val danger: Color,
    /** Verse highlight colours, in a fixed order so a stored index means the same hue in every style. */
    val highlights: List<Color>,
    val isDark: Boolean,
)

/**
 * The three styles from the EOC-church style references (colours and mood only), each with a light and a dark mode.
 * Dark modes are deep and warm rather than grey, with gold carrying the accents, as in the reference Agpeya screen.
 */
object Palettes {
    // Style 1 — Modern Navy + Cream: warm cream, deep navy ink, thin gold details.
    private val navyLight = AgapeColors(
        background = Color(0xFFFAF6EE), surface = Color(0xFFFFFDF8), surfaceRaised = Color(0xFFFFFFFF),
        surfaceSunken = Color(0xFFF1EADC), ink = Color(0xFF12203D), inkMuted = Color(0xFF4B5670),
        inkFaint = Color(0xFF878EA0), primary = Color(0xFF14244A), onPrimary = Color(0xFFFAF6EE),
        accent = Color(0xFFB08A3E), onAccent = Color(0xFFFFFDF8), line = Color(0xFFE6DCC8), danger = Color(0xFFA23B3B),
        highlights = listOf(Color(0x66E8C77A), Color(0x5591B4D9), Color(0x55A9C9A0), Color(0x55E3A6A0)), isDark = false,
    )
    private val navyDark = AgapeColors(
        background = Color(0xFF0A0F19), surface = Color(0xFF111827), surfaceRaised = Color(0xFF172036),
        surfaceSunken = Color(0xFF070B13), ink = Color(0xFFF1EBDD), inkMuted = Color(0xFFA9B0BF),
        inkFaint = Color(0xFF6B7387), primary = Color(0xFFE0B865), onPrimary = Color(0xFF0A0F19),
        accent = Color(0xFFE0B865), onAccent = Color(0xFF0A0F19), line = Color(0xFF243048), danger = Color(0xFFE08A84),
        highlights = listOf(Color(0x55B8963F), Color(0x554F78A8), Color(0x55628A5C), Color(0x55A35E58)), isDark = true,
    )

    // Style 2 — Classic Sacred: parchment, deep burgundy and gold; Byzantine and reverent.
    private val sacredLight = AgapeColors(
        background = Color(0xFFF5EDDC), surface = Color(0xFFFBF6EA), surfaceRaised = Color(0xFFFFFAEF),
        surfaceSunken = Color(0xFFEDE2CA), ink = Color(0xFF2A1612), inkMuted = Color(0xFF684C40),
        inkFaint = Color(0xFF9C8573), primary = Color(0xFF6E1A26), onPrimary = Color(0xFFFBF6EA),
        accent = Color(0xFFAE8834), onAccent = Color(0xFFFFFAEF), line = Color(0xFFDCC9A2), danger = Color(0xFF9E2B2B),
        highlights = listOf(Color(0x66E2BE6A), Color(0x558FA9C9), Color(0x559DBB8E), Color(0x55D99A93)), isDark = false,
    )
    private val sacredDark = AgapeColors(
        background = Color(0xFF110A0B), surface = Color(0xFF1C1214), surfaceRaised = Color(0xFF26191B),
        surfaceSunken = Color(0xFF0B0607), ink = Color(0xFFF3E7CF), inkMuted = Color(0xFFC4AE8F),
        inkFaint = Color(0xFF80695A), primary = Color(0xFFD6B062), onPrimary = Color(0xFF1C1214),
        accent = Color(0xFFCBA455), onAccent = Color(0xFF1C1214), line = Color(0xFF3A2725), danger = Color(0xFFE28A80),
        highlights = listOf(Color(0x55B08A3A), Color(0x55506E92), Color(0x55607F52), Color(0x559A4B4B)), isDark = true,
    )

    // Style 3 — Warm Terracotta: soft beige, terracotta and soft gold; warm and inviting.
    private val terracottaLight = AgapeColors(
        background = Color(0xFFFAF1E7), surface = Color(0xFFFFFAF4), surfaceRaised = Color(0xFFFFFDF9),
        surfaceSunken = Color(0xFFF2E4D4), ink = Color(0xFF45291E), inkMuted = Color(0xFF7A5F51),
        inkFaint = Color(0xFFA8928A), primary = Color(0xFFBF5F3D), onPrimary = Color(0xFFFFF8F1),
        accent = Color(0xFFC6A05A), onAccent = Color(0xFF45291E), line = Color(0xFFECDAC6), danger = Color(0xFFA63A2E),
        highlights = listOf(Color(0x66EBC982), Color(0x5598B6CF), Color(0x55AFC79A), Color(0x55E6A592)), isDark = false,
    )
    private val terracottaDark = AgapeColors(
        background = Color(0xFF191310), surface = Color(0xFF231B17), surfaceRaised = Color(0xFF2D241F),
        surfaceSunken = Color(0xFF130E0C), ink = Color(0xFFF4E8DA), inkMuted = Color(0xFFBFAA99),
        inkFaint = Color(0xFF7E6E62), primary = Color(0xFFE39472), onPrimary = Color(0xFF231A15),
        accent = Color(0xFFD9B672), onAccent = Color(0xFF231A15), line = Color(0xFF3A2F28), danger = Color(0xFFE38F80),
        highlights = listOf(Color(0x55B9944A), Color(0x55577A96), Color(0x55688758), Color(0x55A2594A)), isDark = true,
    )

    fun of(style: VisualStyle, dark: Boolean): AgapeColors = when (style) {
        VisualStyle.NAVY_CREAM -> if (dark) navyDark else navyLight
        VisualStyle.CLASSIC_SACRED -> if (dark) sacredDark else sacredLight
        VisualStyle.WARM_TERRACOTTA -> if (dark) terracottaDark else terracottaLight
    }
}
