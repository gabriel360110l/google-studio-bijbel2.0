package agape.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons Material does not have, drawn in the same 24dp outlined language. */
object AgapeIcons {
    /** A lit candle, used for the Prayer tab. */
    val Candle: ImageVector by lazy {
        ImageVector.Builder("Candle", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // flame
                moveTo(12f, 2.5f)
                curveTo(13.8f, 4.6f, 14.4f, 6.1f, 14.4f, 7.2f)
                curveTo(14.4f, 8.6f, 13.3f, 9.6f, 12f, 9.6f)
                curveTo(10.7f, 9.6f, 9.6f, 8.6f, 9.6f, 7.2f)
                curveTo(9.6f, 6.1f, 10.2f, 4.6f, 12f, 2.5f)
                close()
                // candle body
                moveTo(9f, 12f); lineTo(15f, 12f); lineTo(15f, 21f); lineTo(9f, 21f); close()
                // base
                moveTo(6.5f, 21f); lineTo(17.5f, 21f)
            }
        }.build()
    }

    /** A simple Greek cross. */
    val Cross: ImageVector by lazy {
        ImageVector.Builder("Cross", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
                moveTo(12f, 3f); lineTo(12f, 21f)
                moveTo(5f, 9f); lineTo(19f, 9f)
            }
        }.build()
    }
}
