package studio.knit.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import studio.knit.chart.cell
import studio.knit.io.ImportResult
import studio.knit.io.KnitFiles
import studio.knit.model.Pattern
import studio.knit.ui.platform.DesktopPlatform
import studio.knit.ui.platform.PickedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end workflows driven through the real UI, checking what ends up in the database.
 */
@OptIn(ExperimentalTestApi::class)
class WorkflowTest {
    private fun UiEnv.patterns(): List<Pattern> = runBlocking { app.flushAll(); kotlinx.coroutines.delay(50); repo.patterns.first() }

    @Test fun createBlankPatternAndEditDetails() = knitUiTest { env ->
        tap("New pattern")
        tap("Blank pattern")
        assertShown("Untitled pattern")
        onNode(hasContentDescription("Name") and hasSetTextAction()).performTextInput("Harbour Cowl")
        waitForIdle()
        val saved = env.patterns().single()
        assertEquals("Harbour Cowl", saved.name)
        assertTrue(saved.createdAt > 0 && saved.draft)
    }

    @Test fun drawOnAChartPersistsAndCanBeUndone() = knitUiTest { env ->
        tap("New pattern")
        tap("Chart")
        tap("Create chart")
        onNodeWithText("Draw").assertExists()
        // Purl is the default stitch: tap one cell, then drag across a few more.
        val canvas = onNode(hasContentDescription("Chart canvas", substring = true))
        canvas.performTouchInput { down(center); up() }
        waitForIdle()
        canvas.performTouchInput {
            down(center + Offset(0f, 60f)); moveBy(Offset(60f, 0f)); moveBy(Offset(60f, 0f)); moveBy(Offset(60f, 0f)); up()
        }
        waitForIdle()
        val chart = env.patterns().single().charts.single()
        assertEquals(16 to 12, chart.cols to chart.rows)
        val purls = chart.cells.count { it.stitch == "p" }
        assertTrue(purls >= 3, "expected several purl cells, found $purls")
        shot("21-chart-drawn")

        onNodeWithContentDescription("Undo").performClick(); waitForIdle()
        onNodeWithContentDescription("Undo").performClick(); waitForIdle()
        assertEquals(0, env.patterns().single().charts.single().cells.count { it.stitch == "p" })
        onNodeWithContentDescription("Redo").performClick(); waitForIdle()
        assertEquals(1, env.patterns().single().charts.single().cells.count { it.stitch == "p" })
    }

    @Test fun selectFillAndMarkRepeat() = knitUiTest { env ->
        tap("New pattern"); tap("Chart"); tap("Create chart")
        tap("Select")
        val canvas = onNode(hasContentDescription("Chart canvas", substring = true))
        canvas.performTouchInput { down(center); moveBy(Offset(70f, 0f)); moveBy(Offset(70f, -70f)); up() }
        waitForIdle()
        tap("Fill")
        tap("Repeat")
        shot("22-chart-selection")
        val chart = env.patterns().single().charts.single()
        val box = chart.repeats.single()
        val filled = chart.cells.count { it.stitch == "p" }
        assertEquals((box.colEnd - box.colStart + 1) * (box.rowEnd - box.rowStart + 1), filled)
        assertTrue(filled >= 4)
    }

    @Test fun writtenRowsAreCheckedLive() = knitUiTest { env ->
        tap("New pattern"); tap("Written pattern")
        onNode(hasContentDescription("Stitches before row 1") and hasSetTextAction()).performTextInput("22")
        onNode(hasContentDescription("Instructions") and hasSetTextAction()).performTextInput("K2, *p2, k2; rep from * to end")
        waitForIdle()
        onNodeWithText("22 sts").assertExists()
        tap("Row")
        onAllNodes(hasContentDescription("Instructions") and hasSetTextAction())[1].performTextInput("P2, *k2, p2; rep from * to last 3 sts, p3")
        waitForIdle()
        // The second row does not fit 22 stitches: the error names the problem.
        onNodeWithText("does not fit", substring = true).assertExists()
        shot("23-written-error")
        onAllNodes(hasContentDescription("Instructions") and hasSetTextAction())[1].performTextReplacement("P2, *k2, p2; rep from * to end")
        waitForIdle()
        onNodeWithText("does not fit", substring = true).assertDoesNotExist()
        val section = env.patterns().single().sections.single()
        assertEquals(listOf("K2, *p2, k2; rep from * to end", "P2, *k2, p2; rep from * to end"), section.rows.map { it.text })
        assertEquals(22, section.castOn!!.forSize(0))
    }

    @Test fun rowTrackingAdvancesAndResumes() = knitUiTest(seed = ::seedLibrary) { env ->
        tap("Patterns"); tap("Ribbed scarf"); tap("Start knitting")
        repeat(5) { onNodeWithContentDescription("Row done, next row").performClick(); waitForIdle() }
        onNodeWithContentDescription("Back one row").performClick(); waitForIdle()
        assertShown("Repeat 2 of")
        val p = env.patterns().first { it.name == "Ribbed scarf" }
        assertEquals(4, p.progress!!.positions.values.single())
        // Leave and come back: the position is restored.
        onNodeWithContentDescription("Back").performClick(); waitForIdle()
        onNodeWithText("Continue").performClick(); waitForIdle()
        onNodeWithText("5").assertExists()
    }

    @Test fun pdfAndPatternFileExport() = knitUiTest(seed = ::seedLibrary) { env ->
        tap("Patterns"); tap("Feather & fan lace"); tap("Export")
        shot("24-export-sheet")
        tap("Share PDF")
        waitUntil(timeoutMillis = 5_000) { DesktopPlatform.written.isNotEmpty() }
        val (name, pdf) = DesktopPlatform.written.single()
        assertEquals("Feather fan lace.pdf", name)
        assertTrue(pdf.decodeToString(0, 8).startsWith("%PDF-1.4"))
        assertTrue(pdf.size > 3_000)

        tap("Knit Studio file"); tap("Share file")
        waitUntil(timeoutMillis = 5_000) { DesktopPlatform.written.size == 2 }
        val file = DesktopPlatform.written[1]
        assertTrue(file.first.endsWith(".knit"))
        val decoded = assertIs<ImportResult.Success<Pattern>>(KnitFiles.decodePattern(file.second.decodeToString()))
        assertEquals("Feather & fan lace", decoded.value.name)
        assertEquals(18, decoded.value.charts.single().cols)
    }

    @Test fun importAPatternFileThroughTheUi() = knitUiTest { env ->
        val source = Pattern(id = "from-other-phone", name = "Travelling Mitts", notes = "made on another device")
        DesktopPlatform.nextFile = PickedFile("Travelling Mitts.knit", KnitFiles.encodePattern(source).encodeToByteArray())
        tap("New pattern"); tap("Import a Knit Studio file")
        waitUntil(timeoutMillis = 5_000) { runBlocking { env.repo.patterns.first().isNotEmpty() } }
        waitForIdle()
        assertShown("Travelling Mitts")
        assertEquals("made on another device", assertNotNull(env.patterns().single()).notes)
    }

    @Test fun templateCreatesAValidatedPattern() = knitUiTest { env ->
        tap("New pattern"); tap("Template"); tap("Classic beanie")
        shot("25-template-form")
        tap("Create pattern")
        assertShown("Classic beanie")
        val p = env.patterns().single()
        assertEquals(3, p.sections.size)
        assertEquals(104, p.sections[0].castOn!!.forSize(0))
        tap("Check")
        onNodeWithText("PROBLEMS", substring = true).assertDoesNotExist()
    }

    @Test fun settingsChangeUnitsAndTerms() = knitUiTest { env ->
        tap("Settings")
        tap("Imperial (in, yd, oz)")
        tap("UK")
        val s = runBlocking { env.repo.settingsNow() }
        assertTrue(s.imperial && s.ukTerms)
        onNodeWithText("Tension, cast off", substring = true).assertExists()
    }
}
