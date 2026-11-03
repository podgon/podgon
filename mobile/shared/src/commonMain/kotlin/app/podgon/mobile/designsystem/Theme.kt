package app.podgon.mobile.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.podgon.mobile.resources.Res
import app.podgon.mobile.resources.manrope
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.Font

/** Режим темы из «Настроек»: по умолчанию — как в системе (И11). */
@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Шрифт приложения — Manrope (SIL OFL 1.1, `docs/licenses/Manrope-OFL.txt`): один вариативный файл,
 * насыщенность задаётся осью `wght`.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun appFontFamily(): FontFamily {
    val weights =
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
    return FontFamily(
        weights.map { w ->
            Font(Res.font.manrope, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
        },
    )
}

/** Скругления: карточки 22, чипы и строка поиска — капсула (`docs/design.md`, «Формы»). */
object PodgonShapes {
    val card = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(28.dp)
    val capsule = CircleShape
    val tile = RoundedCornerShape(13.dp)
}

@Composable
private fun typography(): Typography {
    val family = appFontFamily()

    fun style(
        size: Int,
        weight: FontWeight,
        line: Int,
        spacing: Double = 0.0,
    ) = TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = line.sp,
        letterSpacing = spacing.sp,
    )
    return Typography(
        displaySmall = style(28, FontWeight.ExtraBold, 34, -0.4),
        headlineMedium = style(24, FontWeight.ExtraBold, 30, -0.3),
        headlineSmall = style(22, FontWeight.ExtraBold, 28, -0.3),
        titleLarge = style(21, FontWeight.ExtraBold, 26, -0.4),
        titleMedium = style(16, FontWeight.ExtraBold, 22),
        titleSmall = style(15, FontWeight.Bold, 20),
        bodyLarge = style(16, FontWeight.Normal, 23),
        bodyMedium = style(15, FontWeight.Medium, 21),
        bodySmall = style(13, FontWeight.Medium, 18),
        labelLarge = style(15, FontWeight.Bold, 20),
        labelMedium = style(13, FontWeight.Bold, 17),
        labelSmall = style(11, FontWeight.SemiBold, 14),
    )
}

/** Тёмная ли тема при режиме [mode] (для системного — по настройке устройства). */
@Composable
fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

@Composable
fun PodgonTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark()
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalPodgonColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(dark),
            typography = typography(),
            shapes =
                Shapes(
                    small = RoundedCornerShape(14.dp),
                    medium = RoundedCornerShape(18.dp),
                    large = PodgonShapes.card,
                    extraLarge = PodgonShapes.sheet,
                ),
            content = content,
        )
    }
}

/** Палитра текущей темы. */
val podgonColors: PodgonColors
    @Composable get() = LocalPodgonColors.current

/** Каркас экрана без навигационной панели: безопасные отступы, ограничение ширины, отступы формы. */
@Composable
fun FormScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                modifier
                    .safeContentPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(32.dp))
            content()
        }
    }
}
