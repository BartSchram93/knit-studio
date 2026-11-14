package studio.knit

import studio.knit.data.PaperSize
import studio.knit.model.Gauge
import studio.knit.model.Pattern
import studio.knit.pdf.PatternPdf
import studio.knit.pdf.PdfOptions
import studio.knit.pdf.PdfWriter
import studio.knit.template.Templates
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdfTest {
    private val outDir = File("build/pdf-samples").also { it.mkdirs() }

    /** Structural check: header, trailer, and every xref entry pointing at its object. */
    private fun assertWellFormed(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        val startXref = text.substringAfterLast("startxref").trim().lineSequence().first().toInt()
        assertTrue(text.startsWith("xref", startXref), "startxref must point at the xref table")
        val lines = text.substring(startXref).lines()
        val count = lines[1].split(" ")[1].toInt()
        for (i in 1 until count) {
            val offset = lines[2 + i].substring(0, 10).toInt()
            assertTrue(text.startsWith("$i 0 obj", offset), "object $i is not at offset $offset")
        }
        // Stream lengths must be exact.
        for (m in Regex("/Length (\\d+) >>\nstream\n").findAll(text)) {
            val start = m.range.last + 1
            val len = m.groupValues[1].toInt()
            assertTrue(text.startsWith("\nendstream", start + len), "stream length mismatch")
        }
        return text
    }

    private fun pages(text: String) = Regex("/Type /Page[^s]").findAll(text).count()

    @Test fun richPatternExportsEverySection() {
        val bytes = PatternPdf(richPattern(), lib.with(richPattern().customStitches)).render()
        File(outDir, "rich.pdf").writeBytes(bytes)
        val text = assertWellFormed(bytes)
        assertTrue(pages(text) >= 2)
        // Text is hex encoded; check a few headings by their encoded form.
        fun has(s: String) = text.contains(s.map { "%02X".format(PdfWriter.winAnsi(it)) }.joinToString(""))
        for (heading in listOf("MATERIALS", "ABBREVIATIONS", "INSTRUCTIONS", "CHARTS", "NOTES", "KEY", "Pattern repeat", "1 / ")) {
            assertTrue(has(heading), "missing “$heading”")
        }
        assertTrue(has("Row 1 (RS):"))
        assertTrue(has("Søren’s".replace('’', '\'')) || has("Søren"), "non-ASCII title")
    }

    @Test fun sectionsCanBeLeftOut() {
        val all = PatternPdf(richPattern(), lib).render()
        val minimal = PatternPdf(richPattern(), lib, PdfOptions(cover = false, materials = false, measurements = false,
            abbreviations = false, charts = false, legend = false, notes = false)).render()
        val text = assertWellFormed(minimal)
        assertTrue(minimal.size < all.size)
        fun has(s: String) = text.contains(s.map { "%02X".format(PdfWriter.winAnsi(it)) }.joinToString(""))
        assertTrue(has("INSTRUCTIONS"))
        assertTrue(!has("CHARTS") && !has("MATERIALS"))
    }

    @Test fun paperSizesAndOrientation() {
        fun box(o: PdfOptions) = Regex("/MediaBox \\[0 0 ([\\d.]+) ([\\d.]+)]").find(PatternPdf(richPattern(), lib, o).render().toString(Charsets.ISO_8859_1))!!.destructured
        assertEquals("595.28" to "841.89", box(PdfOptions(paper = PaperSize.A4)).let { (w, h) -> w to h })
        assertEquals("612" to "792", box(PdfOptions(paper = PaperSize.LETTER)).let { (w, h) -> w to h })
        assertEquals("841.89" to "595.28", box(PdfOptions(paper = PaperSize.A4, landscape = true)).let { (w, h) -> w to h })
    }

    @Test fun everyTemplateExports() {
        for (t in Templates.all) {
            val p = Templates.build(t, t.defaultGauge, emptyMap(), lib, ::id, 0)
            val bytes = PatternPdf(p, lib).render()
            assertWellFormed(bytes)
            File(outDir, "${t.id}.pdf").writeBytes(bytes)
        }
    }

    @Test fun largeChartSplitsAcrossPages() {
        val chart = studio.knit.chart.blankChart("big", 60, 150, "Big chart")
        val bytes = PatternPdf(Pattern(id = "p", name = "Big", gauge = Gauge(20.0, 28.0), charts = listOf(chart)), lib).render()
        val text = assertWellFormed(bytes)
        assertTrue(pages(text) >= 3, "pages = ${pages(text)}")
        File(outDir, "big.pdf").writeBytes(bytes)
    }

    @Test fun emptyPatternStillProducesADocument() {
        assertWellFormed(PatternPdf(Pattern(id = "e"), lib).render())
    }

    @Test fun jpegIsEmbedded() {
        // Minimal JPEG header with a 3×2 SOF0 frame; enough for size detection and embedding.
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, 0, 2, 0, 3, 3, 0, 0, 0, 0xFF.toByte(), 0xD9.toByte())
        val w = PdfWriter(200f, 200f)
        assertEquals(3 to 2, assertNotNull(w.jpegSize(jpeg)))
        assertTrue(w.jpeg(jpeg, 10f, 10f, 30f, 20f))
        assertTrue(!w.jpeg(byteArrayOf(1, 2, 3, 4), 0f, 0f, 1f, 1f))
        val text = assertWellFormed(w.build())
        assertTrue(text.contains("/DCTDecode") && text.contains("/Width 3 /Height 2"))
    }

    @Test fun textMeasurementAndWrapping() {
        val w = PdfWriter(200f, 200f)
        // "Knit" in Helvetica: K 667 + n 556 + i 222 + t 278 = 1723 → 17.23 pt at 10 pt.
        assertEquals(17.23f, w.textWidth("Knit", PdfWriter.Font.REGULAR, 10f), 0.001f)
        val lines = w.wrap("K2, *p2, k2; rep from * to end of the row and then some more", PdfWriter.Font.REGULAR, 10f, 100f)
        assertTrue(lines.size > 1 && lines.all { w.textWidth(it, PdfWriter.Font.REGULAR, 10f) <= 100f })
        assertEquals(listOf("a", "", "b"), w.wrap("a\n\nb", PdfWriter.Font.REGULAR, 10f, 100f))
    }
}
