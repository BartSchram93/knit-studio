package studio.knit.ui.platform

import studio.knit.pdf.PdfWriter
import studio.knit.pdfimport.RowExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs on the iOS simulator: PDFKit reads the text of a PDF and the rows are found in it. */
class IosPdfTextTest {
    @Test fun pdfKitReadsRows() {
        val w = PdfWriter(595f, 842f)
        listOf("Cast on 20 sts.", "Row 1 (RS): K2, P2, rep to end.", "Row 2 (WS): K2, P2, rep to end.", "Repeat Rows 1 and 2 a total of 3 times.")
            .forEachIndexed { i, line -> w.text(50f, 60f + i * 16f, line) }
        val text = pdfTextOf(w.build("Test"))
        assertTrue(text != null && text.contains("Row 2 (WS): K2, P2, rep to end."), "PDFKit text: $text")
        val found = RowExtractor.extract(text!!)
        assertEquals(2, found.rowCount)
        assertEquals(1, found.sections.single().repeatCount)
    }

    @Test fun notAPdfGivesNull() {
        assertNull(pdfTextOf("hello".encodeToByteArray()))
    }
}
