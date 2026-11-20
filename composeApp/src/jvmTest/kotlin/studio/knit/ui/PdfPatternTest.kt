package studio.knit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import studio.knit.ui.platform.DesktopPlatform
import studio.knit.ui.platform.PickedFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PdfPatternTest {
    private val pdf = "%PDF-1.7\n".encodeToByteArray() + ByteArray(40_000) { (it % 251).toByte() }

    @Test fun pdfBecomesAPatternWithARowCounter() = knitUiTest { env ->
        DesktopPlatform.nextFile = PickedFile("Fjord_sjaal.pdf", pdf)
        tap("New pattern"); tap("PDF pattern")
        waitForIdle()
        assertShown("ROW")
        repeat(3) { onNodeWithContentDescription("Row done").performClick(); waitForIdle() }
        onNodeWithContentDescription("One row back").performClick(); waitForIdle()
        shot("40-pdf-viewer")
        runBlocking { env.app.flushAll(); kotlinx.coroutines.delay(100) }
        val p = runBlocking { env.repo.patterns.first() }.single()
        assertEquals("Fjord sjaal", p.name)
        val f = p.files.single()
        assertContentEquals(pdf, runBlocking { env.repo.fileBytes(f.id) })
        assertEquals(2, p.progress!!.counters.single().value)

        onNodeWithContentDescription("Back").performClick(); waitForIdle()
        assertShown("Open PDF")
        shot("41-pattern-with-pdf")
    }

    @Test fun choosingAPdfUnderImportAlsoWorks() = knitUiTest { env ->
        DesktopPlatform.nextFile = PickedFile("Mutsje.pdf", pdf)
        tap("New pattern"); tap("Import a Knit Studio file")
        waitForIdle()
        assertShown("ROW")
        assertEquals(listOf("Mutsje"), runBlocking { env.repo.patterns.first() }.map { it.name })
    }

    @Test fun aNonPdfIsRefusedWithAReason() = knitUiTest { env ->
        DesktopPlatform.nextFile = PickedFile("foto.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3))
        tap("New pattern"); tap("PDF pattern")
        waitForIdle()
        assertShown("“foto.jpg” is not a PDF: This is a photo")
        assertEquals(0, runBlocking { env.repo.patterns.first() }.size)
    }
}
