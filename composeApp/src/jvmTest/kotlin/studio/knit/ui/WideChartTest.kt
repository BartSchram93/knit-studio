package studio.knit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import studio.knit.model.Cell
import studio.knit.model.Chart
import studio.knit.model.Pattern
import studio.knit.ui.chart.columnsFor
import studio.knit.ui.chart.panelRanges
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WideChartTest {
    /** A 90-stitch crown-like chart: k2tog every 6 sts on alternate rows. */
    private val chart = Chart(
        id = "wide", name = "Crown", cols = 90, rows = 6,
        cells = List(90 * 6) { i -> val r = i / 90; val c = i % 90; Cell(if (r % 2 == 0 && c % 6 == 0) "yo" else if (r % 2 == 0 && c % 6 == 1) "k2tog" else "k") },
    )

    @Test fun panelsCoverEveryStitchOnce() {
        assertEquals(listOf(1..20, 21..40, 41..60, 61..80, 81..90), panelRanges(90, 20))
        // Right-to-left: stitch 1 is the rightmost column.
        assertEquals(70..89, chart.columnsFor(1..20))
        assertEquals(0..9, chart.columnsFor(81..90))
    }

    @Test fun knitModeShowsPanelsAndTheWrittenRow() = knitUiTest(seed = { repo ->
        repo.create(Pattern(id = "w", name = "Wide hat", charts = listOf(chart)))
    }) { env ->
        tap("Patterns"); tap("Wide hat"); tap("Start knitting")
        assertShown("Panels")
        assertShown("Stitches 1–")
        assertShown("k2tog")
        shot("50-knit-wide-chart-panels")
        onNodeWithContentDescription("Row done, next row").performClick()
        waitForIdle()
        shot("51-knit-wide-chart-row2")
    }

    @Test fun editorPanelsAllowDrawingByTap() = knitUiTest(seed = { repo ->
        repo.create(Pattern(id = "w", name = "Wide hat", charts = listOf(chart)))
    }) { env ->
        tap("Patterns"); tap("Wide hat"); tap("Charts"); tap("Crown")
        assertShown("tap a cell to draw")
        shot("52-editor-wide-chart-panels")
        runBlocking { env.app.flushAll() }
        assertEquals(90, runBlocking { env.repo.patterns.first() }.single().charts.single().cols)
    }

    @Test fun stitchesPerPanelCanBeChosen() {
        // Auto: as many as fit with readable cells, in fives once there is room for ten.
        assertEquals(15, studio.knit.ui.chart.stitchesPerPanel(380.dp, 90))
        assertEquals(20, studio.knit.ui.chart.stitchesPerPanel(380.dp, 90, chosen = 20))
        assertEquals(12, studio.knit.ui.chart.stitchesPerPanel(380.dp, 12, chosen = 20))
    }

    @Test fun moreStitchesPerPanelInKnitMode() = knitUiTest(seed = { repo ->
        repo.create(Pattern(id = "w", name = "Wide hat", charts = listOf(chart)))
    }) { env ->
        tap("Patterns"); tap("Wide hat"); tap("Start knitting")
        assertShown("Auto ·")
        repeat(4) { onNodeWithContentDescription("More stitches per panel").performClick(); waitForIdle() }
        eventually { assertEquals(true, runBlocking { env.repo.settingsNow() }.panelStitches >= 20) }
        waitForIdle()
        assertShown("Stitches 1–${runBlocking { env.repo.settingsNow() }.panelStitches}")
        shot("53-knit-panel-width")
    }
}
