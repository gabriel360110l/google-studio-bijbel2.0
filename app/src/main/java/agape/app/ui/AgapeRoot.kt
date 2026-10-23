package agape.app.ui

import agape.app.AppContainer
import agape.app.core.model.AppLanguage
import agape.app.core.model.ThemeMode
import agape.app.core.model.Tradition
import agape.app.core.model.VisualStyle
import agape.app.data.auth.AuthState
import agape.app.ui.main.MainShell
import agape.app.ui.onboarding.Onboarding
import agape.app.ui.theme.AgapeTheme
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AgapeRoot(container: AppContainer, newIntents: StateFlow<Intent?>, onIntentHandled: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val s = settings ?: return // DataStore answers within a frame; the splash screen covers it.

    // While picking a style during onboarding, the screen previews the candidate before it is saved.
    var previewStyle by remember { mutableStateOf<VisualStyle?>(null) }
    var showStartupIntro by rememberSaveable { mutableStateOf(true) }
    var lastTraditionName by rememberSaveable { mutableStateOf(s.tradition?.name) }
    var animKey by remember { mutableIntStateOf(0) }

    // Trigger the smooth AGAPE opening animation whenever the user switches tradition (Eastern <-> Oriental <-> Catholic).
    LaunchedEffect(s.tradition) {
        val currentName = s.tradition?.name
        if (lastTraditionName != null && currentName != null && currentName != lastTraditionName) {
            animKey++
            showStartupIntro = true
        }
        lastTraditionName = currentName
    }

    val dark = when (s.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val style = previewStyle ?: s.style ?: VisualStyle.NAVY_CREAM
    val lang = container.language()

    CompositionLocalProvider(
        LocalContainer provides container,
        LocalLanguage provides lang,
        LocalSettings provides s,
    ) {
        AgapeTheme(style = style, dark = dark, fontScale = s.fontScale) {
            Box(Modifier.fillMaxSize().background(AgapeTheme.colors.background)) {
                val inApp = s.readyForSignIn && auth is AuthState.SignedIn
                Crossfade(targetState = inApp, label = "root") { signedIn ->
                    if (signedIn) MainShell(newIntents = newIntents, onIntentHandled = onIntentHandled)
                    else if (auth !is AuthState.Loading) Onboarding(settings = s, onPreviewStyle = { previewStyle = it })
                }

                AnimatedVisibility(
                    visible = showStartupIntro,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(450, easing = FastOutSlowInEasing)),
                ) {
                    key(animKey) {
                        AgapeStartupIntroOverlay(
                            lang = lang,
                            tradition = s.tradition,
                            onFinished = { showStartupIntro = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgapeStartupIntroOverlay(
    lang: AppLanguage,
    tradition: Tradition?,
    onFinished: () -> Unit,
) {
    val c = AgapeTheme.colors
    val letters = remember { listOf("A", "G", "A", "P", "E") }
    var step by remember { mutableIntStateOf(0) } // 0 = initial, 1 = cross, 2..6 = letters, 7 = subtitle

    LaunchedEffect(Unit) {
        delay(80)
        step = 1 // Show subtle cross
        for (i in letters.indices) {
            delay(115)
            step = i + 2
        }
        delay(120)
        step = 7 // Show subtle motto & tradition
        delay(520)
        onFinished()
    }

    val crossAlpha by animateFloatAsState(
        targetValue = if (step >= 1) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "cross_alpha",
    )
    val crossScale by animateFloatAsState(
        targetValue = if (step >= 1) 1f else 0.82f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "cross_scale",
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (step >= 7) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "subtitle_alpha",
    )

    val crossSymbol = when (tradition) {
        Tradition.EASTERN_ORTHODOX -> "☦"
        Tradition.ORIENTAL_ORTHODOX -> "☩"
        Tradition.CATHOLIC -> "✝"
        null -> "☩"
    }

    val subtitleText = when (tradition) {
        Tradition.EASTERN_ORTHODOX ->
            if (lang == AppLanguage.DUTCH) "OOSTERS-ORTHODOX · GOD IS LIEFDE" else "EASTERN ORTHODOX · GOD IS LOVE"
        Tradition.ORIENTAL_ORTHODOX ->
            if (lang == AppLanguage.DUTCH) "ORIËNTAALS-ORTHODOX · GOD IS LIEFDE" else "ORIENTAL ORTHODOX · GOD IS LOVE"
        Tradition.CATHOLIC ->
            if (lang == AppLanguage.DUTCH) "ROOMS-KATHOLIEK · GOD IS LIEFDE" else "ROMAN CATHOLIC · GOD IS LOVE"
        null ->
            if (lang == AppLanguage.DUTCH) "GOD IS LIEFDE · 1 JOHANNES 4:16" else "GOD IS LOVE · 1 JOHN 4:16"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        c.accent.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            // Subtle liturgical cross matching the selected tradition
            Text(
                text = crossSymbol,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                color = c.accent,
                modifier = Modifier.graphicsLayer {
                    alpha = crossAlpha
                    scaleX = crossScale
                    scaleY = crossScale
                },
            )

            Spacer(Modifier.height(14.dp))

            // Letter-by-letter smooth "A G A P E" reveal
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                letters.forEachIndexed { index, letter ->
                    val revealed = step >= (index + 2)
                    val letterAlpha by animateFloatAsState(
                        targetValue = if (revealed) 1f else 0f,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                        label = "letter_alpha_$index",
                    )
                    val letterOffsetY by animateFloatAsState(
                        targetValue = if (revealed) 0f else 18f,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "letter_offset_$index",
                    )

                    Text(
                        text = letter,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 38.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp,
                        ),
                        color = c.ink,
                        modifier = Modifier.graphicsLayer {
                            alpha = letterAlpha
                            translationY = letterOffsetY
                        },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Subtle divider + tagline
            Box(
                Modifier
                    .width(48.dp)
                    .height(1.dp)
                    .graphicsLayer { alpha = subtitleAlpha * 0.6f }
                    .background(c.accent),
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.2.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = c.inkMuted,
                modifier = Modifier.graphicsLayer {
                    alpha = subtitleAlpha
                },
            )
        }
    }
}
