package studio.knit.ui.companion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import studio.knit.companion.startProgress
import studio.knit.stats.dayKey
import studio.knit.stats.logTime
import studio.knit.ui.app.PatternEditor
import studio.knit.util.nowMillis

/**
 * Measures active knitting time while a knitting screen is open.
 *
 * Time counts only while the app is in the foreground and the knitter has touched the screen
 * within the last [IDLE_LIMIT_MS] (knitting a long row without tapping is normal; leaving the
 * phone for an hour is not). It is added to the pattern's log per calendar day.
 */
@Stable
class KnitTimer internal constructor() {
    internal var lastActivity by mutableStateOf(nowMillis())
    var paused by mutableStateOf(false)
        internal set

    /** Call on any interaction; also resumes a paused timer. */
    fun touch() { lastActivity = nowMillis(); paused = false }

    companion object {
        const val IDLE_LIMIT_MS = 20 * 60 * 1000L
        const val TICK_MS = 15_000L
    }
}

@Composable
fun rememberKnitTimer(editor: PatternEditor): KnitTimer {
    val timer = remember(editor) { KnitTimer() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val foreground = state.isAtLeast(Lifecycle.State.RESUMED)

    fun record(from: Long, to: Long) {
        // Never count time beyond the idle limit after the last interaction.
        val end = minOf(to, timer.lastActivity + KnitTimer.IDLE_LIMIT_MS)
        val seconds = (end - from) / 1000
        if (seconds <= 0) return
        editor.update { p -> p.copy(progress = (p.progress ?: startProgress(p, from)).logTime(dayKey(from), seconds)) }
    }

    LaunchedEffect(timer, foreground) {
        if (!foreground) return@LaunchedEffect
        timer.touch() // coming back to the app counts as activity
        var last = nowMillis()
        try {
            while (true) {
                delay(KnitTimer.TICK_MS)
                val now = nowMillis()
                timer.paused = now - timer.lastActivity > KnitTimer.IDLE_LIMIT_MS
                record(last, now)
                last = now
            }
        } finally {
            // Leaving the screen or the app: keep the seconds since the last tick.
            record(last, nowMillis())
        }
    }
    DisposableEffect(timer) { onDispose { editor.flush() } }
    return timer
}

/** Marks every touch on the screen as activity, without interfering with it. */
fun Modifier.knitActivity(timer: KnitTimer): Modifier = pointerInput(timer) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            timer.touch()
        }
    }
}
