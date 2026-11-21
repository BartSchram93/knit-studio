package studio.knit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import studio.knit.model.Pattern
import studio.knit.stats.dayKey
import studio.knit.stats.formatDuration
import studio.knit.ui.theme.Knit
import kotlin.time.Clock

/**
 * Rows of knit stitches ("V"s), the way stockinette looks, as a soft background texture. The
 * stitches fade out towards [fadeTo] so text on top stays readable.
 */
@Composable
fun KnitTexture(modifier: Modifier = Modifier, color: Color = Knit.colors.accent, stitch: Dp = 22.dp, fadeFrom: Float = 0.35f) {
    Canvas(modifier) {
        val w = stitch.toPx()
        val h = w * 0.9f
        val stroke = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        var y = -h * 0.3f
        var row = 0
        while (y < size.height + h) {
            var x = -w / 2f + (if (row % 2 == 0) 0f else 0f)
            while (x < size.width + w) {
                // Stitches fade in from the left so the greeting has a calm background.
                val t = ((x / size.width) - fadeFrom).coerceIn(0f, 1f) / (1f - fadeFrom)
                if (t > 0f) {
                    val c = color.copy(alpha = 0.22f * t)
                    val path = Path().apply {
                        moveTo(x + w * 0.12f, y)
                        lineTo(x + w * 0.5f, y + h * 0.78f)
                        lineTo(x + w * 0.88f, y)
                    }
                    drawPath(path, c, style = stroke)
                }
                x += w
            }
            y += h * 0.62f
            row++
        }
    }
}

/** "Good morning", "Good afternoon" or "Good evening" for the device's local time. */
fun greetingNow(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> "Knitting late tonight?"
    }
}

/** What the knitter did today across all patterns, e.g. "Today: 24 rows · 1 h 05 min". */
fun todayLine(patterns: List<Pattern>, now: Long): String? {
    val day = dayKey(now)
    val logs = patterns.mapNotNull { it.progress?.days?.get(day) }
    val rows = logs.sumOf { it.rows }
    val seconds = logs.sumOf { it.seconds }
    if (rows == 0 && seconds < 60) return null
    return "Today: $rows ${if (rows == 1) "row" else "rows"}" + if (seconds >= 60) " · ${formatDuration(seconds)}" else ""
}

/** The warm card at the top of the home screen: a greeting on a knitted texture. */
@Composable
fun GreetingCard(title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Knit.colors.accentSoft)) {
        KnitTexture(Modifier.matchParentSize())
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(title, style = Knit.type.title2, color = Knit.colors.label)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = Knit.type.subhead, color = Knit.colors.secondaryLabel)
            if (action != null) { Spacer(Modifier.height(14.dp)); action() }
        }
    }
}
