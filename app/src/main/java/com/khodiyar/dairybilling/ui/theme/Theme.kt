@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.khodiyar.dairybilling.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.prefs.ThemeMode

/*
 * DESIGN SYSTEM — "Fresh Dairy"
 * Clean milk-white surfaces, a deep dairy-green brand, a single warm ghee-gold accent used sparingly.
 * Status always pairs color with an icon and a text label.
 */

object Brand {
    val Green900 = Color(0xFF07382F)
    val Green700 = Color(0xFF0F5C4D)
    val Green500 = Color(0xFF2E8B74)
    val Green200 = Color(0xFF9FD8C6)
    val Green100 = Color(0xFFD5F0E6)
    val Green050 = Color(0xFFEDF7F3)
    val Gold600 = Color(0xFFB7791F)
    val Gold100 = Color(0xFFFBEFD5)
    val Milk = Color(0xFFF6F7F4)
    val Ink = Color(0xFF15201C)
}

@Immutable
data class ExtendedColors(
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val surfaceElevated: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val accent: Color,
    val accentContainer: Color,
    val chartPrimary: Color,
    val chartMuted: Color,
)

private val LightColors = lightColorScheme(
    primary = Brand.Green700,
    onPrimary = Color.White,
    primaryContainer = Brand.Green100,
    onPrimaryContainer = Brand.Green900,
    secondary = Color(0xFF4D635B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE8E3),
    onSecondaryContainer = Color(0xFF0B1F18),
    tertiary = Brand.Gold600,
    onTertiary = Color.White,
    tertiaryContainer = Brand.Gold100,
    onTertiaryContainer = Color(0xFF3D2700),
    background = Brand.Milk,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFEDF1EE),
    onSurfaceVariant = Color(0xFF55645E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FAF8),
    surfaceContainer = Color(0xFFF1F4F1),
    surfaceContainerHigh = Color(0xFFEBEFEC),
    surfaceContainerHighest = Color(0xFFE4E9E6),
    outline = Color(0xFFC3CCC7),
    outlineVariant = Color(0xFFE1E6E3),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E6),
    onErrorContainer = Color(0xFF5F1009),
    inverseSurface = Color(0xFF26302C),
    inverseOnSurface = Color(0xFFEFF2F0),
    inversePrimary = Brand.Green200,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD6BD),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF14483C),
    onPrimaryContainer = Color(0xFFBDEFDD),
    secondary = Color(0xFFB4CBC2),
    onSecondary = Color(0xFF1F352E),
    secondaryContainer = Color(0xFF2A3C36),
    onSecondaryContainer = Color(0xFFD0E7DE),
    tertiary = Color(0xFFF0C26A),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF4A3712),
    onTertiaryContainer = Color(0xFFFFE2AE),
    background = Color(0xFF0D1412),
    onBackground = Color(0xFFE3EAE7),
    surface = Color(0xFF131B18),
    onSurface = Color(0xFFE3EAE7),
    surfaceVariant = Color(0xFF1E2925),
    onSurfaceVariant = Color(0xFFA9B8B2),
    surfaceContainerLowest = Color(0xFF0A100E),
    surfaceContainerLow = Color(0xFF141C19),
    surfaceContainer = Color(0xFF18211E),
    surfaceContainerHigh = Color(0xFF1F2A26),
    surfaceContainerHighest = Color(0xFF26322E),
    outline = Color(0xFF3F4D48),
    outlineVariant = Color(0xFF2A3632),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1712),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE3EAE7),
    inverseOnSurface = Color(0xFF1B2421),
    inversePrimary = Brand.Green700,
    scrim = Color.Black,
)

private val LightExtended = ExtendedColors(
    textSecondary = Color(0xFF55645E),
    textTertiary = Color(0xFF7D8A85),
    border = Color(0xFFE1E6E3),
    surfaceElevated = Color.White,
    success = Color(0xFF1C7C54),
    successContainer = Color(0xFFE3F4EA),
    onSuccessContainer = Color(0xFF0C3D27),
    warning = Color(0xFF9A6210),
    warningContainer = Color(0xFFFCF0D8),
    onWarningContainer = Color(0xFF4A2F00),
    info = Color(0xFF2558C9),
    infoContainer = Color(0xFFE6EEFC),
    onInfoContainer = Color(0xFF0E2A66),
    danger = Color(0xFFB42318),
    dangerContainer = Color(0xFFFDE8E6),
    onDangerContainer = Color(0xFF5F1009),
    accent = Brand.Gold600,
    accentContainer = Brand.Gold100,
    chartPrimary = Brand.Green700,
    chartMuted = Color(0xFFD6E3DD),
)

private val DarkExtended = ExtendedColors(
    textSecondary = Color(0xFFA9B8B2),
    textTertiary = Color(0xFF7F8E88),
    border = Color(0xFF2A3632),
    surfaceElevated = Color(0xFF1A2420),
    success = Color(0xFF6FD39D),
    successContainer = Color(0xFF173A29),
    onSuccessContainer = Color(0xFFC4F0D6),
    warning = Color(0xFFF2C46D),
    warningContainer = Color(0xFF42330F),
    onWarningContainer = Color(0xFFFFE3B0),
    info = Color(0xFF9DBBFF),
    infoContainer = Color(0xFF1A2B4F),
    onInfoContainer = Color(0xFFD9E3FF),
    danger = Color(0xFFFFB4AB),
    dangerContainer = Color(0xFF5C1712),
    onDangerContainer = Color(0xFFFFDAD6),
    accent = Color(0xFFF0C26A),
    accentContainer = Color(0xFF4A3712),
    chartPrimary = Color(0xFF7FD6BD),
    chartMuted = Color(0xFF2B3934),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

private val Manrope = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/** Tabular figures keep amounts aligned in lists and tables. */
private const val TNUM = "tnum"

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.8).sp, fontFeatureSettings = TNUM),
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp, fontFeatureSettings = TNUM),
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = TNUM),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

/** Extra type roles beyond Material's scale. */
object AppType {
    val money = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM)
    val moneyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp, fontFeatureSettings = TNUM)
    val tableValue = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM)
    val overline = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.9.sp)
    val quantity = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = TNUM)
}

/** Spacing scale (4dp grid). */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val screen = 16.dp
    val touch = 48.dp
}

object Radius {
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(Radius.s),
    medium = RoundedCornerShape(Radius.m),
    large = RoundedCornerShape(Radius.l),
    extraLarge = RoundedCornerShape(Radius.xl),
)

object AppTheme {
    val colors: ColorScheme @Composable get() = MaterialTheme.colorScheme
    val ext: ExtendedColors @Composable get() = LocalExtendedColors.current
}

@Composable
fun DairyTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // A consistent branded palette is used instead of wallpaper-based dynamic color so that
    // status colors, invoices and charts stay recognisable on every device.
    val scheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalExtendedColors provides if (dark) DarkExtended else LightExtended) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
