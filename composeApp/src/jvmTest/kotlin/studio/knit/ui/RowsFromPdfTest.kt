package studio.knit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import studio.knit.model.Construction
import studio.knit.model.RowKind
import studio.knit.model.SizedInt
import studio.knit.pdf.PdfWriter
import studio.knit.ui.platform.DesktopPlatform
import studio.knit.ui.platform.PickedFile
import studio.knit.ui.platform.pdfTextOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A real PDF with selectable text, made with the app's own PDF writer. */
fun patternPdf(lines: List<String>): ByteArray {
    val w = PdfWriter(595f, 842f)
    lines.forEachIndexed { i, line -> w.text(50f, 60f + i * 16f, line) }
    return w.build("Test pattern")
}

@OptIn(ExperimentalTestApi::class)
class RowsFromPdfTest {
    private val lines = listOf(
        "Easy Cowl",
        "Cast on 40 (48) sts and join in the round.",
        "Ribbing:",
        "Rnd 1: (K2, P2) to end.",
        "Repeat Rnd 1 a total of 6 times.",
        "Body:",
        "Rnd 1: Knit.",
        "Rnd 2: (K2tog, yo) to end.",
        "Rep rnds 1-2 9 more times.",
        "Bind off loosely.",
    )

    @Test fun desktopReaderGetsTheText() {
        val text = pdfTextOf(patternPdf(lines))!!
        assertTrue(text.contains("Rnd 2: (K2tog, yo) to end."), text)
    }

    @Test fun rowsAreReadReviewedAndAdded() = knitUiTest { env ->
        DesktopPlatform.nextFile = PickedFile("Easy_cowl.pdf", patternPdf(lines))
        tap("New pattern"); tap("PDF pattern")
        waitForIdle()
        onNodeWithContentDescription("More").performClick(); waitForIdle()
        tap("Read rows from PDF")
        waitUntil(timeoutMillis = 5_000) { runCatching { assertShown("Found 3 rows in 2 parts") }.isSuccess }
        assertShown("RIBBING"); assertShown("BODY")
        shot("45-rows-from-pdf")
        tap("Add 3 rows to pattern")
        waitForIdle()
        runBlocking { env.app.flushAllAndWait() }
        val p = runBlocking { env.repo.patterns.first() }.single()
        assertEquals(listOf("Ribbing", "Body"), p.sections.map { it.title })
        assertEquals(listOf("Size 1", "Size 2"), p.sizes)
        val rib = p.sections[0]
        assertEquals(Construction.ROUND, rib.construction)
        assertEquals(SizedInt(listOf(40, 48)), rib.castOn)
        assertEquals("Easy_cowl.pdf", rib.importedFrom)
        assertEquals(listOf(RowKind.INSTRUCTION, RowKind.REPEAT_ROWS), rib.rows.map { it.kind })
        assertEquals(SizedInt(9), p.sections[1].rows.single { it.kind == RowKind.REPEAT_ROWS }.repeatTimes)
    }

    @Test fun aScanWithoutTextIsExplained() = knitUiTest { env ->
        DesktopPlatform.nextFile = PickedFile("Scan.pdf", PdfWriter(595f, 842f).build("Scan"))
        tap("New pattern"); tap("PDF pattern")
        waitForIdle()
        onNodeWithContentDescription("More").performClick(); waitForIdle()
        tap("Read rows from PDF")
        waitUntil(timeoutMillis = 5_000) { runCatching { assertShown("This PDF has no text") }.isSuccess }
        assertEquals(0, runBlocking { env.repo.patterns.first() }.single().sections.size)
    }
}
