package studio.knit.data

const val DATABASE_NAME = "knitstudio.db"

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
/** The app's accent colour, chosen in Settings. */
enum class AccentColor(val label: String) {
    TERRACOTTA("Terracotta"), SAGE("Sage"), LAVENDER("Lavender"), OCEAN("Ocean"), ROSE("Rose"), MUSTARD("Mustard"),
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
        "imperial" to imperial.toString(), "ukTerms" to ukTerms.toString(), "theme" to theme.name, "accent" to accent.name,
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
