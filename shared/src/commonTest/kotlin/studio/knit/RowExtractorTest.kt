package studio.knit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import studio.knit.model.Construction
import studio.knit.model.Side
import studio.knit.model.SizedInt
import studio.knit.pdfimport.ImportItem
import studio.knit.pdfimport.RowExtractor
import studio.knit.validate.Severity
import studio.knit.written.evaluateSection

/** Texts here are written for the tests, in the styles patterns commonly use. */
class RowExtractorTest {

    private fun rows(s: studio.knit.pdfimport.ImportedSection) = s.items.filterIsInstance<ImportItem.Instruction>()

    @Test fun numberedRowsWithSidesRepeatAndCastOn() {
        val text = """
            Simple Cowl
            Page 1 of 3
            Using 4 mm needles cast on 20 (24, 28) sts.
            Border:
            Row 1 (RS): K2, P2, rep to end.
            Row 2 (WS): K2, P2, rep to end.
            Repeat Rows 1 and 2 a total of 3 times.
            Page 2 of 3
            Body:
            Row 1 (RS): Knit to end.
            Row 2 (WS): K2, purl to last 2 sts,
            k2.
            Rep rows 1-2 10 (12, 14) more times.
            Page 3 of 3
        """.trimIndent()
        val r = RowExtractor.extract(text)
        assertEquals(listOf("Border", "Body"), r.sections.map { it.title })
        assertEquals(3, r.sizeCount)
        val border = r.sections[0]
        assertEquals(SizedInt(listOf(20, 24, 28)), border.castOn)
        assertEquals(Side.RS, border.firstSide)
        assertEquals(ImportItem.Repeat(1, 2, SizedInt(2), "Repeat Rows 1 and 2 a total of 3 times."), border.items[2])
        val body = r.sections[1]
        assertEquals("K2, purl to last 2 sts, k2.", rows(body)[1].text)
        assertEquals(SizedInt(listOf(10, 12, 14)), (body.items[2] as ImportItem.Repeat).more)
        assertTrue(r.warnings.isEmpty(), r.warnings.toString())
    }

    @Test fun roundsNextRoundAndPlainKnitRounds() {
        val text = """
            Hat
            Cast on 96 sts and join to work in the round.
            Round 1: (K2, P2) 24 times.
            Knit 4 rounds.
            Next round: (K10, K2tog) 8 times. 88 sts.
            Next round: Knit.
        """.trimIndent()
        val s = RowExtractor.extract(text).sections.single()
        assertEquals("Hat", s.title)
        assertTrue(s.round)
        assertEquals(SizedInt(96), s.castOn)
        val r = rows(s)
        assertEquals(listOf("(K2, P2) 24 times.", "k to end.", "(K10, K2tog) 8 times. 88 sts.", "Knit."), r.map { it.text })
        assertEquals(ImportItem.Repeat(2, 2, SizedInt(3), "Knit 4 rounds."), s.items[2])
    }

    @Test fun asRowCopiesTextAndRemarksBecomeNotes() {
        val text = """
            Row 1 (WS): Purl across.
            Row 2 (RS): K1, M1L, knit to last st, M1R, k1. (2 sts have been increased)
            Row 3: as Row 1.
        """.trimIndent()
        val s = RowExtractor.extract(text).sections.single()
        assertEquals(Side.WS, s.firstSide)
        val r = rows(s)
        assertEquals("K1, M1L, knit to last st, M1R, k1.", r[1].text)
        assertEquals("2 sts have been increased", r[1].note)
        assertEquals("Purl across.", r[2].text)
    }

    @Test fun petiteStyleSizesAreNormalised() {
        val text = """
            Cast on 6 sts.
            Row 1 (WS): Purl.
            Row 2 (RS): K1, M1L, knit to last st, M1R, k1.
            Work Rows 1 and 2 a total of 40 (50) 60 times.
        """.trimIndent()
        val r = RowExtractor.extract(text)
        assertEquals(3, r.sizeCount)
        assertEquals(SizedInt(listOf(39, 49, 59)), (r.sections.single().items.last() as ImportItem.Repeat).more)
    }

    @Test fun allWsRowsAreFilledIn() {
        val text = """
            Lace:
            Row 1 (RS): K1, yo, k2tog, k1.
            Row 2 and all WS rows: Purl.
            Row 3 (RS): K1, k2tog, yo, k1.
            Row 5 (RS): Knit.
            Row 6 (WS): Knit.
        """.trimIndent()
        val r = RowExtractor.extract(text)
        val s = r.sections.single()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), rows(s).map { it.number })
        assertEquals("Purl.", rows(s)[3].text)
        assertTrue(r.warnings.single().contains("1 rows were filled in"), r.warnings.toString())
    }

    @Test fun lastRowsRepeatAndUncertainTimes() {
        val text = """
            Row 1: K1, p1, rep to end.
            Row 2: P1, k1, rep to end.
            Repeat these 2 rows 5 times.
            Repeat until the piece measures 20 cm.
        """.trimIndent()
        val r = RowExtractor.extract(text)
        assertEquals(SizedInt(4), (r.sections.single().items[2] as ImportItem.Repeat).more)
        assertEquals(2, r.warnings.size)
    }

    @Test fun copyrightAndFinishingProseIsLeftOut() {
        val text = """
            Row 1: Knit.
            Row 2: Purl.
            Bind off.
            Finishing
            Weave in ends. Block the piece. Enjoy!
            This pattern is copyrighted and may not be reproduced.
            Follow me on Instagram.
        """.trimIndent()
        val r = RowExtractor.extract(text)
        assertEquals(1, r.sections.size)
        assertEquals(listOf("Bind off."), r.sections.single().items.filterIsInstance<ImportItem.Note>().map { it.text })
    }

    @Test fun slipWithYarnInFrontIsNotCutOff() {
        val text = """
            Set-up row (WS): k1, sl1 st wyf, k1.
            Row 1 (RS): sl1 st wyf, k1, sl1 st
            wyf. 3 sts
        """.trimIndent()
        val s = RowExtractor.extract(text).sections.single()
        assertEquals(listOf(null, 1), rows(s).map { it.number })
        assertEquals("sl1 st wyf, k1, sl1 st wyf. 3 sts", rows(s)[1].text)
        assertEquals(Side.WS, s.firstSide)
    }

    @Test fun noRowsMeansEmpty() {
        val r = RowExtractor.extract("Materials:\n200 g yarn\nGauge: 22 sts x 30 rows = 10 cm")
        assertTrue(r.isEmpty)
    }

    @Test fun importedSectionsAreCheckedWhereUnderstood() {
        var n = 0
        val text = """
            Cast on 12 sts.
            Row 1 (RS): (K2, P2) repeat to end.
            Row 2 (WS): Work all stitches as they appear.
            Row 3 (RS): K2tog, knit to end. 11 sts.
        """.trimIndent()
        val section = RowExtractor.toSections(RowExtractor.extract(text), "cowl.pdf") { "id${n++}" }.single()
        assertEquals("cowl.pdf", section.importedFrom)
        assertEquals(Construction.FLAT, section.construction)
        val eval = evaluateSection(section, 0, lib)
        assertEquals(12, eval.rows[0].eval.produced)
        // Row 2 is not understood: it is marked "not checked", not an error, and the count is unknown after it.
        assertTrue(eval.rows[1].eval.issues.all { it.severity == Severity.INFO })
        assertNull(eval.rows[2].eval.produced)
        assertTrue(eval.issues.none { it.severity == Severity.ERROR }, eval.issues.toString())
    }
}
