package studio.knit.pdf

import studio.knit.calc.Needles
import studio.knit.calc.UnitFormat
import studio.knit.calc.pretty
import studio.knit.chart.cell
import studio.knit.chart.rowNumber
import studio.knit.chart.sideOf
import studio.knit.chart.stitchNumber
import studio.knit.chart.usedColors
import studio.knit.chart.usedStitches
import studio.knit.data.PaperSize
import studio.knit.model.Chart
import studio.knit.model.Construction
import studio.knit.model.Direction
import studio.knit.model.Pattern
import studio.knit.model.RowKind
import studio.knit.model.Section
import studio.knit.model.Side
import studio.knit.model.StitchDef
import studio.knit.model.YarnWeight
import studio.knit.pdf.PdfWriter.Font
import studio.knit.stitch.BuiltInStitches
import studio.knit.stitch.Glyphs
import studio.knit.stitch.Prim
import studio.knit.stitch.StitchLibrary
import studio.knit.written.RowParser
import studio.knit.written.Node
import studio.knit.written.evaluateSection

/** What to put in an exported pattern document. */
data class PdfOptions(
    val paper: PaperSize = PaperSize.A4,
    val landscape: Boolean = false,
    val cover: Boolean = true,
    val materials: Boolean = true,
    val measurements: Boolean = true,
    val abbreviations: Boolean = true,
    val written: Boolean = true,
    val charts: Boolean = true,
    val legend: Boolean = true,
    val notes: Boolean = true,
    val imperial: Boolean = false,
    val uk: Boolean = false,
    /** Accent colour for rules, repeat boxes and headings (ARGB). */
    val accent: Long = 0xFFB4533C,
)

/**
 * Lays a pattern out as a print-ready document: cover, materials, gauge, measurements,
 * abbreviations, written instructions, vector charts with legends, notes and page numbers.
 */
class PatternPdf(
    private val pattern: Pattern,
    private val library: StitchLibrary,
    private val options: PdfOptions = PdfOptions(),
    /** JPEG bytes of reference images, by id. Other formats are skipped. */
    private val images: Map<String, ByteArray> = emptyMap(),
) {
    private val pageW = if (options.landscape) options.paper.heightPt else options.paper.widthPt
    private val pageH = if (options.landscape) options.paper.widthPt else options.paper.heightPt
    private val pdf = PdfWriter(pageW, pageH)
    private val margin = 54f
    private val left = margin
    private val right = pageW - margin
    private val contentW = right - left
    private val top = margin + 6f
    private val bottom = pageH - margin - 10f
    private var y = top
    private val units = UnitFormat(options.imperial)
    private var bodyStartPage = 0

    private val ink = 0xFF1C1C1E
    private val grey = 0xFF6E6E73
    private val faint = 0xFFD1D1D6
    private val wash = 0xFFF2F2F7

    fun render(): ByteArray {
        pdf.roundCaps()
        if (options.cover) { cover(); pdf.newPage(); pdf.roundCaps() }
        bodyStartPage = pdf.currentPage
        y = top
        if (!options.cover) titleBlock()
        overview()
        if (options.materials) materials()
        if (options.measurements) measurements()
        if (options.abbreviations) abbreviations()
        if (options.written) written()
        if (options.charts) charts()
        if (options.notes && pattern.notes.isNotBlank()) {
            heading("Notes")
            paragraph(pattern.notes)
        }
        footers()
        return pdf.build(title = pattern.displayName)
    }

    // --- Flow helpers --------------------------------------------------------------------------

    private fun ensure(height: Float) {
        if (y + height > bottom) newPage()
    }

    private fun newPage() {
        pdf.newPage()
        pdf.roundCaps()
        y = top
    }

    private fun heading(text: String) {
        ensure(64f)
        y += 14f
        pdf.fillColor(options.accent)
        pdf.text(left, y + 4f, text.uppercase(), Font.BOLD, 9f)
        y += 10f
        pdf.strokeColor(faint); pdf.lineWidth(0.5f)
        pdf.line(left, y, right, y)
        y += 16f
        pdf.fillColor(ink)
    }

    private fun subheading(text: String) {
        ensure(40f)
        y += 4f
        pdf.fillColor(ink)
        pdf.text(left, y + 4f, text, Font.BOLD, 12.5f)
        y += 18f
    }

    /** Wrapped body text. Returns nothing; advances the cursor. */
    private fun paragraph(text: String, font: Font = Font.REGULAR, size: Float = 10.5f, color: Long = ink, indent: Float = 0f, width: Float = contentW - indent) {
        val leading = size * 1.42f
        pdf.fillColor(color)
        for (line in pdf.wrap(text, font, size, width)) {
            ensure(leading)
            pdf.fillColor(color)
            pdf.text(left + indent, y + size * 0.8f, line, font, size)
            y += leading
        }
        y += 4f
        pdf.fillColor(ink)
    }

    /** "Label   value" line with a fixed label column. */
    private fun fact(label: String, value: String, labelWidth: Float = 110f) {
        if (value.isBlank()) return
        val size = 10.5f
        val leading = size * 1.42f
        val lines = pdf.wrap(value, Font.REGULAR, size, contentW - labelWidth)
        ensure(leading * lines.size)
        pdf.fillColor(grey)
        pdf.text(left, y + size * 0.8f, label, Font.REGULAR, size)
        pdf.fillColor(ink)
        for (line in lines) {
            pdf.text(left + labelWidth, y + size * 0.8f, line, Font.REGULAR, size)
            y += leading
        }
        y += 2f
    }

    // --- Cover ---------------------------------------------------------------------------------

    private fun cover() {
        // Accent bar at the left edge, title low on the page.
        pdf.fillColor(options.accent)
        pdf.rect(0f, 0f, 14f, pageH, fill = true, stroke = false)

        var cy = pageH * 0.14f
        val image = pattern.imageIds.firstNotNullOfOrNull { images[it] }
        if (image != null) {
            val size = pdf.jpegSize(image)
            if (size != null) {
                val maxW = contentW
                val maxH = pageH * 0.42f
                val scale = minOf(maxW / size.first, maxH / size.second)
                val w = size.first * scale
                val h = size.second * scale
                pdf.jpeg(image, left, cy, w, h)
                cy += h + 36f
            }
        } else {
            cy = pageH * 0.36f
        }

        pdf.fillColor(grey)
        pdf.text(left, cy, listOf(pattern.category.label, pattern.difficulty.label).joinToString("  ·  ").uppercase(), Font.BOLD, 9.5f)
        cy += 30f
        pdf.fillColor(ink)
        val titleSize = 34f
        for (line in pdf.wrap(pattern.displayName, Font.BOLD, titleSize, contentW)) {
            pdf.text(left, cy + titleSize * 0.3f, line, Font.BOLD, titleSize)
            cy += titleSize * 1.15f
        }
        cy += 6f
        if (pattern.description.isNotBlank()) {
            pdf.fillColor(grey)
            for (line in pdf.wrap(pattern.description, Font.REGULAR, 12.5f, contentW * 0.86f).take(8)) {
                pdf.text(left, cy + 8f, line, Font.REGULAR, 12.5f)
                cy += 18f
            }
        }
        // Key facts along the bottom.
        val facts = ArrayList<Pair<String, String>>()
        pattern.gauge?.let { facts += (if (options.uk) "Tension" else "Gauge") to units.gauge(it) }
        if (pattern.sizes.isNotEmpty()) facts += "Sizes" to pattern.sizes.joinToString(" / ")
        pattern.needles.firstOrNull()?.let { facts += "Needles" to Needles.label(it.sizeMm, options.imperial) }
        var fy = pageH - margin - 20f - facts.size * 16f
        pdf.strokeColor(faint); pdf.lineWidth(0.5f)
        if (facts.isNotEmpty()) pdf.line(left, fy - 12f, right, fy - 12f)
        for ((k, v) in facts) {
            pdf.fillColor(grey); pdf.text(left, fy + 4f, k, Font.REGULAR, 10f)
            pdf.fillColor(ink); pdf.text(left + 80f, fy + 4f, v, Font.REGULAR, 10f)
            fy += 16f
        }
    }

    private fun titleBlock() {
        pdf.fillColor(ink)
        for (line in pdf.wrap(pattern.displayName, Font.BOLD, 24f, contentW)) {
            pdf.text(left, y + 18f, line, Font.BOLD, 24f)
            y += 28f
        }
        pdf.fillColor(grey)
        pdf.text(left, y + 8f, "${pattern.category.label}  ·  ${pattern.difficulty.label}", Font.REGULAR, 10f)
        y += 22f
        if (pattern.description.isNotBlank()) paragraph(pattern.description, color = grey)
    }

    // --- Sections ------------------------------------------------------------------------------

    private fun overview() {
        val gauge = pattern.gauge
        if (gauge == null && pattern.sizes.isEmpty() && (options.cover || pattern.description.isBlank())) return
        heading("Overview")
        if (options.cover && pattern.description.isNotBlank()) paragraph(pattern.description)
        fact("Difficulty", pattern.difficulty.label)
        if (pattern.sizes.isNotEmpty()) {
            fact("Sizes", if (pattern.sizes.size > 1) pattern.sizes.first() + " (" + pattern.sizes.drop(1).joinToString(", ") + ")" else pattern.sizes.first())
        }
        if (gauge != null) {
            fact(if (options.uk) "Tension" else "Gauge", units.gauge(gauge) + " in " + (if (options.uk) gauge.over.replace("stockinette", "stocking") else gauge.over) +
                (gauge.needleMm?.let { " on ${Needles.label(it, options.imperial)} needles" } ?: ""))
        }
        if (pattern.sizes.size > 1) {
            paragraph("Instructions are given for the smallest size, with larger sizes in brackets: ${pattern.sizes.first()} (${pattern.sizes.drop(1).joinToString(", ")}). Where only one number is given it applies to all sizes.", Font.ITALIC, 9.5f, grey)
        }
    }

    private fun materials() {
        if (pattern.yarns.isEmpty() && pattern.needles.isEmpty()) return
        heading("Materials")
        for (yarn in pattern.yarns) {
            val title = listOf(yarn.label.takeIf { it.isNotBlank() }?.let { "$it:" }, yarn.displayName).filterNotNull().joinToString(" ")
            ensure(34f)
            pdf.fillColor(ink)
            if (yarn.colorArgb != null) {
                pdf.fillColor(yarn.colorArgb); pdf.strokeColor(faint); pdf.lineWidth(0.5f)
                pdf.circle(left + 5f, y + 5f, 5f, fill = true, stroke = true)
                pdf.fillColor(ink)
                pdf.text(left + 16f, y + 9f, title, Font.BOLD, 10.5f)
            } else pdf.text(left, y + 9f, title, Font.BOLD, 10.5f)
            y += 15f
            val details = listOfNotNull(
                yarn.weight.takeIf { it != YarnWeight.UNKNOWN }?.let { if (options.uk) it.ukLabel else it.label },
                yarn.fiber.takeIf { it.isNotBlank() },
                yarn.color.takeIf { it.isNotBlank() },
                yarn.dyeLot.takeIf { it.isNotBlank() }?.let { "lot $it" },
                if (yarn.metersPerSkein != null && yarn.gramsPerSkein != null) "${units.yarn(yarn.metersPerSkein)} / ${units.weight(yarn.gramsPerSkein)}"
                else yarn.metersPerSkein?.let { units.yarn(it) + " per skein" },
                yarn.skeins?.let { "${it.pretty()} ${if (it == 1.0) "skein" else "skeins"}" + (yarn.totalMeters?.let { m -> " (${units.yarn(m)})" } ?: "") },
            )
            if (details.isNotEmpty()) paragraph(details.joinToString("  ·  "), size = 9.5f, color = grey, indent = if (yarn.colorArgb != null) 16f else 0f)
            if (yarn.notes.isNotBlank()) paragraph(yarn.notes, Font.ITALIC, 9.5f, grey)
        }
        if (pattern.needles.isNotEmpty()) {
            y += 4f
            for (needle in pattern.needles) {
                val parts = listOfNotNull(
                    Needles.label(needle.sizeMm, options.imperial),
                    needle.type.label.lowercase(),
                    needle.lengthCm?.let { units.length(it, 0) },
                    needle.material.takeIf { it.isNotBlank() }?.lowercase(),
                    needle.notes.takeIf { it.isNotBlank() },
                )
                paragraph("•  " + parts.joinToString(", "))
                y -= 3f
            }
            y += 3f
        }
    }

    private fun measurements() {
        if (pattern.measurements.isEmpty()) return
        heading(if (pattern.sizes.size > 1) "Finished measurements" else "Measurements")
        val sizes = if (pattern.sizes.isEmpty()) listOf("") else pattern.sizes
        val nameW = minOf(170f, contentW * 0.4f)
        val colW = ((contentW - nameW) / sizes.size).coerceAtMost(72f)
        if (pattern.sizes.size > 1) {
            ensure(18f)
            pdf.fillColor(grey)
            sizes.forEachIndexed { i, s -> pdf.textRight(left + nameW + colW * (i + 1), y + 8f, s, Font.BOLD, 9f) }
            y += 16f
        }
        for (m in pattern.measurements) {
            ensure(18f)
            pdf.fillColor(ink)
            pdf.text(left, y + 8f, m.name, Font.REGULAR, 10.5f)
            sizes.indices.forEach { i ->
                val v = m.valuesCm.getOrNull(i) ?: m.valuesCm.singleOrNull()
                if (v != null) pdf.textRight(left + nameW + colW * (i + 1), y + 8f, units.length(v), Font.REGULAR, 10.5f)
            }
            y += 14f
            pdf.strokeColor(wash); pdf.lineWidth(0.5f)
            pdf.line(left, y, left + nameW + colW * sizes.size, y)
            y += 4f
        }
        y += 4f
    }

    /** Every stitch the pattern actually uses, in library order. */
    private fun usedStitches(): List<StitchDef> {
        val ids = LinkedHashSet<String>()
        for (chart in pattern.charts) {
            if (chart.cells.size != chart.cols * chart.rows) continue
            for (r in 0 until chart.rows) for (c in 0 until chart.cols) {
                val def = library.require(chart.cell(r, c).stitch)
                ids += def.id
                if (chart.sideOf(r) == Side.WS) def.ws?.let { ids += it }
            }
        }
        val parser = RowParser(library)
        fun collect(nodes: List<Node>) {
            for (n in nodes) when (n) {
                is Node.Stitch -> ids += n.def.id
                is Node.Group -> collect(n.body)
                else -> Unit
            }
        }
        for (s in pattern.sections) for (row in s.rows) if (row.kind == RowKind.INSTRUCTION) collect(parser.parse(row.text).nodes)
        return library.all.filter { it.id in ids && it.id != BuiltInStitches.NO_STITCH }
    }

    private fun abbreviations() {
        val used = usedStitches()
        if (used.isEmpty()) return
        heading("Abbreviations")
        val entries = ArrayList<Pair<String, String>>()
        for (def in used) entries += def.abbrFor(options.uk) to def.name.replaceFirstChar { it.lowercase() }
        entries += "RS" to "right side"
        entries += "WS" to "wrong side"
        entries += "st(s)" to "stitch(es)"
        if (pattern.sections.any { s -> s.rows.any { it.text.contains("rep", ignoreCase = true) } }) entries += "rep" to "repeat"
        val sorted = entries.distinctBy { it.first.lowercase() }.sortedBy { it.first.lowercase() }
        // Two columns.
        val colW = contentW / 2f
        val rows = (sorted.size + 1) / 2
        for (r in 0 until rows) {
            ensure(15f)
            for (col in 0..1) {
                val e = sorted.getOrNull(r + col * rows) ?: continue
                val x = left + col * colW
                pdf.fillColor(ink); pdf.text(x, y + 8f, e.first, Font.BOLD, 10f)
                pdf.fillColor(grey); pdf.text(x + 62f, y + 8f, e.second, Font.REGULAR, 10f)
            }
            y += 15f
        }
        y += 4f
        pdf.fillColor(ink)
    }

    private fun written() {
        val sections = pattern.sections.filter { it.rows.isNotEmpty() }
        if (sections.isEmpty()) return
        heading("Instructions")
        for (section in sections) writtenSection(section)
    }

    private fun writtenSection(section: Section) {
        subheading(section.title.ifBlank { "Section" })
        if (section.description.isNotBlank()) paragraph(section.description, color = grey)
        section.castOn?.let {
            val st = if (!it.isSized && it.values[0] == 1) "st" else "sts"
            paragraph("${if (section.construction == Construction.ROUND) "Round" else "Row"} count starts with $it $st on the needle.", Font.ITALIC, 9.5f, grey)
        }
        // Stitch counts after each row, when the pattern has one size and they are known.
        val eval = if (pattern.sizeCount == 1) evaluateSection(section, 0, library) else null
        val labelW = if (section.construction == Construction.ROUND) 48f else 74f
        val size = 10.5f
        val leading = size * 1.42f
        var number = 0
        var side = section.firstRowSide
        for (row in section.rows) {
            when (row.kind) {
                RowKind.NOTE -> paragraph(row.text, Font.ITALIC, 10f, grey)
                RowKind.REPEAT_ROWS -> {
                    val span = row.repeatTo - row.repeatFrom + 1
                    val word = if (section.construction == Construction.ROUND) "rnd" else "row"
                    val what = if (span == 1) "$word ${row.repeatFrom}" else "${word}s ${row.repeatFrom}–${row.repeatTo}"
                    val times = row.repeatTimes
                    val howOften = if (!times.isSized && times.values[0] == 1) "once more" else "$times more times"
                    paragraph("Repeat $what $howOften.", Font.BOLD, 10.5f)
                    number += span * times.values[0]
                    if (span * times.values[0] % 2 == 1) side = side.flipped()
                }
                RowKind.INSTRUCTION -> {
                    number++
                    val label = if (section.construction == Construction.ROUND) "Rnd $number:" else "Row $number (${side.name}):"
                    side = side.flipped()
                    var text = row.text.trim().ifEmpty { "—" }
                    val produced = eval?.firstFor(row.id)?.eval?.produced
                    val stated = RowParser(library).parse(row.text).stated
                    val changes = eval?.firstFor(row.id)?.eval?.delta?.let { it != 0 } ?: false
                    if (produced != null && stated == null && changes) text = text.trimEnd('.') + " ($produced sts)"
                    val lines = pdf.wrap(text, Font.REGULAR, size, contentW - labelW)
                    ensure(leading * minOf(lines.size, 3))
                    pdf.fillColor(ink)
                    pdf.text(left, y + size * 0.8f, label, Font.BOLD, size)
                    for (line in lines) {
                        ensure(leading)
                        pdf.fillColor(ink)
                        pdf.text(left + labelW, y + size * 0.8f, line, Font.REGULAR, size)
                        y += leading
                    }
                    y += 2f
                    if (row.note.isNotBlank()) paragraph(row.note, Font.ITALIC, 9.5f, grey, indent = labelW)
                }
            }
        }
        y += 8f
    }

    // --- Charts --------------------------------------------------------------------------------

    private fun charts() {
        val charts = pattern.charts.filter { it.cells.size == it.cols * it.rows }
        if (charts.isEmpty()) return
        heading("Charts")
        for (chart in charts) chart(chart)
    }

    private fun chart(chart: Chart) {
        val numberGutter = 22f
        val available = contentW - numberGutter * 2
        val cell = (available / chart.cols).coerceIn(5f, 17f)
        val gridW = cell * chart.cols
        val x0 = left + numberGutter + (available - gridW).coerceAtLeast(0f) / 2f

        subheading(chart.name.ifBlank { "Chart" })
        val flat = chart.construction == Construction.FLAT
        val reading = if (flat) {
            val rs = if (chart.rsDirection == Direction.RIGHT_TO_LEFT) "right to left" else "left to right"
            val ws = if (chart.rsDirection == Direction.RIGHT_TO_LEFT) "left to right" else "right to left"
            "Worked flat. Read right-side rows from $rs and wrong-side rows from $ws. The chart shows the right side of the fabric."
        } else {
            "Worked in the round. Read every round from ${if (chart.rsDirection == Direction.RIGHT_TO_LEFT) "right to left" else "left to right"}."
        }
        paragraph(reading, Font.ITALIC, 9.5f, grey)
        if (chart.notes.isNotBlank()) paragraph(chart.notes, size = 10f)

        // Draw in horizontal bands from the top row down, breaking across pages when needed.
        var rowTop = chart.rows - 1
        while (rowTop >= 0) {
            val room = bottom - y - cell * 1.6f
            var rowsFit = (room / cell).toInt()
            if (rowsFit < minOf(4, rowTop + 1)) { newPage(); continue }
            rowsFit = minOf(rowsFit, rowTop + 1)
            val rowBottom = rowTop - rowsFit + 1
            band(chart, x0, y, cell, rowBottom, rowTop, lastBand = rowBottom == 0)
            y += rowsFit * cell + (if (rowBottom == 0) cell * 1.5f else 8f)
            rowTop = rowBottom - 1
        }
        y += 6f
        if (options.legend) legend(chart)
        y += 10f
    }

    /** Draws chart rows [rowBottom]..[rowTop] with the top row at [topY]. */
    private fun band(chart: Chart, x0: Float, topY: Float, cell: Float, rowBottom: Int, rowTop: Int, lastBand: Boolean) {
        val rows = rowTop - rowBottom + 1
        fun cellTop(row: Int) = topY + (rowTop - row) * cell
        val fontSize = (cell * 0.5f).coerceIn(5f, 8f)

        // Cell backgrounds and symbols.
        for (r in rowBottom..rowTop) for (c in 0 until chart.cols) {
            val cellData = chart.cell(r, c)
            val x = x0 + c * cell
            val t = cellTop(r)
            val color = chart.palette.getOrNull(cellData.color)?.argb
            if (color != null) {
                pdf.fillColor(color)
                pdf.rect(x, t, cell, cell, fill = true, stroke = false)
            }
            if (cellData.part != 0) continue
            val def = library.require(cellData.stitch)
            val width = def.width.coerceAtLeast(1)
            val onDark = color != null && luminance(color) < 0.45f
            glyph(def.symbol, width, x, t, cell, if (onDark) 0xFFFFFFFF else ink)
        }
        // Grid lines; lines inside a wide stitch are omitted so cables read as one symbol.
        pdf.strokeColor(0xFF8E8E93); pdf.lineWidth(0.4f)
        for (r in rowBottom..rowTop + 1) pdf.line(x0, cellTop(r - 1), x0 + chart.cols * cell, cellTop(r - 1))
        for (c in 0..chart.cols) {
            var runStart = -1
            for (r in rowBottom..rowTop + 1) {
                val draw = r <= rowTop && (c == 0 || c == chart.cols || chart.cell(r, c).part == 0)
                if (draw && runStart < 0) runStart = r
                if (!draw && runStart >= 0) {
                    pdf.line(x0 + c * cell, cellTop(r - 1), x0 + c * cell, cellTop(runStart) + cell)
                    runStart = -1
                }
            }
        }
        // Heavier line every 10 stitches / rows helps counting on large charts.
        if (chart.cols > 12 || chart.rows > 12) {
            pdf.strokeColor(0xFF48484A); pdf.lineWidth(0.9f)
            for (c in 0..chart.cols) {
                val n = if (chart.rsDirection == Direction.RIGHT_TO_LEFT) chart.cols - c else c
                if (n % 10 == 0 && c != 0 && c != chart.cols) pdf.line(x0 + c * cell, topY, x0 + c * cell, topY + rows * cell)
            }
            for (r in rowBottom..rowTop) if (r % 10 == 0 && r != 0) pdf.line(x0, cellTop(r) + cell, x0 + chart.cols * cell, cellTop(r) + cell)
        }
        pdf.strokeColor(ink); pdf.lineWidth(0.9f)
        pdf.rect(x0, topY, chart.cols * cell, rows * cell)

        // Repeat boxes.
        pdf.strokeColor(options.accent); pdf.lineWidth(1.6f)
        for (rep in chart.repeats) {
            val r0 = maxOf(rep.rowStart, rowBottom)
            val r1 = minOf(rep.rowEnd, rowTop)
            if (r0 > r1) continue
            pdf.rect(x0 + rep.colStart * cell, cellTop(r1), (rep.colEnd - rep.colStart + 1) * cell, (r1 - r0 + 1) * cell)
        }

        // Row numbers: on the side where the row starts.
        pdf.fillColor(grey)
        for (r in rowBottom..rowTop) {
            val label = chart.rowNumber(r).toString()
            val base = cellTop(r) + cell / 2f + fontSize * 0.35f
            val startsRight = (chart.sideOf(r) == Side.RS) == (chart.rsDirection == Direction.RIGHT_TO_LEFT)
            if (startsRight) pdf.text(x0 + chart.cols * cell + 4f, base, label, Font.REGULAR, fontSize)
            else pdf.textRight(x0 - 4f, base, label, Font.REGULAR, fontSize)
        }
        // Stitch numbers under the bottom band.
        if (lastBand) {
            val step = if (cell < 9f) 5 else if (chart.cols > 30) 2 else 1
            for (c in 0 until chart.cols) {
                val n = chart.stitchNumber(c)
                if (n == 1 || n % step == 0) pdf.textCentered(x0 + c * cell + cell / 2f, topY + rows * cell + fontSize + 3f, n.toString(), Font.REGULAR, fontSize)
            }
        }
        pdf.fillColor(ink)
    }

    private fun luminance(argb: Long): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Draws a symbol into a box of [width] cells whose top-left corner is ([x], [top]). */
    private fun glyph(key: String, width: Int, x: Float, top: Float, cell: Float, color: Long) {
        pdf.strokeColor(color); pdf.fillColor(color)
        pdf.lineWidth((cell * 0.085f).coerceIn(0.6f, 1.4f))
        for (p in Glyphs.prims(key, width)) when (p) {
            is Prim.Line -> pdf.line(x + p.x1 * cell, top + p.y1 * cell, x + p.x2 * cell, top + p.y2 * cell)
            is Prim.Poly -> pdf.polyline(
                p.points.mapIndexed { i, v -> if (i % 2 == 0) x + v * cell else top + v * cell },
                closed = p.closed, fill = p.filled, stroke = true,
            )
            is Prim.Dot -> pdf.circle(x + p.cx * cell, top + p.cy * cell, p.r * cell, fill = p.filled, stroke = !p.filled)
            is Prim.Label -> {
                val size = cell * (if (p.text.length <= 1) 0.62f else if (p.text.length == 2) 0.5f else 0.38f)
                pdf.textCentered(x + width * cell / 2f, top + cell / 2f + size * 0.36f, p.text, Font.BOLD, size)
            }
            Prim.Shade -> {
                pdf.fillColor(0xFFC7C7CC)
                pdf.rect(x, top, cell * width, cell, fill = true, stroke = false)
                pdf.fillColor(color)
            }
        }
    }

    private fun legend(chart: Chart) {
        val used = chart.usedStitches().map { library.require(it) }.distinctBy { it.id }
        val flat = chart.construction == Construction.FLAT
        val hasWs = flat && chart.rows > 1
        val cell = 13f
        val lineH = 19f
        ensure(lineH * minOf(used.size, 3) + 20f)
        pdf.fillColor(grey)
        pdf.text(left, y + 8f, "KEY", Font.BOLD, 8f)
        y += 16f
        for (def in used) {
            ensure(lineH)
            val w = def.width.coerceAtLeast(1)
            pdf.strokeColor(0xFF8E8E93); pdf.lineWidth(0.4f)
            pdf.rect(left, y, cell * w, cell)
            glyph(def.symbol, w, left, y, cell, ink)
            val textX = left + cell * maxOf(w, 2) + 10f
            val meaning = when {
                def.id == BuiltInStitches.NO_STITCH -> "No stitch — skip this cell"
                !hasWs || def.ws == null || def.ws == def.id -> "${def.name} (${def.abbrFor(options.uk)})"
                else -> {
                    val ws = library.require(def.ws)
                    "RS: ${def.name.lowercase()} (${def.abbrFor(options.uk)})   ·   WS: ${ws.name.lowercase()} (${ws.abbrFor(options.uk)})"
                }
            }
            pdf.fillColor(ink)
            pdf.text(textX, y + cell * 0.72f, meaning, Font.REGULAR, 9.5f)
            y += lineH
        }
        // Colours.
        val colors = chart.usedColors().sorted()
        if (colors.isNotEmpty()) {
            for (index in listOf(-1) + colors) {
                ensure(lineH)
                val color = chart.palette.getOrNull(index)
                pdf.strokeColor(0xFF8E8E93); pdf.lineWidth(0.4f)
                if (color != null) pdf.fillColor(color.argb) else pdf.fillColor(0xFFFFFFFF)
                pdf.rect(left, y, cell, cell, fill = true, stroke = true)
                pdf.fillColor(ink)
                pdf.text(left + cell * 2 + 10f, y + cell * 0.72f, if (color != null) "Colour ${color.name}" else "Main colour (MC)", Font.REGULAR, 9.5f)
                y += lineH
            }
        }
        if (chart.repeats.isNotEmpty()) {
            ensure(lineH)
            pdf.strokeColor(options.accent); pdf.lineWidth(1.6f)
            pdf.rect(left + 1f, y + 1f, cell * 2 - 2f, cell - 2f)
            pdf.fillColor(ink)
            pdf.text(left + cell * 2 + 10f, y + cell * 0.72f, "Pattern repeat", Font.REGULAR, 9.5f)
            y += lineH
        }
    }

    // --- Footers -------------------------------------------------------------------------------

    private fun footers() {
        val total = pdf.pageCount
        for (i in bodyStartPage until total) {
            pdf.selectPage(i)
            pdf.fillColor(grey)
            pdf.text(left, pageH - margin + 18f, pattern.displayName, Font.REGULAR, 8.5f)
            pdf.textRight(right, pageH - margin + 18f, "${i - bodyStartPage + 1} / ${total - bodyStartPage}", Font.REGULAR, 8.5f)
        }
    }
}
