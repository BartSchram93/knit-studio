package studio.knit.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A scrolling row of chips that can be rearranged: touch and hold a chip, then drag it left or
 * right. [onMove] gets the old and new position when it is let go.
 */
@Composable
fun <T> ReorderableChipRow(
    items: List<T>, key: (T) -> String, onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier, onDragStart: () -> Unit = {},
    chip: @Composable (item: T, dragging: Boolean) -> Unit,
) {
    val widths = remember { mutableStateMapOf<String, Int>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val spacing = with(LocalDensity.current) { 8.dp.toPx() }
    val current by rememberUpdatedState(items)

    /** Where the dragged chip lands, from how far it has been moved. */
    fun target(from: Int): Int {
        var to = from
        var travelled = 0f
        if (offset > 0) {
            for (i in from + 1 until current.size) {
                val w = (widths[key(current[i])] ?: 0) + spacing
                if (offset > travelled + w / 2) { to = i; travelled += w } else break
            }
        } else {
            for (i in from - 1 downTo 0) {
                val w = (widths[key(current[i])] ?: 0) + spacing
                if (-offset > travelled + w / 2) { to = i; travelled += w } else break
            }
        }
        return to
    }

    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (item in items) {
            val k = key(item)
            val isDragged = dragging == k
            Box(
                Modifier.onSizeChanged { widths[k] = it.width }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        translationX = if (isDragged) offset else 0f
                        val s = if (isDragged) 1.08f else 1f
                        scaleX = s; scaleY = s
                    }
                    .pointerInput(k) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = k; offset = 0f; onDragStart() },
                            onDrag = { change, amount -> change.consume(); offset += amount.x },
                            onDragEnd = {
                                val from = current.indexOfFirst { key(it) == k }
                                if (from >= 0) { val to = target(from); if (to != from) onMove(from, to) }
                                dragging = null; offset = 0f
                            },
                            onDragCancel = { dragging = null; offset = 0f },
                        )
                    },
            ) { chip(item, isDragged) }
        }
    }
}
