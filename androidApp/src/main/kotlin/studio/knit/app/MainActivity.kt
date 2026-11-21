package studio.knit.app

import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.runBlocking
import studio.knit.data.KnitRepository
import studio.knit.data.ThemeMode
import studio.knit.data.androidDriver
import studio.knit.ui.App
import studio.knit.ui.app.AppContainer

/** Owns the app-wide state so it survives activity recreation (rotation, theme change). */
class KnitApplication : Application() {
    val container: AppContainer by lazy {
        val repo = KnitRepository(androidDriver(this), files = studio.knit.data.AndroidFileStore(this))
        // Read the settings before the first frame so the chosen theme is there from the start.
        val settings = runBlocking { repo.settingsNow() }
        AppContainer(repo, initialSettings = settings).also { it.lockScreen = lockScreen }
    }
    val lockScreen by lazy { AndroidLockScreen(this) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as KnitApplication
        val container = app.container
        // Android 13+ asks before showing notifications; asked the first time Knit mode opens.
        val askNotifications = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) container.message("Without notifications the row cannot be shown on the lock screen. You can allow them in Android settings.")
        }
        app.lockScreen.onPermissionNeeded = {
            if (android.os.Build.VERSION.SDK_INT >= 33) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // The activity is recreated when the system theme changes, so this stays current.
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        setContent {
            val settings by container.settings.collectAsState()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app's own light/dark choice.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            App(container)
        }
    }

    override fun onStop() {
        // Make sure nothing typed in the last moments is lost when the app goes to the background.
        (application as KnitApplication).container.flushAll()
        super.onStop()
    }
}
