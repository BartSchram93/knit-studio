package studio.knit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import studio.knit.data.ThemeMode

/*
 * Knit Studio design system.
 *
 * One restrained palette (grouped greys plus a single madder-red accent), an iOS-style type scale
 * rendered in each platform's system font, and generous spacing. The same tokens drive both
 * platforms so the two apps are recognisably one product.
 */

@Immutable
data class KnitColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val fill: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val chartPaper: Color,
    val chartGrid: Color,
    val chartInk: Color,
    val chartShade: Color,
    /** Background of wrong-side rows on flat charts: a faint warm tint, clearly unlike "no stitch" grey. */
    val chartWsRow: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LightKnitColors = KnitColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    fill = Color(0x1F767680),
    separator = Color(0x2E3C3C43),
    label = Color(0xFF1C1C1E),
    secondaryLabel = Color(0xFF6E6E73),
    tertiaryLabel = Color(0xFFAEAEB2),
    accent = Color(0xFFB4533C),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0x1FB4533C),
    success = Color(0xFF248A3D),
    warning = Color(0xFFB25000),
    error = Color(0xFFD70015),
    info = Color(0xFF3A6EA5),
    chartPaper = Color(0xFFFFFFFF),
    chartGrid = Color(0xFFC7C7CC),
    chartInk = Color(0xFF1C1C1E),
    chartShade = Color(0xFFD1D1D6),
    chartWsRow = Color(0xFFF7EAE4),
    scrim = Color(0x66000000),
    isDark = false,
)

val DarkKnitColors = KnitColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceRaised = Color(0xFF2C2C2E),
    fill = Color(0x3D767680),
    separator = Color(0xA6545458),
    label = Color(0xFFF5F5F7),
    secondaryLabel = Color(0xFF98989F),
    tertiaryLabel = Color(0xFF636366),
    accent = Color(0xFFE2775F),
    onAccent = Color(0xFF1C1C1E),
    accentSoft = Color(0x33E2775F),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    error = Color(0xFFFF453A),
    info = Color(0xFF64A8E8),
    chartPaper = Color(0xFF1C1C1E),
    chartGrid = Color(0xFF48484A),
    chartInk = Color(0xFFF2F2F7),
    chartShade = Color(0xFF3A3A3C),
    chartWsRow = Color(0xFF2E2421),
    scrim = Color(0x99000000),
    isDark = true,
)

@Immutable
data class KnitType(
    val largeTitle: TextStyle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.37.sp),
    val title1: TextStyle = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.36.sp),
    val title2: TextStyle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.35.sp),
    val title3: TextStyle = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    val headline: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    val callout: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    val subhead: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    val footnote: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    val overline: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    /** The big row counter in knitting mode. */
    val counter: TextStyle = TextStyle(fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold),
    /** Written instructions, slightly larger for reading at arm's length. */
    val instruction: TextStyle = TextStyle(fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
)

val LocalKnitColors = staticCompositionLocalOf { LightKnitColors }
val LocalKnitType = staticCompositionLocalOf { KnitType() }

object Knit {
    val colors: KnitColors @Composable get() = LocalKnitColors.current
    val type: KnitType @Composable get() = LocalKnitType.current
}

@Composable
fun KnitTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkKnitColors else LightKnitColors
    // Material components used under the hood (sheets, menus, switches) pick up the same palette.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, onBackground = c.label,
            surface = c.surface, onSurface = c.label, surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.secondaryLabel,
            surfaceContainer = c.surfaceRaised, surfaceContainerHigh = c.surfaceRaised, surfaceContainerLow = c.surface,
            surfaceContainerHighest = c.surfaceRaised, outline = c.separator, outlineVariant = c.separator, error = c.error,
            secondaryContainer = c.accentSoft, onSecondaryContainer = c.label,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.background, onBackground = c.label,
            surface = c.surface, onSurface = c.label, surfaceVariant = c.background, onSurfaceVariant = c.secondaryLabel,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerLow = c.surface,
            surfaceContainerHighest = c.background, outline = c.separator, outlineVariant = c.separator, error = c.error,
            secondaryContainer = c.accentSoft, onSecondaryContainer = c.label,
        )
    }
    CompositionLocalProvider(LocalKnitColors provides c, LocalKnitType provides KnitType()) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
