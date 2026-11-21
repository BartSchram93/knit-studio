package studio.knit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RenameStitchUiTest {
    @Test fun builtInStitchCanBeRenamedAndReset() = knitUiTest { env ->
        tap("Tools"); tap("Stitch library")
        onAllNodes(hasSetTextAction()).onFirst().performTextInput("pass over"); waitForIdle()
        tap("Slip 1, knit 1, pass over")
        tap("Rename")
        onAllNodesWithText("skpo").onFirst().performTextReplacement("skp")
        waitForIdle()
        shot("55-rename-stitch")
        tap("Save")
        waitForIdle()
        eventually { assertEquals("skp", runBlocking { env.repo.library.first() }["skpo"]!!.abbr) }
        tap("Slip 1, knit 1, pass over"); tap("Rename"); tap("Use the original name")
        eventually { assertEquals("skpo", runBlocking { env.repo.library.first() }["skpo"]!!.abbr) }
    }
}

@OptIn(ExperimentalTestApi::class)
class NewStitchCountTest {
    @Test fun minusOneMeansDecreaseByOne() = knitUiTest { env ->
        tap("Tools"); tap("Stitch library")
        onNodeWithContentDescription("New stitch").performClick(); waitForIdle()
        onAllNodes(hasSetTextAction())[1].performTextInput("My dec")
        onAllNodes(hasSetTextAction())[2].performTextInput("mdec")
        onNodeWithContentDescription("Increase Worked over (stitches)").performClick()
        onNodeWithContentDescription("Decrease Change in stitch count").performClick(); waitForIdle()
        assertShown("−1")
        assertShown("uses 2 and leaves 1")
        shot("56-new-stitch-count")
        tap("Save")
        val def = eventually { runBlocking { env.repo.library.first() }.custom.single() }
        assertEquals(2, def.consumes); assertEquals(1, def.produces)
    }
}
