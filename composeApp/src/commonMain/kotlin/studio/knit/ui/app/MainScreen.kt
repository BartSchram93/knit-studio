package studio.knit.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.LocalBottomInset
import studio.knit.ui.components.pressable
import studio.knit.ui.home.HomeScreen
import studio.knit.ui.library.LibraryScreen
import studio.knit.ui.settings.SettingsScreen
import studio.knit.ui.theme.Knit
import studio.knit.ui.tools.ToolsScreen

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Home", Icons.Home), Tab("Patterns", Icons.Library), Tab("Tools", Icons.Tools), Tab("Settings", Icons.Settings),
)
private val TAB_BAR_HEIGHT = 54.dp

/** The four top-level destinations behind a bottom tab bar. */
@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomInset provides TAB_BAR_HEIGHT) {
            when (tab) {
                0 -> HomeScreen(onSeeAll = { tab = 1 })
                1 -> LibraryScreen()
                2 -> ToolsScreen()
                else -> SettingsScreen()
            }
        }
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Knit.colors.surface.copy(alpha = 0.97f))) {
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(Knit.colors.separator))
            Row(Modifier.fillMaxWidth().height(TAB_BAR_HEIGHT), horizontalArrangement = Arrangement.SpaceAround) {
                TABS.forEachIndexed { i, t ->
                    val on = i == tab
                    val color = if (on) Knit.colors.accent else Knit.colors.secondaryLabel
                    Column(
                        Modifier.weight(1f).fillMaxSize().pressable(role = Role.Tab) { tab = i }.semantics { selected = on },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        KIcon(t.icon, color, 24.dp)
                        Spacer(Modifier.height(2.dp))
                        Text(t.label, style = Knit.type.caption.copy(fontSize = Knit.type.caption.fontSize * 0.9f), color = color, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(insets.calculateBottomPadding()))
        }
    }
}
