package studio.knit.achieve

import kotlinx.datetime.LocalDate
import studio.knit.model.Category
import studio.knit.model.Pattern
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** A knitting year at a glance, for the "Your knitting year" page. */
data class YearReview(
    val year: Int,
    val seconds: Long,
    val rows: Long,
    val knitDays: Int,
    val projectsFinished: List<String>,
    /** Month (1–12) with the most rows, and its rows. */
    val busiestMonth: Pair<Int, Long>?,
    val bestStreak: Int,
    /** The pattern she spent the most time on, and that time. */
    val favouritePattern: Pair<String, Long>?,
    val favouriteCategory: Category?,
    val bestDay: Pair<LocalDate, Int>?,
) {
    val isEmpty: Boolean get() = rows == 0L && seconds < 60
}

@OptIn(ExperimentalTime::class)
fun yearReview(patterns: List<Pattern>, year: Int, zone: TimeZone = TimeZone.currentSystemDefault()): YearReview {
    val rowsByDay = HashMap<LocalDate, Int>()
    val secondsByDay = HashMap<LocalDate, Long>()
    val timeByPattern = HashMap<Pattern, Long>()
    for (p in patterns) for ((key, log) in p.progress?.days.orEmpty()) {
        val day = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
        if (day.year != year) continue
        rowsByDay[day] = (rowsByDay[day] ?: 0) + log.rows
        secondsByDay[day] = (secondsByDay[day] ?: 0) + log.seconds
        timeByPattern[p] = (timeByPattern[p] ?: 0) + log.seconds + log.rows * 30L // rows count a little when time was not measured
    }
    val days = (rowsByDay.keys + secondsByDay.keys).filter { (rowsByDay[it] ?: 0) > 0 || (secondsByDay[it] ?: 0) >= 60 }.distinct().sorted()
    val finished = patterns.filter { p ->
        p.progress?.finishedAt?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).year == year } == true
    }
    val byMonth = rowsByDay.entries.groupBy { it.key.month.ordinal + 1 }.mapValues { e -> e.value.sumOf { it.value.toLong() } }
    val favourite = timeByPattern.maxByOrNull { it.value }
    val categories = (finished.map { it.category } + timeByPattern.keys.map { it.category }).filter { it != Category.OTHER }
    return YearReview(
        year = year,
        seconds = secondsByDay.values.sum(),
        rows = rowsByDay.values.sumOf { it.toLong() },
        knitDays = days.size,
        projectsFinished = finished.map { it.displayName },
        busiestMonth = byMonth.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.toPair(),
        bestStreak = FactsBuilder.streaks(days, null).first,
        favouritePattern = favourite?.let { it.key.displayName to it.value },
        favouriteCategory = categories.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
        bestDay = rowsByDay.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.toPair(),
    )
}

val MONTH_NAMES = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
