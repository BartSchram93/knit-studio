package studio.knit.convert

import studio.knit.chart.MAX_CHART_SIZE
import studio.knit.model.Cell
import studio.knit.model.Chart
import studio.knit.model.ChartColor
import studio.knit.model.ChartRepeat
import studio.knit.model.Section
import studio.knit.model.Side
import studio.knit.stitch.BuiltInStitches
import studio.knit.stitch.StitchLibrary
import studio.knit.validate.Severity
import studio.knit.written.RowEvaluator
import studio.knit.written.Worked
import studio.knit.written.evaluateSection
import studio.knit.written.isOpenEnded

data class TextChart(val chart: Chart?, val notes: List<ConversionNote>)

/** Colours handed out to `with A`, `with B`, … when a chart is drawn from written rows. */
val DefaultChartColors: List<Long> = listOf(
    0xFFC2553D, 0xFF2F5D62, 0xFFE0B04A, 0xFF5B6CA8, 0xFF8A9A5B, 0xFF9B5C8F, 0xFF3F3F46, 0xFFE8DCC8,
)

/**
 * Draws a chart from a written section.
 *
 * Two strategies, chosen automatically:
 *  - **One repeat** — when every row can be laid out without knowing the stitch count, each
 *    `*…; rep from *` is drawn once inside a repeat box, with the edge stitches around it.
 *  - **Full width** — otherwise every stitch is drawn, which needs a known cast-on count.
 *
 * Returns no chart, with an explanation, when neither is possible. Rows whose stitch counts
 * differ are right-aligned and padded with "no stitch" cells; that alignment is a guess and is
 * reported as such.
 */
fun sectionToChart(
    section: Section,
    sizeIndex: Int,
    library: StitchLibrary,
    newId: () -> String,
): TextChart {
    val notes = ArrayList<ConversionNote>()
    val eval = evaluateSection(section, sizeIndex, library)
    if (eval.rows.isEmpty()) return TextChart(null, listOf(ConversionNote(Severity.ERROR, null, "There are no rows to draw")))
    if (eval.rows.size > MAX_CHART_SIZE) {
        return TextChart(null, listOf(ConversionNote(Severity.ERROR, null, "This section has ${eval.rows.size} rows; charts are limited to $MAX_CHART_SIZE")))
    }
    for (row in eval.rows) {
        for (e in row.parsed.errors) notes += ConversionNote(Severity.ERROR, row.index, "Row ${row.number}: ${e.message}")
    }
    if (notes.any { it.severity == Severity.ERROR }) return TextChart(null, notes)

    // Strategy 1: one repeat per row.
    val compactEval = RowEvaluator(sizeIndex)
    val hasRepeats = eval.rows.any { r -> r.parsed.nodes.any { it.isOpenEnded() } }
    var layout: List<List<Worked>>? = null
    var compact = false
    if (hasRepeats) {
        val rows = eval.rows.map { compactEval.evaluate(it.parsed, null, compact = true).stitches }
        if (rows.all { it != null }) {
            val widths = rows.map { r -> r!!.sumOf { it.def.width } }
            if (widths.distinct().size == 1 && widths[0] in 1..MAX_CHART_SIZE) {
                layout = rows.map { it!! }
                compact = true
            }
        }
    }
    // Strategy 2: everything, needs resolved counts.
    if (layout == null) {
        val unresolved = eval.rows.firstOrNull { it.eval.stitches == null }
        if (unresolved != null) {
            notes += ConversionNote(
                Severity.ERROR, unresolved.index,
                "Row ${unresolved.number} uses “to end” but the stitch count is unknown. Set the cast-on count for this section, then try again",
            )
            return TextChart(null, notes)
        }
        layout = eval.rows.map { it.eval.stitches!! }
    }

    val widths = layout.map { r -> r.sumOf { it.def.width } }
    val cols = widths.max()
    if (cols < 1) return TextChart(null, notes + ConversionNote(Severity.ERROR, null, "The rows contain no stitches that can be charted"))
    if (cols > MAX_CHART_SIZE) {
        return TextChart(null, notes + ConversionNote(Severity.ERROR, null, "The widest row has $cols stitches; charts are limited to $MAX_CHART_SIZE"))
    }
    if (widths.distinct().size > 1) {
        notes += ConversionNote(
            Severity.INFO, null,
            "Rows have different stitch counts. “No stitch” cells were added at the left edge — move them in the chart editor if the shaping sits elsewhere",
        )
    }

    // Colours named in the text.
    val colorNames = LinkedHashSet<String>()
    for (row in layout) for (w in row) w.color?.let { if (it != "MC") colorNames += it }
    val palette = colorNames.mapIndexed { i, name -> ChartColor(name, DefaultChartColors[i % DefaultChartColors.size]) }
    val colorIndex = colorNames.withIndex().associate { it.value to it.index }

    val rowsCount = layout.size
    val cells = MutableList(cols * rowsCount) { Cell(BuiltInStitches.NO_STITCH) }
    val repeatRanges = arrayOfNulls<IntRange>(rowsCount)
    val warnedWs = HashSet<String>()

    layout.forEachIndexed { r, stitches ->
        val ws = eval.rows[r].side == Side.WS
        val width = widths[r]
        // RS rows run right → left from the right edge; WS rows left → right, ending at it.
        var x = if (ws) cols - width else cols - 1
        var repMin = Int.MAX_VALUE
        var repMax = Int.MIN_VALUE
        for (w in stitches) {
            var def = w.def
            if (def.width == 0) continue
            if (ws) {
                val symbol = library.symbolForWorkedOnWs(def)
                if (symbol != null) def = symbol
                else if (warnedWs.add(def.id)) {
                    notes += ConversionNote(
                        Severity.WARNING, r,
                        "“${def.name}” worked on a wrong-side row has no chart symbol of its own; the right-side symbol is shown",
                    )
                }
            }
            val left = if (ws) x else x - def.width + 1
            val color = w.color?.let { colorIndex[it] } ?: -1
            for (k in 0 until def.width) cells[r * cols + left + k] = Cell(def.id, color, k)
            if (w.inRepeat) { repMin = minOf(repMin, left); repMax = maxOf(repMax, left + def.width - 1) }
            x = if (ws) x + def.width else x - def.width
        }
        if (repMin <= repMax) repeatRanges[r] = repMin..repMax
    }

    // Merge consecutive rows that share a repeat range into one box.
    val repeats = ArrayList<ChartRepeat>()
    var r = 0
    while (r < rowsCount) {
        val range = repeatRanges[r]
        if (range == null) { r++; continue }
        var end = r
        while (end + 1 < rowsCount && repeatRanges[end + 1] == range) end++
        repeats += ChartRepeat(newId(), range.first, range.last, r, end)
        r = end + 1
    }
    if (compact) {
        notes += ConversionNote(Severity.INFO, null, "Each repeat is drawn once inside a repeat box")
    }

    val chart = Chart(
        id = newId(),
        name = section.title,
        cols = cols,
        rows = rowsCount,
        cells = cells,
        construction = section.construction,
        firstRowSide = section.firstRowSide,
        repeats = repeats,
        palette = palette,
    )
    return TextChart(chart, notes)
}
