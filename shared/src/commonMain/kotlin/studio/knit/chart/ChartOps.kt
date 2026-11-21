package studio.knit.chart

import studio.knit.model.Cell
import studio.knit.model.Chart
import studio.knit.model.ChartColor
import studio.knit.model.ChartRepeat
import studio.knit.model.Construction
import studio.knit.model.Direction
import studio.knit.model.Side
import studio.knit.model.StitchDef
import studio.knit.stitch.BuiltInStitches
import studio.knit.stitch.StitchLibrary

/*
 * Pure, immutable chart operations. Every edit returns a new Chart, which makes undo/redo a
 * simple stack of values and keeps the editor free of knitting rules.
 *
 * Coordinates: row 0 is the bottom row (knitted first), column 0 is the left edge as drawn.
 */

const val MAX_CHART_SIZE = 500

fun blankChart(id: String, cols: Int, rows: Int, name: String = "Chart", construction: Construction = Construction.FLAT): Chart {
    val c = cols.coerceIn(1, MAX_CHART_SIZE)
    val r = rows.coerceIn(1, MAX_CHART_SIZE)
    return Chart(id = id, name = name, cols = c, rows = r, cells = List(c * r) { Cell.Knit }, construction = construction)
}

fun Chart.index(row: Int, col: Int): Int = row * cols + col
fun Chart.inBounds(row: Int, col: Int): Boolean = row in 0 until rows && col in 0 until cols
fun Chart.cell(row: Int, col: Int): Cell = cells[index(row, col)]
fun Chart.rowCells(row: Int): List<Cell> = cells.subList(row * cols, row * cols + cols)

/** Side of the fabric facing the knitter while working [row]. */
fun Chart.sideOf(row: Int): Side =
    if (construction == Construction.ROUND) Side.RS
    else if (row % 2 == 0) firstRowSide else firstRowSide.flipped()

/** Direction in which [row] is read on the chart. */
fun Chart.directionOf(row: Int): Direction =
    if (sideOf(row) == Side.RS) rsDirection else rsDirection.flipped()

/** Row number printed beside [row]. */
fun Chart.rowNumber(row: Int): Int = firstRowNumber + row

/** Stitch number printed under [col]; stitch 1 is where right-side rows begin. */
fun Chart.stitchNumber(col: Int): Int = if (rsDirection == Direction.RIGHT_TO_LEFT) cols - col else col + 1

/** Column indices of [row] in knitting order. */
fun Chart.readingOrder(row: Int): IntProgression =
    if (directionOf(row) == Direction.RIGHT_TO_LEFT) (cols - 1) downTo 0 else 0 until cols

/** Column of the first cell of the (possibly multi-cell) stitch covering [row],[col]. */
fun Chart.headCol(row: Int, col: Int): Int = col - cell(row, col).part

/**
 * The stitches of [row] in knitting order. Multi-cell stitches appear once; "no stitch" cells are
 * included (callers skip them where appropriate). Each entry carries the head column.
 */
data class ChartStitch(val col: Int, val cell: Cell, val def: StitchDef)

fun Chart.stitchesInOrder(row: Int, library: StitchLibrary): List<ChartStitch> {
    val out = ArrayList<ChartStitch>(cols)
    for (c in readingOrder(row)) {
        val cell = cell(row, c)
        if (cell.part != 0) continue
        out += ChartStitch(c, cell, library.require(cell.stitch))
    }
    return out
}

/** Stitches consumed and produced by one chart row, taking the working side into account. */
data class RowCount(val consumed: Int, val produced: Int)

fun Chart.rowCount(row: Int, library: StitchLibrary): RowCount {
    var consumed = 0
    var produced = 0
    val ws = sideOf(row) == Side.WS
    for (s in stitchesInOrder(row, library)) {
        // A symbol on a WS row is worked as its counterpart; counts can differ only in theory,
        // but use the worked stitch to stay exact.
        val worked = if (ws) s.def.ws?.let { library[it] } ?: s.def else s.def
        consumed += worked.consumes
        produced += worked.produces
    }
    return RowCount(consumed, produced)
}

// ---------------------------------------------------------------------------------------------
// Editing
// ---------------------------------------------------------------------------------------------

private fun Chart.withCells(block: (MutableList<Cell>) -> Unit): Chart {
    val m = cells.toMutableList()
    block(m)
    return copy(cells = m)
}

/** Replaces every cell of the multi-cell stitch covering (row, col) with plain knit, keeping colours. */
private fun Chart.clearStitchAt(m: MutableList<Cell>, row: Int, col: Int, library: StitchLibrary) {
    val cur = m[index(row, col)]
    val width = library[cur.stitch]?.width ?: 1
    if (width <= 1 && cur.part == 0) return
    val head = col - cur.part
    for (c in head until head + maxOf(width, cur.part + 1)) {
        if (c in 0 until cols) {
            val i = index(row, c)
            if (m[i].stitch == cur.stitch) m[i] = Cell("k", m[i].color, 0)
        }
    }
}

/**
 * Places [def] with its left-most cell at (row, col). Wide stitches are shifted left when they
 * would overhang the right edge. Any cable partly covered is dissolved into knit stitches.
 * [color] of null keeps the existing cell colours.
 */
fun Chart.place(row: Int, col: Int, def: StitchDef, color: Int? = null, library: StitchLibrary): Chart {
    if (!inBounds(row, col) || def.width < 1 || def.width > cols) return this
    val start = col.coerceAtMost(cols - def.width)
    // Fast path: nothing to do.
    if (def.width == 1) {
        val cur = cell(row, start)
        if (cur.stitch == def.id && cur.part == 0 && (color == null || cur.color == color)) return this
    }
    return withCells { m ->
        for (c in start until start + def.width) clearStitchAt(m, row, c, library)
        for (c in start until start + def.width) {
            val i = index(row, c)
            m[i] = Cell(def.id, color ?: m[i].color, c - start)
        }
    }
}

/** Sets only the colour of a cell (colourwork painting). */
fun Chart.paintColor(row: Int, col: Int, color: Int): Chart {
    if (!inBounds(row, col) || cell(row, col).color == color) return this
    return withCells { m -> val i = index(row, col); m[i] = m[i].copy(color = color) }
}

/** Resets a cell (and the whole cable it belongs to) to an uncoloured knit stitch. */
fun Chart.erase(row: Int, col: Int, library: StitchLibrary): Chart {
    if (!inBounds(row, col)) return this
    val cur = cell(row, col)
    if (cur == Cell.Knit) return this
    return withCells { m ->
        clearStitchAt(m, row, col, library)
        m[index(row, col)] = Cell.Knit
    }
}

/** A rectangular selection, all bounds inclusive and normalised. */
data class CellRect(val row0: Int, val col0: Int, val row1: Int, val col1: Int) {
    val rows: Int get() = row1 - row0 + 1
    val cols: Int get() = col1 - col0 + 1
    fun contains(row: Int, col: Int) = row in row0..row1 && col in col0..col1

    companion object {
        fun of(rowA: Int, colA: Int, rowB: Int, colB: Int) =
            CellRect(minOf(rowA, rowB), minOf(colA, colB), maxOf(rowA, rowB), maxOf(colA, colB))
    }
}

fun Chart.clamp(rect: CellRect): CellRect = CellRect(
    rect.row0.coerceIn(0, rows - 1), rect.col0.coerceIn(0, cols - 1),
    rect.row1.coerceIn(0, rows - 1), rect.col1.coerceIn(0, cols - 1),
)

/** Fills [rect] with [def] (tiled for wide stitches) and optionally a colour. */
fun Chart.fill(rect: CellRect, def: StitchDef?, color: Int?, library: StitchLibrary): Chart {
    var chart = this
    val r = clamp(rect)
    for (row in r.row0..r.row1) {
        var col = r.col0
        while (col <= r.col1) {
            if (def != null) {
                if (col + def.width - 1 <= r.col1) {
                    chart = chart.place(row, col, def, color, library)
                    col += def.width
                } else {
                    // Not enough room for another wide stitch: leave the remainder as knit.
                    chart = chart.place(row, col, library.require(BuiltInStitches.KNIT), color, library)
                    col++
                }
            } else {
                if (color != null) chart = chart.paintColor(row, col, color)
                col++
            }
        }
    }
    return chart
}

fun Chart.clear(rect: CellRect, library: StitchLibrary): Chart {
    var chart = this
    val r = clamp(rect)
    for (row in r.row0..r.row1) for (col in r.col0..r.col1) chart = chart.erase(row, col, library)
    return chart
}

/** A copied block of cells, stored like a chart (bottom row first). */
data class Clip(val cols: Int, val rows: Int, val cells: List<Cell>)

/** Copies [rect]. Cables cut by the selection edge are copied as plain knit stitches. */
fun Chart.copyRect(rect: CellRect, library: StitchLibrary): Clip {
    val r = clamp(rect)
    val out = ArrayList<Cell>(r.rows * r.cols)
    for (row in r.row0..r.row1) for (col in r.col0..r.col1) {
        val c = cell(row, col)
        val width = library[c.stitch]?.width ?: 1
        val head = col - c.part
        val whole = head >= r.col0 && head + width - 1 <= r.col1
        out += if (whole) c else Cell("k", c.color, 0)
    }
    return Clip(r.cols, r.rows, out)
}

/** Pastes [clip] with its bottom-left cell at (row, col); parts outside the chart are dropped. */
fun Chart.paste(clip: Clip, row: Int, col: Int, library: StitchLibrary): Chart {
    var chart = this
    // First dissolve anything the paste will overlap, then write the cells verbatim.
    for (r in 0 until clip.rows) for (c in 0 until clip.cols) {
        if (chart.inBounds(row + r, col + c)) chart = chart.erase(row + r, col + c, library)
    }
    return chart.withCells { m ->
        for (r in 0 until clip.rows) for (c in 0 until clip.cols) {
            val tr = row + r
            val tc = col + c
            if (tr !in 0 until rows || tc !in 0 until cols) continue
            val src = clip.cells[r * clip.cols + c]
            val width = library[src.stitch]?.width ?: 1
            val headC = tc - src.part
            // Drop wide stitches that would be cut by the chart edge.
            val fits = headC >= 0 && headC + width - 1 < cols
            m[tr * cols + tc] = if (fits) src else Cell("k", src.color, 0)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Structure
// ---------------------------------------------------------------------------------------------

/** Inserts a knit row so that it becomes row [at] (0 = new bottom row, rows = new top row). */
fun Chart.insertRow(at: Int): Chart {
    if (rows >= MAX_CHART_SIZE) return this
    val pos = at.coerceIn(0, rows)
    val m = ArrayList<Cell>(cells.size + cols)
    m.addAll(cells.subList(0, pos * cols))
    repeat(cols) { m += Cell.Knit }
    m.addAll(cells.subList(pos * cols, cells.size))
    return copy(rows = rows + 1, cells = m, repeats = repeats.map {
        it.copy(
            rowStart = if (it.rowStart >= pos) it.rowStart + 1 else it.rowStart,
            rowEnd = if (it.rowEnd >= pos) it.rowEnd + 1 else it.rowEnd,
        )
    })
}

fun Chart.deleteRow(at: Int): Chart {
    if (rows <= 1 || at !in 0 until rows) return this
    val m = ArrayList<Cell>(cells.size - cols)
    m.addAll(cells.subList(0, at * cols))
    m.addAll(cells.subList((at + 1) * cols, cells.size))
    return copy(rows = rows - 1, cells = m, repeats = repeats.mapNotNull {
        when {
            it.rowStart == at && it.rowEnd == at -> null
            else -> it.copy(
                rowStart = if (it.rowStart > at) it.rowStart - 1 else it.rowStart,
                rowEnd = if (it.rowEnd >= at) it.rowEnd - 1 else it.rowEnd,
            )
        }
    })
}

/** Inserts a knit column so that it becomes column [at] (0 = new left edge). */
fun Chart.insertCol(at: Int, library: StitchLibrary): Chart {
    if (cols >= MAX_CHART_SIZE) return this
    val pos = at.coerceIn(0, cols)
    val m = ArrayList<Cell>(cells.size + rows)
    for (r in 0 until rows) {
        val row = rowCells(r).toMutableList()
        // Inserting inside a cable would tear it apart: dissolve it first.
        if (pos in 1 until cols && row[pos].part != 0) {
            val id = row[pos].stitch
            var c = pos - row[pos].part
            val width = library[id]?.width ?: 1
            repeat(width) { if (c in 0 until cols && row[c].stitch == id) row[c] = Cell("k", row[c].color, 0); c++ }
        }
        row.add(pos, Cell.Knit)
        m.addAll(row)
    }
    return copy(cols = cols + 1, cells = m, repeats = repeats.map {
        it.copy(
            colStart = if (it.colStart >= pos) it.colStart + 1 else it.colStart,
            colEnd = if (it.colEnd >= pos) it.colEnd + 1 else it.colEnd,
        )
    })
}

fun Chart.deleteCol(at: Int, library: StitchLibrary): Chart {
    if (cols <= 1 || at !in 0 until cols) return this
    var chart = this
    // Dissolve cables that run through the column.
    for (r in 0 until rows) {
        val c = chart.cell(r, at)
        if ((library[c.stitch]?.width ?: 1) > 1) chart = chart.erase(r, at, library)
    }
    val m = ArrayList<Cell>(cells.size - rows)
    for (r in 0 until rows) {
        val row = chart.rowCells(r)
        m.addAll(row.subList(0, at))
        m.addAll(row.subList(at + 1, cols))
    }
    return chart.copy(cols = cols - 1, cells = m, repeats = repeats.mapNotNull {
        when {
            it.colStart == at && it.colEnd == at -> null
            else -> it.copy(
                colStart = if (it.colStart > at) it.colStart - 1 else it.colStart,
                colEnd = if (it.colEnd >= at) it.colEnd - 1 else it.colEnd,
            )
        }
    })
}

enum class Anchor { START, END }

/**
 * Resizes the chart. New rows are added at the top ([Anchor.END]) or bottom; new columns at the
 * right ([Anchor.END]) or left. Shrinking removes from the same edge.
 */
fun Chart.resize(newCols: Int, newRows: Int, colAnchor: Anchor = Anchor.START, rowAnchor: Anchor = Anchor.END, library: StitchLibrary): Chart {
    var chart = this
    val targetCols = newCols.coerceIn(1, MAX_CHART_SIZE)
    val targetRows = newRows.coerceIn(1, MAX_CHART_SIZE)
    while (chart.rows < targetRows) chart = chart.insertRow(if (rowAnchor == Anchor.END) chart.rows else 0)
    while (chart.rows > targetRows) chart = chart.deleteRow(if (rowAnchor == Anchor.END) chart.rows - 1 else 0)
    while (chart.cols < targetCols) chart = chart.insertCol(if (colAnchor == Anchor.END) chart.cols else 0, library)
    while (chart.cols > targetCols) chart = chart.deleteCol(if (colAnchor == Anchor.END) chart.cols - 1 else 0, library)
    return chart.copy(repeats = chart.repeats.filter { it.colEnd < chart.cols && it.rowEnd < chart.rows && it.colStart <= it.colEnd && it.rowStart <= it.rowEnd })
}

/**
 * Mirrors [rect] left ↔ right. Directional stitches are swapped for their mirror image
 * (k2tog ↔ ssk, left cross ↔ right cross) so the fabric is truly mirrored.
 */
fun Chart.flipHorizontal(rect: CellRect, library: StitchLibrary): Chart {
    val r = clamp(rect)
    val clip = copyRect(r, library)
    val flipped = ArrayList<Cell>(clip.cells.size)
    for (row in 0 until clip.rows) {
        val src = clip.cells.subList(row * clip.cols, (row + 1) * clip.cols)
        val out = arrayOfNulls<Cell>(clip.cols)
        var c = 0
        while (c < clip.cols) {
            val cell = src[c]
            val def = library[cell.stitch]
            val width = def?.width?.coerceAtLeast(1) ?: 1
            val mirrored = def?.mirror?.let { library[it] }?.takeIf { it.width == width } ?: def
            val id = mirrored?.id ?: cell.stitch
            // Head at c, occupying c..c+width-1 → mirrored block starts at cols-(c+width).
            val start = clip.cols - (c + width)
            for (k in 0 until width) {
                val colorSrc = src[(c + width - 1 - k).coerceIn(0, clip.cols - 1)].color
                if (start + k in 0 until clip.cols) out[start + k] = Cell(id, colorSrc, k)
            }
            c += width
        }
        for (cell in out) flipped += cell ?: Cell.Knit
    }
    return paste(Clip(clip.cols, clip.rows, flipped), r.row0, r.col0, library)
}

/** Mirrors [rect] top ↔ bottom. */
fun Chart.flipVertical(rect: CellRect, library: StitchLibrary): Chart {
    val r = clamp(rect)
    val clip = copyRect(r, library)
    val out = ArrayList<Cell>(clip.cells.size)
    for (row in clip.rows - 1 downTo 0) out.addAll(clip.cells.subList(row * clip.cols, (row + 1) * clip.cols))
    return paste(Clip(clip.cols, clip.rows, out), r.row0, r.col0, library)
}

// ---------------------------------------------------------------------------------------------
// Repeats and colours
// ---------------------------------------------------------------------------------------------

fun Chart.addRepeat(id: String, rect: CellRect): Chart {
    val r = clamp(rect)
    val rep = ChartRepeat(id, r.col0, r.col1, r.row0, r.row1)
    // A new box replaces any box it overlaps; overlapping repeats are not meaningful.
    val kept = repeats.filterNot { it.colStart <= r.col1 && it.colEnd >= r.col0 && it.rowStart <= r.row1 && it.rowEnd >= r.row0 }
    return copy(repeats = kept + rep)
}

fun Chart.removeRepeat(id: String): Chart = copy(repeats = repeats.filterNot { it.id == id })

fun Chart.repeatAt(row: Int, col: Int): ChartRepeat? =
    repeats.firstOrNull { row in it.rowStart..it.rowEnd && col in it.colStart..it.colEnd }

/** Repeat boxes that apply to [row], ordered left to right. */
fun Chart.repeatsOnRow(row: Int): List<ChartRepeat> =
    repeats.filter { row in it.rowStart..it.rowEnd }.sortedBy { it.colStart }

fun Chart.addColor(argb: Long): Chart {
    val name = nextColorName(palette.map { it.name })
    return copy(palette = palette + ChartColor(name, argb))
}

/** Removes a palette colour; cells using it become uncoloured and higher indices shift down. */
fun Chart.removeColor(index: Int): Chart {
    if (index !in palette.indices) return this
    return copy(
        palette = palette.filterIndexed { i, _ -> i != index },
        cells = cells.map {
            when {
                it.color == index -> it.copy(color = -1)
                it.color > index -> it.copy(color = it.color - 1)
                else -> it
            }
        },
    )
}

/** Indices of palette colours actually used by at least one cell. */
fun Chart.usedColors(): Set<Int> = cells.mapNotNullTo(HashSet()) { if (it.color >= 0) it.color else null }

/** Ids of all stitches used. */
fun Chart.usedStitches(): Set<String> = cells.mapTo(LinkedHashSet()) { it.stitch }

private fun nextColorName(taken: List<String>): String {
    for (c in 'A'..'Z') if (c.toString() !in taken) return c.toString()
    return "C${taken.size + 1}"
}

/** Name used for a cell colour in written instructions; uncoloured cells are the main colour. */
fun Chart.colorName(index: Int): String = palette.getOrNull(index)?.name ?: "MC"
