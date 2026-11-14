package studio.knit

import studio.knit.chart.blankChart
import studio.knit.companion.bump
import studio.knit.companion.isComplete
import studio.knit.companion.overallProgress
import studio.knit.companion.position
import studio.knit.companion.startProgress
import studio.knit.companion.statusLine
import studio.knit.companion.step
import studio.knit.companion.tracksOf
import studio.knit.convert.chartToSection
import studio.knit.model.Counter
import studio.knit.model.Pattern
import studio.knit.model.SizedInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionTest {
    private fun pattern(): Pattern {
        val body = section("Knit", "Purl", "@rep 1-2 x2, 4", castOn = SizedInt(listOf(10, 12)))
        val chart = blankChart("free", 4, 3, "Motif")
        return Pattern(id = "p", name = "P", sizes = listOf("S", "L"), sections = listOf(body.copy(title = "Body")), charts = listOf(chart))
    }

    @Test fun tracksAreSectionsPlusUnlinkedCharts() {
        val p = pattern()
        val tracks = tracksOf(p, 0, lib)
        assertEquals(listOf("Body", "Motif"), tracks.map { it.title })
        assertEquals(6, tracks[0].length)
        assertEquals(10, tracksOf(p, 1, lib)[0].length)   // the larger size repeats more often
        assertTrue(tracks[1].repeats)

        val linkedChart = blankChart("linked", 4, 2)
        val (generated, _) = chartToSection(linkedChart, lib, ::id)
        val q = Pattern(id = "q", charts = listOf(linkedChart), sections = listOf(generated))
        assertEquals(1, tracksOf(q, 0, lib).size)
    }

    @Test fun steppingForwardAndBackStaysInBounds() {
        val p = pattern()
        val track = tracksOf(p, 0, lib)[0]
        var progress = startProgress(p, now = 1)
        assertEquals(0, track.position(progress).rowIndex)
        progress = progress.step(track, -1, 2)
        assertEquals(0, track.position(progress).done)
        repeat(4) { progress = progress.step(track, 1, 3) }
        val pos = track.position(progress)
        assertEquals(4, pos.done)
        assertEquals(5, pos.expanded!!.number)
        assertEquals(2, pos.expanded!!.repeatIteration)
        assertFalse(pos.finished)
        repeat(10) { progress = progress.step(track, 1, 4) }
        assertEquals(6, track.position(progress).done)
        assertTrue(track.position(progress).finished)
        assertEquals(4, progress.lastKnitAt)
    }

    @Test fun chartTracksWrapAndCountPasses() {
        val p = pattern()
        val track = tracksOf(p, 0, lib)[1]
        var progress = startProgress(p, 1)
        repeat(7) { progress = progress.step(track, 1, 2) }
        val pos = track.position(progress)
        assertEquals(1, pos.rowIndex)
        assertEquals(2, pos.pass)
        assertEquals(1, pos.chartRow)
        assertFalse(pos.finished)
    }

    @Test fun linkedChartRowFollowsRowRepeats() {
        val chart = blankChart("c", 4, 2)
        val (generated, _) = chartToSection(chart, lib, ::id)
        val s = generated.copy(castOn = SizedInt(4), rows = generated.rows + studio.knit.model.Row(id(), studio.knit.model.RowKind.REPEAT_ROWS, repeatFrom = 1, repeatTo = 2, repeatTimes = SizedInt(2)))
        val p = Pattern(id = "p", charts = listOf(chart), sections = listOf(s))
        val track = tracksOf(p, 0, lib).single()
        var progress = startProgress(p, 1)
        val rows = ArrayList<Int?>()
        repeat(6) { rows += track.position(progress).chartRow; progress = progress.step(track, 1, 2) }
        assertEquals(listOf<Int?>(0, 1, 0, 1, 0, 1), rows)
    }

    @Test fun linkedCountersFollowTheRowCounter() {
        val p = pattern()
        val track = tracksOf(p, 0, lib)[0]
        var progress = startProgress(p, 1).copy(counters = listOf(
            Counter("a", "Cable every 4", resetAt = 4, linked = true),
            Counter("b", "Sleeve decs"),
        ))
        repeat(5) { progress = progress.step(track, 1, 2) }
        assertEquals(1, progress.counters[0].value)
        assertEquals(1, progress.counters[0].laps)
        assertEquals(0, progress.counters[1].value)
        progress = progress.step(track, -2, 3)
        assertEquals(3 to 0, progress.counters[0].value to progress.counters[0].laps)
    }

    @Test fun counterBumpNeverGoesNegative() {
        assertEquals(0, Counter("a", value = 1).bump(-5).value)
        assertEquals(0 to 0, Counter("a", value = 1, resetAt = 3).bump(-5).let { it.value to it.laps })
        assertEquals(2 to 3, Counter("a", resetAt = 3).bump(11).let { it.value to it.laps })
    }

    @Test fun overallProgressAndStatus() {
        val p = pattern()
        assertNull(overallProgress(p, lib))
        val track = tracksOf(p, 0, lib)[0]
        var progress = startProgress(p, 1)
        repeat(3) { progress = progress.step(track, 1, 2) }
        val started = p.copy(progress = progress)
        assertEquals(0.5f, overallProgress(started, lib))
        assertEquals("Body · Row 4 of 6", statusLine(started, lib))
        assertFalse(isComplete(started, lib))
        repeat(3) { progress = progress.step(track, 1, 2) }
        assertTrue(isComplete(p.copy(progress = progress), lib))
        assertEquals("Body · finished", statusLine(p.copy(progress = progress), lib))
    }
}
