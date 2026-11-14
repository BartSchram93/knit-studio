package studio.knit

import studio.knit.calc.GaugeCalc
import studio.knit.calc.Needles
import studio.knit.calc.UnitFormat
import studio.knit.calc.Units
import studio.knit.calc.YarnCalc
import studio.knit.calc.pretty
import studio.knit.model.Gauge
import studio.knit.model.Pattern
import studio.knit.model.SizedInt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalcTest {
    private fun near(expected: Double, actual: Double, eps: Double = 0.01) =
        assertTrue(abs(expected - actual) <= eps, "expected $expected but was $actual")

    @Test fun gaugeFromSwatch() {
        // 23 stitches and 31 rows measured over 10.5 cm × 10 cm.
        val g = assertNotNull(GaugeCalc.fromSwatch(23.0, 31.0, 10.5, 10.0))
        near(21.90, g.stitchesPer10cm)
        near(31.0, g.rowsPer10cm)
        assertNull(GaugeCalc.fromSwatch(0.0, 31.0, 10.0, 10.0))
        assertNull(GaugeCalc.fromSwatch(20.0, 30.0, 0.0, 10.0))
    }

    @Test fun imperialSwatch() {
        // 22 sts over 4 in = 22 / 10.16 cm → 21.65 per 10 cm.
        val g = GaugeCalc.fromSwatch(22.0, 30.0, Units.inToCm(4.0), Units.inToCm(4.0))!!
        near(21.65, g.stitchesPer10cm)
        assertEquals("22 sts × 30 rows = 4 in", UnitFormat(imperial = true).gauge(g))
    }

    @Test fun castOnRespectsMultiples() {
        val g = Gauge(22.0, 30.0)
        assertEquals(110, GaugeCalc.castOn(50.0, g))
        // 2×2 rib needs a multiple of 4 plus 2: 110 already fits.
        assertEquals(110, GaugeCalc.castOn(50.0, g, multiple = 4, extra = 2))
        assertEquals(108, GaugeCalc.castOn(50.0, g, multiple = 12))
        assertEquals(18, GaugeCalc.roundToMultiple(3.0, 18))
        assertEquals(46, GaugeCalc.roundToMultiple(44.0, 4, 2))
    }

    @Test fun dimensionsFromCounts() {
        val g = Gauge(20.0, 28.0)
        near(50.0, GaugeCalc.widthOf(100, g))
        near(25.0, GaugeCalc.heightOf(70, g))
    }

    @Test fun compareGauge() {
        val pattern = Gauge(20.0, 28.0)
        assertEquals("On gauge", GaugeCalc.compare(pattern, Gauge(20.2, 28.0)).verdict)
        val tight = GaugeCalc.compare(pattern, Gauge(22.0, 30.0))
        assertEquals("Too tight", tight.verdict)
        near(10.0, tight.stitchPercent)
        // A 100 cm piece comes out at 90.9 cm.
        near(0.909, tight.widthFactor, 0.001)
        assertEquals("Too loose", GaugeCalc.compare(pattern, Gauge(18.0, 26.0)).verdict)
    }

    @Test fun convertCountsBetweenGauges() {
        val pattern = Gauge(20.0, 28.0)
        val mine = Gauge(22.0, 30.0)
        assertEquals(110, GaugeCalc.convertStitches(100, pattern, mine))
        assertEquals(112, GaugeCalc.convertStitches(100, pattern, mine, multiple = 4)) // 110 → nearest multiple of 4, ties round up
        assertEquals(75, GaugeCalc.convertRows(70, pattern, mine))
    }

    @Test fun yarnEstimateIsPlausible() {
        // DK sweater territory: about 0.9 m² at 22 × 30.
        val g = Gauge(22.0, 30.0)
        near(1.81, YarnCalc.cmPerStitch(g), 0.01)
        val e = YarnCalc.forArea(90.0, 100.0, g, metersPerSkein = 120.0, gramsPerSkein = 50.0, marginPercent = 10)
        assertTrue(e.meters in 1100.0..1300.0, "meters = ${e.meters}")
        assertEquals(kotlin.math.ceil(e.meters / 120.0).toInt(), e.skeins)
        near(e.meters / 120.0 * 50.0, e.grams!!)
        // Chunkier fabric uses less length for the same area.
        assertTrue(YarnCalc.forArea(90.0, 100.0, Gauge(14.0, 20.0)).meters < e.meters)
    }

    @Test fun yarnFromWeighedSwatch() {
        // A 15 × 15 cm swatch weighs 9 g; the project is 4500 cm².
        val e = assertNotNull(YarnCalc.fromSwatchWeight(9.0, 15.0, 15.0, 4500.0, metersPerSkein = 200.0, gramsPerSkein = 100.0, marginPercent = 0))
        near(180.0, e.grams!!)
        assertEquals(2, e.skeins)
        near(360.0, e.meters)
        assertNull(YarnCalc.fromSwatchWeight(0.0, 15.0, 15.0, 4500.0))
    }

    @Test fun totalStitchesOfAPattern() {
        val s = section("Knit", "Purl", "@rep 1-2 x4", castOn = SizedInt(listOf(20, 30)))
        val p = Pattern(id = "p", sizes = listOf("S", "L"), sections = listOf(s))
        assertEquals(200L, YarnCalc.totalStitches(p, 0, lib))
        assertEquals(300L, YarnCalc.totalStitches(p, 1, lib))
        assertNull(YarnCalc.totalStitches(Pattern(id = "e"), 0, lib))
    }

    @Test fun needleSizes() {
        assertEquals("6", Needles.nearest(4.0).us)
        assertEquals("8", Needles.nearest(4.0).uk)
        assertEquals("4 mm (US 6)", Needles.label(4.0))
        assertEquals("US 8 (5 mm)", Needles.label(5.0, imperial = true))
        assertEquals("4.25 mm", Needles.label(4.25))
    }

    @Test fun unitsAndFormatting() {
        near(10.16, Units.inToCm(4.0))
        near(109.36, Units.mToYd(100.0))
        assertEquals("22", 22.0.pretty())
        assertEquals("22.5", 22.5.pretty())
        assertEquals("21.9", 21.904.pretty())
        assertEquals("7.9 in", UnitFormat(true).length(20.0))
        assertEquals("20 cm", UnitFormat(false).length(20.0))
        assertEquals("109 yd", UnitFormat(true).yarn(100.0))
    }
}
