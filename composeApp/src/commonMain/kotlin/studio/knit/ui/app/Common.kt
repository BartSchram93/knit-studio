package studio.knit.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import studio.knit.calc.UnitFormat
import studio.knit.companion.overallProgress
import studio.knit.companion.statusLine
import studio.knit.model.Category
import studio.knit.model.Pattern
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.chart.ChartThumbnail
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.theme.Knit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@OptIn(ExperimentalTime::class)
private fun localDate(millis: Long): Triple<Int, Int, Int> {
    // LocalDate prints as ISO yyyy-MM-dd on every platform.
    val iso = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    val parts = iso.split('-')
    return Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

/** "Today", "Yesterday" or "14 Nov 2026". */
fun formatDate(millis: Long, now: Long): String {
    if (millis <= 0) return "—"
    val d = localDate(millis)
    if (d == localDate(now)) return "Today"
    if (d == localDate(now - 86_400_000L)) return "Yesterday"
    val sameYear = d.first == localDate(now).first
    return "${d.third} ${MONTHS[d.second - 1]}" + if (sameYear) "" else " ${d.first}"
}

@Composable
fun unitFormat(): UnitFormat {
    val settings by LocalApp.current.settings.collectAsState()
    return remember(settings.imperial) { UnitFormat(settings.imperial) }
}

/** Light tick used when painting cells and stepping counters; silent when haptics are off. */
@Composable
fun rememberHaptic(): (strong: Boolean) -> Unit {
    val haptics = LocalHapticFeedback.current
    val app = LocalApp.current
    return remember(haptics, app) {
        { strong ->
            if (app.settings.value.haptics) {
                haptics.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

// Warm, earthy tile colours per category: terracotta, sage, mustard, lavender, ocean, rose, …
private val TILE_COLORS = listOf(0xFFC2603F, 0xFF6F9068, 0xFFC99332, 0xFF8C7BB8, 0xFF3F7E9C, 0xFFC8637F, 0xFF9C6644, 0xFF7A8B4F, 0xFF8A776A)

fun categoryColor(category: Category): Color = Color(TILE_COLORS[category.ordinal % TILE_COLORS.size])

/** The picture that represents a pattern in lists: its first chart, or a quiet category tile. */
@Composable
fun PatternThumb(pattern: Pattern, library: StitchLibrary, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val chart = pattern.charts.firstOrNull { it.cells.size == it.cols * it.rows }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.2f)).background(if (chart != null) Knit.colors.chartPaper else categoryColor(pattern.category).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (chart != null) {
            ChartThumbnail(chart, library, Modifier.fillMaxSize().padding(size * 0.08f))
        } else {
            Text(
                pattern.category.label.take(1), style = Knit.type.title2.copy(fontSize = Knit.type.title2.fontSize * (size.value / 56f)),
                color = categoryColor(pattern.category),
            )
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Knit.colors.fill)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(Knit.colors.accent))
    }
}

/** A library row: thumbnail, name, one line of context and status marks. */
@Composable
fun PatternRowContent(pattern: Pattern, library: StitchLibrary, now: Long, modifier: Modifier = Modifier) {
    val status = remember(pattern, library) { statusLine(pattern, library) }
    val fraction = remember(pattern, library) { overallProgress(pattern, library) }
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PatternThumb(pattern, library)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pattern.displayName, style = Knit.type.headline, color = Knit.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (pattern.favorite) { Spacer(Modifier.width(5.dp)); KIcon(Icons.StarFilled, Knit.colors.accent, 14.dp, description = "Favourite") }
            }
            val meta = buildList {
                add(pattern.category.label)
                if (pattern.files.isNotEmpty()) add("PDF")
                if (pattern.archived) add("Archived") else if (pattern.draft && pattern.progress == null) add("Draft")
                add(formatDate(pattern.updatedAt, now))
            }.joinToString("  ·  ")
            val knitted = remember(pattern.progress?.days) { pattern.progress?.days?.values?.sumOf { it.seconds } ?: 0L }
            val line = listOfNotNull(status ?: meta, knitted.takeIf { it >= 60 }?.let { studio.knit.stats.formatDuration(it) }).joinToString("  ·  ")
            Text(line, style = Knit.type.footnote, color = Knit.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (fraction != null && pattern.progress?.finishedAt == null) {
                Spacer(Modifier.height(6.dp))
                ProgressBar(fraction)
            }
        }
        Spacer(Modifier.width(6.dp))
        KIcon(Icons.ChevronRight, Knit.colors.tertiaryLabel, 16.dp)
    }
}

/** "Size S" for a size called "S", but just "Size 1" for one already called "Size 1". */
fun sizeLabel(name: String): String = if (name.trim().startsWith("size", ignoreCase = true)) name.trim() else "Size $name"
