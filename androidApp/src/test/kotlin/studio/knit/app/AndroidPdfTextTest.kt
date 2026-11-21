package studio.knit.app

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import studio.knit.pdf.PdfWriter
import studio.knit.pdfimport.RowExtractor
import studio.knit.ui.platform.pdfTextOf

/** PdfBox-Android reads the text of a PDF (as on the phone) and the rows are found in it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = KnitApplication::class)
class AndroidPdfTextTest {
    @Test fun pdfBoxAndroidReadsRows() {
        val w = PdfWriter(595f, 842f)
        listOf("Cast on 20 sts.", "Row 1 (RS): K2, P2, rep to end.", "Row 2 (WS): K2, P2, rep to end.", "Repeat Rows 1 and 2 a total of 3 times.")
            .forEachIndexed { i, line -> w.text(50f, 60f + i * 16f, line) }
        val context = ApplicationProvider.getApplicationContext<KnitApplication>()
        val text = pdfTextOf(context, w.build("Test"))
        assertTrue("text: $text", text != null && text.contains("Row 2 (WS): K2, P2, rep to end."))
        val found = RowExtractor.extract(text!!)
        assertEquals(2, found.rowCount)
        assertEquals(1, found.sections.single().repeatCount)
    }

    @Test fun notAPdfGivesNull() {
        assertNull(pdfTextOf(ApplicationProvider.getApplicationContext(), "hello".encodeToByteArray()))
    }
}
