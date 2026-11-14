package studio.knit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import studio.knit.ui.app.AppContainer
import studio.knit.ui.app.KnitNavHost
import studio.knit.ui.app.LocalApp
import studio.knit.ui.theme.KnitTheme

/** Root of the shared user interface. Each platform creates the [AppContainer] and calls this. */
@Composable
fun App(app: AppContainer) {
    val settings by app.settings.collectAsState()
    CompositionLocalProvider(LocalApp provides app) {
        KnitTheme(settings.theme) { KnitNavHost() }
    }
}
