package studio.knit.companion

import studio.knit.model.Chart
import studio.knit.model.Counter
import studio.knit.model.Pattern
import studio.knit.model.Progress
import studio.knit.model.Section
import studio.knit.stitch.StitchLibrary
import studio.knit.written.ExpandedRow
import studio.knit.written.SectionEval
import studio.knit.written.evaluateSection

/*
 * Knitting-progress logic for the companion mode, kept free of UI so it can be unit tested.
 *
 * A "track" is something the knitter works through row by row: a written section, or a chart
 * that has no written section of its own. Charts are worked repeatedly from bottom to top, so
 * a chart track has no fixed end.
 */

data class Track(
    val id: String,
    val title: String,
    val section: Section? = null,
    val chart: Chart? = null,
    /** Unrolled rows for a section track. */
    val eval: SectionEval? = null,
) {
    /** Rows in one pass. For sections this is the whole section. */
    val length: Int get() = eval?.rows?.size ?: chart?.rows ?: 0
    /** Chart-only tracks start over after the last row. */
    val repeats: Boolean get() = section == null
}

fun tracksOf(pattern: Pattern, sizeIndex: Int, library: StitchLibrary): List<Track> {
    val out = ArrayList<Track>()
    val linked = pattern.sections.mapNotNull { it.chartId }.toSet()
    for (s in pattern.sections) {
        val eval = evaluateSection(s, sizeIndex, library)
        if (eval.rows.isEmpty()) continue
        out += Track(s.id, s.title.ifBlank { "Section" }, section = s, chart = pattern.charts.firstOrNull { it.id == s.chartId }, eval = eval)
    }
    for (c in pattern.charts) {
        if (c.id in linked || c.cells.size != c.cols * c.rows) continue
        out += Track(c.id, c.name.ifBlank { "Chart" }, chart = c)
    }
    return out
}

/** Where the knitter is within one track. */
data class Position(
    /** Rows completed in total. */
    val done: Int,
    /** Zero-based row to work next within the track (wraps for chart tracks). */
    val rowIndex: Int,
    /** Completed passes of a repeating chart track. */
    val pass: Int,
    val finished: Boolean,
    val expanded: ExpandedRow?,
    /** Chart row to highlight, if the track has a chart whose rows line up with the track. */
    val chartRow: Int?,
)

fun Track.position(progress: Progress?): Position {
    val done = (progress?.positions?.get(id) ?: 0).coerceAtLeast(0)
    val len = length
    if (len == 0) return Position(done, 0, 0, true, null, null)
    return if (repeats) {
        Position(done, done % len, done / len, false, null, done % len)
    } else {
        val finished = done >= len
        val index = done.coerceAtMost(len - 1)
        val row = eval!!.rows[index]
        // A linked chart mirrors the section's instruction rows one to one; row repeats wrap
        // back to the chart rows they repeat.
        val chartRow = chart?.let { c ->
            val instructionIndex = section!!.rows.filter { it.kind == studio.knit.model.RowKind.INSTRUCTION }.indexOfFirst { it.id == row.source.id }
            instructionIndex.takeIf { it in 0 until c.rows }
        }
        Position(done.coerceAtMost(len), index, 0, finished, row, chartRow)
    }
}

fun startProgress(pattern: Pattern, now: Long, sizeIndex: Int = 0): Progress =
    pattern.progress ?: Progress(startedAt = now, lastKnitAt = now, sizeIndex = sizeIndex.coerceIn(0, pattern.sizeCount - 1))

/**
 * Moves [trackId] forward or back by [delta] rows. Counters linked to the row counter follow.
 * Never goes below zero, and never past the end of a section.
 */
fun Progress.step(track: Track, delta: Int, now: Long): Progress {
    val cur = positions[track.id] ?: 0
    val max = if (track.repeats) Int.MAX_VALUE else track.length
    val next = (cur + delta).coerceIn(0, max)
    val moved = next - cur
    if (moved == 0) return this
    return copy(
        positions = positions + (track.id to next),
        activeTrackId = track.id,
        lastKnitAt = now,
        counters = counters.map { if (it.linked) it.bump(moved) else it },
    )
}

fun Progress.setPosition(track: Track, done: Int, now: Long): Progress =
    copy(positions = positions + (track.id to done.coerceIn(0, if (track.repeats) Int.MAX_VALUE else track.length)), activeTrackId = track.id, lastKnitAt = now)

/** Adds [delta] to a counter, wrapping at [Counter.resetAt] and counting completed laps. */
fun Counter.bump(delta: Int): Counter {
    val limit = resetAt
    if (limit == null || limit <= 0) return copy(value = (value + delta).coerceAtLeast(0))
    var total = laps * limit + value + delta
    if (total < 0) total = 0
    return copy(value = total % limit, laps = total / limit)
}

/** Overall progress 0..1 across all section tracks, or null when there is nothing finite to measure. */
fun overallProgress(pattern: Pattern, library: StitchLibrary): Float? {
    val progress = pattern.progress ?: return null
    val tracks = tracksOf(pattern, progress.sizeIndex, library).filter { !it.repeats }
    val total = tracks.sumOf { it.length }
    if (total == 0) return null
    val done = tracks.sumOf { (progress.positions[it.id] ?: 0).coerceIn(0, it.length) }
    return done.toFloat() / total
}

/** True when every finite track has been worked to its end. */
fun isComplete(pattern: Pattern, library: StitchLibrary): Boolean = overallProgress(pattern, library)?.let { it >= 1f } ?: false

/** One-line status such as "Body · Row 12 of 80". */
fun statusLine(pattern: Pattern, library: StitchLibrary): String? {
    val progress = pattern.progress ?: return null
    val tracks = tracksOf(pattern, progress.sizeIndex, library)
    val track = tracks.firstOrNull { it.id == progress.activeTrackId } ?: tracks.firstOrNull() ?: return null
    val pos = track.position(progress)
    return when {
        track.repeats -> "${track.title} · Row ${pos.rowIndex + 1} of ${track.length}" + if (pos.pass > 0) " · repeat ${pos.pass + 1}" else ""
        pos.finished -> "${track.title} · finished"
        else -> "${track.title} · Row ${pos.rowIndex + 1} of ${track.length}"
    }
}
