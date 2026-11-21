package studio.knit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import studio.knit.achieve.AchievementState
import studio.knit.data.KnitRepository
import studio.knit.model.Category
import studio.knit.model.DayLog
import studio.knit.model.Pattern
import studio.knit.model.Progress
import studio.knit.model.Row
import studio.knit.model.Section
import studio.knit.model.SizedInt
import studio.knit.stats.dayKey
import studio.knit.util.nowMillis
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AchievementsUiTest {
    /** A knitter with some history: 40 days, a finished hat, a night of knitting. */
    private suspend fun history(repo: KnitRepository) {
        val day = 86_400_000L
        val now = nowMillis()
        val days = (0 until 40).associate { i -> dayKey(now - i * day) to DayLog(rows = 30 + i % 7, seconds = 2400, hours = mapOf(20 to 30, 2 to (if (i == 3) 5 else 0)).filterValues { it > 0 }) }
        val s = Section("rib", "Rib", castOn = SizedInt(96), rows = listOf(Row("r1", text = "(K2, P2) to end."), Row("r2", text = "(K2tog, yo, k2) to end.")))
        repo.create(Pattern(id = "hat", name = "Cosy hat", category = Category.HAT, sections = listOf(s),
            progress = Progress(startedAt = now - 30 * day, finishedAt = now - 2 * day, positions = mapOf("rib" to 2), days = days, longestSession = 5400)))
    }

    @Test fun homeShowsLevelAndAchievementsScreenShowsBadges() = knitUiTest(seed = { history(it) }) { env ->
        eventually { assertShown("Next:") }
        shot("70-home-level")
        tap("Tools"); tap("Achievements")
        eventually { assertShown("badges earned") }
        shot("71-achievements")
        // A found hidden achievement and one still hidden.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Night owl")); waitForIdle()
        assertShown("Night owl")
        assertShown("???")
        tap("Night owl")
        assertShown("Knit between midnight and 4 am")
        shot("72-achievement-detail")
    }

    @Test fun yearReviewAndRewards() = knitUiTest(seed = { history(it) }) { env ->
        tap("Tools"); tap("Knitting stats")
        eventually { onNode(hasScrollToNodeAction()).performScrollToNode(hasText("PERSONAL RECORDS")) }
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Your knitting year")); tap("Your knitting year")
        eventually { assertShown("HIGHLIGHTS") }
        shot("73-year-review")
        // Night owl unlocked Midnight; Gold (5 finished projects) is still locked.
        onNodeWithContentDescription("Back").performClick(); waitForIdle()
        onNodeWithContentDescription("Back").performClick(); waitForIdle()
        tap("Settings")
        eventually { onNodeWithContentDescription("Midnight colour").assertExists(); onNodeWithContentDescription("Gold colour, locked").assertExists() }
        onNodeWithContentDescription("Midnight colour").performClick(); waitForIdle()
        eventually { assertTrue(runBlocking { env.repo.settingsNow() }.accent == studio.knit.data.AccentColor.MIDNIGHT) }
        shot("74-settings-rewards")
    }

    @Test fun aNewAchievementIsCelebrated() = knitUiTest(seed = { repo ->
        // Earlier knitting was already counted; a first row is new.
        repo.updateSettings { it.copy(achievementState = AchievementState(initialized = true).toJson()) }
        repo.create(Pattern(id = "p", name = "First try", progress = Progress(startedAt = 0, days = mapOf(dayKey(nowMillis()) to DayLog(rows = 1)))))
    }) { env ->
        eventually { assertShown("First stitch") }
        shot("75-unlock-toast")
        eventually { assertTrue("first-row:1" in AchievementState.fromJson(runBlocking { env.repo.settingsNow() }.achievementState).unlocked) }
    }
}
