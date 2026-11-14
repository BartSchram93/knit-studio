package studio.knit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import studio.knit.model.Pattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs on the iOS simulator. Checks that the iPhone database lives where iOS preserves it across
 * app updates and AltStore refreshes, and that data survives closing and reopening it.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosStorageTest {
    @Test fun databaseLivesInApplicationSupportAndSurvivesReopening(): Unit = runBlocking {
        val name = "storage-test-${kotlin.random.Random.nextInt(1_000_000)}.db"
        val first = iosDriver(name)
        KnitRepository(first, io = Dispatchers.Unconfined).create(Pattern(id = "kept", name = "Kept across updates"))
        first.close()

        val dir = databaseDirectory()
        assertTrue(dir.endsWith("/Library/Application Support/databases"), dir)
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath("$dir/$name"), "database file not found in $dir")

        // Reopening is what happens after an app update: same container, same file.
        val second = iosDriver(name)
        val repo = KnitRepository(second, io = Dispatchers.Unconfined)
        assertEquals("Kept across updates", repo.getPattern("kept")?.name)
        assertTrue(repo.integrityCheck().isEmpty())
        second.close()
        NSFileManager.defaultManager.removeItemAtPath("$dir/$name", null)
    }
}
