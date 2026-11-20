package studio.knit.ui

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIViewController
import studio.knit.data.KnitRepository
import studio.knit.data.iosDriver
import studio.knit.ui.app.AppContainer

/** App-wide state for the iOS process; created on first use. */
private val container: AppContainer by lazy {
    val repo = KnitRepository(iosDriver(), files = studio.knit.data.IosFileStore())
    AppContainer(repo, initialSettings = runBlocking { repo.settingsNow() })
}

/** Entry point called from Swift: hosts the shared Compose UI in a UIViewController. */
fun MainViewController(): UIViewController = ComposeUIViewController { App(container) }

/** Called from Swift when the app moves to the background so pending edits are written. */
fun flushPendingChanges() = container.flushAll()
