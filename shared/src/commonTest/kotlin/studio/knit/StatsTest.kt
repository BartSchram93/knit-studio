package studio.knit

import studio.knit.model.DayLog
import studio.knit.model.Progress
import studio.knit.stats.formatDuration
import studio.knit.stats.logRows
import studio.knit.stats.logTime
import studio.knit.stats.statsOf
import kotlinx.datetime.TimeZone
import studio.knit.stats.dayKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsTest {
    @Test fun rowsAndTimeAreLoggedPerDay() {
        var p = Progress(startedAt = 0)
        p = p.logRows("2026-11-19", 5).logRows("2026-11-20", 3).logRows("2026-11-20", -1).logTime("2026-11-20", 1800)
        assertEquals(DayLog(5, 0), p.days["2026-11-19"])
        assertEquals(DayLog(2, 1800), p.days["2026-11-20"])
        // Going back never makes a day negative, and an emptied day disappears.
        p = p.logRows("2026-11-19", -9)
        assertNull(p.days["2026-11-19"])
    }

    @Test fun totalsAveragesStreakAndRecentDays() {
        val a = mapOf("2026-11-18" to DayLog(10, 3600), "2026-11-19" to DayLog(20, 3600), "2026-11-20" to DayLog(6, 1200))
        val b = mapOf("2026-11-20" to DayLog(4, 600), "2026-11-01" to DayLog(0, 900))
        val s = statsOf(listOf(a, b), today = "2026-11-20", daysShown = 7)
        assertEquals(3600 + 3600 + 1200 + 600 + 900L, s.totalSeconds)
        assertEquals(40, s.totalRows)
        assertEquals(4, s.knittingDays)
        assertEquals(40 / 3.0, s.rowsPerKnittingDay!!, 0.001)       // only days with rows count
        assertEquals(40 / (9900 / 3600.0), s.rowsPerHour!!, 0.001)
        assertEquals(3, s.streak)
        assertEquals(10 to 1800L, s.todayRows to s.todaySeconds)
        assertEquals(40 to 9000L, s.weekRows to s.weekSeconds)
        assertEquals(7, s.recent.size)
        assertEquals("2026-11-14", s.recent.first().day.toString())
        assertEquals(10, s.recent.last().rows)
    }

    @Test fun streakCountsFromYesterdayWhenTodayIsEmpty() {
        val log = mapOf("2026-11-18" to DayLog(1), "2026-11-19" to DayLog(1))
        assertEquals(2, statsOf(listOf(log), "2026-11-20").streak)
        assertEquals(0, statsOf(listOf(log), "2026-11-22").streak)
    }

    @Test fun emptyAndFormatting() {
        val s = statsOf(emptyList(), "2026-11-20")
        assertTrue(s.isEmpty); assertNull(s.rowsPerHour); assertEquals(0, s.streak)
        assertEquals("0 min", formatDuration(0)); assertEquals("under a minute", formatDuration(30))
        assertEquals("42 min", formatDuration(42 * 60)); assertEquals("2 h 05 min", formatDuration(125 * 60))
    }

    @Test fun dayKeyUsesTheLocalCalendarDay() {
        // 2026-11-20 23:30 UTC is already the 21st in Amsterdam.
        val t = 1795217400000L
        assertEquals("2026-11-20", dayKey(t, TimeZone.UTC))
        assertEquals("2026-11-21", dayKey(t, TimeZone.of("Europe/Amsterdam")))
    }
}
