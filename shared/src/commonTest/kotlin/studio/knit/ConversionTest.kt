package studio.knit

import studio.knit.chart.CellRect
import studio.knit.chart.addColor
import studio.knit.chart.addRepeat
import studio.knit.chart.blankChart
import studio.knit.chart.cell
import studio.knit.chart.paintColor
import studio.knit.chart.place
import studio.knit.convert.chartToSection
import studio.knit.convert.chartToText
import studio.knit.convert.sectionToChart
import studio.knit.model.Chart
import studio.knit.model.Construction
import studio.knit.model.Side
import studio.knit.model.SizedInt
import studio.knit.validate.Severity
import studio.knit.written.evaluateSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversionTest {
    private fun Chart.put(row: Int, col: Int, id: String) = place(row, col, lib.require(id), null, lib)
    private fun Chart.ids(row: Int) = (0 until cols).map { cell(row, it).stitch }

    /** k2 p2 rib as drawn: purl columns on the left. */
    private fun rib(): Chart {
        var c = blankChart("rib", 4, 2)
        for (r in 0..1) c = c.put(r, 0, "p").put(r, 1, "p")
        return c
    }

    @Test fun rightSideRowsReadRightToLeftWrongSideLeftToRightAndInverted() {
        val (rows, notes) = chartToText(rib(), lib)
        assertTrue(notes.isEmpty())
        // Row 1 (RS) read from the right: k2, p2. Row 2 (WS) read from the left, symbols inverted:
        // the purl symbols are knitted, the knit symbols purled.
        assertEquals(listOf("K2, p2", "K2, p2"), rows)
    }

    @Test fun inTheRoundEveryRowReadsTheSame() {
        val (rows, _) = chartToText(rib().copy(construction = Construction.ROUND), lib)
        assertEquals(listOf("K2, p2", "K2, p2"), rows)
        var c = blankChart("x", 3, 2, construction = Construction.ROUND)
        c = c.put(1, 0, "p")
        assertEquals(listOf("K3", "K2, p1"), chartToText(c, lib).first)
    }

    @Test fun stockinetteFlatIsKnitThenPurl() {
        val (rows, _) = chartToText(blankChart("s", 5, 2), lib)
        assertEquals(listOf("K5", "P5"), rows)
    }

    @Test fun decreasesSwapOnWrongSide() {
        var c = blankChart("d", 4, 2)
        c = c.put(1, 0, "k2tog").put(1, 3, "yo")
        // Row 2 is WS, read left → right: k2tog symbol is worked p2tog; yo stays yo.
        assertEquals("P2tog, p2, yo", chartToText(c, lib).first[1])
    }

    @Test fun repeatBoxBecomesStarRepeatWithEdgeStitches() {
        // 1 edge st | 4-st repeat | 2 edge sts, as drawn left to right: k | p p k k | k k
        var c = blankChart("r", 7, 1)
        c = c.put(0, 1, "p").put(0, 2, "p").addRepeat("box", CellRect.of(0, 1, 0, 4))
        val (rows, _) = chartToText(c, lib)
        assertEquals("K2, *k2, p2; rep from * to last 1 st, k1", rows[0])
    }

    @Test fun noStitchCellsAreSkipped() {
        var c = blankChart("n", 5, 1)
        c = c.put(0, 2, "none").put(0, 0, "yo")
        assertEquals("K3, yo", chartToText(c, lib).first[0])
    }

    @Test fun cableOnWrongSideIsFlaggedNotInvented() {
        val c = blankChart("c", 4, 2).put(1, 0, "c4f")
        val (_, notes) = chartToText(c, lib)
        assertTrue(notes.any { it.severity == Severity.WARNING && it.message.contains("no wrong-side equivalent") }, notes.toString())
    }

    @Test fun colourworkNamesColours() {
        var c = blankChart("cw", 4, 1, construction = Construction.ROUND).addColor(0xFFAA0000)
        c = c.paintColor(0, 0, 0).paintColor(0, 1, 0)
        assertEquals("With MC k2, with A k2", chartToText(c, lib).first[0])
    }

    @Test fun generatedSectionIsLinkedAndConsistent() {
        val chart = rib().addRepeat("b", CellRect.of(0, 0, 1, 3))
        val (section, _) = chartToSection(chart, lib, ::id)
        assertEquals(chart.id, section.chartId)
        assertTrue(section.generated)
        val eval = evaluateSection(section.copy(castOn = SizedInt(24)), 0, lib)
        assertTrue(eval.issues.isEmpty(), eval.issues.toString())
        assertEquals(24, eval.endCount)
    }

    // --- Text → chart --------------------------------------------------------------------------

    @Test fun writtenRibBecomesChartWithRepeatBox() {
        // Row 2 is written so its repeat lines up with row 1 (edge stitches at the same edge).
        val s = section("K2, *p2, k2; rep from * to end", "*P2, k2; rep from * to last 2 sts, p2")
        val result = sectionToChart(s, 0, lib, ::id)
        val chart = assertNotNull(result.chart, result.notes.toString())
        assertEquals(6 to 2, chart.cols to chart.rows)
        // Drawn left → right, RS appearance: row 1 knitted R→L (k2, p2, k2).
        assertEquals(listOf("k", "k", "p", "p", "k", "k"), chart.ids(0))
        // Row 2 (WS) "p2, k2, p2" looks the same from the right side.
        assertEquals(listOf("k", "k", "p", "p", "k", "k"), chart.ids(1))
        val box = chart.repeats.single()
        assertEquals(0 to 3, box.colStart to box.colEnd)
        assertEquals(0 to 1, box.rowStart to box.rowEnd)
    }

    @Test fun staggeredRepeatsGetOneBoxPerRow() {
        // Edge stitches at the start of both rows sit at opposite edges of the fabric.
        val s = section("K2, *p2, k2; rep from * to end", "P2, *k2, p2; rep from * to end")
        val chart = sectionToChart(s, 0, lib, ::id).chart!!
        assertEquals(listOf(0 to 3, 2 to 5), chart.repeats.map { it.colStart to it.colEnd })
    }

    @Test fun chartToTextToChartIsStable() {
        var original = blankChart("o", 6, 4)
        original = original.put(0, 0, "yo").put(0, 1, "k2tog").put(2, 4, "ssk").put(2, 5, "yo").put(1, 2, "p").put(3, 3, "p")
        val (section, _) = chartToSection(original, lib, ::id)
        val back = assertNotNull(sectionToChart(section, 0, lib, ::id).chart)
        assertEquals(original.cells, back.cells)
    }

    @Test fun cableChartSurvivesTheRoundTrip() {
        val original = blankChart("o", 8, 1).put(0, 2, "c4b").put(0, 0, "p").put(0, 1, "p").put(0, 6, "p").put(0, 7, "p")
        val (section, _) = chartToSection(original, lib, ::id)
        assertEquals("P2, C4B, p2", section.rows[0].text)
        assertEquals(original.cells, sectionToChart(section, 0, lib, ::id).chart!!.cells)
    }

    @Test fun openEndedRowsNeedACastOnCount() {
        val s = section("Knit", "Purl")
        val result = sectionToChart(s, 0, lib, ::id)
        assertNull(result.chart)
        assertTrue(result.notes.any { it.severity == Severity.ERROR && it.message.contains("cast-on") }, result.notes.toString())
        val ok = sectionToChart(s.copy(castOn = SizedInt(5)), 0, lib, ::id)
        assertEquals(5 to 2, ok.chart!!.cols to ok.chart!!.rows)
    }

    @Test fun shapingIsPaddedWithNoStitchAndSaysSo() {
        val s = section("K1, k2tog, knit to last 3 sts, ssk, k1", "Purl", castOn = SizedInt(8))
        val result = sectionToChart(s, 0, lib, ::id)
        val chart = assertNotNull(result.chart)
        assertEquals(6, chart.cols)
        assertTrue(result.notes.none { it.severity == Severity.ERROR })
        // Row 1 has 6 symbols for 8 stitches worked; row 2 has 6 purls. No padding needed here.
        assertEquals(listOf("k", "ssk", "k", "k", "k2tog", "k"), chart.ids(0))

        val inc = section("K1, yo, k1", "Purl", castOn = SizedInt(2))
        val r2 = sectionToChart(inc, 0, lib, ::id)
        assertEquals(3, r2.chart!!.cols)
    }

    @Test fun differentWidthsArePaddedAndReported() {
        val s = section("K4", "P2tog, p2tog", "K2", castOn = SizedInt(4))
        val result = sectionToChart(s, 0, lib, ::id)
        val chart = assertNotNull(result.chart)
        assertEquals(4, chart.cols)
        assertEquals(listOf("none", "none", "k2tog", "k2tog"), chart.ids(1))
        assertTrue(result.notes.any { it.severity == Severity.INFO && it.message.contains("No stitch") }, result.notes.toString())
    }

    @Test fun wrongSideFirstSectionKeepsItsSide() {
        val s = section("Purl", "Knit", castOn = SizedInt(3), firstSide = Side.WS)
        val chart = sectionToChart(s, 0, lib, ::id).chart!!
        assertEquals(Side.WS, chart.firstRowSide)
        assertTrue(chart.cells.all { it.stitch == "k" })
    }

    @Test fun parseErrorsBlockConversion() {
        val result = sectionToChart(section("K2, wibble"), 0, lib, ::id)
        assertNull(result.chart)
        assertTrue(result.notes.any { it.message.contains("wibble") })
    }

    @Test fun colourNamesBecomeAPalette() {
        val s = section("With MC k2, with B k2", construction = Construction.ROUND)
        val chart = sectionToChart(s, 0, lib, ::id).chart!!
        assertEquals(listOf("B"), chart.palette.map { it.name })
        // Round, read right → left: MC k2 on the right, B on the left.
        assertEquals(listOf(0, 0, -1, -1), (0..3).map { chart.cell(0, it).color })
    }
}
