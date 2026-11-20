package studio.knit.ui.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import studio.knit.data.ImportOutcome
import studio.knit.data.KnitRepository
import studio.knit.data.iosDriver
import studio.knit.model.Chart
import studio.knit.model.Cell
import studio.knit.model.Pattern
import studio.knit.model.Yarn
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Runs on the iOS simulator: export a pattern the way the share sheet does (temporary file), read
 * it back the way the Files picker does, and import it — the full iPhone path except the UI.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosFileRoundTripTest {
    @Test fun exportedPatternFileReadsBackAndImports(): Unit = runBlocking {
        val db = "roundtrip-${Random.nextInt(1_000_000)}.db"
        val repo = KnitRepository(iosDriver(db), io = Dispatchers.Unconfined)
        val photo = Random(1).nextBytes(150_000)
        val pattern = repo.create(
            Pattern(
                id = "p", name = "Søren’s “Fjord” sjaal — ✓", notes = "Notitie ".repeat(2000),
                yarns = listOf(Yarn(id = "y", brand = "Drops", name = "Alpaca", colorArgb = 0xFF8C2F39)),
                charts = listOf(Chart(id = "c", cols = 3, rows = 2, cells = List(6) { Cell("p") })),
            ),
        )
        val imageId = repo.addImage(pattern.id, photo)
        repo.save(pattern.copy(imageIds = listOf(imageId)))

        val text = assertNotNull(repo.exportPattern(pattern.id))
        val url = assertNotNull(tempFile("Søren sjaal.knit", text.encodeToByteArray()))
        val picked = assertNotNull(readPickedFile(url))
        assertEquals("Søren sjaal.knit", picked.name)
        assertContentEquals(text.encodeToByteArray(), picked.bytes)

        val imported = assertIs<ImportOutcome.Imported>(repo.importPattern(picked.bytes.decodeToString()))
        assertEquals(pattern.charts, imported.pattern.charts)
        assertEquals(pattern.notes, imported.pattern.notes)
        assertContentEquals(photo, repo.image(imported.pattern.imageIds.single()))

        NSFileManager.defaultManager.removeItemAtURL(url, null)
    }

    @Test fun byteConversionIsExact() {
        for (size in listOf(0, 1, 7, 65_536, 1_000_003)) {
            val bytes = Random(size).nextBytes(size)
            assertContentEquals(bytes, bytes.toNSData().toByteArray())
        }
    }
}
