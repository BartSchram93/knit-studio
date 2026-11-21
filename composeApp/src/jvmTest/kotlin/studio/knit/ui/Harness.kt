package studio.knit.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import studio.knit.data.KnitRepository
import studio.knit.data.Settings
import studio.knit.data.jvmDriver
import studio.knit.ui.app.AppContainer
import studio.knit.ui.platform.DesktopPlatform
import java.io.File
import javax.imageio.ImageIO

/**
 * Runs the real shared UI headlessly on the JVM against an in-memory database.
 * The window is a 412 × 892 dp phone at 2× density, so screenshots match what a phone shows.
 */
const val SCALE = 2f
const val PHONE_W = 412
const val PHONE_H = 892

class UiEnv(val app: AppContainer, val repo: KnitRepository)

@OptIn(ExperimentalTestApi::class)
fun knitUiTest(settings: Settings = Settings(haptics = false), seed: suspend (KnitRepository) -> Unit = {}, block: ComposeUiTest.(UiEnv) -> Unit) {
    val repo = KnitRepository(jvmDriver(), io = Dispatchers.Unconfined, files = studio.knit.data.JvmFileStore(java.nio.file.Files.createTempDirectory("knit-ui-files").toFile()))
    runBlocking { repo.updateSettings { settings }; seed(repo) }
    val app = AppContainer(repo, Dispatchers.Unconfined, settings)
    DesktopPlatform.written.clear()
    DesktopPlatform.nextFile = null
    DesktopPlatform.nextImage = null
    // Lifecycle and navigation insist on "the main thread"; in a headless test that is this thread.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    Dispatchers.setMain(Dispatchers.Unconfined)
    try { runUi(app, repo, block) } finally {
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun runUi(app: AppContainer, repo: KnitRepository, block: ComposeUiTest.(UiEnv) -> Unit) {
    runDesktopComposeUiTest(width = (PHONE_W * SCALE).toInt(), height = (PHONE_H * SCALE).toInt()) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(SCALE, 1f),
                // Navigation needs a started lifecycle, which a real Activity / UIViewController provides.
                androidx.lifecycle.compose.LocalLifecycleOwner provides ResumedOwner,
            ) { App(app) }
        }
        waitForIdle()
        block(UiEnv(app, repo))
    }
}

private val shotDir = File("build/screenshots").also { it.mkdirs() }

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.shot(name: String) {
    waitForIdle()
    // Capture the whole scene so sheets, menus and dialogs (separate roots) are included.
    val image = (this as androidx.compose.ui.test.SkikoComposeUiTest).captureToImage()
    ImageIO.write(image.toAwtImage(), "png", File(shotDir, "$name.png"))
}

private object ResumedOwner : androidx.lifecycle.LifecycleOwner {
    private val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).also { it.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
}


/**
 * Retries [check] until it passes or [timeoutMs] is over, then reports the last failure.
 * Use this instead of a fixed delay when waiting for work on other threads (database writes).
 */
fun <T> eventually(timeoutMs: Long = 10_000, check: () -> T): T {
    val end = System.currentTimeMillis() + timeoutMs
    while (true) {
        try { return check() } catch (e: Throwable) {
            if (System.currentTimeMillis() > end) throw e
            Thread.sleep(20)
        }
    }
}
