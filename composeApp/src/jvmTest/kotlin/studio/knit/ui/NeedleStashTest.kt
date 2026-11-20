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

    @Test fun cablesLiveInTheBrandFolder() = knitUiTest(seed = { repo ->
        seedRealStash(repo)
        repo.needleStash.first().forEach { repo.saveNeedle(studio.knit.stash.moveNotesToBrand(it)) }
    }) { env ->
        tap("Tools"); tap("Yarn & needle stash")
        tap("ChiaoGoo")
        assertShown("CABLES")
        assertShown("12 cm tips")
        tap("Add ChiaoGoo cable")
        assertShown("New cable")
        tap("60 cm")
        tap("Save")
        waitForIdle()
        val cables = runBlocking { env.repo.cableStash.first() }
        assertEquals(listOf("ChiaoGoo" to 60.0), cables.map { it.brand to it.lengthCm })
        assertShown("60 cm cable")
        assertShown("7 · 1 cable")
        shot("35-stash-cables")
        // Tip length is labelled as such for interchangeable needles.
        tap("1.5 mm (US 000)")
        assertShown("Tip length")
        shot("36-needle-tip-length")
    }

    @Test fun yarnInTheStashCanHaveAPhoto() = knitUiTest { env ->
        // A tiny real JPEG would be decoded; any bytes are stored, and the list falls back to the colour dot.
        studio.knit.ui.platform.DesktopPlatform.nextImage = javaClass.getResourceAsStream("/yarn.jpg")?.readBytes() ?: byteArrayOf(1, 2, 3)
        tap("Tools"); tap("Yarn & needle stash")
        tap("Add yarn")
        tap("Add photo")
        waitForIdle()
        assertShown("Replace photo")
        shot("37-yarn-photo")
        tap("Save")
        waitForIdle()
        val y = runBlocking { env.repo.yarnStash.first() }.single()
        val id = kotlin.test.assertNotNull(y.photoId)
        kotlin.test.assertNotNull(runBlocking { env.repo.image(id) })
    }

    @Test fun pickingAPdfToImportSaysWhatItIs() = knitUiTest { _ ->
        studio.knit.ui.platform.DesktopPlatform.nextFile = studio.knit.ui.platform.PickedFile("Sokken patroon.pdf", "%PDF-1.7\n...".encodeToByteArray())
        tap("New pattern"); tap("Import a Knit Studio file")
        waitForIdle()
        assertShown("“Sokken patroon.pdf”: This is a PDF document")
        shot("38-import-pdf-message")
    }
}
