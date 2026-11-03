package app.podgon.mobile.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Палитра приложения («Визуальный стиль» в `docs/design.md`); значения — из макетов. */
@Immutable
data class PodgonColors(
    val background: Color,
    val surface: Color,
    /** Подложка чипов, скелетонов, счётчиков. */
    val chip: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    /** Мягкий акцент: фон бейджа оценки. */
    val accentSoft: Color,
    /** Выбранная «таблетка»: заливка цветом текста. */
    val pillOn: Color,
    val pillOnText: Color,
    val scrim: Color,
    /** Мягкие тени только в светлой теме; в тёмной вместо теней — линии. */
    val shadows: Boolean,
)

val LightColors =
    PodgonColors(
        background = Color(0xFFF6F5F2),
        surface = Color(0xFFFFFFFF),
        chip = Color(0xFFECEAE5),
        ink = Color(0xFF1B1B1F),
        muted = Color(0xFF5E5D66),
        line = Color(0xFFE4E2DD),
        accent = Color(0xFFD9532F),
        accentSoft = Color(0xFFFBE4DC),
        pillOn = Color(0xFF1B1B1F),
        pillOnText = Color(0xFFFFFFFF),
        scrim = Color(0x521B1B1F),
        shadows = true,
    )

val DarkColors =
    PodgonColors(
        background = Color(0xFF121214),
        surface = Color(0xFF1E1E22),
        chip = Color(0xFF2C2C31),
        ink = Color(0xFFF2F1EE),
        muted = Color(0xFFA9A8B0),
        line = Color(0xFF2E2E34),
        accent = Color(0xFFF0673F),
        accentSoft = Color(0xFF3A2219),
        pillOn = Color(0xFFF2F1EE),
        pillOnText = Color(0xFF121214),
        scrim = Color(0x8C000000),
        shadows = false,
    )

val LocalPodgonColors = staticCompositionLocalOf { LightColors }

/** Схема Material 3 из палитры: стандартные компоненты получают те же цвета. */
fun PodgonColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        secondary = ink,
        onSecondary = surface,
        secondaryContainer = chip,
        onSecondaryContainer = ink,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = chip,
        onSurfaceVariant = muted,
        surfaceContainer = surface,
        surfaceContainerLow = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = chip,
        outline = line,
        outlineVariant = line,
        scrim = scrim,
    )
}
