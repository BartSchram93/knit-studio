package studio.knit.convert

import studio.knit.chart.colorName
import studio.knit.chart.repeatsOnRow
import studio.knit.chart.sideOf
import studio.knit.chart.stitchesInOrder
import studio.knit.chart.usedColors
import studio.knit.model.Chart
import studio.knit.model.Construction
import studio.knit.model.Row
import studio.knit.model.Section
import studio.knit.model.Side
import studio.knit.model.SizedInt
import studio.knit.stitch.BuiltInStitches
import studio.knit.stitch.StitchLibrary
import studio.knit.validate.Severity
import studio.knit.written.Count
import studio.knit.written.Node
import studio.knit.written.RowPrinter

/** Something the converter could not express faithfully. Never dropped silently. */
data class ConversionNote(val severity: Severity, val row: Int?, val message: String)

data class ChartText(
    /** One entry per chart row, bottom row first. */
    val rows: List<List<Node>>,
    val notes: List<ConversionNote>,
)

/**
 * Reads a chart the way a knitter does and produces structured written rows.
 *
 * - Rows are read in knitting order for their side.
 * - On wrong-side rows each symbol is replaced by the stitch actually worked (blank → purl).
 * - A repeat box becomes `*…; rep from * to last N sts`, where N is what the stitches after the
 *   box consume.
 * - Colourwork charts name the colour in use whenever it changes.
 */
fun chartToNodes(chart: Chart, library: StitchLibrary): ChartText {
    val notes = ArrayList<ConversionNote>()
    val colorwork = chart.usedColors().isNotEmpty()
    val rows = ArrayList<List<Node>>(chart.rows)
    val reportedMissingWs = HashSet<String>()

    for (r in 0 until chart.rows) {
        val ws = chart.sideOf(r) == Side.WS
        val boxes = chart.repeatsOnRow(r)
        val box = boxes.firstOrNull()
        if (boxes.size > 1) {
            notes += ConversionNote(Severity.WARNING, r, "Row ${r + chart.firstRowNumber} has ${boxes.size} repeat boxes; only the first is written as a repeat, the others are written out once")
        }

        // Segments: before the repeat, inside it, after it — in knitting order.
        val before = ArrayList<Node>()
        val inside = ArrayList<Node>()
        val after = ArrayList<Node>()
        var afterConsumes = 0
        var seenInside = false
        var lastColor: String? = null

        for (s in chart.stitchesInOrder(r, library)) {
            if (s.def.id == BuiltInStitches.NO_STITCH) continue
            var def = s.def
            if (library[s.cell.stitch] == null && reportedMissingWs.add("missing:${s.cell.stitch}")) {
                notes += ConversionNote(Severity.ERROR, r, "Stitch “${s.cell.stitch}” is not in the stitch library")
            }
            if (ws) {
                val worked = def.ws?.let { library[it] }
                if (worked != null) def = worked
                else if (reportedMissingWs.add(def.id)) {
                    notes += ConversionNote(
                        Severity.WARNING, r,
                        "“${def.name}” has no wrong-side equivalent defined; row ${r + chart.firstRowNumber} uses its right-side wording",
                    )
                }
            }
            val inBox = box != null && s.col in box.colStart..box.colEnd
            val target = when {
                inBox -> { seenInside = true; inside }
                box != null && seenInside -> after
                else -> before
            }
            if (target === after) afterConsumes += def.consumes

            if (colorwork) {
                val name = chart.colorName(s.cell.color)
                // Restate the colour at the start of each segment so a repeat reads on its own.
                if (name != lastColor || target.isEmpty()) {
                    target += Node.Color(name)
                    lastColor = name
                }
            }
            val last = target.lastOrNull()
            if (last is Node.Stitch && last.def.id == def.id && last.count is Count.Fixed) {
                target[target.size - 1] = Node.Stitch(def, Count.Fixed(last.count.n.values[0] + 1))
            } else {
                target += Node.Stitch(def, Count.Fixed(1))
            }
        }

        val nodes = ArrayList<Node>(compressRuns(before))
        if (inside.isNotEmpty()) nodes += Node.Group(inside, Count.ToEnd(SizedInt(afterConsumes)), star = true)
        nodes += compressRuns(after)
        rows += nodes
    }
    return ChartText(rows, notes)
}

/** Written text for every row of [chart], bottom row first. */
fun chartToText(chart: Chart, library: StitchLibrary, uk: Boolean = false): Pair<List<String>, List<ConversionNote>> {
    val result = chartToNodes(chart, library)
    val printer = RowPrinter(uk)
    return result.rows.map { printer.print(it) } to result.notes
}

/**
 * Builds a written section mirroring [chart]. The section is linked to the chart and flagged as
 * generated so the UI can tell it apart from hand-written rows.
 */
fun chartToSection(chart: Chart, library: StitchLibrary, newId: () -> String, uk: Boolean = false): Pair<Section, List<ConversionNote>> {
    val (texts, notes) = chartToText(chart, library, uk)
    val section = Section(
        id = newId(),
        title = chart.name,
        construction = chart.construction,
        firstRowSide = if (chart.construction == Construction.ROUND) Side.RS else chart.firstRowSide,
        rows = texts.map { Row(id = newId(), text = it) },
        chartId = chart.id,
        generated = true,
    )
    return section to notes
}

/**
 * Writes a sequence that repeats as a bracketed group: "k4, k2tog, yo, k4, k2tog, yo, k4,
 * k2tog, yo" becomes "(k4, k2tog, yo) 3 times". Only exact repeats of 2 or more stitches are
 * grouped, so nothing changes meaning.
 */
internal fun compressRuns(nodes: List<Node>): List<Node> {
    if (nodes.size < 4) return nodes
    val out = ArrayList<Node>()
    var i = 0
    while (i < nodes.size) {
        var bestP = 0
        var bestK = 1
        for (p in 2..((nodes.size - i) / 2)) {
            var k = 1
            while (i + (k + 1) * p <= nodes.size && (0 until p).all { j -> nodes[i + j] == nodes[i + k * p + j] }) k++
            // Prefer the grouping that covers the most nodes.
            if (k >= 2 && p * k > bestP * bestK) { bestP = p; bestK = k }
        }
        if (bestK >= 2 && nodes.subList(i, i + bestP).none { it is Node.Color }) {
            out += Node.Group(nodes.subList(i, i + bestP).toList(), Count.Fixed(bestK))
            i += bestP * bestK
        } else {
            out += nodes[i]
            i++
        }
    }
    return out
}
