package studio.knit.template

import studio.knit.calc.GaugeCalc
import studio.knit.chart.blankChart
import studio.knit.chart.place
import studio.knit.convert.chartToSection
import studio.knit.model.Category
import studio.knit.model.Cell
import studio.knit.model.Chart
import studio.knit.model.ChartColor
import studio.knit.model.ChartRepeat
import studio.knit.model.Construction
import studio.knit.model.Difficulty
import studio.knit.model.Gauge
import studio.knit.model.Measurement
import studio.knit.model.Pattern
import studio.knit.model.Row
import studio.knit.model.RowKind
import studio.knit.model.Section
import studio.knit.model.SizedInt
import studio.knit.stitch.StitchLibrary
import kotlin.math.roundToInt

/** A value the user supplies before a template is built, in centimetres. */
data class TemplateField(val key: String, val label: String, val defaultCm: Double)

data class Template(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: Category,
    val difficulty: Difficulty,
    val needsGauge: Boolean,
    val fields: List<TemplateField> = emptyList(),
    val defaultGauge: Gauge = Gauge(20.0, 28.0),
)

/**
 * Starting points that are worked out from the knitter's own gauge and measurements.
 * Every template yields an ordinary, fully editable pattern that passes validation.
 */
object Templates {
    val all: List<Template> = listOf(
        Template("scarf", "Ribbed scarf", "2×2 rib, sized from your gauge", Category.SCARF, Difficulty.BEGINNER, true,
            listOf(TemplateField("width", "Width", 20.0), TemplateField("length", "Length", 180.0)), Gauge(22.0, 28.0)),
        Template("hat", "Classic beanie", "Knit in the round with a shaped crown", Category.HAT, Difficulty.EASY, true,
            listOf(TemplateField("head", "Head circumference", 56.0), TemplateField("height", "Height", 21.0)), Gauge(20.0, 28.0)),
        Template("cloth", "Diagonal garter cloth", "Corner-to-corner with eyelet edges", Category.OTHER, Difficulty.BEGINNER, true,
            listOf(TemplateField("side", "Side length", 22.0)), Gauge(18.0, 36.0)),
        Template("lace", "Feather & fan lace", "A charted 18-stitch lace repeat with written rows", Category.SHAWL, Difficulty.INTERMEDIATE, false),
        Template("cable", "Cable panel", "A 2/2 cable on a purl ground, charted", Category.OTHER, Difficulty.INTERMEDIATE, false),
        Template("colorwork", "Colourwork band", "A two-colour motif worked in the round", Category.OTHER, Difficulty.INTERMEDIATE, false),
    )

    fun byId(id: String): Template? = all.firstOrNull { it.id == id }

    fun build(
        template: Template,
        gauge: Gauge,
        values: Map<String, Double>,
        library: StitchLibrary,
        newId: () -> String,
        now: Long,
    ): Pattern {
        fun v(key: String) = values[key] ?: template.fields.first { it.key == key }.defaultCm
        val base = Pattern(
            id = newId(), name = template.title, category = template.category, difficulty = template.difficulty,
            gauge = if (template.needsGauge) gauge else null, createdAt = now, updatedAt = now,
        )
        return when (template.id) {
            "scarf" -> scarf(base, gauge, v("width"), v("length"), newId)
            "hat" -> hat(base, gauge, v("head"), v("height"), newId)
            "cloth" -> cloth(base, gauge, v("side"), newId)
            "lace" -> lace(base, library, newId)
            "cable" -> cable(base, library, newId)
            "colorwork" -> colorwork(base, library, newId)
            else -> base
        }
    }

    private fun row(newId: () -> String, text: String) = Row(newId(), text = text)
    private fun note(newId: () -> String, text: String) = Row(newId(), kind = RowKind.NOTE, text = text)
    private fun repeat(newId: () -> String, from: Int, to: Int, times: Int) =
        Row(newId(), kind = RowKind.REPEAT_ROWS, repeatFrom = from, repeatTo = to, repeatTimes = SizedInt(times))

    // --- Ribbed scarf --------------------------------------------------------------------------

    private fun scarf(base: Pattern, gauge: Gauge, widthCm: Double, lengthCm: Double, newId: () -> String): Pattern {
        // 2×2 rib that starts and ends with k2: a multiple of 4, plus 2.
        val castOn = GaugeCalc.castOn(widthCm, gauge, multiple = 4, extra = 2)
        val totalRows = (GaugeCalc.rowsFor(lengthCm, gauge).roundToInt() / 2 * 2).coerceAtLeast(4)
        val section = Section(
            id = newId(), title = "Scarf", construction = Construction.FLAT, castOn = SizedInt(castOn),
            description = "Cast on $castOn stitches.",
            rows = listOf(
                row(newId, "K2, *p2, k2; rep from * to end"),
                row(newId, "P2, *k2, p2; rep from * to end"),
                repeat(newId, 1, 2, totalRows / 2 - 1),
                note(newId, "Bind off loosely in pattern. Weave in the ends and block lightly."),
            ),
        )
        return base.copy(
            description = "A reversible 2×2 ribbed scarf, about ${widthCm.roundToInt()} × ${lengthCm.roundToInt()} cm.",
            measurements = listOf(
                Measurement(newId(), "Width", listOf(widthCm)),
                Measurement(newId(), "Length", listOf(lengthCm)),
            ),
            sections = listOf(section),
        )
    }

    // --- Beanie --------------------------------------------------------------------------------

    private fun hat(base: Pattern, gauge: Gauge, headCm: Double, heightCm: Double, newId: () -> String): Pattern {
        val wedges = 8
        // About 10 % negative ease so the hat stays put; a multiple of 8 for rib and crown.
        val castOn = GaugeCalc.roundToMultiple(GaugeCalc.stitchesFor(headCm * 0.9, gauge), wedges).coerceAtLeast(wedges * 3)
        val wedge = castOn / wedges
        val brimRounds = GaugeCalc.rowsFor(4.0, gauge).roundToInt().coerceAtLeast(4)

        // Crown: decrease 8 stitches every other round, then every round for the last few.
        val crown = ArrayList<Row>()
        var w = wedge
        while (w > 1) {
            val knit = w - 2
            crown += row(newId, if (knit > 0) "*K$knit, k2tog; rep from * to end" else "*K2tog; rep from * to end")
            w--
            if (w > 4) crown += row(newId, "Knit")
        }
        val crownRounds = crown.size
        val totalRounds = GaugeCalc.rowsFor(heightCm, gauge).roundToInt()
        val bodyRounds = (totalRounds - brimRounds - crownRounds).coerceAtLeast(2)

        val brim = Section(
            id = newId(), title = "Brim", construction = Construction.ROUND, castOn = SizedInt(castOn),
            description = "Cast on $castOn stitches and join to work in the round, taking care not to twist.",
            rows = listOf(row(newId, "*K2, p2; rep from * to end"), repeat(newId, 1, 1, brimRounds - 1)),
        )
        val body = Section(
            id = newId(), title = "Body", construction = Construction.ROUND, castOn = SizedInt(castOn),
            rows = listOf(row(newId, "Knit"), repeat(newId, 1, 1, bodyRounds - 1)),
        )
        val top = Section(
            id = newId(), title = "Crown", construction = Construction.ROUND, castOn = SizedInt(castOn),
            description = "Change to double-pointed needles or magic loop when the stitches no longer reach.",
            rows = crown + note(newId, "Cut the yarn, thread it through the remaining $wedges stitches and pull tight."),
        )
        return base.copy(
            description = "A classic ribbed-brim beanie for a ${headCm.roundToInt()} cm head.",
            measurements = listOf(
                Measurement(newId(), "Head circumference", listOf(headCm)),
                Measurement(newId(), "Height", listOf(heightCm)),
            ),
            sections = listOf(brim, body, top),
        )
    }

    // --- Diagonal garter cloth -----------------------------------------------------------------

    private fun cloth(base: Pattern, gauge: Gauge, sideCm: Double, newId: () -> String): Pattern {
        // The widest row runs along the diagonal of the square.
        val widest = (GaugeCalc.stitchesFor(sideCm * 1.4142, gauge).roundToInt() / 2 * 2).coerceAtLeast(8)
        val inc = Section(
            id = newId(), title = "Increase half", construction = Construction.FLAT, castOn = SizedInt(4),
            description = "Cast on 4 stitches.",
            rows = listOf(
                row(newId, "K2, yo, knit to end"),
                row(newId, "K2, yo, knit to end"),
                repeat(newId, 1, 2, (widest - 4) / 2 - 1),
                note(newId, "You now have $widest stitches."),
            ),
        )
        val dec = Section(
            id = newId(), title = "Decrease half", construction = Construction.FLAT, castOn = SizedInt(widest),
            rows = listOf(
                row(newId, "K1, k2tog, yo, k2tog, knit to end"),
                row(newId, "K1, k2tog, yo, k2tog, knit to end"),
                repeat(newId, 1, 2, (widest - 4) / 2 - 1),
                note(newId, "Bind off the remaining 4 stitches."),
            ),
        )
        return base.copy(
            description = "A corner-to-corner garter square, about ${sideCm.roundToInt()} cm on each side.",
            measurements = listOf(Measurement(newId(), "Side", listOf(sideCm))),
            sections = listOf(inc, dec),
        )
    }

    // --- Charted templates ---------------------------------------------------------------------

    /** Builds a chart from rows of space-separated stitch ids, written top row first as drawn. */
    private fun chartOf(id: String, name: String, drawn: List<String>, library: StitchLibrary, construction: Construction = Construction.FLAT): Chart {
        val rows = drawn.reversed().map { it.trim().split(Regex("\\s+")) }
        val cols = rows.maxOf { r -> r.sumOf { library.require(it).width.coerceAtLeast(1) } }
        var chart = blankChart(id, cols, rows.size, name, construction)
        rows.forEachIndexed { r, tokens ->
            var c = 0
            for (t in tokens) {
                val def = library.require(t)
                chart = chart.place(r, c, def, null, library)
                c += def.width.coerceAtLeast(1)
            }
        }
        return chart
    }

    private fun withSection(base: Pattern, chart: Chart, library: StitchLibrary, newId: () -> String): Pattern {
        val (section, _) = chartToSection(chart, library, newId)
        return base.copy(charts = listOf(chart), sections = listOf(section))
    }

    private fun lace(base: Pattern, library: StitchLibrary, newId: () -> String): Pattern {
        val chart = chartOf(
            newId(), "Feather & fan",
            listOf(
                // Row 4 (WS) is knitted, which shows as purl bumps on the right side.
                "p p p p p p p p p p p p p p p p p p",
                "k2tog k2tog k2tog k yo k yo k yo k yo k yo k yo k2tog k2tog k2tog",
                "k k k k k k k k k k k k k k k k k k",
                "k k k k k k k k k k k k k k k k k k",
            ),
            library,
        ).let { it.copy(repeats = listOf(ChartRepeat(newId(), 0, it.cols - 1, 0, it.rows - 1))) }
        return withSection(
            base.copy(description = "The classic scalloped lace. Cast on a multiple of 18 stitches and repeat the four rows."),
            chart, library, newId,
        )
    }

    private fun cable(base: Pattern, library: StitchLibrary, newId: () -> String): Pattern {
        val chart = chartOf(
            newId(), "Cable panel",
            listOf(
                "p p k k k k p p",
                "p p k k k k p p",
                "p p k k k k p p",
                "p p c4b p p",
                "p p k k k k p p",
                "p p k k k k p p",
            ),
            library,
        )
        return withSection(
            base.copy(description = "An 8-stitch panel: a four-stitch rope cable crossing every sixth row, framed by purl stitches."),
            chart, library, newId,
        )
    }

    private fun colorwork(base: Pattern, library: StitchLibrary, newId: () -> String): Pattern {
        // 1 = contrast colour, . = main colour. Top row first.
        val art = listOf(
            "........",
            "...1....",
            "..111...",
            ".11.11..",
            "..111...",
            "...1....",
            "........",
        )
        val rows = art.reversed()
        val cells = ArrayList<Cell>()
        for (line in rows) for (ch in line) cells += Cell("k", if (ch == '1') 0 else -1)
        val chart = Chart(
            id = newId(), name = "Diamond band", cols = 8, rows = rows.size, cells = cells,
            construction = Construction.ROUND,
            palette = listOf(ChartColor("B", 0xFFC2553D)),
            repeats = listOf(ChartRepeat(newId(), 0, 7, 0, rows.size - 1)),
        )
        return withSection(
            base.copy(description = "A small stranded motif. Work over a multiple of 8 stitches in the round; MC is the background."),
            chart, library, newId,
        )
    }
}
