package studio.knit

import studio.knit.chart.CellRect
import studio.knit.chart.addRepeat
import studio.knit.chart.blankChart
import studio.knit.chart.place
import studio.knit.convert.chartToSection
import studio.knit.model.Cell
import studio.knit.model.Chart
import studio.knit.model.Gauge
import studio.knit.model.Measurement
import studio.knit.model.Pattern
import studio.knit.model.SizedInt
import studio.knit.validate.Issue
import studio.knit.validate.Severity
import studio.knit.validate.Validator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatorTest {
    private fun Chart.put(row: Int, col: Int, id: String) = place(row, col, lib.require(id), null, lib)
    private fun List<Issue>.codes(sev: Severity) = filter { it.severity == sev }.map { it.code }
    private fun pattern(vararg charts: Chart) = Pattern(id = "p", name = "Test", gauge = Gauge(20.0, 28.0), charts = charts.toList())

    @Test fun cleanChartHasNoErrors() {
        val c = blankChart("c", 6, 4).put(0, 0, "yo").put(0, 1, "k2tog")
        assertTrue(Validator.validateChart(c, lib).none { it.severity != Severity.INFO })
    }

    @Test fun unbalancedLaceRowIsCaughtOnTheNextRow() {
        // A yarn over without its decrease on row 2: it works only 5 of the 6 stitches row 1 left.
        val c = blankChart("c", 6, 3).put(1, 0, "yo")
        val issues = Validator.validateChart(c, lib)
        val error = issues.single { it.severity == Severity.ERROR }
        assertEquals("chart.count-mismatch", error.code)
        assertEquals(1, error.location.row)
        assertTrue(error.message.contains("works 5") && error.message.contains("leaves 6") && error.message.contains("1 too few"), error.message)
        assertTrue(issues.any { it.code == "chart.count-change" && it.location.row == 1 })
    }

    @Test fun noStitchKeepsShapingConsistent() {
        // Row 1: 4 sts. Row 2 decreases to 3 and the chart shows a "no stitch" on row 3.
        var c = blankChart("c", 4, 3)
        c = c.put(1, 0, "k2tog").put(1, 1, "none")   // row 2: consumes 2+0+1+1 = 4, makes 3
        c = c.put(2, 1, "none")                      // row 3: 3 stitches
        assertTrue(Validator.validateChart(c, lib).none { it.severity == Severity.ERROR }, Validator.validateChart(c, lib).toString())
    }

    @Test fun unknownStitchAndBrokenCable() {
        val broken = blankChart("c", 4, 1).let { it.copy(cells = listOf(Cell("c4f", -1, 0), Cell("c4f", -1, 1), Cell("k"), Cell("mystery"))) }
        val codes = Validator.validateChart(broken, lib).codes(Severity.ERROR)
        assertTrue("chart.unknown-stitch" in codes)
        assertTrue("chart.broken-stitch" in codes)
    }

    @Test fun damagedDimensionsAreReportedWithoutCrashing() {
        val bad = blankChart("c", 4, 2).copy(cols = 5)
        assertEquals(listOf("chart.dimensions"), Validator.validateChart(bad, lib).codes(Severity.ERROR))
    }

    @Test fun cableOnWrongSideRowIsAWarning() {
        val c = blankChart("c", 4, 2).put(1, 0, "c4f")
        assertTrue("chart.wrong-side" in Validator.validateChart(c, lib).codes(Severity.WARNING))
    }

    @Test fun repeatBoxMustNotCutACable() {
        val c = blankChart("c", 6, 1).put(0, 1, "c4f").addRepeat("r", CellRect.of(0, 0, 0, 2))
        assertTrue("chart.repeat-cuts-stitch" in Validator.validateChart(c, lib).codes(Severity.ERROR))
    }

    @Test fun repeatThatDoesNotStackIsAWarning() {
        // The rows agree overall (4 stitches each), but inside the box row 1 makes 2 stitches
        // from one cell (kfb) while row 2 works only 1 there — the box cannot be repeated.
        var c = blankChart("c", 4, 2).put(0, 0, "kfb").put(0, 1, "none").put(0, 2, "k2tog")
        c = c.addRepeat("r", CellRect.of(0, 0, 1, 0))
        assertTrue("chart.repeat-unbalanced" in Validator.validateChart(c, lib).codes(Severity.WARNING), Validator.validateChart(c, lib).toString())
    }

    @Test fun sectionErrorsCarryTheirLocation() {
        val s = section("K2, *p2, k2; rep from * to end", "Purl 5", castOn = SizedInt(22))
        val p = Pattern(id = "p", name = "T", gauge = Gauge(20.0, 28.0), sections = listOf(s))
        val issues = Validator.validate(p, lib)
        val e = issues.single { it.severity == Severity.ERROR }
        assertEquals(s.id, e.location.sectionId)
        assertEquals(s.rows[1].id, e.location.rowId)
        assertTrue(e.where.endsWith("Row 2"), e.where)
    }

    @Test fun sizeListLengthMustMatchPatternSizes() {
        val s = section("K10 (12, 14)", castOn = SizedInt(listOf(10, 12)))
        val p = Pattern(id = "p", name = "T", sizes = listOf("S", "M"), gauge = Gauge(20.0, 28.0), sections = listOf(s))
        assertTrue("section.size-count" in Validator.validate(p, lib).codes(Severity.ERROR))
    }

    @Test fun eachSizeIsChecked() {
        // Correct for S (20) and L (28) but wrong for M: 23 is not 2 + a multiple of 4... it is 26 that works.
        val s = section("K2, *p2, k2; rep from * to end", castOn = SizedInt(listOf(22, 23, 30)))
        val p = Pattern(id = "p", name = "T", sizes = listOf("S", "M", "L"), gauge = Gauge(20.0, 28.0), sections = listOf(s))
        val errors = Validator.validate(p, lib).filter { it.severity == Severity.ERROR }
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.startsWith("Size M:"), errors[0].message)
    }

    @Test fun oddRowRepeatInFlatKnittingIsAWarning() {
        val s = section("Knit", "Purl", "Knit", "@rep 1-3 x2", castOn = SizedInt(10))
        val p = Pattern(id = "p", name = "T", gauge = Gauge(20.0, 28.0), sections = listOf(s))
        assertTrue("section.odd-repeat" in Validator.validate(p, lib).codes(Severity.WARNING))
    }

    @Test fun linkedSectionThatDriftsFromItsChartIsFlagged() {
        val chart = blankChart("c", 4, 2).put(0, 0, "p")
        val (generated, _) = chartToSection(chart, lib, ::id)
        val clean = pattern(chart).copy(sections = listOf(generated))
        assertTrue(Validator.validate(clean, lib).none { it.code.startsWith("section.chart") })

        val edited = generated.copy(rows = generated.rows.mapIndexed { i, r -> if (i == 0) r.copy(text = "K4") else r })
        val issues = Validator.validate(pattern(chart).copy(sections = listOf(edited)), lib)
        val w = issues.single { it.code == "section.chart-differs" }
        assertEquals(Severity.WARNING, w.severity)
        assertEquals(edited.rows[0].id, w.location.rowId)
        // Different spelling of the same instruction is not a difference.
        val respelled = generated.copy(rows = generated.rows.mapIndexed { i, r -> if (i == 0) r.copy(text = "knit 3, purl 1") else r })
        assertTrue(Validator.validate(pattern(chart).copy(sections = listOf(respelled)), lib).none { it.code == "section.chart-differs" })
    }

    @Test fun metaChecks() {
        val p = Pattern(id = "p", sizes = listOf("S", "M"), charts = listOf(blankChart("c", 2, 2)),
            measurements = listOf(Measurement("m", "Chest", listOf(90.0, 100.0, 110.0))))
        val issues = Validator.validate(p, lib)
        assertTrue("meta.gauge" in issues.codes(Severity.INFO))
        assertTrue("meta.name" in issues.codes(Severity.INFO))
        assertTrue("meta.measurement-sizes" in issues.codes(Severity.WARNING))
    }

    @Test fun validatorNeverChangesThePattern() {
        val c = blankChart("c", 6, 2).put(0, 0, "yo")
        val p = pattern(c)
        val before = p.copy()
        Validator.validate(p, lib)
        assertEquals(before, p)
    }
}
