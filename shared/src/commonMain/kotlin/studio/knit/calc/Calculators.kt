package studio.knit.calc

import studio.knit.model.Gauge
import studio.knit.model.Pattern
import studio.knit.stitch.StitchLibrary
import studio.knit.written.evaluateSection
import studio.knit.chart.rowCount
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Knitting arithmetic. Everything is computed in centimetres and metres internally;
 * conversion to inches and yards happens at the edges.
 */

object Units {
    const val CM_PER_INCH = 2.54
    const val M_PER_YARD = 0.9144
    const val G_PER_OZ = 28.349523125

    fun inToCm(inches: Double) = inches * CM_PER_INCH
    fun cmToIn(cm: Double) = cm / CM_PER_INCH
    fun ydToM(yd: Double) = yd * M_PER_YARD
    fun mToYd(m: Double) = m / M_PER_YARD
    fun ozToG(oz: Double) = oz * G_PER_OZ
    fun gToOz(g: Double) = g / G_PER_OZ
}

/** Rounds to [decimals] places without platform formatting APIs. */
fun Double.rounded(decimals: Int = 1): Double {
    val f = 10.0.pow(decimals)
    return kotlin.math.round(this * f) / f
}

/** Compact number text: `22`, `22.5`, never `22.0`. */
fun Double.pretty(decimals: Int = 1): String {
    val r = rounded(decimals)
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

object GaugeCalc {

    /**
     * Gauge from a measured swatch.
     * @param widthCm width over which [stitches] were counted; [heightCm] likewise for [rows].
     */
    fun fromSwatch(stitches: Double, rows: Double, widthCm: Double, heightCm: Double): Gauge? {
        if (stitches <= 0 || rows <= 0 || widthCm <= 0 || heightCm <= 0) return null
        return Gauge(stitchesPer10cm = stitches / widthCm * 10.0, rowsPer10cm = rows / heightCm * 10.0)
    }

    /** Stitches needed for [widthCm], as an exact (unrounded) number. */
    fun stitchesFor(widthCm: Double, gauge: Gauge): Double = widthCm * gauge.stitchesPer10cm / 10.0
    fun rowsFor(heightCm: Double, gauge: Gauge): Double = heightCm * gauge.rowsPer10cm / 10.0
    fun widthOf(stitches: Int, gauge: Gauge): Double = stitches / gauge.stitchesPer10cm * 10.0
    fun heightOf(rows: Int, gauge: Gauge): Double = rows / gauge.rowsPer10cm * 10.0

    /**
     * Nearest stitch count to [exact] of the form `multiple × n + extra` (a "multiple of 4 plus 2").
     * Never returns fewer than one repeat.
     */
    fun roundToMultiple(exact: Double, multiple: Int, extra: Int = 0): Int {
        val m = multiple.coerceAtLeast(1)
        val n = ((exact - extra) / m).roundToInt().coerceAtLeast(1)
        return n * m + extra
    }

    /** Cast-on count for a target width, honouring a stitch multiple. */
    fun castOn(widthCm: Double, gauge: Gauge, multiple: Int = 1, extra: Int = 0): Int =
        roundToMultiple(stitchesFor(widthCm, gauge), multiple, extra)

    data class Comparison(
        /** Percent difference of my stitch gauge vs the pattern's; positive = I knit tighter. */
        val stitchPercent: Double,
        val rowPercent: Double,
        /** What a 100 cm wide piece of the pattern would measure at my gauge. */
        val widthFactor: Double,
        val heightFactor: Double,
        val verdict: String,
        val advice: String,
    )

    /** Compares the knitter's swatch with the gauge the pattern was written for. */
    fun compare(pattern: Gauge, mine: Gauge): Comparison {
        val sp = (mine.stitchesPer10cm / pattern.stitchesPer10cm - 1.0) * 100.0
        val rp = (mine.rowsPer10cm / pattern.rowsPer10cm - 1.0) * 100.0
        val verdict: String
        val advice: String
        when {
            abs(sp) < 2.0 -> { verdict = "On gauge"; advice = "Your stitch gauge matches. Knit the pattern as written." }
            sp > 0 -> { verdict = "Too tight"; advice = "You have more stitches per 10 cm, so the piece will come out smaller. Try a larger needle, or adjust the stitch counts." }
            else -> { verdict = "Too loose"; advice = "You have fewer stitches per 10 cm, so the piece will come out larger. Try a smaller needle, or adjust the stitch counts." }
        }
        return Comparison(sp, rp, pattern.stitchesPer10cm / mine.stitchesPer10cm, pattern.rowsPer10cm / mine.rowsPer10cm, verdict, advice)
    }

    /** Stitch count that gives the same width at [mine] as [stitches] gives at [pattern]. */
    fun convertStitches(stitches: Int, pattern: Gauge, mine: Gauge, multiple: Int = 1, extra: Int = 0): Int =
        roundToMultiple(stitches * mine.stitchesPer10cm / pattern.stitchesPer10cm, multiple, extra)

    fun convertRows(rows: Int, pattern: Gauge, mine: Gauge): Int =
        (rows * mine.rowsPer10cm / pattern.rowsPer10cm).roundToInt().coerceAtLeast(1)
}

object YarnCalc {
    /**
     * Munden's constant for relaxed plain knitting: stitch density × loop length² ≈ 21.6.
     * It links how dense a fabric is to how much yarn one stitch uses.
     */
    private const val MUNDEN_K = 21.6

    /** Approximate yarn used by one stockinette stitch at [gauge], in centimetres. */
    fun cmPerStitch(gauge: Gauge): Double {
        val density = gauge.stitchesPer10cm / 10.0 * (gauge.rowsPer10cm / 10.0) // stitches per cm²
        return sqrt(MUNDEN_K / density)
    }

    data class Estimate(val meters: Double, val grams: Double?, val skeins: Int?, val marginPercent: Int)

    /**
     * Yarn for [stitches] stitches at [gauge], with a safety [marginPercent].
     * Skeins and grams are filled in when the yarn's put-up is known.
     */
    fun forStitches(
        stitches: Long, gauge: Gauge, metersPerSkein: Double? = null, gramsPerSkein: Double? = null, marginPercent: Int = 10,
    ): Estimate {
        val meters = stitches * cmPerStitch(gauge) / 100.0 * (1 + marginPercent / 100.0)
        return finish(meters, metersPerSkein, gramsPerSkein, marginPercent)
    }

    /** Yarn for a rectangle of fabric. */
    fun forArea(
        widthCm: Double, heightCm: Double, gauge: Gauge, metersPerSkein: Double? = null, gramsPerSkein: Double? = null, marginPercent: Int = 10,
    ): Estimate {
        val stitches = GaugeCalc.stitchesFor(widthCm, gauge) * GaugeCalc.rowsFor(heightCm, gauge)
        return forStitches(stitches.toLong(), gauge, metersPerSkein, gramsPerSkein, marginPercent)
    }

    /**
     * The most reliable method: weigh a swatch, then scale by area.
     * @param swatchGrams weight of a swatch measuring [swatchWidthCm] × [swatchHeightCm].
     */
    fun fromSwatchWeight(
        swatchGrams: Double, swatchWidthCm: Double, swatchHeightCm: Double, projectAreaCm2: Double,
        metersPerSkein: Double? = null, gramsPerSkein: Double? = null, marginPercent: Int = 10,
    ): Estimate? {
        val area = swatchWidthCm * swatchHeightCm
        if (swatchGrams <= 0 || area <= 0 || projectAreaCm2 <= 0) return null
        val grams = swatchGrams / area * projectAreaCm2 * (1 + marginPercent / 100.0)
        val meters = if (metersPerSkein != null && gramsPerSkein != null && gramsPerSkein > 0) grams / gramsPerSkein * metersPerSkein else null
        val skeins = if (gramsPerSkein != null && gramsPerSkein > 0) ceil(grams / gramsPerSkein).toInt() else null
        return Estimate(meters ?: 0.0, grams, skeins, marginPercent)
    }

    private fun finish(meters: Double, metersPerSkein: Double?, gramsPerSkein: Double?, margin: Int): Estimate {
        val skeins = if (metersPerSkein != null && metersPerSkein > 0) ceil(meters / metersPerSkein).toInt() else null
        val grams = if (metersPerSkein != null && metersPerSkein > 0 && gramsPerSkein != null) meters / metersPerSkein * gramsPerSkein else null
        return Estimate(meters, grams, skeins, margin)
    }

    /**
     * Total stitches knitted in a pattern for one size: every resolvable written section, plus
     * charts that are not mirrored by a section. Null when nothing can be counted.
     */
    fun totalStitches(pattern: Pattern, sizeIndex: Int, library: StitchLibrary): Long? {
        var total = 0L
        var counted = false
        val chartsInSections = pattern.sections.mapNotNull { it.chartId }.toSet()
        for (section in pattern.sections) {
            val eval = evaluateSection(section, sizeIndex, library)
            for (row in eval.rows) {
                val p = row.eval.produced ?: continue
                total += p
                counted = true
            }
        }
        for (chart in pattern.charts) {
            if (chart.id in chartsInSections || chart.cells.size != chart.cols * chart.rows) continue
            for (r in 0 until chart.rows) total += chart.rowCount(r, library).produced
            counted = true
        }
        return if (counted) total else null
    }
}

/** Needle sizes: metric with the nearest US and old UK/Canadian numbers. */
data class NeedleSize(val mm: Double, val us: String?, val uk: String?)

object Needles {
    val sizes: List<NeedleSize> = listOf(
        NeedleSize(1.5, "000", null), NeedleSize(1.75, "00", null),
        NeedleSize(2.0, "0", "14"), NeedleSize(2.25, "1", "13"), NeedleSize(2.5, "1½", null),
        NeedleSize(2.75, "2", "12"), NeedleSize(3.0, "2½", "11"), NeedleSize(3.25, "3", "10"),
        NeedleSize(3.5, "4", null), NeedleSize(3.75, "5", "9"), NeedleSize(4.0, "6", "8"),
        NeedleSize(4.5, "7", "7"), NeedleSize(5.0, "8", "6"), NeedleSize(5.5, "9", "5"),
        NeedleSize(6.0, "10", "4"), NeedleSize(6.5, "10½", "3"), NeedleSize(7.0, null, "2"),
        NeedleSize(7.5, null, "1"), NeedleSize(8.0, "11", "0"), NeedleSize(9.0, "13", "00"),
        NeedleSize(10.0, "15", "000"), NeedleSize(12.0, "17", null), NeedleSize(15.0, "19", null),
        NeedleSize(19.0, "35", null), NeedleSize(25.0, "50", null),
    )

    fun nearest(mm: Double): NeedleSize = sizes.minBy { abs(it.mm - mm) }

    /** `4 mm (US 6)` or `US 6 (4 mm)` depending on the preferred system. */
    fun label(mm: Double, imperial: Boolean = false): String {
        val exact = sizes.firstOrNull { abs(it.mm - mm) < 0.01 }
        val metric = "${mm.pretty(2)} mm"
        val us = exact?.us?.let { "US $it" }
        return when {
            us == null -> metric
            imperial -> "$us ($metric)"
            else -> "$metric ($us)"
        }
    }
}

/** Formats lengths and yardage in the user's preferred system. */
class UnitFormat(val imperial: Boolean) {
    val lengthUnit: String get() = if (imperial) "in" else "cm"
    val yarnUnit: String get() = if (imperial) "yd" else "m"
    val gaugeSpan: String get() = if (imperial) "4 in" else "10 cm"
    val weightUnit: String get() = if (imperial) "oz" else "g"

    fun length(cm: Double, decimals: Int = 1): String =
        if (imperial) "${Units.cmToIn(cm).pretty(decimals)} in" else "${cm.pretty(decimals)} cm"

    fun lengthValue(cm: Double): Double = if (imperial) Units.cmToIn(cm) else cm
    fun toCm(value: Double): Double = if (imperial) Units.inToCm(value) else value

    fun yarn(m: Double): String = if (imperial) "${Units.mToYd(m).roundToInt()} yd" else "${m.roundToInt()} m"
    fun yarnValue(m: Double): Double = if (imperial) Units.mToYd(m) else m
    fun toMeters(value: Double): Double = if (imperial) Units.ydToM(value) else value

    fun weight(g: Double): String = if (imperial) "${Units.gToOz(g).pretty(1)} oz" else "${g.roundToInt()} g"
    fun weightValue(g: Double): Double = if (imperial) Units.gToOz(g) else g
    fun toGrams(value: Double): Double = if (imperial) Units.ozToG(value) else value

    /** Gauge over 10 cm is numerically the same count over 4 in for practical purposes (10.16 cm). */
    fun gauge(g: Gauge, uk: Boolean = false): String {
        val s = if (imperial) g.stitchesPer10cm * 1.016 else g.stitchesPer10cm
        val r = if (imperial) g.rowsPer10cm * 1.016 else g.rowsPer10cm
        return "${s.pretty(1)} sts × ${r.pretty(1)} rows = $gaugeSpan"
    }
}
