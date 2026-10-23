package agape.app.ui.theme

import agape.app.R
import agape.app.core.model.VisualStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

private val SourceSans = variable(R.font.source_sans, 400, 500, 600, 700)
private val Literata = variable(R.font.literata, 400, 500, 600)
private val Cormorant = variable(R.font.cormorant_garamond, 500, 600, 700)
private val EbGaramond = variable(R.font.eb_garamond, 400, 500, 600)
private val Cinzel = variable(R.font.cinzel, 400, 500, 600)
private val Lora = variable(R.font.lora, 400, 500, 600)

/** The style-specific text roles that Material's Typography does not cover. */
@Immutable
data class AgapeType(
    val display: FontFamily,
    val ui: FontFamily,
    val reading: FontFamily,
    val kicker: TextStyle,
    val scripture: TextStyle,
    val verseNumber: TextStyle,
)

fun agapeType(style: VisualStyle, scale: Float): AgapeType {
    val (display, reading, kickerFamily, kickerSpacing) = when (style) {
        VisualStyle.NAVY_CREAM -> Quad(Cormorant, Literata, SourceSans, 0.14.em)
        VisualStyle.CLASSIC_SACRED -> Quad(EbGaramond, EbGaramond, Cinzel, 0.12.em)
        VisualStyle.WARM_TERRACOTTA -> Quad(Lora, Lora, SourceSans, 0.1.em)
    }
    val readingSize = (if (style == VisualStyle.CLASSIC_SACRED) 20f else 18.5f) * scale
    return AgapeType(
        display = display,
        ui = SourceSans,
        reading = reading,
        kicker = TextStyle(fontFamily = kickerFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = kickerSpacing),
        scripture = TextStyle(fontFamily = reading, fontSize = readingSize.sp, lineHeight = (readingSize * 1.62f).sp),
        verseNumber = TextStyle(fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = (readingSize * 0.58f).sp),
    )
}

private data class Quad(val a: FontFamily, val b: FontFamily, val c: FontFamily, val d: TextUnit)

fun materialTypography(style: VisualStyle, scale: Float): Typography {
    val t = agapeType(style, scale)
    // Cormorant runs small, so it gets a little more size than the other display faces.
    val d = if (style == VisualStyle.NAVY_CREAM) 1.12f else 1f
    fun s(size: Float) = (size * scale).sp
    fun heading(size: Float, weight: Int = 600) = TextStyle(fontFamily = t.display, fontWeight = FontWeight(weight), fontSize = s(size * d), lineHeight = s(size * d * 1.18f))
    fun body(size: Float, weight: Int = 400, line: Float = 1.45f) = TextStyle(fontFamily = t.ui, fontWeight = FontWeight(weight), fontSize = s(size), lineHeight = s(size * line))
    return Typography(
        displayLarge = heading(44f),
        displayMedium = heading(36f),
        displaySmall = heading(30f),
        headlineLarge = heading(28f),
        headlineMedium = heading(24f),
        headlineSmall = heading(21f),
        titleLarge = heading(20f),
        titleMedium = body(16.5f, 600, 1.3f),
        titleSmall = body(14.5f, 600, 1.3f),
        bodyLarge = body(16.5f),
        bodyMedium = body(15f),
        bodySmall = body(13f),
        labelLarge = body(15f, 600, 1.2f),
        labelMedium = body(13f, 600, 1.2f),
        labelSmall = body(11.5f, 600, 1.2f),
    )
}
