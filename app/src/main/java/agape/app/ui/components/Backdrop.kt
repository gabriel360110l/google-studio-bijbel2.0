package agape.app.ui.components

import agape.app.data.content.BackgroundEntry
import agape.app.ui.theme.AgapeTheme
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The darkest tone the backdrop fades through, so light text stays readable in light mode too. */
val BackdropInk = Color(0xFF0E0B09)

/**
 * A photograph or painting behind the top of a screen. It is darkened for the text on it and fades at the bottom
 * into the page, so the content below seems to rise out of the image.
 */
@Composable
fun Backdrop(entry: BackgroundEntry?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val c = AgapeTheme.colors
    val bitmap by produceState<ImageBitmap?>(null, entry?.file) {
        value = entry?.let { e ->
            withContext(Dispatchers.IO) {
                runCatching { context.assets.open(e.file).use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
            }
        }
    }
    Box(modifier.background(BackdropInk)) {
        Crossfade(bitmap, animationSpec = tween(700), label = "backdrop", modifier = Modifier.matchParentSize()) { image ->
            if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to BackdropInk.copy(alpha = 0.45f),
                    0.28f to BackdropInk.copy(alpha = 0.10f),
                    0.62f to BackdropInk.copy(alpha = 0.55f),
                    0.86f to BackdropInk.copy(alpha = 0.92f),
                    1f to c.background,
                ),
            ),
        )
        content()
    }
}
