package studio.knit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import studio.knit.data.KnitRepository
import studio.knit.model.Pattern
import studio.knit.model.Yarn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class YarnStashTest {
    private suspend fun seed(repo: KnitRepository) {
        repo.saveYarn(Yarn(id = "y1", brand = "Holst", name = "Supersoft", color = "Teal", skeins = 6.0))
        repo.saveYarn(Yarn(id = "y2", brand = "holst", name = "Coast", skeins = 2.0))
        repo.saveYarn(Yarn(id = "y3", brand = "Drops", name = "Alpaca", skeins = 3.0))
        repo.create(Pattern(id = "p", name = "Muts"))
    }

    @Test fun yarnIsSortedIntoBrandFolders() = knitUiTest(seed = { seed(it) }) { env ->
        tap("Tools"); tap("Yarn & needle stash")
        assertShown("Holst"); assertShown("Drops")
        assertShown("2 yarns · 8 skeins")
        // Folders start closed.
        assertEquals(0, onAllNodesWithText("Supersoft").fetchSemanticsNodes().size)
        shot("70-yarn-folders")
        tap("Holst")
        assertShown("Supersoft"); assertShown("Coast")
        tap("Add Holst yarn")
        assertShown("New yarn")
        tap("Save")
        waitForIdle()
        eventually { assertEquals(4, runBlocking { env.repo.yarnStash.first() }.size) }
        val added = runBlocking { env.repo.yarnStash.first() }.single { it.id !in setOf("y1", "y2", "y3") }
        assertEquals("Holst", added.brand)
        shot("71-yarn-folder-open")
    }

    @Test fun yarnFromTheStashDoesNotBringTheSkeinsYouOwn() = knitUiTest(seed = { seed(it) }) { env ->
        tap("Patterns"); tap("Muts"); tap("Materials")
        tap("Add yarn")
        tap("From stash: Holst Supersoft · Teal")
        assertShown("You have 6 skeins of this yarn in your stash")
        assertShown("Skeins needed")
        shot("72-yarn-from-stash")
        tap("Save")
        // Pattern edits are saved in the background.
        eventually { assertEquals(1, runBlocking { env.repo.patterns.first() }.single().yarns.size) }
        val y = runBlocking { env.repo.patterns.first() }.single().yarns.single()
        assertEquals("Supersoft", y.name)
        assertNull(y.skeins)
        // The stash itself is untouched.
        assertEquals(6.0, runBlocking { env.repo.yarnStash.first() }.single { it.id == "y1" }.skeins)
    }
}
