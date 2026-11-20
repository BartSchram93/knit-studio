package studio.knit

import studio.knit.model.SizedInt
import studio.knit.written.Count
import studio.knit.written.Node
import studio.knit.written.RowParser
import studio.knit.written.RowPrinter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParserTest {
    private val parser = RowParser(lib)
    private fun parse(s: String) = parser.parse(s)
    private fun stitch(n: Node) = (n as Node.Stitch).let { it.def.id to it.count }

    @Test fun simpleStitches() {
        val r = parse("K2, p2, yo, k2tog")
        assertTrue(r.errors.isEmpty())
        assertEquals(listOf("k" to Count.Fixed(2), "p" to Count.Fixed(2), "yo" to Count.Fixed(1), "k2tog" to Count.Fixed(1)), r.nodes.map(::stitch))
    }

    @Test fun largeCountsAreNotSplit() {
        assertEquals("k" to Count.Fixed(12), stitch(parse("k12").nodes.single()))
        assertEquals("p" to Count.Fixed(110), stitch(parse("P110").nodes.single()))
    }

    @Test fun starRepeatToEnd() {
        val r = parse("K2, *p2, k2; rep from * to end")
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        assertEquals(2, r.nodes.size)
        val g = assertIs<Node.Group>(r.nodes[1])
        assertTrue(g.star)
        assertEquals(Count.ToEnd(), g.times)
        assertEquals(2, g.body.size)
    }

    @Test fun starRepeatToLast() {
        val g = parse("*k2tog, yo; repeat from * to last 3 sts, k3").nodes
        assertEquals(Count.ToEnd(SizedInt(3)), (g[0] as Node.Group).times)
        assertEquals("k" to Count.Fixed(3), stitch(g[1]))
    }

    @Test fun repFromWithoutStarWrapsEverythingBefore() {
        // The asterisk is easily lost when a pattern is copied from formatted text.
        val r = parse("K2, P2, K4, repeat from * to end")
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        val g = assertIs<Node.Group>(r.nodes.single())
        assertEquals(3, g.body.size)
    }

    @Test fun repFromMoreTimes() {
        val g = parse("*k1, p1; rep from * 3 more times").nodes.single() as Node.Group
        assertEquals(Count.Fixed(4), g.times)
        val h = parse("*k1, p1; rep from * once more, k2").nodes[0] as Node.Group
        assertEquals(Count.Fixed(2), h.times)
    }

    @Test fun bracketGroups() {
        val r = parse("(k2tog, yo) 3 times, [k1, p1] twice, (k1, p1) x 4")
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        assertEquals(listOf(Count.Fixed(3), Count.Fixed(2), Count.Fixed(4)), r.nodes.map { (it as Node.Group).times })
        assertTrue(r.nodes.none { (it as Node.Group).star })
    }

    @Test fun openEnded() {
        assertEquals("k" to Count.ToEnd(), stitch(parse("Knit to end").nodes.single()))
        assertEquals("k" to Count.ToEnd(), stitch(parse("Knit.").nodes.single()))
        assertEquals("p" to Count.ToEnd(), stitch(parse("Purl").nodes.single()))
        assertEquals("p" to Count.ToEnd(SizedInt(2)), stitch(parse("p to last 2 sts").nodes.single()))
        assertEquals("k" to Count.ToEnd(), stitch(parse("k across").nodes.single()))
        val r = parse("K1, k2tog, knit to last 3 sts, ssk, k1")
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        assertEquals("k" to Count.ToEnd(SizedInt(3)), stitch(r.nodes[2]))
    }

    @Test fun sizedCounts() {
        val r = parse("K10 (12, 14), p2")
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        assertEquals("k" to Count.Fixed(SizedInt(listOf(10, 12, 14))), stitch(r.nodes[0]))
        assertEquals("p" to Count.Fixed(2), stitch(r.nodes[1]))
    }

    @Test fun statedCount() {
        assertEquals(SizedInt(24), parse("K2, p2 (24 sts)").stated)
        assertEquals(SizedInt(24), parse("K2, p2 — 24 sts.").stated)
        assertEquals(SizedInt(listOf(20, 24)), parse("Knit. 20 (24) sts").stated)
        // "to last 3 sts" is an instruction, not a stated count.
        val r = parse("Knit to last 3 sts")
        assertEquals(null, r.stated)
        assertEquals("k" to Count.ToEnd(SizedInt(3)), stitch(r.nodes.single()))
    }

    @Test fun labelIsIgnored() {
        val r = parse("Row 1 (RS): K2, p2")
        assertEquals(2, r.nodes.size)
        assertEquals(1, parse("Rnd 12: knit").nodes.size)
    }

    @Test fun aliasesAndTerminology() {
        assertEquals("bo", stitch(parse("cast off 3 sts").nodes.single()).first)
        assertEquals("bo" to Count.Fixed(3), stitch(parse("BO 3 sts").nodes.single()))
        assertEquals("yo", stitch(parse("yfwd").nodes.single()).first)
        assertEquals("skpo", stitch(parse("skpo").nodes.single()).first)
        assertEquals("skpo", stitch(parse("sl1 k1 psso").nodes.single()).first)
        assertEquals("ssk", stitch(parse("ssk").nodes.single()).first)
        assertEquals("cdd", stitch(parse("s2kp").nodes.single()).first)
        assertEquals("ktbl" to Count.Fixed(1), stitch(parse("k1tbl").nodes.single()))
        assertEquals("ktbl" to Count.Fixed(3), stitch(parse("k3tbl").nodes.single()))
        assertEquals("sl" to Count.Fixed(2), stitch(parse("sl2").nodes.single()))
        assertEquals("slwyif" to Count.Fixed(1), stitch(parse("sl1 wyif").nodes.single()))
        assertEquals("c4f", stitch(parse("C4F").nodes.single()).first)
        assertEquals("k2tog" to Count.Fixed(2), stitch(parse("k2tog twice").nodes.single()))
    }

    @Test fun markersParse() {
        val r = parse("K10, pm, k10, sm")
        assertTrue(r.errors.isEmpty())
        assertEquals(listOf("k", "pm", "k", "sm"), r.nodes.map { stitch(it).first })
    }

    @Test fun colours() {
        val r = parse("With A k3, with B k1, with MC k4")
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        assertEquals(listOf("A", "B", "MC"), r.nodes.filterIsInstance<Node.Color>().map { it.name })
        assertEquals(3, r.nodes.filterIsInstance<Node.Stitch>().size)
    }

    @Test fun unknownInstructionIsReportedNotDropped() {
        val r = parse("K2, frobnicate 3, p2")
        assertEquals(1, r.errors.size)
        assertTrue(r.errors[0].message.contains("frobnicate"))
        assertEquals(3, r.nodes.size)
        assertIs<Node.Unknown>(r.nodes[1])
    }

    @Test fun unbalancedBrackets() {
        assertTrue(parse("(k2, p2").errors.isNotEmpty())
        assertTrue(parse("k2, p2)").errors.isNotEmpty())
    }

    @Test fun printerRoundTrips() {
        val printer = RowPrinter()
        val samples = listOf(
            "K2, *p2, k2; rep from * to end",
            "K1, k2tog, knit to last 3 sts, ssk, k1",
            "(k2tog, yo) 3 times, k1",
            "*K1, p1; rep from * 3 more times",
            "K10 (12, 14), p2",
            "Sl1, k2tog twice, yo, k3tbl, BO 3 sts",
            "With A k3, with B k1",
            "*yo, k2tog; rep from * to last 2 sts, k2 (24 sts)",
        )
        for (s in samples) {
            val first = parse(s)
            assertTrue(first.errors.isEmpty(), "$s: ${first.errors}")
            val text = printer.print(first.nodes, first.stated)
            val second = parse(text)
            assertTrue(second.errors.isEmpty(), "$text: ${second.errors}")
            assertEquals(first.nodes, second.nodes, "round trip of “$s” via “$text”")
            assertEquals(first.stated, second.stated)
            assertEquals(text, printer.print(second.nodes, second.stated))
        }
    }

    @Test fun ukTerminologyPrints() {
        assertEquals("Cast off 3 sts", RowPrinter(uk = true).print(parse("BO 3 sts").nodes))
    }

    @Test fun skpoIsKeptAsWrittenAndHasItsOwnInstructions() {
        val r = parse("K1, skpo, knit to last 3 sts, k2tog, k1")
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        assertEquals("K1, skpo, knit to last 3 sts, k2tog, k1", RowPrinter().print(r.nodes))
        val skpo = lib.require("skpo")
        assertEquals(2 to 1, skpo.consumes to skpo.produces)
        assertTrue(skpo.instructions.contains("pass", ignoreCase = true) || skpo.instructions.contains("lift", ignoreCase = true))
        assertTrue(!lib.require("ssk").aliases.contains("skpo"))
    }

    @Test fun designerPhrasingRepeatBeforeTheMultiplier() {
        val r = parse("K3, (K2, S1, K2) repeat to last 3 sts, K3.")
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        assertEquals(Count.ToEnd(SizedInt(3)), (r.nodes[1] as Node.Group).times)
        assertEquals("sl", stitch((r.nodes[1] as Node.Group).body[1]).first)
        assertEquals(Count.ToEnd(), (parse("(K1, P1) repeat to end of row.").nodes.single() as Node.Group).times)
        assertEquals(Count.Fixed(3), (parse("(k2tog, yo) rep 3 times").nodes.single() as Node.Group).times)
        // A misspelt instruction is still reported, not guessed.
        assertTrue(parse("(K1, P1) repeat the last 4 sts").errors.isNotEmpty())
    }
}
