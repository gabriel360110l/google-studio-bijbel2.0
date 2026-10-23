package agape.app.ui.tour

import agape.app.R
import agape.app.core.prefs.TourStop
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.PrimaryButton
import agape.app.ui.theme.AgapeTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private fun title(stop: TourStop) = when (stop) {
    TourStop.HOME -> R.string.tour_home_title
    TourStop.BIBLE -> R.string.tour_bible_title
    TourStop.PRAYER -> R.string.tour_prayer_title
    TourStop.ME -> R.string.tour_me_title
}

private fun body(stop: TourStop) = when (stop) {
    TourStop.HOME -> R.string.tour_home_body
    TourStop.BIBLE -> R.string.tour_bible_body
    TourStop.PRAYER -> R.string.tour_prayer_body
    TourStop.ME -> R.string.tour_me_body
}

/** Explains a tab the first time it is opened, and points to the tabs not yet visited. */
@Composable
fun TourOverlay(stop: TourStop, seen: Set<TourStop>, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(stop) { visible = true }
    val next = TourStop.entries.firstOrNull { it != stop && it !in seen }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(visible, enter = slideInVertically { it / 3 } + fadeIn()) {
            AgapeCard(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                Kicker(stringResource(R.string.tour_kicker, seen.size + 1, TourStop.entries.size))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(title(stop)), style = MaterialTheme.typography.headlineSmall, color = AgapeTheme.colors.ink)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(body(stop)), style = MaterialTheme.typography.bodyMedium, color = AgapeTheme.colors.inkMuted)
                if (next != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.tour_next_hint, stringResource(tabName(next))),
                        style = MaterialTheme.typography.bodySmall, color = AgapeTheme.colors.accent,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Ornament(Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(14.dp))
                PrimaryButton(stringResource(R.string.tour_got_it), onDismiss)
            }
        }
    }
}

fun tabName(stop: TourStop) = when (stop) {
    TourStop.HOME -> R.string.tab_home
    TourStop.BIBLE -> R.string.tab_bible
    TourStop.PRAYER -> R.string.tab_prayer
    TourStop.ME -> R.string.tab_me
}
