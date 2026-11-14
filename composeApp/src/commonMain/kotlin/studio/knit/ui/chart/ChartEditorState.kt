package studio.knit.ui.chart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import studio.knit.chart.CellRect
import studio.knit.chart.Clip
import studio.knit.chart.addRepeat
import studio.knit.chart.cell
import studio.knit.chart.clear
import studio.knit.chart.copyRect
import studio.knit.chart.erase
import studio.knit.chart.fill
import studio.knit.chart.flipHorizontal
import studio.knit.chart.flipVertical
import studio.knit.chart.inBounds
import studio.knit.chart.paintColor
import studio.knit.chart.paste
import studio.knit.chart.place
import studio.knit.chart.removeRepeat
import studio.knit.chart.repeatAt
import studio.knit.convert.chartToSection
import studio.knit.model.Chart
import studio.knit.stitch.BuiltInStitches
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.app.PatternEditor
import studio.knit.util.newId
import kotlin.math.abs
import kotlin.math.floor

enum class ChartTool(val label: String) { DRAW("Draw"), ERASE("Erase"), SELECT("Select"), MOVE("Move") }
enum class ChartLayer { STITCH, COLOR }

/**
 * Everything the chart editor keeps while it is open: the current tool, the viewport, the
 * selection, the clipboard and the undo history. All chart changes go through [commit] so they
 * are undoable and saved.
 */
class ChartEditorState(
    private val editor: PatternEditor,
    val chartId: String,
    private val library: () -> StitchLibrary,
    private val ukTerms: () -> Boolean,
    private val haptic: (Boolean) -> Unit,
) {
    var tool by mutableStateOf(ChartTool.DRAW)
    var layer by mutableStateOf(ChartLayer.STITCH)
    var stitchId by mutableStateOf(BuiltInStitches.PURL)
    var colorIndex by mutableIntStateOf(0)
    var selection by mutableStateOf<CellRect?>(null)
    var clip by mutableStateOf<Clip?>(null)

    /** Chart being drawn during a stroke, before it is committed. */
    var preview by mutableStateOf<Chart?>(null)
        private set

    private val undoStack = mutableStateListOf<Chart>()
    private val redoStack = mutableStateListOf<Chart>()
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Stitches offered in the quick palette, most recently used first. */
    val recent = mutableStateListOf<String>().apply { addAll(BuiltInStitches.quickPalette) }

    // Viewport: size of one cell in pixels and the position of the grid's top-left corner.
    var cellPx by mutableFloatStateOf(0f)
    var offset by mutableStateOf(Offset.Zero)
    var viewSize by mutableStateOf(Size.Zero)
    private var minCell = 6f
    private var maxCell = 160f

    val chart: Chart? get() = editor.current?.charts?.firstOrNull { it.id == chartId }

    fun useStitch(id: String) {
        stitchId = id
        layer = ChartLayer.STITCH
        if (tool != ChartTool.DRAW && tool != ChartTool.SELECT) tool = ChartTool.DRAW
        recent.remove(id)
        recent.add(0, id)
        while (recent.size > 14) recent.removeAt(recent.size - 1)
    }

    // --- Viewport --------------------------------------------------------------------------------

    fun fit(chart: Chart, density: Float) {
        if (viewSize.width <= 0f || viewSize.height <= 0f) return
        minCell = 5f * density
        maxCell = 72f * density
        // Leave a margin of about one and a half cells for the row and stitch numbers.
        val cell = minOf(viewSize.width / (chart.cols + 3.4f), viewSize.height / (chart.rows + 2.6f)).coerceIn(minCell, 44f * density)
        cellPx = cell
        offset = Offset((viewSize.width - chart.cols * cell) / 2f, (viewSize.height - chart.rows * cell) / 2f - cell * 0.4f)
    }

    fun transform(chart: Chart, centroid: Offset, pan: Offset, zoom: Float) {
        val newCell = (cellPx * zoom).coerceIn(minCell, maxCell)
        val factor = newCell / cellPx
        cellPx = newCell
        offset = clampOffset(chart, centroid - (centroid - offset) * factor + pan)
    }

    fun panBy(chart: Chart, delta: Offset) { offset = clampOffset(chart, offset + delta) }

    /** Keeps at least a couple of cells of the chart on screen. */
    private fun clampOffset(chart: Chart, o: Offset): Offset {
        val keep = cellPx * 2
        val w = chart.cols * cellPx
        val h = chart.rows * cellPx
        return Offset(o.x.coerceIn(keep - w, viewSize.width - keep), o.y.coerceIn(keep - h, viewSize.height - keep))
    }

    fun cellAt(chart: Chart, pos: Offset): Pair<Int, Int>? {
        if (cellPx <= 0f) return null
        val col = floor((pos.x - offset.x) / cellPx).toInt()
        val fromTop = floor((pos.y - offset.y) / cellPx).toInt()
        val row = chart.rows - 1 - fromTop
        return if (chart.inBounds(row, col)) row to col else null
    }

    /** Like [cellAt] but clamps to the nearest cell, for dragging a selection past the edge. */
    fun nearestCell(chart: Chart, pos: Offset): Pair<Int, Int> {
        val col = floor((pos.x - offset.x) / cellPx).toInt().coerceIn(0, chart.cols - 1)
        val row = (chart.rows - 1 - floor((pos.y - offset.y) / cellPx).toInt()).coerceIn(0, chart.rows - 1)
        return row to col
    }

    fun centerOn(chart: Chart, row: Int, col: Int, density: Float) {
        if (cellPx < 22f * density) cellPx = (28f * density).coerceIn(minCell, maxCell)
        val x = (col + 0.5f) * cellPx
        val y = (chart.rows - 1 - row + 0.5f) * cellPx
        offset = clampOffset(chart, Offset(viewSize.width / 2f - x, viewSize.height / 2f - y))
    }

    // --- Committing changes ----------------------------------------------------------------------

    /**
     * Stores [next] as the chart, records undo, and rewrites the linked written rows when those
     * rows are still exactly what was generated from the chart.
     */
    fun commit(next: Chart, before: Chart? = chart) {
        if (before == null || next == before) return
        undoStack.add(before)
        if (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        redoStack.clear()
        store(next)
    }

    private fun store(next: Chart) {
        val lib = library()
        editor.update { p ->
            p.copy(
                charts = p.charts.map { if (it.id == chartId) next else it },
                sections = p.sections.map { s ->
                    if (s.chartId == chartId && s.generated) {
                        val (fresh, _) = chartToSection(next, lib, ::newId, ukTerms())
                        // Keep row ids where the row count is unchanged so progress and focus survive.
                        val rows = if (fresh.rows.size == s.rows.size) fresh.rows.mapIndexed { i, r -> r.copy(id = s.rows[i].id) } else fresh.rows
                        s.copy(rows = rows, construction = fresh.construction, firstRowSide = fresh.firstRowSide)
                    } else s
                },
            )
        }
    }

    fun undo() {
        val current = chart ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.add(current)
        selection = selection?.let { clampSelection(previous, it) }
        store(previous)
    }

    fun redo() {
        val current = chart ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(current)
        selection = selection?.let { clampSelection(next, it) }
        store(next)
    }

    private fun clampSelection(chart: Chart, r: CellRect): CellRect? =
        if (r.row0 >= chart.rows || r.col0 >= chart.cols) null
        else CellRect(r.row0, r.col0, r.row1.coerceAtMost(chart.rows - 1), r.col1.coerceAtMost(chart.cols - 1))

    // --- Strokes ---------------------------------------------------------------------------------

    private var strokeBase: Chart? = null
    private var lastCell: Pair<Int, Int>? = null
    private var selectAnchor: Pair<Int, Int>? = null

    fun beginStroke(chart: Chart, start: Pair<Int, Int>?) {
        strokeBase = chart
        lastCell = null
        if (tool == ChartTool.SELECT) {
            selectAnchor = start
            if (start != null) selection = CellRect.of(start.first, start.second, start.first, start.second)
        } else {
            preview = chart
            if (start != null) strokeTo(start)
        }
    }

    fun strokeTo(cell: Pair<Int, Int>?) {
        if (cell == null || cell == lastCell) return
        if (tool == ChartTool.SELECT) {
            val a = selectAnchor ?: cell.also { selectAnchor = it }
            selection = CellRect.of(a.first, a.second, cell.first, cell.second)
            lastCell = cell
            return
        }
        var working = preview ?: return
        val before = working
        // Fill in the cells between the previous and the current position so fast strokes leave no gaps.
        for ((r, c) in line(lastCell ?: cell, cell)) working = applyAt(working, r, c)
        lastCell = cell
        if (working !== before) {
            preview = working
            haptic(false)
        }
    }

    fun endStroke() {
        val base = strokeBase
        val result = preview
        preview = null
        strokeBase = null
        lastCell = null
        selectAnchor = null
        if (base != null && result != null) commit(result, base)
    }

    fun tap(chart: Chart, cell: Pair<Int, Int>?) {
        when (tool) {
            ChartTool.MOVE -> Unit
            ChartTool.SELECT -> {
                val current = selection
                selection = when {
                    cell == null -> null
                    current != null && current.rows == 1 && current.cols == 1 && current.contains(cell.first, cell.second) -> null
                    else -> CellRect.of(cell.first, cell.second, cell.first, cell.second)
                }
            }
            else -> if (cell != null) {
                val next = applyAt(chart, cell.first, cell.second)
                if (next !== chart) { haptic(false); commit(next, chart) }
            }
        }
    }

    private fun applyAt(chart: Chart, row: Int, col: Int): Chart {
        val lib = library()
        return when (tool) {
            ChartTool.ERASE -> chart.erase(row, col, lib)
            ChartTool.DRAW -> when (layer) {
                ChartLayer.COLOR -> chart.paintColor(row, col, colorIndex)
                ChartLayer.STITCH -> {
                    val def = lib.require(stitchId)
                    // Dragging a wide stitch should not smear it: only place where it is not already.
                    val cur = chart.cell(row, col)
                    if (def.width > 1 && cur.stitch == def.id) chart else chart.place(row, col, def, null, lib)
                }
            }
            else -> chart
        }
    }

    private fun line(from: Pair<Int, Int>, to: Pair<Int, Int>): List<Pair<Int, Int>> {
        var (r, c) = from
        val dr = abs(to.first - r)
        val dc = abs(to.second - c)
        val sr = if (r < to.first) 1 else -1
        val sc = if (c < to.second) 1 else -1
        var err = dc - dr
        val out = ArrayList<Pair<Int, Int>>()
        while (true) {
            out += r to c
            if (r == to.first && c == to.second) break
            val e2 = 2 * err
            if (e2 > -dr) { err -= dr; c += sc }
            if (e2 < dc) { err += dc; r += sr }
        }
        return out
    }

    // --- Selection actions -----------------------------------------------------------------------

    fun copySelection() {
        val c = chart ?: return
        val s = selection ?: return
        clip = c.copyRect(s, library())
    }

    fun cutSelection() {
        val c = chart ?: return
        val s = selection ?: return
        clip = c.copyRect(s, library())
        commit(c.clear(s, library()))
    }

    /** Pastes with the clipboard's bottom-left cell on the selection's bottom-left cell. */
    fun paste() {
        val c = chart ?: return
        val s = selection ?: return
        val data = clip ?: return
        commit(c.paste(data, s.row0, s.col0, library()))
        selection = clampSelection(c, CellRect(s.row0, s.col0, s.row0 + data.rows - 1, s.col0 + data.cols - 1))
    }

    fun fillSelection() {
        val c = chart ?: return
        val s = selection ?: return
        val lib = library()
        commit(if (layer == ChartLayer.COLOR) c.fill(s, null, colorIndex, lib) else c.fill(s, lib.require(stitchId), null, lib))
    }

    fun clearSelection() {
        val c = chart ?: return
        val s = selection ?: return
        commit(c.clear(s, library()))
    }

    fun flip(horizontal: Boolean) {
        val c = chart ?: return
        val s = selection ?: return
        commit(if (horizontal) c.flipHorizontal(s, library()) else c.flipVertical(s, library()))
    }

    /** The repeat box exactly matching or containing the selection, if any. */
    fun repeatInSelection(): String? {
        val c = chart ?: return null
        val s = selection ?: return null
        return c.repeatAt(s.row0, s.col0)?.id
    }

    fun toggleRepeat() {
        val c = chart ?: return
        val s = selection ?: return
        val existing = repeatInSelection()
        commit(if (existing != null) c.removeRepeat(existing) else c.addRepeat(newId(), s))
    }

    private companion object {
        const val MAX_UNDO = 80
    }
}
