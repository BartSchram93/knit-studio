package studio.knit.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import studio.knit.stats.DayPoint
import studio.knit.stats.Stats
import studio.knit.stats.dayKey
import studio.knit.stats.formatDuration
import studio.knit.stats.statsOf
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.Group
import studio.knit.ui.components.Icons
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.Page
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Segmented
import studio.knit.ui.theme.Knit
import studio.knit.util.nowMillis
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

private val WEEKDAYS = listOf("M", "T", "W", "T", "F", "S", "S")

/** A row of figures: big number with a small label under it. */
@Composable
fun StatTiles(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in items.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((value, label) in row) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Knit.colors.surface).padding(14.dp)) {
                        Text(value, style = Knit.type.title2, color = Knit.colors.label, maxLines = 1)
                        Text(label, style = Knit.type.footnote, color = Knit.colors.secondaryLabel, maxLines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Rows or minutes per day as bars; today's bar in the accent colour. */
@Composable
fun DayBars(points: List<DayPoint>, showTime: Boolean, modifier: Modifier = Modifier) {
    val values = points.map { if (showTime) it.seconds / 60f else it.rows.toFloat() }
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val accent = Knit.colors.accent
    val muted = Knit.colors.accent.copy(alpha = 0.45f)
    val track = Knit.colors.fill
    val labelColor = Knit.colors.secondaryLabel
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    // The day shown in full above the bars: tapped, or else the most recent day with knitting.
    var picked by androidx.compose.runtime.remember(points) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val shown = picked ?: points.indexOfLast { it.rows > 0 || it.seconds >= 60 }.takeIf { it >= 0 }
    val description = points.joinToString(", ") { p ->
        "${p.day}: " + if (showTime) formatDuration(p.seconds) else "${p.rows} rows"
    }
    Column(modifier.padding(horizontal = PagePadding).clip(RoundedCornerShape(14.dp)).background(Knit.colors.surface).padding(14.dp)) {
        Text(
            shown?.let { dayLine(points[it]) } ?: "No knitting in these days yet",
            style = Knit.type.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Knit.colors.label,
        )
        Text("Tap a day to see it", style = Knit.type.caption, color = Knit.colors.secondaryLabel)
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier.fillMaxWidth().height(136.dp).semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { pos -> picked = ((pos.x / size.width) * points.size).toInt().coerceIn(0, points.size - 1) }
                },
        ) {
            val n = values.size.coerceAtLeast(1)
            val gap = size.width / n * 0.28f
            val w = size.width / n - gap
            val top = 18.dp.toPx() // room for the numbers above the bars
            val chartH = size.height - top
            val style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = labelColor)
            values.forEachIndexed { i, v ->
                val x = i * (w + gap) + gap / 2
                drawRoundRect(track, Offset(x, top), Size(w, chartH), CornerRadius(w / 3, w / 3))
                val h = if (v <= 0f) 0f else (v / max * chartH).coerceAtLeast(w / 2)
                val selected = i == shown
                if (h > 0f) drawRoundRect(if (selected) accent else muted, Offset(x, top + chartH - h), Size(w, h), CornerRadius(w / 3, w / 3))
                if (v > 0f) {
                    val text = if (showTime) shortDuration(points[i].seconds) else points[i].rows.toString()
                    val layout = measurer.measure(text, style.copy(color = if (selected) accent else labelColor), maxLines = 1)
                    drawText(layout, topLeft = Offset(x + (w - layout.size.width) / 2f, (top + chartH - h - layout.size.height - 2f).coerceAtLeast(0f)))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row {
            for (p in points) Text(
                if (points.size <= 14) WEEKDAYS[p.day.dayOfWeek.ordinal] else "",
                style = Knit.type.caption, color = Knit.colors.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
        }
    }
}

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Saturday 21 Nov · 12 rows · 1 h 05 min". */
fun dayLine(p: DayPoint): String =
    "${DAY_NAMES[p.day.dayOfWeek.ordinal]} ${p.day.day} ${MONTHS_SHORT[p.day.month.ordinal]} · ${p.rows} ${if (p.rows == 1) "row" else "rows"}" +
        if (p.seconds >= 60) " · ${formatDuration(p.seconds)}" else ""

/** "45m", "1h05" — short enough to sit above a bar. */
private fun shortDuration(seconds: Long): String {
    val m = seconds / 60
    return if (m < 60) "${m}m" else "${m / 60}h" + (m % 60).toString().padStart(2, '0')
}

/** Every knitting day in [points], newest first, with rows and time. */
@Composable
fun PerDayList(points: List<DayPoint>) {
    val days = points.filter { it.rows > 0 || it.seconds >= 60 }.asReversed()
    if (days.isEmpty()) return
    SectionHeader("Per day")
    Group {
        days.forEachIndexed { i, p ->
            if (i > 0) RowDivider()
            ListRow(
                "${DAY_NAMES[p.day.dayOfWeek.ordinal]} ${p.day.day} ${MONTHS_SHORT[p.day.month.ordinal]}",
                value = "${p.rows} ${if (p.rows == 1) "row" else "rows"}",
                subtitle = if (p.seconds >= 60) formatDuration(p.seconds) else "time not measured",
            )
        }
    }
}

/** The common statistics block, used on a pattern and on the overview. */
@Composable
fun StatsBlock(stats: Stats) {
    var view by rememberSaveable { mutableIntStateOf(0) }
    StatTiles(listOfNotNull(
        formatDuration(stats.totalSeconds) to "knitted in total",
        "${stats.totalRows}" to "rows",
        "${stats.knittingDays}" to if (stats.knittingDays == 1) "day knitted" else "days knitted",
        (stats.rowsPerKnittingDay?.let { "${kotlin.math.round(it).toInt()}" } ?: "—") to "rows per knitting day",
        (stats.rowsPerHour?.let { "${kotlin.math.round(it).toInt()}" } ?: "—") to "rows per hour",
        "${stats.streak}" to if (stats.streak == 1) "day in a row" else "days in a row",
    ))
    SectionHeader("Last 14 days")
    Segmented(listOf("Rows", "Time"), view, { view = it }, Modifier.padding(horizontal = PagePadding).padding(bottom = 10.dp).fillMaxWidth())
    DayBars(stats.recent, showTime = view == 1)
    PerDayList(stats.recent)
    SectionFooter(
        "Time counts while the knitting or PDF screen is open, and pauses after 20 minutes without touching the screen." +
            (stats.firstDay?.let { " First knitted on $it." } ?: ""),
    )
}

/** Tools › Knitting stats: everything across all patterns. */
@Composable
fun StatsScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val patterns by app.patterns.collectAsState()
    val achievementFacts by app.achievements.facts.collectAsState()
    val today = remember { dayKey(nowMillis()) }
    val all = patterns.orEmpty()
    val stats = remember(all, today) { statsOf(all.mapNotNull { it.progress?.days }, today) }
    Page("Knitting stats", onBack = nav::back, backLabel = "Tools", largeTitle = false) {
        if (stats.isEmpty) {
            item { EmptyState(Icons.Counter, "No knitting logged yet", "Open a pattern and tap Start knitting, or open a PDF. Time and rows are counted from then on.") }
            return@Page
        }
        item { SectionHeader("This week") }
        item { StatTiles(listOf(formatDuration(stats.weekSeconds) to "knitted in the last 7 days", "${stats.weekRows}" to "rows in the last 7 days")) }
        item { SectionHeader("All time") }
        item { StatsBlock(stats) }
        achievementFacts?.let { f ->
            item { SectionHeader("Personal records") }
            item {
                Group {
                    ListRow("Most rows in one day", value = "${f.maxRowsInDay}")
                    RowDivider()
                    ListRow("Longest session", value = if (f.longestSession > 0) formatDuration(f.longestSession) else "—", subtitle = "Without a break of 20 minutes")
                    RowDivider()
                    ListRow("Longest streak", value = "${f.bestStreak} ${if (f.bestStreak == 1) "day" else "days"}", subtitle = "One rest day a week allowed")
                    RowDivider()
                    ListRow("Stitches knitted", value = studio.knit.achieve.formatCount(f.totalStitches), subtitle = "In rows the app could count")
                    val fastest = all.mapNotNull { p -> p.progress?.let { pr -> pr.finishedAt?.let { (it - pr.startedAt) / 86_400_000L } } }.minOrNull()
                    if (fastest != null) { RowDivider(); ListRow("Fastest finished project", value = if (fastest < 1) "under a day" else "$fastest ${if (fastest == 1L) "day" else "days"}") }
                }
            }
            item {
                Group(Modifier.padding(top = 12.dp)) {
                    ListRow("Achievements", subtitle = "Your level, badges and hidden surprises", leading = { Text("🏅", style = Knit.type.title3) }, chevron = true) { nav.tool("achievements") }
                    RowDivider()
                    ListRow("Your knitting year", subtitle = "This year on one page", leading = { Text("📖", style = Knit.type.title3) }, chevron = true) { nav.tool("year") }
                }
            }
        }
        val perPattern = all.mapNotNull { p ->
            val s = statsOf(listOfNotNull(p.progress?.days), today)
            if (s.isEmpty) null else p to s
        }.sortedByDescending { it.second.totalSeconds }
        item { SectionHeader("Per pattern") }
        item {
            Group {
                perPattern.forEachIndexed { i, (p, s) ->
                    if (i > 0) RowDivider()
                    ListRow(p.displayName, subtitle = "${s.totalRows} rows  ·  ${s.knittingDays} ${if (s.knittingDays == 1) "day" else "days"}",
                        value = formatDuration(s.totalSeconds), chevron = true) { nav.openPattern(p.id) }
                }
            }
        }
    }
}
