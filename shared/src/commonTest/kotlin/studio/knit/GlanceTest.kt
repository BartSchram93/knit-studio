package studio.knit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import studio.knit.companion.knitGlance
import studio.knit.companion.stepKnitting
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
