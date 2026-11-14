package studio.knit.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import studio.knit.data.ImportOutcome
import studio.knit.model.Pattern

/**
 * Starts the real Android application and activity (manifest, theme, Compose content, SQLite
 * driver) under Robolectric, and checks that data written through the Android database driver
 * is read back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = KnitApplication::class)
class AndroidLaunchTest {

    @Test fun activityStartsAndShowsTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertNotNull(activity.window.decorView)
                assertEquals("Knit Studio", activity.applicationInfo.loadLabel(activity.packageManager).toString())
            }
            scenario.recreate()   // rotation / theme change must not lose the container
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }
    }

    @Test fun androidDatabaseStoresAndRestoresPatterns() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<KnitApplication>()
        val repo = app.container.repo
        val saved = repo.create(Pattern(id = "android-1", name = "Saved on Android"))
        assertTrue(saved.updatedAt > 0)
        assertEquals("Saved on Android", repo.getPattern("android-1")?.name)

        // A file produced by the same shared code as on iOS imports through the Android driver.
        val file = repo.exportPattern("android-1")!!
        val imported = repo.importPattern(file) as ImportOutcome.Imported
        assertEquals(2, repo.patterns.first().size)
        assertTrue(imported.pattern.name.endsWith("(imported)"))
        assertTrue(repo.integrityCheck().isEmpty())
    }
}
