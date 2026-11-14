package studio.knit

import studio.knit.chart.Anchor
import studio.knit.chart.CellRect
import studio.knit.chart.addRepeat
import studio.knit.chart.blankChart
import studio.knit.chart.cell
import studio.knit.chart.copyRect
import studio.knit.chart.deleteCol
import studio.knit.chart.deleteRow
import studio.knit.chart.directionOf
import studio.knit.chart.erase
import studio.knit.chart.fill
import studio.knit.chart.flipHorizontal
import studio.knit.chart.insertCol
import studio.knit.chart.insertRow
import studio.knit.chart.paste
import studio.knit.chart.place
import studio.knit.chart.readingOrder
import studio.knit.chart.resize
import studio.knit.chart.rowCount
import studio.knit.chart.sideOf
import studio.knit.chart.stitchNumber
import studio.knit.model.Cell
import studio.knit.model.Chart
import studio.knit.model.Construction
import studio.knit.model.Direction
import studio.knit.model.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ChartTest {
    private fun chart(cols: Int = 6, rows: Int = 4) = blankChart("c", cols, rows)
    private fun Chart.put(row: Int, col: Int, id: String) = place(row, col, lib.require(id), null, lib)
    private fun Chart.ids(row: Int) = (0 until cols).map { cell(row, it).stitch }

    @Test fun flatChartAlternatesSidesAndDirections() {
        val c = chart()
        assertEquals(listOf(Side.RS, Side.WS, Side.RS, Side.WS), (0..3).map { c.sideOf(it) })
        assertEquals(Direction.RIGHT_TO_LEFT, c.directionOf(0))
        assertEquals(Direction.LEFT_TO_RIGHT, c.directionOf(1))
        assertEquals(listOf(5, 4, 3, 2, 1, 0), c.readingOrder(0).toList())
        assertEquals(listOf(0, 1, 2, 3, 4, 5), c.readingOrder(1).toList())
    }

    @Test fun roundChartIsAlwaysRightSideRightToLeft() {
        val c = chart().copy(construction = Construction.ROUND)
        assertEquals(listOf(Side.RS, Side.RS, Side.RS), (0..2).map { c.sideOf(it) })
        assertEquals(listOf(5, 4, 3, 2, 1, 0), c.readingOrder(1).toList())
    }

    @Test fun wrongSideFirstRowAndMirroredReading() {
        val ws = chart().copy(firstRowSide = Side.WS)
        assertEquals(Side.WS, ws.sideOf(0))
        assertEquals(Direction.LEFT_TO_RIGHT, ws.directionOf(0))
        val mirrored = chart().copy(rsDirection = Direction.LEFT_TO_RIGHT)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), mirrored.readingOrder(0).toList())
        assertEquals(1, mirrored.stitchNumber(0))
        assertEquals(1, chart().stitchNumber(5))
    }

    @Test fun rowCountsFollowStitchSemantics() {
        val c = chart().put(0, 0, "yo").put(0, 1, "k2tog").put(0, 2, "cdd").put(0, 3, "none")
        // yo 0→1, k2tog 2→1, cdd 3→1, none 0→0, k k 1→1 each
        val count = c.rowCount(0, lib)
        assertEquals(7, count.consumed)
        assertEquals(5, count.produced)
    }

    @Test fun cablePlacementAndErase() {
        var c = chart().put(1, 1, "c4f")
        assertEquals(listOf("k", "c4f", "c4f", "c4f", "c4f", "k"), c.ids(1))
        assertEquals(listOf(0, 0, 1, 2, 3, 0), (0..5).map { c.cell(1, it).part })
        // Overwriting one cell dissolves the whole cable.
        c = c.put(1, 3, "p")
        assertEquals(listOf("k", "k", "k", "p", "k", "k"), c.ids(1))
        // A cable placed at the right edge shifts left to fit.
        val edge = chart().put(0, 5, "c4b")
        assertEquals(listOf("k", "k", "c4b", "c4b", "c4b", "c4b"), edge.ids(0))
        // Erasing any part removes it all.
        assertEquals(List(6) { "k" }, edge.erase(0, 4, lib).ids(0))
    }

    @Test fun placingTheSameStitchReturnsTheSameChart() {
        val c = chart().put(0, 0, "p")
        assertSame(c, c.put(0, 0, "p"))
    }

    @Test fun fillCopyPaste() {
        val c = chart().fill(CellRect.of(0, 0, 1, 2), lib.require("p"), null, lib)
        assertEquals(listOf("p", "p", "p", "k", "k", "k"), c.ids(0))
        val clip = c.copyRect(CellRect.of(0, 0, 1, 2), lib)
        val pasted = c.paste(clip, 2, 3, lib)
        assertEquals(listOf("k", "k", "k", "p", "p", "p"), pasted.ids(2))
        assertEquals(listOf("k", "k", "k", "p", "p", "p"), pasted.ids(3))
        // Pasting over the edge drops what does not fit.
        val over = c.paste(clip, 3, 5, lib)
        assertEquals(listOf("k", "k", "k", "k", "k", "p"), over.ids(3))
    }

    @Test fun copyingHalfACableYieldsKnitStitches() {
        val c = chart().put(0, 1, "c4f")
        val clip = c.copyRect(CellRect.of(0, 0, 0, 2), lib)
        assertEquals(listOf("k", "k", "k"), clip.cells.map { it.stitch })
    }

    @Test fun insertAndDeleteRowsAndColumns() {
        var c = chart(3, 2).put(0, 0, "p").put(1, 2, "yo").addRepeat("r", CellRect.of(0, 0, 1, 1))
        c = c.insertRow(1)
        assertEquals(3, c.rows)
        assertEquals(listOf("k", "k", "k"), c.ids(1))
        assertEquals("yo", c.cell(2, 2).stitch)
        assertEquals(2, c.repeats.single().rowEnd)
        c = c.insertCol(0, lib)
        assertEquals(4, c.cols)
        assertEquals("p", c.cell(0, 1).stitch)
        assertEquals(1, c.repeats.single().colStart)
        c = c.deleteCol(0, lib).deleteRow(1)
        assertEquals(3 to 2, c.cols to c.rows)
        assertEquals("p", c.cell(0, 0).stitch)
        assertEquals("yo", c.cell(1, 2).stitch)
        assertEquals(CellRect(0, 0, 1, 1), c.repeats.single().let { CellRect(it.rowStart, it.colStart, it.rowEnd, it.colEnd) })
    }

    @Test fun deletingAColumnThroughACableDissolvesIt() {
        val c = chart().put(0, 1, "c4f").deleteCol(2, lib)
        assertEquals(List(5) { "k" }, c.ids(0))
        assertEquals(c.cols * c.rows, c.cells.size)
    }

    @Test fun resizeKeepsContentAnchored() {
        val c = chart(3, 2).put(0, 0, "p")
        val bigger = c.resize(5, 4, Anchor.END, Anchor.END, lib)
        assertEquals(5 to 4, bigger.cols to bigger.rows)
        assertEquals("p", bigger.cell(0, 0).stitch)
        val left = c.resize(5, 2, Anchor.START, Anchor.END, lib)
        assertEquals("p", left.cell(0, 2).stitch)
        assertEquals(20, bigger.cells.size)
    }

    @Test fun flipMirrorsLeaningStitches() {
        val c = chart(4, 1).put(0, 0, "k2tog").put(0, 1, "yo").put(0, 3, "p")
        val f = c.flipHorizontal(CellRect.of(0, 0, 0, 3), lib)
        assertEquals(listOf("p", "k", "yo", "ssk"), f.ids(0))
        val cable = chart(6, 1).put(0, 0, "c4f").flipHorizontal(CellRect.of(0, 0, 0, 5), lib)
        assertEquals(listOf("k", "k", "c4b", "c4b", "c4b", "c4b"), cable.ids(0))
        assertEquals(listOf(0, 1, 2, 3), (2..5).map { cable.cell(0, it).part })
    }

    @Test fun cellTextFormRoundTrips() {
        for (cell in listOf(Cell(), Cell("p", 2), Cell("c4f", -1, 3), Cell("c4f", 1, 2), Cell("1/1x", 0, 0))) {
            assertEquals(cell, studio.knit.model.CellSerializer.decode(studio.knit.model.CellSerializer.encode(cell)))
        }
    }
}
