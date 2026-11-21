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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import studio.knit.data.AccentColor
import studio.knit.data.ThemeMode
import studio.knit.ui.res.Res
import studio.knit.ui.res.fraunces_bold
import studio.knit.ui.res.fraunces_semibold

/*
 * Knit Studio design system: warm and cosy.
 *
 * Cream paper and warm browns instead of grey, one accent colour the knitter picks herself
 * (terracotta by default), and headings in Fraunces, a soft serif. Body text stays in the
 * platform's own font for easy reading. The same tokens drive both platforms.
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
    /** Colours for confetti, category tiles and other small decorations. */
    val festive: List<Color> = Festive,
)

/** The palette's companion colours: terracotta, sage, mustard, lavender, ocean, rose. */
val Festive = listOf(Color(0xFFC2603F), Color(0xFF6F9068), Color(0xFFD9A441), Color(0xFF8C7BB8), Color(0xFF3F7E9C), Color(0xFFC8637F))

/** Accent colour per choice, for light and dark mode. */
fun accentOf(accent: AccentColor, dark: Boolean): Color = when (accent) {
    AccentColor.TERRACOTTA -> if (dark) Color(0xFFE58B6E) else Color(0xFFB4533C)
    AccentColor.SAGE -> if (dark) Color(0xFF9CBE94) else Color(0xFF5A7D55)
    AccentColor.LAVENDER -> if (dark) Color(0xFFB3A3DE) else Color(0xFF7562A6)
    AccentColor.OCEAN -> if (dark) Color(0xFF78B6D4) else Color(0xFF2E6B8A)
    AccentColor.ROSE -> if (dark) Color(0xFFE88FA7) else Color(0xFFB0476A)
    AccentColor.MUSTARD -> if (dark) Color(0xFFE6BC5C) else Color(0xFF9A6B0E)
    AccentColor.GOLD -> if (dark) Color(0xFFF0C75E) else Color(0xFFA67C00)
    AccentColor.MIDNIGHT -> if (dark) Color(0xFF8FA4E8) else Color(0xFF2B3A78)
    AccentColor.BERRY -> if (dark) Color(0xFFD889C8) else Color(0xFF8B2F72)
}

fun lightColors(accent: Color) = KnitColors(
    background = Color(0xFFF8F1E7),
    surface = Color(0xFFFFFCF7),
    surfaceRaised = Color(0xFFFFFCF7),
    fill = Color(0x1F8A6A4F),
    separator = Color(0x2E6B4E37),
    label = Color(0xFF2E2420),
    secondaryLabel = Color(0xFF7A685B),
    tertiaryLabel = Color(0xFFB9A796),
    accent = accent,
    onAccent = Color(0xFFFFFFFF),
    accentSoft = accent.copy(alpha = 0.13f),
    success = Color(0xFF3F7D45),
    warning = Color(0xFFAD5A00),
    error = Color(0xFFC62828),
    info = Color(0xFF3A6EA5),
    chartPaper = Color(0xFFFFFDF9),
    chartGrid = Color(0xFFD8CABB),
    chartInk = Color(0xFF2E2420),
    chartShade = Color(0xFFDCD1C4),
    chartWsRow = Color(0xFFF6E8DE),
    scrim = Color(0x66221812),
    isDark = false,
)

fun darkColors(accent: Color) = KnitColors(
    background = Color(0xFF17120F),
    surface = Color(0xFF241D19),
    surfaceRaised = Color(0xFF2E2621),
    fill = Color(0x3DA08A78),
    separator = Color(0x66806A5A),
    label = Color(0xFFF6EEE6),
    secondaryLabel = Color(0xFFB4A394),
    tertiaryLabel = Color(0xFF7A6A5E),
    accent = accent,
    onAccent = Color(0xFF1E1714),
    accentSoft = accent.copy(alpha = 0.2f),
    success = Color(0xFF7FC486),
    warning = Color(0xFFFFB45C),
    error = Color(0xFFFF6B5E),
    info = Color(0xFF7DB3E8),
    chartPaper = Color(0xFF241D19),
    chartGrid = Color(0xFF4A3F37),
    chartInk = Color(0xFFF6EEE6),
    chartShade = Color(0xFF3B322C),
    chartWsRow = Color(0xFF33261F),
    scrim = Color(0x99000000),
    isDark = true,
)

/** Kept for code and tests that need a palette outside the theme. */
val LightKnitColors = lightColors(accentOf(AccentColor.TERRACOTTA, false))
val DarkKnitColors = darkColors(accentOf(AccentColor.TERRACOTTA, true))

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

/** The type scale with headings in [display] (Fraunces). */
fun KnitType.withDisplay(display: FontFamily): KnitType = copy(
    largeTitle = largeTitle.copy(fontFamily = display, letterSpacing = 0.sp),
    title1 = title1.copy(fontFamily = display, letterSpacing = 0.sp),
    title2 = title2.copy(fontFamily = display, letterSpacing = 0.sp),
    title3 = title3.copy(fontFamily = display),
    counter = counter.copy(fontFamily = display),
)

val LocalKnitColors = staticCompositionLocalOf { LightKnitColors }
val LocalKnitType = staticCompositionLocalOf { KnitType() }

object Knit {
    val colors: KnitColors @Composable get() = LocalKnitColors.current
    val type: KnitType @Composable get() = LocalKnitType.current
}

@Composable
fun KnitTheme(mode: ThemeMode = ThemeMode.SYSTEM, accent: AccentColor = AccentColor.TERRACOTTA, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accentColor = accentOf(accent, dark)
    val c = if (dark) darkColors(accentColor) else lightColors(accentColor)
    val display = FontFamily(
        Font(Res.font.fraunces_semibold, FontWeight.SemiBold),
        Font(Res.font.fraunces_bold, FontWeight.Bold),
    )
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
    CompositionLocalProvider(LocalKnitColors provides c, LocalKnitType provides KnitType().withDisplay(display)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
