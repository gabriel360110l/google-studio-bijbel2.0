package agape.app.ui

import agape.app.AppContainer
import agape.app.R
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AgapeRoot(container: AppContainer, newIntents: StateFlow<Intent?>, onIntentHandled: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val s = settings ?: return // DataStore answers within a frame; the splash screen covers it.

    // While picking a style during onboarding, the screen previews the candidate before it is saved.
    var previewStyle by remember { mutableStateOf<VisualStyle?>(null) }
    var showStartupIntro by rememberSaveable { mutableStateOf(true) }
    var showBibleEmblemFirst by rememberSaveable { mutableStateOf(true) }
    var lastTraditionName by rememberSaveable { mutableStateOf(s.tradition?.name) }
    var animKey by remember { mutableIntStateOf(0) }

    // Trigger the smooth AGAPE animation (without the Bible icon) whenever the user switches tradition inside the app.
    LaunchedEffect(s.tradition) {
        val currentName = s.tradition?.name
        if (lastTraditionName != null && currentName != null && currentName != lastTraditionName) {
            showBibleEmblemFirst = false
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
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(480, easing = FastOutSlowInEasing)),
                ) {
                    key(animKey) {
                        AgapeStartupIntroOverlay(
                            lang = lang,
                            tradition = s.tradition,
                            showBibleAtStart = showBibleEmblemFirst,
                            onFinished = {
                                showBibleEmblemFirst = false
                                showStartupIntro = false
                            },
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
    showBibleAtStart: Boolean,
    onFinished: () -> Unit,
) {
    val c = AgapeTheme.colors
    val letters = remember { listOf("A", "G", "A", "P", "E") }
    // step: -1 = initial Bible emblem (only on cold start), 0 = transition, 1 = cross & halo, 2..6 = A G A P E, 7 = subtitle & shimmer
    var step by remember { mutableIntStateOf(if (showBibleAtStart) -1 else 0) }

    LaunchedEffect(showBibleAtStart) {
        if (showBibleAtStart) {
            delay(540) // Briefly show the golden Bible emblem on cold app launch
            step = 0
            delay(180)
        } else {
            delay(50)
        }
        step = 1 // Reveal sacred halo, golden light rays, and tradition-specific cross
        for (i in letters.indices) {
            delay(105)
            step = i + 2
        }
        delay(130)
        step = 7 // Reveal tradition motto and golden shimmer
        delay(680)
        onFinished()
    }

    val infinite = rememberInfiniteTransition(label = "sacred_intro")
    val rayRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "ray_rotation",
    )
    val haloPulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo_pulse",
    )

    val bibleAlpha by animateFloatAsState(
        targetValue = if (step == -1) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "bible_alpha",
    )
    val bibleScale by animateFloatAsState(
        targetValue = if (step == -1) 1f else 0.86f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "bible_scale",
    )

    val crossAlpha by animateFloatAsState(
        targetValue = if (step >= 1) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "cross_alpha",
    )
    val crossScale by animateFloatAsState(
        targetValue = if (step >= 1) 1f else 0.76f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "cross_scale",
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (step >= 7) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "subtitle_alpha",
    )
    val dividerWidthFraction by animateFloatAsState(
        targetValue = if (step >= 7) 1f else 0f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "divider_width",
    )

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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Golden light rays & warm ambient aura in the background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.44f)
            val maxR = size.minDimension * 0.72f
            // Soft radial golden sanctuary glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        c.accent.copy(alpha = 0.24f * crossAlpha.coerceAtLeast(bibleAlpha * 0.8f)),
                        c.primary.copy(alpha = 0.08f * crossAlpha),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = maxR,
                ),
                radius = maxR,
                center = center,
            )

            // 12 rotating golden light rays radiating from behind the cross
            if (crossAlpha > 0.01f) {
                val rayCount = 12
                rotate(degrees = rayRotation, pivot = center) {
                    for (i in 0 until rayCount) {
                        val baseAngle = (i * (360f / rayCount)) * (Math.PI / 180.0)
                        val halfSpread = 4.5f * (Math.PI / 180.0)
                        val rayLen = maxR * (if (i % 2 == 0) 0.88f else 0.62f) * haloPulse
                        val path = Path().apply {
                            moveTo(center.x, center.y)
                            lineTo(
                                center.x + (cos(baseAngle - halfSpread) * rayLen).toFloat(),
                                center.y + (sin(baseAngle - halfSpread) * rayLen).toFloat(),
                            )
                            lineTo(
                                center.x + (cos(baseAngle + halfSpread) * rayLen).toFloat(),
                                center.y + (sin(baseAngle + halfSpread) * rayLen).toFloat(),
                            )
                            close()
                        }
                        drawPath(
                            path = path,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    c.accent.copy(alpha = 0.16f * crossAlpha),
                                    c.accent.copy(alpha = 0.05f * crossAlpha),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = rayLen,
                            ),
                        )
                    }
                }
            }
        }

        // Phase 0 (only on cold start): Golden AGAPE Bible icon before transitioning to AGAPE
        if (bibleAlpha > 0.01f) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = bibleAlpha
                    scaleX = bibleScale
                    scaleY = bibleScale
                },
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_fg_agape),
                    contentDescription = "AGAPE",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(118.dp)
                        .clip(RoundedCornerShape(26.dp)),
                )
            }
        }

        // Phase 1..7: Sacred halo + Tradition-specific Cross + flowing A G A P E letters
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        alpha = crossAlpha
                        scaleX = crossScale
                        scaleY = crossScale
                    },
            ) {
                // Sacred golden halo rings around the tradition's cross
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                c.accent.copy(alpha = 0.28f),
                                c.accent.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = r * haloPulse,
                        ),
                        radius = r * haloPulse,
                        center = center,
                    )
                    drawCircle(
                        color = c.accent.copy(alpha = 0.42f),
                        radius = r * 0.72f * haloPulse,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                    drawCircle(
                        color = c.accent.copy(alpha = 0.18f),
                        radius = r * 0.90f,
                        center = center,
                        style = Stroke(width = 0.8.dp.toPx()),
                    )
                }

                // Tradition-specific golden cross (Catholic ✝, Eastern Orthodox ☦, Oriental Orthodox ☩)
                TraditionCrossGraphic(
                    tradition = tradition,
                    color = c.accent,
                    size = 40.dp,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Flowing letter-by-letter "A G A P E" reveal with subtle scale & vertical lift
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                letters.forEachIndexed { index, letter ->
                    val revealed = step >= (index + 2)
                    val letterAlpha by animateFloatAsState(
                        targetValue = if (revealed) 1f else 0f,
                        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                        label = "letter_alpha_$index",
                    )
                    val letterOffsetY by animateFloatAsState(
                        targetValue = if (revealed) 0f else 22f,
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                        label = "letter_offset_$index",
                    )
                    val letterScale by animateFloatAsState(
                        targetValue = if (revealed) 1f else 0.88f,
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                        label = "letter_scale_$index",
                    )

                    Text(
                        text = letter,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 40.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.5.sp,
                        ),
                        color = c.ink,
                        modifier = Modifier.graphicsLayer {
                            alpha = letterAlpha
                            translationY = letterOffsetY
                            scaleX = letterScale
                            scaleY = letterScale
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Expanding golden ornamental divider + church tradition tagline
            Box(
                Modifier
                    .width(72.dp * dividerWidthFraction)
                    .height(1.2.dp)
                    .graphicsLayer { alpha = subtitleAlpha * 0.8f }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                c.accent,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.4.sp,
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

/**
 * Draws the distinct liturgical cross for each Christian tradition (matching Photos 3, 4, and 5):
 * - [Tradition.CATHOLIC]: Clean Latin Cross (✝)
 * - [Tradition.EASTERN_ORTHODOX]: Three-bar Byzantine/Slavic Cross (☦) with top titulus and slanted foot-rest
 * - [Tradition.ORIENTAL_ORTHODOX] / null: Coptic/Oriental Cross (☩) with four-end serifs
 */
@Composable
fun TraditionCrossGraphic(
    tradition: Tradition?,
    color: Color,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val stroke = (w * 0.088f).coerceAtLeast(2.4f)

        when (tradition) {
            Tradition.CATHOLIC -> {
                // Photo 3: Classic Latin Cross (✝)
                drawLine(
                    color = color,
                    start = Offset(cx, h * 0.14f),
                    end = Offset(cx, h * 0.86f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.28f, h * 0.35f),
                    end = Offset(w * 0.72f, h * 0.35f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
            }

            Tradition.EASTERN_ORTHODOX -> {
                // Photo 4: Three-bar Orthodox Cross (☦)
                // Main vertical beam
                drawLine(
                    color = color,
                    start = Offset(cx, h * 0.10f),
                    end = Offset(cx, h * 0.90f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
                // Top titulus bar (INRI / IC XC)
                drawLine(
                    color = color,
                    start = Offset(w * 0.36f, h * 0.24f),
                    end = Offset(w * 0.64f, h * 0.24f),
                    strokeWidth = stroke * 0.88f,
                    cap = StrokeCap.Square,
                )
                // Middle main patibulum bar
                drawLine(
                    color = color,
                    start = Offset(w * 0.23f, h * 0.42f),
                    end = Offset(w * 0.77f, h * 0.42f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
                // Slanted lower suppedaneum (foot-rest) bar: higher on the viewer's left, lower on the right
                drawLine(
                    color = color,
                    start = Offset(w * 0.33f, h * 0.64f),
                    end = Offset(w * 0.67f, h * 0.76f),
                    strokeWidth = stroke * 0.88f,
                    cap = StrokeCap.Square,
                )
            }

            Tradition.ORIENTAL_ORTHODOX, null -> {
                // Photo 5: Oriental / Coptic Cross (☩) with serif crosslets at all 4 ends
                val arm = w * 0.34f
                val serif = w * 0.10f
                val serifStroke = stroke * 0.82f
                // Vertical & horizontal arms
                drawLine(
                    color = color,
                    start = Offset(cx, cy - arm),
                    end = Offset(cx, cy + arm),
                    strokeWidth = stroke * 0.88f,
                    cap = StrokeCap.Square,
                )
                drawLine(
                    color = color,
                    start = Offset(cx - arm, cy),
                    end = Offset(cx + arm, cy),
                    strokeWidth = stroke * 0.88f,
                    cap = StrokeCap.Square,
                )
                // Top serif
                drawLine(
                    color = color,
                    start = Offset(cx - serif, cy - arm),
                    end = Offset(cx + serif, cy - arm),
                    strokeWidth = serifStroke,
                    cap = StrokeCap.Square,
                )
                // Bottom serif
                drawLine(
                    color = color,
                    start = Offset(cx - serif, cy + arm),
                    end = Offset(cx + serif, cy + arm),
                    strokeWidth = serifStroke,
                    cap = StrokeCap.Square,
                )
                // Left serif
                drawLine(
                    color = color,
                    start = Offset(cx - arm, cy - serif),
                    end = Offset(cx - arm, cy + serif),
                    strokeWidth = serifStroke,
                    cap = StrokeCap.Square,
                )
                // Right serif
                drawLine(
                    color = color,
                    start = Offset(cx + arm, cy - serif),
                    end = Offset(cx + arm, cy + serif),
                    strokeWidth = serifStroke,
                    cap = StrokeCap.Square,
                )
            }
        }
    }
}
