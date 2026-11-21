package studio.knit.validate

import studio.knit.chart.cell
import studio.knit.chart.repeatsOnRow
import studio.knit.chart.rowCount
import studio.knit.chart.rowNumber
import studio.knit.chart.sideOf
import studio.knit.chart.stitchNumber
import studio.knit.convert.chartToNodes
import studio.knit.model.Chart
import studio.knit.model.Construction
import studio.knit.model.Pattern
import studio.knit.model.RowKind
import studio.knit.model.Section
import studio.knit.model.Side
import studio.knit.model.SizedInt
import studio.knit.model.WorksOn
import studio.knit.stitch.BuiltInStitches
import studio.knit.stitch.StitchLibrary
import studio.knit.written.Count
import studio.knit.written.Node
import studio.knit.written.RowParser
import studio.knit.written.RowPrinter
import studio.knit.written.evaluateSection

/** Where in the pattern an issue was found. All fields optional; the UI navigates to the most specific. */
data class Location(
    val chartId: String? = null,
    /** Zero-based chart row / column. */
    val row: Int? = null,
    val col: Int? = null,
    val sectionId: String? = null,
    val rowId: String? = null,
)

data class Issue(
    val severity: Severity,
    /** Stable machine-readable code, e.g. `chart.count-mismatch`. */
    val code: String,
    /** Where it is, in words: "Lace chart · Row 5". */
    val where: String,
    val message: String,
    val location: Location = Location(),
)

/**
 * Knitting-specific consistency checks. The validator only reports — it never modifies a pattern.
 */
object Validator {

    fun validate(pattern: Pattern, library: StitchLibrary): List<Issue> {
        val out = ArrayList<Issue>()
        for (chart in pattern.charts) out += validateChart(chart, library)
        for (section in pattern.sections) out += validateSection(section, pattern, library)
        out += validateMeta(pattern)
        return out.sortedBy { it.severity.ordinal }
    }

    // -----------------------------------------------------------------------------------------
    // Charts
    // -----------------------------------------------------------------------------------------

    fun validateChart(chart: Chart, library: StitchLibrary): List<Issue> {
        val out = ArrayList<Issue>()
        fun add(sev: Severity, code: String, row: Int?, col: Int?, msg: String) {
            val where = buildString {
                append(chart.name.ifBlank { "Chart" })
                if (row != null) append(" · Row ${chart.rowNumber(row)}")
                if (col != null) append(", stitch ${chart.stitchNumber(col)}")
            }
            out += Issue(sev, code, where, msg, Location(chartId = chart.id, row = row, col = col))
        }

        if (chart.cols < 1 || chart.rows < 1 || chart.cells.size != chart.cols * chart.rows) {
            add(Severity.ERROR, "chart.dimensions", null, null,
                "The chart data is damaged: ${chart.cells.size} cells for a ${chart.cols} × ${chart.rows} grid")
            return out
        }

        // Unknown stitches.
        val unknown = LinkedHashMap<String, Pair<Int, Int>>()
        for (r in 0 until chart.rows) for (c in 0 until chart.cols) {
            val id = chart.cell(r, c).stitch
            if (library[id] == null && id !in unknown) unknown[id] = r to c
        }
        for ((id, at) in unknown) {
            add(Severity.ERROR, "chart.unknown-stitch", at.first, at.second,
                "Stitch “$id” is used here but is not in the stitch library. Its stitch count is assumed to be 1")
        }

        // Multi-cell stitches must be intact.
        for (r in 0 until chart.rows) {
            var c = 0
            while (c < chart.cols) {
                val cell = chart.cell(r, c)
                val def = library[cell.stitch]
                val width = def?.width ?: 1
                if (cell.part != 0) {
                    add(Severity.ERROR, "chart.broken-stitch", r, c, "Part of a ${def?.name ?: "wide stitch"} is missing here. Erase the cell and place the stitch again")
                    c++
                    continue
                }
                if (width > 1) {
                    val intact = (1 until width).all { k ->
                        c + k < chart.cols && chart.cell(r, c + k).let { it.stitch == cell.stitch && it.part == k }
                    }
                    if (!intact) {
                        add(Severity.ERROR, "chart.broken-stitch", r, c, "${def!!.name} needs $width cells side by side, but it is cut off here")
                        c++
                        continue
                    }
                    c += width
                } else c++
            }
        }

        // Stitches on the wrong side.
        val wsReported = HashSet<String>()
        for (r in 0 until chart.rows) {
            if (chart.sideOf(r) != Side.WS) {
                for (c in 0 until chart.cols) {
                    val def = library[chart.cell(r, c).stitch] ?: continue
                    if (def.worksOn == WorksOn.WS_ONLY && chart.cell(r, c).part == 0) {
                        add(Severity.WARNING, "chart.wrong-side", r, c, "${def.name} is meant for wrong-side rows, but this is a right-side row")
                        break
                    }
                }
                continue
            }
            var rowReported = false
            for (c in 0 until chart.cols) {
                val cell = chart.cell(r, c)
                if (cell.part != 0) continue
                val def = library[cell.stitch] ?: continue
                if (def.worksOn == WorksOn.RS_ONLY && !rowReported) {
                    add(Severity.WARNING, "chart.wrong-side", r, c, "${def.name} is normally worked on right-side rows, but this is a wrong-side row")
                    rowReported = true
                }
                if (def.ws == null && def.worksOn != WorksOn.RS_ONLY && wsReported.add(def.id)) {
                    add(Severity.INFO, "chart.no-ws-equivalent", r, c, "${def.name} has no wrong-side equivalent defined, so written instructions will use its right-side wording")
                }
            }
        }

        // Row-to-row stitch counts: what a row needs must be what the row below leaves.
        val counts = (0 until chart.rows).map { chart.rowCount(it, library) }
        for (r in 1 until chart.rows) {
            val needs = counts[r].consumed
            val has = counts[r - 1].produced
            if (needs != has) {
                add(Severity.ERROR, "chart.count-mismatch", r, null,
                    "This row works $needs ${sts(needs)}, but row ${chart.rowNumber(r - 1)} leaves $has on the needle (${diff(needs - has)})")
            }
        }
        // Rows that change the count.
        var changes = 0
        for (r in 0 until chart.rows) {
            val d = counts[r].produced - counts[r].consumed
            if (d != 0) {
                changes++
                if (changes <= MAX_SIMILAR) {
                    add(Severity.INFO, "chart.count-change", r, null,
                        "Stitch count ${if (d > 0) "increases" else "decreases"} by ${kotlin.math.abs(d)} on this row (${counts[r].consumed} → ${counts[r].produced} sts)")
                }
            }
        }
        if (changes > MAX_SIMILAR) {
            add(Severity.INFO, "chart.count-change", null, null, "${changes - MAX_SIMILAR} more rows change the stitch count")
        }
        // Can the chart be worked again from row 1?
        val first = counts.first().consumed
        val last = counts.last().produced
        if (first != last && chart.rows > 1) {
            add(Severity.INFO, "chart.vertical-repeat", null, null,
                "The chart starts with $first sts and ends with $last, so it cannot simply be repeated from row ${chart.rowNumber(0)}")
        }

        // Repeat boxes.
        for (rep in chart.repeats) {
            if (rep.colStart < 0 || rep.rowStart < 0 || rep.colEnd >= chart.cols || rep.rowEnd >= chart.rows || rep.colStart > rep.colEnd || rep.rowStart > rep.rowEnd) {
                add(Severity.ERROR, "chart.repeat-bounds", null, null, "A repeat box lies outside the chart")
                continue
            }
            var cut = false
            for (r in rep.rowStart..rep.rowEnd) {
                // A wide stitch straddling either edge of the box.
                val leftEdge = chart.cell(r, rep.colStart)
                val afterRight = if (rep.colEnd + 1 < chart.cols) chart.cell(r, rep.colEnd + 1) else null
                if (leftEdge.part != 0 || (afterRight != null && afterRight.part != 0)) {
                    if (!cut) add(Severity.ERROR, "chart.repeat-cuts-stitch", r, if (leftEdge.part != 0) rep.colStart else rep.colEnd,
                        "The repeat box cuts through a cable. Move the box so the whole stitch is inside or outside")
                    cut = true
                }
            }
            if (cut) continue
            // The repeat must stack: within the box, each row must use what the row below made.
            for (r in rep.rowStart + 1..rep.rowEnd) {
                val below = boxCount(chart, r - 1, rep.colStart, rep.colEnd, library).second
                val here = boxCount(chart, r, rep.colStart, rep.colEnd, library).first
                if (below != here) {
                    add(Severity.WARNING, "chart.repeat-unbalanced", r, rep.colStart,
                        "Inside the repeat this row works $here ${sts(here)} but the row below makes $below. Repeats only line up when these match")
                    break
                }
            }
        }
        for (r in 0 until chart.rows) {
            if (chart.repeatsOnRow(r).size > 1) {
                add(Severity.INFO, "chart.multiple-repeats", r, null, "This row has more than one repeat box; written instructions can only express the first as a repeat")
                break
            }
        }

        // Colourwork.
        if (chart.palette.isNotEmpty()) {
            for (r in 0 until chart.rows) {
                val used = (0 until chart.cols).mapTo(HashSet()) { chart.cell(r, it).color }
                if (used.size >= 3) {
                    add(Severity.INFO, "chart.many-colors", r, null, "${used.size} colours are used in this row. More than two per row makes stranded knitting much harder")
                    break
                }
            }
        }
        return out
    }

    /** Stitches consumed / produced by the cells of [row] between two columns. */
    private fun boxCount(chart: Chart, row: Int, c0: Int, c1: Int, library: StitchLibrary): Pair<Int, Int> {
        var consumed = 0
        var produced = 0
        val ws = chart.sideOf(row) == Side.WS
        for (c in c0..c1) {
            val cell = chart.cell(row, c)
            if (cell.part != 0) continue
            val def = library.require(cell.stitch)
            val worked = if (ws) def.ws?.let { library[it] } ?: def else def
            consumed += worked.consumes
            produced += worked.produces
        }
        return consumed to produced
    }

    // -----------------------------------------------------------------------------------------
    // Written sections
    // -----------------------------------------------------------------------------------------

    fun validateSection(section: Section, pattern: Pattern, library: StitchLibrary): List<Issue> {
        val out = ArrayList<Issue>()
        val title = section.title.ifBlank { "Section" }
        val sizeCount = pattern.sizeCount

        fun whereOf(rowId: String?): String {
            if (rowId == null) return title
            val idx = section.rows.indexOfFirst { it.id == rowId }
            if (idx < 0) return title
            val row = section.rows[idx]
            return when (row.kind) {
                RowKind.REPEAT_ROWS -> "$title · Repeat of rows ${row.repeatFrom}–${row.repeatTo}"
                else -> {
                    val number = section.rows.take(idx + 1).count { it.kind == RowKind.INSTRUCTION }
                    "$title · ${if (section.construction == Construction.ROUND) "Rnd" else "Row"} $number"
                }
            }
        }
        fun add(sev: Severity, code: String, rowId: String?, msg: String) {
            out += Issue(sev, code, whereOf(rowId), msg, Location(sectionId = section.id, rowId = rowId))
        }

        if (section.rows.none { it.kind == RowKind.INSTRUCTION }) {
            add(Severity.INFO, "section.empty", null, "This section has no rows yet")
            return out
        }

        // Size lists must match the pattern's sizes.
        val parser = RowParser(library)
        fun checkSized(s: SizedInt, rowId: String?) {
            if (s.values.size > 1 && s.values.size != sizeCount) {
                add(Severity.ERROR, "section.size-count", rowId,
                    "“$s” gives ${s.values.size} values, but the pattern has $sizeCount ${if (sizeCount == 1) "size" else "sizes"}")
            }
        }
        section.castOn?.let { checkSized(it, null) }
        for (row in section.rows) {
            when (row.kind) {
                RowKind.INSTRUCTION -> {
                    val parsed = parser.parse(row.text)
                    forEachSized(parsed.nodes) { checkSized(it, row.id) }
                    parsed.stated?.let { checkSized(it, row.id) }
                }
                RowKind.REPEAT_ROWS -> {
                    checkSized(row.repeatTimes, row.id)
                    val span = row.repeatTo - row.repeatFrom + 1
                    if (section.construction == Construction.FLAT && span > 0 && span % 2 == 1) {
                        add(Severity.WARNING, "section.odd-repeat", row.id,
                            "Repeating an odd number of rows ($span) swaps right and wrong side on every other pass")
                    }
                }
                RowKind.NOTE -> Unit
            }
        }

        // Stitch arithmetic, for every size. Identical messages across sizes are reported once.
        val seen = HashSet<Triple<String?, Severity, String>>()
        for (size in 0 until sizeCount) {
            val eval = evaluateSection(section, size, library)
            for (i in eval.issues) {
                if (seen.add(Triple(i.rowId, i.severity, i.message))) {
                    val prefix = if (sizeCount > 1 && size > 0) "Size ${pattern.sizes.getOrElse(size) { "#${size + 1}" }}: " else ""
                    add(i.severity, "section.row", i.rowId, prefix + i.message)
                }
            }
        }

        // Agreement with the linked chart.
        val chart = section.chartId?.let { id -> pattern.charts.firstOrNull { it.id == id } }
        if (section.chartId != null && chart == null) {
            add(Severity.WARNING, "section.chart-missing", null, "The chart this section was linked to no longer exists")
        }
        if (chart != null && chart.cells.size == chart.cols * chart.rows) {
            val printer = RowPrinter()
            val fromChart = chartToNodes(chart, library).rows.map { printer.print(it).lowercase() }
            val rows = section.rows.filter { it.kind == RowKind.INSTRUCTION }
            if (rows.size != fromChart.size) {
                add(Severity.WARNING, "section.chart-rows", null,
                    "“${chart.name}” has ${fromChart.size} rows but this section has ${rows.size}")
            }
            for (i in 0 until minOf(rows.size, fromChart.size)) {
                val canonical = printer.print(parser.parse(rows[i].text).nodes).lowercase()
                if (canonical != fromChart[i]) {
                    add(Severity.WARNING, "section.chart-differs", rows[i].id,
                        "Differs from “${chart.name}” row ${chart.rowNumber(i)}, which reads: ${fromChart[i].replaceFirstChar { it.uppercase() }}")
                }
            }
        }
        return out
    }

    private fun forEachSized(nodes: List<Node>, block: (SizedInt) -> Unit) {
        for (n in nodes) when (n) {
            is Node.Stitch -> when (val c = n.count) {
                is Count.Fixed -> block(c.n)
                is Count.ToEnd -> block(c.leaving)
                is Count.ToMarker -> block(c.before)
            }
            is Node.Group -> {
                when (val t = n.times) {
                    is Count.Fixed -> block(t.n)
                    is Count.ToEnd -> block(t.leaving)
                    is Count.ToMarker -> block(t.before)
                }
                forEachSized(n.body, block)
            }
            else -> Unit
        }
    }

    // -----------------------------------------------------------------------------------------
    // Pattern details
    // -----------------------------------------------------------------------------------------

    private fun validateMeta(pattern: Pattern): List<Issue> {
        val out = ArrayList<Issue>()
        val hasContent = pattern.charts.isNotEmpty() || pattern.sections.isNotEmpty()
        if (pattern.name.isBlank()) out += Issue(Severity.INFO, "meta.name", "Details", "The pattern has no name yet")
        if (hasContent && pattern.gauge == null) {
            out += Issue(Severity.INFO, "meta.gauge", "Details", "No gauge is set. Without it, finished measurements and yarn amounts cannot be worked out")
        }
        pattern.gauge?.let {
            if (it.stitchesPer10cm <= 0 || it.rowsPer10cm <= 0) {
                out += Issue(Severity.ERROR, "meta.gauge-invalid", "Details", "Gauge must be greater than zero")
            }
        }
        if (pattern.sizes.size > 1) {
            for (m in pattern.measurements) {
                if (m.valuesCm.size > 1 && m.valuesCm.size != pattern.sizes.size) {
                    out += Issue(Severity.WARNING, "meta.measurement-sizes", "Measurements",
                        "“${m.name}” has ${m.valuesCm.size} values for ${pattern.sizes.size} sizes")
                }
            }
            if (pattern.sizes.distinct().size != pattern.sizes.size) {
                out += Issue(Severity.WARNING, "meta.duplicate-size", "Details", "Two sizes share the same name")
            }
        }
        if (pattern.charts.any { c -> c.cells.any { it.stitch == BuiltInStitches.NO_STITCH } } && pattern.sections.isEmpty()) {
            // Purely informational nudge towards the written view.
            out += Issue(Severity.INFO, "meta.no-rows", "Rows", "Tip: “Write rows from chart” creates the written instructions for you")
        }
        return out
    }

    private fun sts(n: Int) = if (n == 1) "stitch" else "stitches"
    private fun diff(d: Int) = if (d > 0) "$d too many" else "${-d} too few"

    private const val MAX_SIMILAR = 6
}
