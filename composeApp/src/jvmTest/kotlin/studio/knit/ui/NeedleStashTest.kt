package studio.knit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import studio.knit.data.KnitRepository
import studio.knit.model.Needle
import studio.knit.model.NeedleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The needle stash from the user's phone (screenshot, 2026-11-20): brands typed into the notes. */
suspend fun seedRealStash(repo: KnitRepository) {
    val list = listOf(
        1.5 to "ChiaoGoo", 2.0 to "Seeknit", 2.5 to "Seeknit", 2.75 to "ChiaoGoo", 2.75 to "Seeknit", 3.0 to "Seeknit",
        3.25 to "ChiaoGoo", 3.25 to "Seeknit", 3.75 to "ChiaoGoo", 4.0 to "ChiaoGoo", 4.0 to "Seeknit", 4.5 to "ChiaoGoo", 5.0 to "ChiaoGoo",
    )
    list.forEachIndexed { i, (mm, brand) ->
        repo.saveNeedle(Needle(id = "n$i", sizeMm = mm, type = NeedleType.INTERCHANGEABLE, lengthCm = if (mm == 2.0) 4.0 else 12.0,
            material = if (brand == "ChiaoGoo") "Steel" else "Bamboo", notes = brand))
    }
}

@OptIn(ExperimentalTestApi::class)
class NeedleStashTest {
    @Test fun brandsInNotesBecomeFolders() = knitUiTest(seed = ::seedRealStash) { env ->
        tap("Tools"); tap("Yarn & needle stash")
        assertShown("13 needles have a brand written in the notes")
        shot("30-stash-before")
        tap("Move to Brand…")
        shot("31-stash-confirm")
        tap("Move")
        waitForIdle()
        val needles = runBlocking { env.repo.needleStash.first() }
        assertTrue(needles.all { it.notes.isEmpty() })
        assertEquals(mapOf("ChiaoGoo" to 7, "Seeknit" to 6), needles.groupingBy { it.brand }.eachCount())
        // Two closed folders now, each with its count.
        assertShown("ChiaoGoo"); assertShown("Seeknit")
        assertEquals(0, onAllNodesWithText("4 mm (US 6)").fetchSemanticsNodes().size)
        shot("32-stash-folders")
        tap("ChiaoGoo")
        assertShown("1.5 mm (US 000)")
        assertShown("Add ChiaoGoo needle")
        shot("33-stash-folder-open")
        tap("By size")
        assertShown("2.75 mm (US 2)")
        shot("34-stash-by-size")
    }
}
