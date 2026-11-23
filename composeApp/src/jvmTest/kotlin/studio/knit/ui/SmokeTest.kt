package studio.knit.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import studio.knit.data.KnitRepository
import studio.knit.data.Settings
import studio.knit.data.ThemeMode
import studio.knit.model.Needle
import studio.knit.model.Yarn
import studio.knit.model.YarnWeight
import studio.knit.stitch.StitchLibrary
import studio.knit.template.Templates
import studio.knit.util.newId
import kotlin.test.Test

/** Seeds a small library the way a user would build it: from the templates. */
suspend fun seedLibrary(repo: KnitRepository) {
    val lib = StitchLibrary()
    for (id in listOf("scarf", "cable", "colorwork", "hat", "lace")) {
        val t = Templates.byId(id)!!
        val p = Templates.build(t, t.defaultGauge, emptyMap(), lib, ::newId, 0)
        repo.create(
            if (id == "lace") p.copy(
                favorite = true, draft = false, sizes = emptyList(),
                yarns = listOf(Yarn(newId(), "MC", "Holst Garn", "Supersoft", YarnWeight.FINGERING, "100% wool", "Cranberry", 0xFF8C2F39, "", 287.0, 50.0, 2.0)),
                needles = listOf(Needle(newId(), 3.5, lengthCm = 80.0, material = "Steel")),
            ) else p,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.tap(text: String) {
    // Screens load their data from the database on another thread: wait for the text to appear.
    eventually(5_000) { onAllNodesWithText(text).onFirst().assertExists() }
    val node = onAllNodesWithText(text).onFirst()
    // Bring the node into view when it sits in a scrolling sheet or page.
    runCatching { node.performScrollTo() }
    waitForIdle()
    node.performClick()
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertShown(text: String) {
    // Waits briefly for text that depends on data loaded in the background.
    eventually(5_000) { onAllNodesWithText(text, substring = true).onFirst().assertExists() }
}

@OptIn(ExperimentalTestApi::class)
class SmokeTest {
    @Test fun emptyHome() = knitUiTest {
        onNodeWithText("Start your first pattern").assertExists()
        shot("01-home-empty")
        tap("New pattern")
        shot("02-create-sheet")
    }

    @Test fun tour() = knitUiTest(seed = ::seedLibrary) {
        onNodeWithText("RECENT").assertExists()
        shot("03-home")
        tap("Patterns")
        shot("04-library")
        tap("Feather & fan lace")
        shot("05-pattern-overview")
        tap("Charts")
        shot("06-pattern-charts")
        tap("Feather & fan")
        shot("07-chart-editor")
        onNodeWithContentDescription("All stitches").performClick()
        shot("08-stitch-picker")
    }

    @Test fun tourRowsAndKnit() = knitUiTest(seed = ::seedLibrary) {
        tap("Patterns")
        tap("Feather & fan lace")
        tap("Rows")
        shot("09-pattern-rows")
        tap("Feather & fan")
        shot("10-section-editor")
        onNodeWithContentDescription("Back").performClick(); waitForIdle()
        tap("Materials")
        shot("11-materials")
        tap("Check")
        shot("12-check")
        tap("Overview")
        tap("Start knitting")
        shot("13-knit")
        onNodeWithContentDescription("Row done, next row").performClick(); waitForIdle()
        onNodeWithContentDescription("Row done, next row").performClick(); waitForIdle()
        shot("14-knit-row3")
    }

    @Test fun tourToolsAndSettings() = knitUiTest(seed = ::seedLibrary) {
        tap("Tools")
        shot("15-tools")
        tap("Gauge")
        shot("16-gauge")
        onNodeWithContentDescription("Back").performClick(); waitForIdle()
        tap("Stitch library")
        shot("17-stitch-library")
        onNodeWithContentDescription("Back").performClick(); waitForIdle()
        tap("Settings")
        shot("18-settings")
    }

    @Test fun darkMode() = knitUiTest(Settings(theme = ThemeMode.DARK, haptics = false), seed = ::seedLibrary) {
        shot("19-home-dark")
        tap("Patterns")
        tap("Cable panel")
        tap("Charts")
        onNodeWithText("8 × 6", substring = true).performClick(); waitForIdle()
        shot("20-chart-editor-dark")
    }
}
