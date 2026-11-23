package studio.knit.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import studio.knit.model.Cell
import studio.knit.model.Chart
import studio.knit.model.DayLog
import studio.knit.model.Pattern
import studio.knit.model.Progress
import studio.knit.model.Row
import studio.knit.model.Section
import studio.knit.model.SizedInt
import studio.knit.stats.dayKey
import studio.knit.util.nowMillis
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FeedbackRoundTest {
    private val pattern = Pattern(
        id = "p", name = "Scarf",
        sections = listOf(
            Section("s1", "Section 1", castOn = SizedInt(10), rows = listOf(Row("a", text = "Knit."))),
            Section("s2", "Section 2", castOn = SizedInt(10), rows = listOf(Row("b", text = "Purl."))),
        ),
        charts = listOf(Chart(id = "c1", name = "Chart 1", cols = 4, rows = 2, cells = List(8) { Cell("k") })),
        progress = Progress(startedAt = 0, days = mapOf(dayKey(nowMillis()) to DayLog(rows = 12, seconds = 3900))),
    )

    private fun order(env: UiEnv) = runBlocking { env.repo.patterns.first() }.single().let { p ->
        studio.knit.companion.tracksOf(p, 0, studio.knit.stitch.StitchLibrary()).map { it.title }
    }

    @Test fun partsCanBeDraggedIntoANewOrder() = knitUiTest(seed = { it.create(pattern) }) { env ->
        tap("Patterns"); tap("Scarf"); tap("Continue")
        assertEquals(listOf("Section 1", "Section 2", "Chart 1"), order(env))
        // Touch and hold "Chart 1", then drag it to the left, past "Section 2".
        onAllNodesWithText("Chart 1").onFirst().performTouchInput {
            down(center); advanceEventTime(800); moveBy(Offset(-40f, 0f)); moveBy(Offset(-200f, 0f)); up()
        }
        waitForIdle()
        eventually { runBlocking { env.app.flushAllAndWait() }; assertEquals(listOf("Section 1", "Chart 1", "Section 2"), order(env)) }
        waitForIdle()
        // The chips on screen follow the new order too.
        val x = listOf("Section 1", "Chart 1", "Section 2").map { onAllNodesWithText(it).onFirst().fetchSemanticsNode().boundsInRoot.left }
        assertEquals(x.sorted(), x)
        shot("80-parts-reordered")
    }

    @Test fun partsCanBeMovedFromTheMenu() = knitUiTest(seed = { it.create(pattern) }) { env ->
        tap("Patterns"); tap("Scarf"); tap("Continue")
        onNodeWithContentDescription("More").performClick(); waitForIdle()
        tap("Order of parts")
        onNodeWithContentDescription("Move Section 1 down").performClick(); waitForIdle()
        shot("81-order-sheet")
        eventually { runBlocking { env.app.flushAllAndWait() }; assertEquals(listOf("Section 2", "Section 1", "Chart 1"), order(env)) }
    }

    @Test fun eachDayShowsRowsAndTime() = knitUiTest(seed = { it.create(pattern) }) { env ->
        tap("Patterns"); tap("Scarf")
        onAllNodesWithText("rows · 1 h 05 min", substring = true).onFirst().assertExists()
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("PER DAY"))
        assertShown("12 rows")
        shot("82-stats-per-day")
    }

    @Test fun anOldProjectCanBeMarkedFinishedWithItsDates() = knitUiTest(seed = { it.create(Pattern(id = "old", name = "Dekentje")) }) { env ->
        tap("Patterns"); tap("Dekentje")
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Finished"))
        assertShown("Not started")
        tap("Finished")
        eventually {
            runBlocking { env.app.flushAllAndWait() }
            val pr = runBlocking { env.repo.patterns.first() }.single().progress!!
            assertEquals(true, pr.finishedAt != null && pr.startedAt <= pr.finishedAt!!)
        }
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Finished on"))
        tap("Finished on")
        waitForIdle()
        shot("83-finished-date-picker")
    }
}
