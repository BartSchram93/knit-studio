package studio.knit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import studio.knit.companion.knitGlance
import studio.knit.companion.stepKnitting
import studio.knit.companion.movePart
import studio.knit.model.Pattern
import studio.knit.model.SizedInt

class GlanceTest {
    private val pattern = Pattern(
        id = "p", name = "Cowl",
        sections = listOf(section("(K2, P2) to end.", "(P2, K2) to end.", "@rep 1-2 x2", castOn = SizedInt(20)).copy(title = "Rib")),
    )

    @Test fun glanceFollowsTheRows() {
        var p = pattern
        val g = knitGlance(p, lib)!!
        assertEquals("Row 1 of 6 (RS)", g.row)
        assertEquals("Rib", g.part)
        assertEquals("(K2, P2) to end.", g.instruction)
        assertEquals("20 sts", g.detail)
        assertFalse(g.canGoBack)

        p = p.stepKnitting(lib, 1, 1_000)
        val g2 = knitGlance(p, lib)!!
        assertEquals("Row 2 of 6 (WS)", g2.row)
        assertTrue(g2.canGoBack)
        assertEquals(1, p.progress!!.days.values.single().rows)

        p = p.stepKnitting(lib, 1, 2_000)
        assertTrue(knitGlance(p, lib)!!.detail.contains("repeat 1 of 2"))
        repeat(10) { p = p.stepKnitting(lib, 1, 3_000) }
        assertTrue(knitGlance(p, lib)!!.finished)
        p = p.stepKnitting(lib, -1, 4_000)
        assertEquals("Row 6 of 6 (WS)", knitGlance(p, lib)!!.row)
    }

    @Test fun nothingToKnit() {
        assertEquals(null, knitGlance(Pattern(id = "x"), lib))
    }
}

class ChartGlanceTest {
    @Test fun chartRowsAreWrittenOut() {
        val chart = studio.knit.model.Chart(
            id = "c", name = "Chart 1", cols = 4, rows = 2,
            cells = List(8) { i -> studio.knit.model.Cell(if (i < 4) "k" else "p") },
        )
        var p = Pattern(id = "p", name = "Bladeren", charts = listOf(chart))
        val g = knitGlance(p, lib)!!
        assertEquals("Row 1 of 2 (RS)", g.row)
        assertEquals("K4.", g.instruction)
        p = p.stepKnitting(lib, 1, 1_000)
        val g2 = knitGlance(p, lib, todayKey = studio.knit.stats.dayKey(1_000))!!
        assertEquals("Row 2 of 2 (WS)", g2.row)
        assertTrue(g2.instruction.isNotBlank() && !g2.instruction.startsWith("Work chart"), g2.instruction)
        assertEquals("Today 1 row", g2.today)
    }
}

class CompressRunsTest {
    @Test fun repeatsBecomeGroups() {
        val chart = studio.knit.model.Chart(
            id = "c", cols = 14, rows = 1,
            cells = List(14) { c -> studio.knit.model.Cell(listOf("k", "k", "k2tog", "yo")[c % 4].takeIf { c < 12 } ?: "p") },
        )
        val text = studio.knit.convert.chartToText(chart, lib).first.single()
        assertEquals("P2, (yo, k2tog, k2) 3 times", text)
    }
}

class RenameStitchTest {
    @Test fun renamedBuiltInKeepsWorkingUnderBothNames() {
        val original = studio.knit.stitch.StitchLibrary()["skpo"]!!
        val lib2 = studio.knit.stitch.StitchLibrary(listOf(original.copy(name = "Slip knit pass", abbr = "skp2", builtIn = false)))
        val def = lib2["skpo"]!!
        assertEquals("skp2", def.abbr)
        assertTrue(def.builtIn)
        assertEquals(original.consumes, def.consumes)
        assertTrue(lib2.isRenamed("skpo"))
        val parser = studio.knit.written.RowParser(lib2)
        // Old and new spellings both mean the same stitch.
        for (row in listOf("k1, skpo, k1", "k1, skp2, k1")) {
            val r = parser.parse(row)
            assertTrue(r.errors.isEmpty(), row)
            assertEquals(3, studio.knit.written.RowEvaluator().evaluate(r, 4).produced)
        }
        // Written rows use the knitter's name.
        assertEquals("K1, skp2, k1", studio.knit.written.RowPrinter().print(parser.parse("k1, skpo, k1").nodes))
        // A rename cannot change how the stitch is counted.
        val sneaky = studio.knit.stitch.StitchLibrary(listOf(original.copy(abbr = "x", consumes = 9, builtIn = false)))
        assertEquals(2, sneaky["skpo"]!!.consumes)
    }
}

class PartOrderTest {
    @Test fun partsCanBeReordered() {
        val chart = studio.knit.model.Chart(id = "c1", name = "Chart 1", cols = 2, rows = 1, cells = List(2) { studio.knit.model.Cell("k") })
        val a = section("Knit.", castOn = SizedInt(2)).copy(id = "a", title = "Section 1")
        val b = section("Knit.", castOn = SizedInt(2)).copy(id = "b", title = "Section 2")
        var p = Pattern(id = "p", sections = listOf(a, b), charts = listOf(chart))
        fun order() = studio.knit.companion.tracksOf(p, 0, lib).map { it.id }
        assertEquals(listOf("a", "b", "c1"), order())
        // Chart 1 between Section 1 and Section 2.
        p = p.movePartFor(2, 1)
        assertEquals(listOf("a", "c1", "b"), order())
        // A part added later comes after the arranged ones.
        p = p.copy(sections = p.sections + section("Purl.", castOn = SizedInt(2)).copy(id = "d", title = "Section 3"))
        assertEquals(listOf("a", "c1", "b", "d"), order())
    }

    private fun Pattern.movePartFor(from: Int, to: Int): Pattern =
        movePart(studio.knit.companion.tracksOf(this, 0, lib), from, to)
}
