package studio.knit.companion

import studio.knit.model.Construction
import studio.knit.model.Pattern
import studio.knit.chart.sideOf
import studio.knit.convert.chartToText
import studio.knit.stats.dayKey
import studio.knit.stats.formatDuration
import studio.knit.stats.logRowsAt
import studio.knit.stitch.StitchLibrary

/**
 * What the lock screen shows while knitting: the row to work now, small enough for a
 * notification or a Live Activity, and the same on every platform.
 */
data class KnitGlance(
    val patternId: String,
    val patternName: String,
    /** Section or chart being worked. */
    val part: String,
    /** "Row 12 of 80 (WS)", "Rnd 4 of 30", "Chart row 3". */
    val row: String,
    /** The instruction for the row, as written. */
    val instruction: String,
    /** "24 sts" or "", plus the repeat being worked. */
    val detail: String,
    /** Rows done / rows in the part; null for charts that repeat without end. */
    val fraction: Float?,
    val finished: Boolean,
    val canGoBack: Boolean,
    /** "Today 12 rows · 1 h 17 min", or "" before any knitting today. */
    val today: String = "",
)

/** The glance for the part currently being knitted, or null when there is nothing to knit. */
fun knitGlance(pattern: Pattern, library: StitchLibrary, todayKey: String? = null, uk: Boolean = false): KnitGlance? =
    glanceOf(pattern, library, uk)?.let { g ->
        val day = todayKey?.let { pattern.progress?.days?.get(it) }
        if (day == null || (day.rows == 0 && day.seconds < 60)) g
        else g.copy(today = "Today ${day.rows} ${if (day.rows == 1) "row" else "rows"}" + if (day.seconds >= 60) " · ${formatDuration(day.seconds)}" else "")
    }

private fun glanceOf(pattern: Pattern, library: StitchLibrary, uk: Boolean): KnitGlance? {
    val sizeIndex = pattern.progress?.sizeIndex ?: 0
    val tracks = tracksOf(pattern, sizeIndex, library)
    val track = tracks.firstOrNull { it.id == pattern.progress?.activeTrackId } ?: tracks.firstOrNull() ?: return null
    val pos = track.position(pattern.progress)
    val name = pattern.displayName
    if (track.repeats) {
        // A chart worked on its own: the row written out, as the knitter would read it.
        val chart = track.chart!!
        val index = pos.rowIndex
        val text = runCatching { chartToText(chart, library, uk).first.getOrNull(index) }.getOrNull()
        val round = chart.construction == Construction.ROUND
        val side = if (round) "" else " (${chart.sideOf(index).name})"
        val number = index + chart.firstRowNumber
        return KnitGlance(
            pattern.id, name, track.title, "${if (round) "Rnd" else "Row"} $number of ${chart.rows}$side",
            text?.takeIf { it.isNotBlank() }?.let { it.replaceFirstChar { c -> c.uppercase() } + "." } ?: "Work chart row $number",
            if (pos.pass > 0) "Chart repeat ${pos.pass + 1}" else "",
            (index.toFloat() / chart.rows), finished = false, canGoBack = pos.done > 0,
        )
    }
    val round = track.section?.construction == Construction.ROUND
    if (pos.finished) {
        return KnitGlance(pattern.id, name, track.title, "Finished", "All ${track.length} ${if (round) "rounds" else "rows"} of ${track.title} are done.", "", 1f, finished = true, canGoBack = true)
    }
    val r = pos.expanded!!
    val word = if (round) "Rnd" else "Row"
    val side = if (round) "" else " (${r.side.name})"
    val detail = listOfNotNull(
        r.eval.produced?.let { "$it sts" },
        r.viaRepeat?.let { "repeat ${r.repeatIteration} of ${r.repeatTotal}" },
        r.source.note.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    return KnitGlance(
        pattern.id, name, track.title, "$word ${pos.done + 1} of ${track.length}$side",
        r.source.text.trim().ifEmpty { "(no instructions)" }, detail,
        pos.done.toFloat() / track.length, finished = false, canGoBack = pos.done > 0,
    )
}

/** Moves the active part forward or back, as the buttons in Knit mode do, and logs the rows. */
fun Pattern.stepKnitting(library: StitchLibrary, delta: Int, now: Long): Pattern {
    val progress = progress ?: startProgress(this, now)
    val tracks = tracksOf(this, progress.sizeIndex, library)
    val track = tracks.firstOrNull { it.id == progress.activeTrackId } ?: tracks.firstOrNull() ?: return this
    val next = progress.step(track, delta, now)
    val moved = (next.positions[track.id] ?: 0) - (progress.positions[track.id] ?: 0)
    return copy(progress = next.logRowsAt(now, moved))
}
