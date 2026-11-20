package studio.knit

import studio.knit.model.Construction
import studio.knit.model.Side
import studio.knit.model.SizedInt
import studio.knit.validate.Severity
import studio.knit.written.RowEvaluator
import studio.knit.written.RowParser
import studio.knit.written.evaluateSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvaluatorTest {
    private val parser = RowParser(lib)
    private fun eval(text: String, stitchesIn: Int?, size: Int = 0) = RowEvaluator(size).evaluate(parser.parse(text), stitchesIn)

    @Test fun plainRowKeepsCount() {
        val e = eval("K2, p2, k2", 6)
        assertEquals(6, e.consumed); assertEquals(6, e.produced); assertFalse(e.hasErrors)
    }

    @Test fun increasesAndDecreases() {
        // yo and m1 add one; k2tog/ssk remove one; kfb turns one into two; cdd removes two.
        assertEquals(1, eval("k1, yo, k1", 2).delta)
        assertEquals(-1, eval("k2tog, k1", 3).delta)
        assertEquals(-1, eval("ssk, k1", 3).delta)
        assertEquals(-1, eval("skpo, k1", 3).delta)
        assertEquals(1, eval("kfb, k1", 2).delta)
        assertEquals(-2, eval("k1, cdd, k1", 5).delta)
        assertEquals(1, eval("k1, m1l, k1", 2).delta)
        assertEquals(-2, eval("k3tog", 3).delta)
        assertEquals(0, eval("yo, k2tog", 2).delta)
        val bo = eval("BO 3 sts, knit to end", 10)
        assertEquals(10, bo.consumed); assertEquals(7, bo.produced)
    }

    @Test fun repeatToEndFitsExactly() {
        val e = eval("K2, *p2, k2; rep from * to end", 22)
        assertFalse(e.hasErrors, e.issues.toString())
        assertEquals(22, e.produced)
        assertEquals(5, e.repeatsWorked)
    }

    @Test fun repeatThatDoesNotFitIsAnError() {
        val e = eval("K2, *p2, k2; rep from * to end", 24)
        assertTrue(e.hasErrors)
        assertTrue(e.issues.any { it.message.contains("does not fit") }, e.issues.toString())
    }

    @Test fun repeatToLastStitches() {
        val e = eval("*yo, k2tog; rep from * to last 2 sts, k2", 12)
        assertFalse(e.hasErrors, e.issues.toString())
        assertEquals(12, e.produced)
        assertEquals(5, e.repeatsWorked)
    }

    @Test fun knitToLast() {
        val e = eval("K1, k2tog, knit to last 3 sts, ssk, k1", 20)
        assertFalse(e.hasErrors, e.issues.toString())
        assertEquals(20, e.consumed); assertEquals(18, e.produced)
    }

    @Test fun tooFewAndTooManyStitches() {
        val few = eval("K2, p2", 6)
        assertTrue(few.issues.any { it.message.contains("2 sts left over") }, few.issues.toString())
        val many = eval("K4, p4", 6)
        assertTrue(many.issues.any { it.message.contains("Needs 8") }, many.issues.toString())
    }

    @Test fun statedCountIsChecked() {
        assertFalse(eval("K1, yo, k1 (3 sts)", 2).hasErrors)
        val wrong = eval("K1, yo, k1 (4 sts)", 2)
        assertTrue(wrong.issues.any { it.message.contains("says 4") && it.message.contains("works out to 3") }, wrong.issues.toString())
    }

    @Test fun unknownCountMakesOpenEndedUnresolved() {
        val e = eval("Knit to end", null)
        assertNull(e.produced)
        assertTrue(e.issues.all { it.severity == Severity.INFO })
        // A repeat still reveals the stitch multiple.
        val rib = eval("K2, *p2, k2; rep from * to end", null)
        assertEquals(4 to 2, rib.multiple to rib.extra)
        assertTrue(rib.issues.single().message.contains("multiple of 4 sts plus 2"))
        // A closed row defines its own count.
        val closed = eval("K2, p2", null)
        assertEquals(4, closed.consumed)
        assertFalse(closed.hasErrors)
    }

    @Test fun sizesAreEvaluatedIndependently() {
        val text = "K10 (12, 14), *p1, k1; rep from * to end"
        assertFalse(eval(text, 20, size = 0).hasErrors)
        assertFalse(eval(text, 22, size = 1).hasErrors)
        assertEquals(24, eval(text, 24, size = 2).produced)
        assertTrue(eval(text, 21, size = 0).hasErrors)
    }

    @Test fun nestedGroups() {
        val e = eval("*(k2tog, yo) twice, k1; rep from * to end", 15)
        assertFalse(e.hasErrors, e.issues.toString())
        assertEquals(15, e.produced)
    }

    @Test fun markersDoNotCount() {
        val e = eval("K5, pm, k5", 10)
        assertFalse(e.hasErrors); assertEquals(10, e.produced)
    }

    @Test fun cablesCountTheirWidth() {
        val e = eval("P2, C4F, p2", 8)
        assertFalse(e.hasErrors, e.issues.toString()); assertEquals(8, e.produced)
    }

    // --- Sections ------------------------------------------------------------------------------

    @Test fun ribSectionIsConsistent() {
        val s = section("K2, *p2, k2; rep from * to end", "P2, *k2, p2; rep from * to end", "@rep 1-2 x9", castOn = SizedInt(22))
        val e = evaluateSection(s, 0, lib)
        assertEquals(20, e.rows.size)
        assertTrue(e.issues.isEmpty(), e.issues.toString())
        assertEquals(22, e.endCount)
        assertEquals(listOf(Side.RS, Side.WS, Side.RS), e.rows.take(3).map { it.side })
        assertEquals(9, e.rows.last().repeatIteration)
    }

    @Test fun roundsAreAlwaysRightSide() {
        val s = section("*K2, p2; rep from * to end", "@rep 1-1 x3", castOn = SizedInt(80), construction = Construction.ROUND)
        val e = evaluateSection(s, 0, lib)
        assertEquals(4, e.rows.size)
        assertTrue(e.rows.all { it.side == Side.RS })
    }

    @Test fun wrongSideStartAlternatesCorrectly() {
        val e = evaluateSection(section("Purl", "Knit", castOn = SizedInt(10), firstSide = Side.WS), 0, lib)
        assertEquals(listOf(Side.WS, Side.RS), e.rows.map { it.side })
    }

    @Test fun shapingCarriesTheCountFromRowToRow() {
        // Raglan-style decreases: 2 stitches fewer every right-side row.
        val s = section("K1, k2tog, knit to last 3 sts, ssk, k1", "Purl", "@rep 1-2 x4", castOn = SizedInt(40))
        val e = evaluateSection(s, 0, lib)
        assertTrue(e.issues.isEmpty(), e.issues.toString())
        assertEquals(30, e.endCount)
        assertEquals(listOf(40, 38, 38, 36), e.rows.take(4).map { it.stitchesBefore })
    }

    @Test fun mismatchIsReportedOnTheOffendingRow() {
        val s = section("K2, *p2, k2; rep from * to end", "P2, *k2, p2; rep from * to last 3 sts, p3", castOn = SizedInt(22))
        val e = evaluateSection(s, 0, lib)
        assertEquals(1, e.issues.count { it.severity == Severity.ERROR })
        assertEquals(s.rows[1].id, e.issues.single { it.severity == Severity.ERROR }.rowId)
    }

    @Test fun invalidRowRepeatRange() {
        val e = evaluateSection(section("Knit", "@rep 1-3 x2", castOn = SizedInt(10)), 0, lib)
        assertTrue(e.issues.any { it.severity == Severity.ERROR && it.message.contains("rows 1–3") }, e.issues.toString())
    }

    @Test fun sizedRowRepeats() {
        val s = section("Knit", "Purl", "@rep 1-2 x2, 4", castOn = SizedInt(listOf(10, 12)))
        assertEquals(6, evaluateSection(s, 0, lib).rows.size)
        assertEquals(10, evaluateSection(s, 1, lib).rows.size)
        assertEquals(12, evaluateSection(s, 1, lib).endCount)
    }

    @Test fun firstRowDefinesCountWhenCastOnUnknown() {
        val e = evaluateSection(section("K2, p2, k2", "P2, k2, p2", "K2, p2, k3"), 0, lib)
        assertEquals(6, e.startCount)
        assertEquals(1, e.issues.count { it.severity == Severity.ERROR })
        assertEquals(e.rows[2].source.id, e.issues.first { it.severity == Severity.ERROR }.rowId)
    }

    @Test fun notesTakeNoRowNumber() {
        val e = evaluateSection(section("Knit", "# change needles", "Purl", castOn = SizedInt(8)), 0, lib)
        assertEquals(listOf(1, 2), e.rows.map { it.number })
    }
}
