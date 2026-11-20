package studio.knit.ui.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import studio.knit.ui.chart.ChartEditorScreen
import studio.knit.ui.companion.KnitScreen
import studio.knit.ui.pattern.PatternScreen
import studio.knit.ui.theme.Knit
import studio.knit.ui.tools.ToolScreen
import studio.knit.ui.written.SectionEditorScreen

@Serializable data object MainRoute
@Serializable data class PatternRoute(val id: String, val tab: Int = 0)
@Serializable data class ChartRoute(val patternId: String, val chartId: String, val row: Int = -1, val col: Int = -1)
@Serializable data class SectionRoute(val patternId: String, val sectionId: String, val rowId: String = "")
@Serializable data class KnitRoute(val patternId: String)
@Serializable data class ToolRoute(val tool: String)
@Serializable data class PdfRoute(val patternId: String, val fileId: String)
@Serializable data class ImportRowsRoute(val patternId: String, val fileId: String)

/** Navigation actions, so screens never touch the controller directly. */
class Nav(private val controller: NavHostController) {
    fun back() { controller.popBackStack() }
    fun openPattern(id: String, tab: Int = 0) = controller.navigate(PatternRoute(id, tab)) { launchSingleTop = true }
    fun openChart(patternId: String, chartId: String, row: Int = -1, col: Int = -1) = controller.navigate(ChartRoute(patternId, chartId, row, col))
    fun openSection(patternId: String, sectionId: String, rowId: String = "") = controller.navigate(SectionRoute(patternId, sectionId, rowId))
    fun knit(patternId: String) = controller.navigate(KnitRoute(patternId))
    fun tool(tool: String) = controller.navigate(ToolRoute(tool))
    fun openPdf(patternId: String, fileId: String) = controller.navigate(PdfRoute(patternId, fileId))
    fun openImportRows(patternId: String, fileId: String) = controller.navigate(ImportRowsRoute(patternId, fileId))
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }

private const val DURATION = 300

@Composable
fun KnitNavHost() {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    CompositionLocalProvider(LocalNav provides nav) {
        Box(Modifier.fillMaxSize().background(Knit.colors.background)) {
            NavHost(
                controller, startDestination = MainRoute,
                // The push/pop motion of a native navigation stack.
                enterTransition = { slideInHorizontally(tween(DURATION)) { it } },
                exitTransition = { slideOutHorizontally(tween(DURATION)) { -it / 4 } + fadeOut(tween(DURATION), targetAlpha = 0.6f) },
                popEnterTransition = { slideInHorizontally(tween(DURATION)) { -it / 4 } + fadeIn(tween(DURATION), initialAlpha = 0.6f) },
                popExitTransition = { slideOutHorizontally(tween(DURATION)) { it } },
            ) {
                composable<MainRoute> { MainScreen() }
                composable<PatternRoute> { entry -> entry.toRoute<PatternRoute>().let { PatternScreen(it.id, it.tab) } }
                composable<ChartRoute> { entry -> entry.toRoute<ChartRoute>().let { ChartEditorScreen(it.patternId, it.chartId, it.row, it.col) } }
                composable<SectionRoute> { entry -> entry.toRoute<SectionRoute>().let { SectionEditorScreen(it.patternId, it.sectionId, it.rowId) } }
                composable<KnitRoute> { entry -> KnitScreen(entry.toRoute<KnitRoute>().patternId) }
                composable<ToolRoute> { entry -> ToolScreen(entry.toRoute<ToolRoute>().tool) }
                composable<PdfRoute> { entry -> entry.toRoute<PdfRoute>().let { studio.knit.ui.pattern.PdfScreen(it.patternId, it.fileId) } }
                composable<ImportRowsRoute> { entry -> entry.toRoute<ImportRowsRoute>().let { studio.knit.ui.pattern.ImportRowsScreen(it.patternId, it.fileId) } }
            }
            MessageBanner()
        }
    }
}

/** Transient confirmations ("Copied", "Saved to Files") shown at the top of the screen. */
@Composable
private fun MessageBanner() {
    val app = LocalApp.current
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(app) {
        app.messages.collect { message ->
            text = message
            // Longer messages stay longer, so they can be read.
            delay((2400L + message.length * 45L).coerceAtMost(9000L))
            if (text == message) text = null
        }
    }
    val shown = text ?: return
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding() + 8.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier.clip(RoundedCornerShape(22.dp)).background(if (Knit.colors.isDark) Color(0xFF3A3A3C) else Color(0xF21C1C1E))
                .padding(horizontal = 18.dp, vertical = 11.dp).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(shown, style = Knit.type.subhead, color = Color.White)
        }
    }
}
