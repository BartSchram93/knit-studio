package studio.knit

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import studio.knit.achieve.AchievementState
import studio.knit.achieve.Achievements
import studio.knit.achieve.FactsBuilder
import studio.knit.achieve.KnitFacts
import studio.knit.achieve.Reward
import studio.knit.achieve.checkAchievements
import studio.knit.model.Category
import studio.knit.model.Cell
import studio.knit.model.Chart
import studio.knit.model.DayLog
import studio.knit.model.Pattern
import studio.knit.model.Progress
import studio.knit.model.SizedInt
import studio.knit.stats.logRows
import studio.knit.stats.logRowsAt

class AchievementsTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun days(vararg s: String) = s.map(::d)

    // --- Streaks -----------------------------------------------------------------------------

    @Test fun streakCountsConsecutiveDays() {
        assertEquals(3 to 3, FactsBuilder.streaks(days("2026-11-01", "2026-11-02", "2026-11-03"), d("2026-11-03")))
        assertEquals(0 to 0, FactsBuilder.streaks(emptyList(), d("2026-11-03")))
    }

    @Test fun oneRestDayAWeekKeepsTheStreak() {
        // Rest day on the 3rd: still one streak of 4 knitting days.
        assertEquals(4, FactsBuilder.streaks(days("2026-11-01", "2026-11-02", "2026-11-04", "2026-11-05"), null).first)
        // A second rest day within the same 7 days breaks it.
        assertEquals(3, FactsBuilder.streaks(days("2026-11-01", "2026-11-02", "2026-11-04", "2026-11-06", "2026-11-07", "2026-11-08"), null).first)
        // Two missed days in a row always break it.
        assertEquals(2, FactsBuilder.streaks(days("2026-11-01", "2026-11-02", "2026-11-05", "2026-11-06"), null).first)
    }

    @Test fun currentStreakSurvivesUntilTheDayAfterTomorrow() {
        val ds = days("2026-11-01", "2026-11-02", "2026-11-03")
        assertEquals(3, FactsBuilder.streaks(ds, d("2026-11-04")).second) // yesterday
        assertEquals(3, FactsBuilder.streaks(ds, d("2026-11-05")).second) // rest day
        assertEquals(0, FactsBuilder.streaks(ds, d("2026-11-06")).second)
    }

    // --- Facts from patterns ------------------------------------------------------------------

    private fun knitted(vararg log: Pair<String, DayLog>, positions: Map<String, Int> = emptyMap(), finished: Long? = null, started: Long = 0) =
        Progress(startedAt = started, finishedAt = finished, days = log.toMap(), positions = positions)

    @Test fun rowsTimeAndStitchesAreCounted() {
        val s = section("(K2, yo, k2tog) to end.", "P to end.", castOn = SizedInt(12))
        val p = Pattern(id = "a", sections = listOf(s), progress = knitted("2026-11-01" to DayLog(rows = 2, seconds = 3600), positions = mapOf(s.id to 2)))
        val f = FactsBuilder.build(listOf(p), lib)
        assertEquals(2, f.totalRows)
        assertEquals(3600, f.totalSeconds)
        assertEquals(24, f.totalStitches)
        assertEquals(3, f.yarnOvers)
        assertEquals(3, f.decreases)
        assertEquals(1, f.partsFinished)
    }

    @Test fun chartTracksCountEveryTimeARowIsWorked() {
        val chart = Chart(id = "c", cols = 4, rows = 2, cells = listOf(Cell("k"), Cell("yo"), Cell("k2tog"), Cell("k"), Cell("p"), Cell("p"), Cell("p"), Cell("p")))
        // 5 rows done: row 1 three times, row 2 twice.
        val p = Pattern(id = "b", charts = listOf(chart), progress = knitted(positions = mapOf("c" to 5)))
        val f = FactsBuilder.build(listOf(p), lib)
        assertEquals(3, f.yarnOvers)
        assertEquals(3 * 4 + 2 * 4L, f.totalStitches)
    }

    @Test fun nightAndEarlyRowsComeFromTheHourLog() {
        var pr = Progress(startedAt = 0)
        pr = pr.logRows("2026-11-01", 3, hour = 2).logRows("2026-11-01", 2, hour = 6).logRows("2026-11-01", 4, hour = 20)
        val f = FactsBuilder.build(listOf(Pattern(id = "n", progress = pr)), lib)
        assertEquals(3, f.nightRows)
        assertEquals(2, f.earlyRows)
        assertEquals(9, f.totalRows)
    }

    @Test fun undoingRowsIsCountedButNeverBelowZero() {
        var pr = Progress(startedAt = 0).logRows("2026-11-01", 5, hour = 10).logRows("2026-11-01", -2, hour = 10)
        assertEquals(2, pr.undone)
        assertEquals(3, pr.days["2026-11-01"]!!.rows)
        assertEquals(3, pr.days["2026-11-01"]!!.hours[10])
        pr = pr.logRowsAt(0, 1)
        assertTrue(pr.days.values.sumOf { it.rows } >= 4)
    }

    @Test fun finishedProjectsAndDates() {
        val day = FactsBuilder.DAY_MS
        val quick = Pattern(id = "q", category = Category.SOCKS, progress = knitted("2026-01-01" to DayLog(rows = 1), started = 0, finished = 3 * day))
        val slow = Pattern(id = "s", category = Category.SOCKS, progress = knitted("2027-01-05" to DayLog(rows = 1), started = 0, finished = 400 * day))
        val f = FactsBuilder.build(listOf(quick, slow), lib)
        assertEquals(2, f.projectsFinished)
        assertEquals(2, f.socksFinished)
        assertTrue(f.quickFinish); assertTrue(f.slowFinish); assertTrue(f.longHaulFinish)
        assertTrue(f.anniversary) // knit days a year apart
        assertTrue(f.comeback)    // more than 30 days between them
    }

    @Test fun specialDays() {
        val p = Pattern(id = "x", progress = knitted("2026-12-25" to DayLog(rows = 1), "2026-06-13" to DayLog(rows = 1)))
        val f = FactsBuilder.build(listOf(p), lib)
        assertTrue(f.christmas)
        assertTrue(f.knitInPublicDay) // 13 June 2026 is the second Saturday of June
        assertFalse(FactsBuilder.build(listOf(Pattern(id = "y", progress = knitted("2026-06-06" to DayLog(rows = 1)))), lib).knitInPublicDay)
    }

    // --- Tiers, rewards, levels ------------------------------------------------------------

    @Test fun tiersAndRewards() {
        val rows = Achievements.byId("rows")!!
        assertEquals(0, Achievements.tierOf(rows, KnitFacts(totalRows = 99)))
        assertEquals(1, Achievements.tierOf(rows, KnitFacts(totalRows = 100)))
        assertEquals(3, Achievements.tierOf(rows, KnitFacts(totalRows = 50_000)))
        // Silver row counter unlocks yarn-ball confetti; bronze does not.
        assertTrue(Achievements.rewards(KnitFacts(totalRows = 100)).none { it is Reward.Confetti && it.name == "YARN" })
        assertTrue(Achievements.rewards(KnitFacts(totalRows = 1000)).any { it is Reward.Confetti && it.name == "YARN" })
        assertTrue(Achievements.rewards(KnitFacts(nightRows = 1)).any { it is Reward.Accent && it.name == "MIDNIGHT" })
    }

    @Test fun everyAchievementCanBeReachedAndNoneIsFree() {
        val none = KnitFacts()
        for (d in Achievements.all) {
            assertEquals(0, Achievements.tierOf(d, none), "${d.id} must not be earned without knitting")
            assertEquals(d.goals.sorted(), d.goals, "${d.id} goals must increase")
            assertTrue(d.goals.all { it > 0 })
            if (d.hidden) assertTrue(d.hint.isNotBlank(), "${d.id} needs a hint")
        }
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size, "ids must be unique")
    }

    @Test fun levelsGrowAndHaveTitles() {
        assertEquals(1, Achievements.levelOf(0))
        assertEquals(2, Achievements.levelOf(Achievements.xpFor(2)))
        assertTrue((2..Achievements.MAX_LEVEL).all { Achievements.xpFor(it) > Achievements.xpFor(it - 1) })
        assertEquals("Slipknot", Achievements.titleOf(1))
        assertEquals("Grand knitter", Achievements.titleOf(40))
        // A thousand rows and ten hours is a few levels, not the top.
        val l = Achievements.level(KnitFacts(totalRows = 1000, totalSeconds = 36_000))
        assertTrue(l.level in 4..12, "level ${l.level}")
    }

    @Test fun nextGoalIsTheClosestStartedOne() {
        val g = Achievements.nextGoal(KnitFacts(totalRows = 90))!!
        assertEquals("rows", g.def.id)
        assertEquals(100, g.goal)
        assertNull(Achievements.nextGoal(KnitFacts()))
    }

    // --- Unlock bookkeeping -----------------------------------------------------------------

    @Test fun firstCheckCatchesUpQuietlyThenCelebratesNewOnes() {
        val first = checkAchievements(AchievementState(), KnitFacts(totalRows = 150), now = 1)
        assertTrue(first.newUnlocks.isEmpty())
        assertTrue("rows:1" in first.state.unlocked)
        val second = checkAchievements(first.state, KnitFacts(totalRows = 1200), now = 2)
        assertEquals(listOf("rows:2"), second.newUnlocks.map { it.key })
        // Nothing is celebrated twice.
        assertTrue(checkAchievements(second.state, KnitFacts(totalRows = 1200), now = 3).newUnlocks.isEmpty())
        // State survives a round trip through JSON.
        assertEquals(second.state, AchievementState.fromJson(second.state.toJson()))
        assertEquals(AchievementState(), AchievementState.fromJson("not json"))
    }
}
