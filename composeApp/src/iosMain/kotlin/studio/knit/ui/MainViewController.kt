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

/**
 * Implemented in Swift (LiveActivityController): shows the current row as a Live Activity on
 * the lock screen. Plain parameters, so Swift sees simple types.
 */
interface LockScreenPresenter {
    fun show(
        patternId: String, patternName: String, part: String, row: String, instruction: String,
        detail: String, today: String, fraction: Double, finished: Boolean, canGoBack: Boolean,
    )
    fun clear()
}

/** Called once from Swift at launch (iOS 17 and later). */
fun setLockScreenPresenter(presenter: LockScreenPresenter) {
    container.lockScreen = object : studio.knit.ui.app.LockScreenKnitting {
        override fun show(glance: studio.knit.companion.KnitGlance) = presenter.show(
            glance.patternId, glance.patternName, glance.part, glance.row, glance.instruction,
            glance.detail, glance.today, glance.fraction?.toDouble() ?: -1.0, glance.finished, glance.canGoBack,
        )
        override fun clear() = presenter.clear()
    }
}

/** Back / Next pressed on the Live Activity; [done] is called once the change is saved. */
fun stepFromLockScreen(patternId: String, delta: Int, done: () -> Unit) =
    container.stepFromLockScreen(patternId, delta.coerceIn(-1, 1), done)
