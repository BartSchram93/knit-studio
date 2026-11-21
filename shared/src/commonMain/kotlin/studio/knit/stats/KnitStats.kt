package studio.knit.stats

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import studio.knit.model.DayLog
import studio.knit.model.Pattern
import studio.knit.model.Progress
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Knitting statistics. Every row tapped and every minute of active knitting is logged per
 * calendar day in the pattern's progress, so the numbers travel with backups and .knit files.
 */

/** Local calendar day for a moment, as stored in [Progress.days]: "2026-11-20". */
@OptIn(ExperimentalTime::class)
fun dayKey(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date.toString()

private fun Progress.withDay(day: String, transform: (DayLog) -> DayLog): Progress {
    val next = transform(days[day] ?: DayLog())
    return copy(days = if (next.rows == 0 && next.seconds == 0L) days - day else days + (day to next))
}

/**
 * Records [delta] rows on [day] (and in [hour] of that day, when given). Going back a row takes
 * it off again, never below zero, and is counted in [Progress.undone].
 */
fun Progress.logRows(day: String, delta: Int, hour: Int? = null): Progress {
    if (delta == 0) return this
    val logged = withDay(day) { d ->
        val hours = if (hour == null) d.hours else {
            val n = ((d.hours[hour] ?: 0) + delta).coerceAtLeast(0)
            if (n == 0) d.hours - hour else d.hours + (hour to n)
        }
        d.copy(rows = (d.rows + delta).coerceAtLeast(0), hours = hours)
    }
    return if (delta < 0) logged.copy(undone = undone - delta) else logged
}

/** Hour of the day (0–23) and minute for a moment, in the device's time zone. */
@OptIn(ExperimentalTime::class)
fun hourMinute(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Pair<Int, Int> =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).let { it.hour to it.minute }

/** Logs rows knitted at [now]: day, hour, and a "midnight" moment for a row done at 23:59–00:00. */
fun Progress.logRowsAt(now: Long, delta: Int): Progress {
    val (h, m) = hourMinute(now)
    val logged = logRows(dayKey(now), delta, h)
    return if (delta > 0 && ((h == 23 && m == 59) || (h == 0 && m == 0))) logged.copy(moments = logged.moments + "midnight") else logged
}

/** Records [seconds] of active knitting on [day]. */
fun Progress.logTime(day: String, seconds: Long): Progress =
    if (seconds <= 0) this else withDay(day) { it.copy(seconds = it.seconds + seconds) }

data class DayPoint(val day: LocalDate, val rows: Int, val seconds: Long)

data class Stats(
    val totalSeconds: Long,
    val totalRows: Int,
    /** Days with any knitting logged. */
    val knittingDays: Int,
    val firstDay: LocalDate?,
    val lastDay: LocalDate?,
    /** Average rows on the days she knitted. */
    val rowsPerKnittingDay: Double?,
    /** Rows per hour of logged time, when there is at least a few minutes of it. */
    val rowsPerHour: Double?,
    /** Consecutive days up to today (or yesterday) with knitting. */
    val streak: Int,
    val todaySeconds: Long,
    val todayRows: Int,
    val weekSeconds: Long,
    val weekRows: Int,
    /** The last [daysShown] days, oldest first, including empty days. */
    val recent: List<DayPoint>,
) {
    val isEmpty: Boolean get() = totalSeconds == 0L && totalRows == 0
}

/** Statistics over the logs of one or more patterns. [today] is "yyyy-MM-dd". */
fun statsOf(logs: List<Map<String, DayLog>>, today: String, daysShown: Int = 14): Stats {
    val merged = HashMap<String, DayLog>()
    for (log in logs) for ((day, entry) in log) {
        val cur = merged[day] ?: DayLog()
        merged[day] = DayLog(cur.rows + entry.rows, cur.seconds + entry.seconds)
    }
    val dated = merged.mapNotNull { (k, v) -> runCatching { LocalDate.parse(k) }.getOrNull()?.let { it to v } }.sortedBy { it.first }
    val todayDate = LocalDate.parse(today)
    val totalSeconds = dated.sumOf { it.second.seconds }
    val totalRows = dated.sumOf { it.second.rows }
    val active = dated.filter { it.second.rows > 0 || it.second.seconds > 0 }
    val weekStart = todayDate.minus(DatePeriod(days = 6))
    val week = dated.filter { it.first >= weekStart && it.first <= todayDate }

    var streak = 0
    var cursor = if (merged[today]?.let { it.rows > 0 || it.seconds > 0 } == true) todayDate else todayDate.minus(DatePeriod(days = 1))
    val activeDays = active.mapTo(HashSet()) { it.first }
    while (cursor in activeDays) { streak++; cursor = cursor.minus(DatePeriod(days = 1)) }

    val recent = (daysShown - 1 downTo 0).map { back ->
        val d = todayDate.minus(DatePeriod(days = back))
        val e = merged[d.toString()]
        DayPoint(d, e?.rows ?: 0, e?.seconds ?: 0)
    }
    return Stats(
        totalSeconds = totalSeconds, totalRows = totalRows, knittingDays = active.size,
        firstDay = active.firstOrNull()?.first, lastDay = active.lastOrNull()?.first,
        rowsPerKnittingDay = if (active.isNotEmpty() && totalRows > 0) totalRows.toDouble() / active.count { it.second.rows > 0 }.coerceAtLeast(1) else null,
        rowsPerHour = if (totalSeconds >= 5 * 60 && totalRows > 0) totalRows / (totalSeconds / 3600.0) else null,
        streak = streak,
        todaySeconds = merged[today]?.seconds ?: 0, todayRows = merged[today]?.rows ?: 0,
        weekSeconds = week.sumOf { it.second.seconds }, weekRows = week.sumOf { it.second.rows },
        recent = recent,
    )
}

fun Pattern.stats(today: String, daysShown: Int = 14): Stats = statsOf(listOfNotNull(progress?.days), today, daysShown)

/** "2 h 05 min", "42 min", "under a minute". */
fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    return when {
        seconds <= 0 -> "0 min"
        minutes == 0L -> "under a minute"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
    }
}
