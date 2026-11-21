package studio.knit.data

const val DATABASE_NAME = "knitstudio.db"

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
/** The app's accent colour, chosen in Settings. */
enum class AccentColor(val label: String, val reward: Boolean = false) {
    TERRACOTTA("Terracotta"), SAGE("Sage"), LAVENDER("Lavender"), OCEAN("Ocean"), ROSE("Rose"), MUSTARD("Mustard"),
    // Unlocked with achievements.
    GOLD("Gold", reward = true), MIDNIGHT("Midnight", reward = true), BERRY("Berry", reward = true),
}

/** What the celebration confetti looks like; the extra styles are achievement rewards. */
enum class ConfettiStyle(val label: String, val reward: Boolean = false) {
    CLASSIC("Classic"), YARN("Yarn balls", reward = true), STARS("Stars", reward = true), LEAVES("Leaves", reward = true),
}

enum class PaperSize(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4", 595.28f, 841.89f), LETTER("US Letter", 612f, 792f),
}

/** User preferences. Persisted as key/value rows so new settings never need a migration. */
data class Settings(
    val imperial: Boolean = false,
    val ukTerms: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.TERRACOTTA,
    val confetti: ConfettiStyle = ConfettiStyle.CLASSIC,
    /** Stitches per panel when a wide chart is shown in panels; 0 = fit to the screen. */
    val panelStitches: Int = 0,
    /** Show levels, badges and goals. */
    val achievements: Boolean = true,
    /** Earned achievements and related bookkeeping, as JSON (see AchievementState). */
    val achievementState: String = "",
    val paper: PaperSize = PaperSize.A4,
    val pdfLandscape: Boolean = false,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Tint rows worked from the wrong side on flat charts. */
    val shadeWsRows: Boolean = true,
    /** Show the current row on the lock screen while Knit mode is open. */
    val lockScreenRow: Boolean = true,
    val lastBackupAt: Long = 0,
) {
    fun toMap(): Map<String, String> = mapOf(
        "imperial" to imperial.toString(), "ukTerms" to ukTerms.toString(), "theme" to theme.name, "accent" to accent.name, "confetti" to confetti.name, "panelStitches" to panelStitches.toString(),
        "achievements" to achievements.toString(), "achievementState" to achievementState,
        "paper" to paper.name, "pdfLandscape" to pdfLandscape.toString(), "haptics" to haptics.toString(),
        "keepScreenOn" to keepScreenOn.toString(), "shadeWsRows" to shadeWsRows.toString(), "lockScreenRow" to lockScreenRow.toString(), "lastBackupAt" to lastBackupAt.toString(),
    )

    companion object {
        fun fromMap(m: Map<String, String>): Settings {
            val d = Settings()
            return Settings(
                imperial = m["imperial"]?.toBooleanStrictOrNull() ?: d.imperial,
                ukTerms = m["ukTerms"]?.toBooleanStrictOrNull() ?: d.ukTerms,
                theme = m["theme"]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: d.theme,
                accent = m["accent"]?.let { v -> AccentColor.entries.firstOrNull { it.name == v } } ?: d.accent,
                panelStitches = m["panelStitches"]?.toIntOrNull()?.coerceIn(0, 60) ?: d.panelStitches,
                confetti = m["confetti"]?.let { v -> ConfettiStyle.entries.firstOrNull { it.name == v } } ?: d.confetti,
                achievements = m["achievements"]?.toBooleanStrictOrNull() ?: d.achievements,
                achievementState = m["achievementState"] ?: d.achievementState,
                paper = m["paper"]?.let { v -> PaperSize.entries.firstOrNull { it.name == v } } ?: d.paper,
                pdfLandscape = m["pdfLandscape"]?.toBooleanStrictOrNull() ?: d.pdfLandscape,
                haptics = m["haptics"]?.toBooleanStrictOrNull() ?: d.haptics,
                keepScreenOn = m["keepScreenOn"]?.toBooleanStrictOrNull() ?: d.keepScreenOn,
                shadeWsRows = m["shadeWsRows"]?.toBooleanStrictOrNull() ?: d.shadeWsRows,
                lockScreenRow = m["lockScreenRow"]?.toBooleanStrictOrNull() ?: d.lockScreenRow,
                lastBackupAt = m["lastBackupAt"]?.toLongOrNull() ?: d.lastBackupAt,
            )
        }
    }
}
