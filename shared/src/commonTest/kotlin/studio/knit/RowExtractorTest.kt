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
            Row 2 (WS): Work the bobble panel over all sts.
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

    @Test fun workAsSetPhrasesKeepTheCount() {
        var n = 0
        val text = """
            Cast on 12 sts.
            Row 1 (RS): K1, M1R, work in pattern until 1 stitch remains, M1L, K1.
            Row 2 (WS): Work all stitches as they appear.
            Row 3 (RS): Rib to end.
            Row 4 (WS): Knit the knits and purl the purls.
        """.trimIndent()
        val section = RowExtractor.toSections(RowExtractor.extract(text), "x.pdf") { "id${n++}" }.single()
        val eval = evaluateSection(section, 0, lib)
        assertEquals(listOf(14, 14, 14, 14), eval.rows.map { it.eval.produced })
        assertTrue(eval.issues.isEmpty(), eval.issues.toString())
    }

    @Test fun countProblemsInImportedRowsAreWarnings() {
        var n = 0
        val text = """
            Cast on 10 sts.
            Row 1: K2tog, knit to end. 10 sts.
        """.trimIndent()
        val section = RowExtractor.toSections(RowExtractor.extract(text), "x.pdf") { "id${n++}" }.single()
        val eval = evaluateSection(section, 0, lib)
        assertTrue(eval.issues.isNotEmpty())
        assertTrue(eval.issues.all { it.severity == Severity.WARNING }, eval.issues.toString())
    }
}

class MarkerTest {
    private val parser = studio.knit.written.RowParser(lib)
    private fun eval(row: String, sts: Int, markers: List<Int>?) =
        studio.knit.written.RowEvaluator().evaluate(parser.parse(row), sts, markersIn = markers)

    @Test fun knitToMarkerAndSlipMarker() {
        val e = eval("K1, M1L, knit to marker, M1R, slip marker, k2, slip marker, M1L, knit to the last stitch on the needle, M1R, k1.", 6, listOf(2, 4))
        assertTrue(e.issues.isEmpty(), e.issues.toString())
        assertEquals(10, e.produced)
        assertEquals(listOf(4, 6), e.markersOut)
    }

    @Test fun markersPassedWithoutSmAreCarried() {
        val e = eval("k to end", 10, listOf(3, 7))
        assertEquals(listOf(3, 7), e.markersOut)
    }

    @Test fun placeAndRemoveMarkers() {
        val e = eval("k3, pm, k4, pm, k3", 10, emptyList())
        assertEquals(listOf(3, 7), e.markersOut)
        val r = eval("k to marker, rm, k to marker, sm, k to end", 10, listOf(3, 7))
        assertTrue(r.issues.isEmpty(), r.issues.toString())
        assertEquals(listOf(7), r.markersOut)
    }

    @Test fun toStitchesBeforeMarkerAndNamedMarkers() {
        val e = eval("k to 2 sts before marker, k2tog, slip M1, ssk, work to M2, slip M2, k to end", 12, listOf(5, 9))
        assertTrue(e.issues.isEmpty(), e.issues.toString())
        assertEquals(10, e.produced)
        assertEquals(listOf(4, 7), e.markersOut)
    }

    @Test fun missingMarkerIsReported() {
        val e = eval("k to marker, k to end", 10, emptyList())
        assertTrue(e.issues.any { it.message.contains("no marker ahead") }, e.issues.toString())
        val unknown = eval("k to marker, k to end", 10, null)
        assertTrue(unknown.issues.single().message.startsWith("Marker positions are not known"))
    }

    @Test fun flatRowsMirrorMarkersAcrossASection() {
        var n = 0
        val text = """
            Cast on 6 sts. Divide the sts with markers: 2 sts, place a marker, 2 sts, place a marker, 2 sts.
            Row 1 (WS): Purl across.
            Row 2 (RS): K1, M1L, knit to marker, M1R, slip marker, k2, slip marker, M1L, knit to the last stitch on the needle, M1R, k1. (4 sts have been increased)
            Work Rows 1 and 2 a total of 5 times.
        """.trimIndent()
        val import = RowExtractor.extract(text)
        assertEquals(listOf(2, 4), import.sections.single().markers)
        val section = RowExtractor.toSections(import, "uma.pdf") { "m${n++}" }.single()
        val e = evaluateSection(section, 0, lib)
        assertTrue(e.issues.isEmpty(), e.issues.toString())
        assertEquals(26, e.endCount)
    }
}

class PdfWordingTest {
    private val parser = studio.knit.written.RowParser(lib)
    private fun produced(row: String, sts: Int) = studio.knit.written.RowEvaluator().evaluate(parser.parse(row), sts)

    @Test fun writtenOutSlipDecreases() {
        val e = produced("p4, yo, sl1, k2tog, psso, yo, p3", 10)
        assertTrue(e.issues.isEmpty(), e.issues.toString())
        assertEquals(10, e.produced)
        assertEquals(8, produced("k3, sl 1, k1, psso, k4", 9).produced)
    }

    @Test fun moreCableCrosses() {
        for (c in listOf("2/1/2 LC", "2/1/2 LPC", "2/1/2 RPC", "1/2 RPC", "1/2 LPC", "2/1 LC")) {
            assertTrue(parser.parse("p2, $c, p2").errors.isEmpty(), c)
        }
        assertEquals(9, produced("p2, 2/1/2 LC, p2", 9).produced)
    }

    @Test fun closedStarsAreWorkedOnce() {
        val e = produced("m1L, k2, *k2, k2tog, yo*, k2", 8)
        assertTrue(e.issues.isEmpty(), e.issues.toString())
        assertEquals(9, e.produced)
    }

    @Test fun turnAndCastOnInTheMiddleOfARow() {
        val e = produced("(K2, P2) 2 times, turn and cast on 4 sts, turn, rib to end.", 12)
        assertTrue(e.issues.isEmpty(), e.issues.toString())
        assertEquals(16, e.produced)
    }

    @Test fun abbreviationNotesInCapitalsAreIgnored() {
        assertTrue(parser.parse("k1, SSK (GGT), k1").errors.isEmpty())
    }

    @Test fun edgingAroundEveryChartRowAndSplitHeaders() {
        var n = 0
        val text = """
            Cast on 9 sts.
            Set-up row (WS): k1, sl1 st wyf, k1, k3, k1, sl1 st wyf, k1.
            All rows are worked with the edging as follows: RS rows: sl1 st wyf, k1, sl1 st wyf, work chart, sl1 st wyf, k1, sl1 st wyf. WS rows: k1, sl1 st wyf, k1, work chart, k1, sl1 st wyf, k1.
            Chart 1
            Row 1 (RS): k1, m1L, k2. 10 sts
            Row 2 and all WS rows unless otherwise
            noted: … knit the knits and purl the purls.
            Row 3 (RS): k4.
        """.trimIndent()
        val import = RowExtractor.extract(text)
        val chart = import.sections.last()
        val rows = chart.items.filterIsInstance<ImportItem.Instruction>()
        assertEquals("sl1 st wyf, k1, sl1 st wyf, k1, m1L, k2, sl1 st wyf, k1, sl1 st wyf. 10 sts", rows[0].text)
        assertEquals("k1, sl1 st wyf, k1, work to last 3 sts, k1, sl1 st wyf, k1.", rows[1].text)
        val sections = RowExtractor.toSections(import, "s.pdf", lib) { "e${n++}" }
        val e = evaluateSection(sections.last(), 0, lib)
        assertTrue(e.issues.none { it.severity != Severity.INFO }, e.issues.toString())
        assertEquals(10, e.endCount)
    }

    @Test fun ellipsisRowsAreExplained() {
        var n = 0
        val section = RowExtractor.toSections(RowExtractor.extract("Cast on 10 sts.\nRow 1: k3, yo, k2tog…"), "x.pdf") { "z${n++}" }.single()
        val e = evaluateSection(section, 0, lib)
        assertTrue(e.issues.single().message.contains("given elsewhere"), e.issues.toString())
    }
}
