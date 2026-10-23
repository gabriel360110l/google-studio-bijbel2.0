package agape.app.ui.theme

import agape.app.core.model.VisualStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalColors = staticCompositionLocalOf { Palettes.of(VisualStyle.NAVY_CREAM, false) }
private val LocalType = staticCompositionLocalOf { agapeType(VisualStyle.NAVY_CREAM, 1f) }
private val LocalStyle = staticCompositionLocalOf { VisualStyle.NAVY_CREAM }

object AgapeTheme {
    val colors: AgapeColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: AgapeType @Composable @ReadOnlyComposable get() = LocalType.current
    val style: VisualStyle @Composable @ReadOnlyComposable get() = LocalStyle.current
}

@Composable
fun AgapeTheme(style: VisualStyle, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val colors = remember(style, dark) { Palettes.of(style, dark) }
    val type = remember(style, fontScale) { agapeType(style, fontScale) }
    val typography = remember(style, fontScale) { materialTypography(style, fontScale) }
    val scheme = remember(colors) {
        val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = colors.primary, onPrimary = colors.onPrimary,
            primaryContainer = colors.surfaceSunken, onPrimaryContainer = colors.ink,
            secondary = colors.accent, onSecondary = colors.onAccent,
            secondaryContainer = colors.surfaceSunken, onSecondaryContainer = colors.ink,
            tertiary = colors.accent, onTertiary = colors.onAccent,
            background = colors.background, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceSunken, onSurfaceVariant = colors.inkMuted,
            surfaceContainerLowest = colors.background, surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfaceRaised,
            outline = colors.line, outlineVariant = colors.line,
            error = colors.danger, onError = colors.onPrimary,
            inverseSurface = colors.ink, inverseOnSurface = colors.background,
        )
    }
    CompositionLocalProvider(LocalColors provides colors, LocalType provides type, LocalStyle provides style) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
