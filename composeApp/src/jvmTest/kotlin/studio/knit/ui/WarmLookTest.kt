package studio.knit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import studio.knit.data.AccentColor
import studio.knit.model.Pattern
import studio.knit.model.Row
import studio.knit.model.Section
import studio.knit.model.SizedInt
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WarmLookTest {
    private val small = Pattern(
        id = "s", name = "Little swatch",
        sections = listOf(Section("sec", "Swatch", castOn = SizedInt(10), rows = listOf(Row("r1", text = "Knit."), Row("r2", text = "Purl.")))),
    )

    @Test fun homeGreetsAndColourCanBeChosen() = knitUiTest(seed = { repo -> seedLibrary(repo) }) { env ->
        assertShown(studio.knit.ui.components.greetingNow())
        shot("60-home-warm")
        tap("Settings")
        onNodeWithContentDescription("Sage colour").performClick(); waitForIdle()
        eventually { assertEquals(AccentColor.SAGE, runBlocking { env.repo.settingsNow() }.accent) }
        shot("61-settings-colour")
        tap("Home")
        shot("62-home-sage")
    }

    @Test fun finishingAPatternIsCelebrated() = knitUiTest(seed = { repo -> repo.create(small) }) { env ->
        tap("Patterns"); tap("Little swatch"); tap("Start knitting")
        onNodeWithContentDescription("Row done, next row").performClick(); waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithContentDescription("Row done, next row").performClick()
        mainClock.advanceTimeBy(500)
        assertShown("Little swatch is finished!")
        shot("63-celebration")
        mainClock.advanceTimeBy(700)
        shot("64-celebration-later")
        mainClock.autoAdvance = true
    }
}
