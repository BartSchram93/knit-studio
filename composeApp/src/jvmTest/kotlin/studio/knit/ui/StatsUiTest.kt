package studio.knit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import studio.knit.model.DayLog
import studio.knit.stats.dayKey
import studio.knit.util.nowMillis
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StatsUiTest {
    @Test fun rowsKnittedTodayAreLoggedAndShown() = knitUiTest(seed = ::seedLibrary) { env ->
        tap("Patterns"); tap("Ribbed scarf"); tap("Start knitting")
        repeat(4) { onNodeWithContentDescription("Row done, next row").performClick(); waitForIdle() }
        onNodeWithContentDescription("Back one row").performClick(); waitForIdle()
        assertShown("Today")
        assertShown("3 rows")
        runBlocking { env.app.flushAll(); kotlinx.coroutines.delay(100) }
        val p = runBlocking { env.repo.patterns.first() }.first { it.name == "Ribbed scarf" }
        assertEquals(3, p.progress!!.days[dayKey(nowMillis())]!!.rows)
        shot("42-knit-today")
    }

    @Test fun overviewShowsTimeRowsAndChart() = knitUiTest(seed = { repo ->
        seedLibrary(repo)
        val today = LocalDate.parse(dayKey(nowMillis()))
        val log = (0..9).associate { back ->
            today.minus(DatePeriod(days = back)).toString() to DayLog(rows = 8 + (back * 7) % 23, seconds = (20L + (back * 13) % 70) * 60)
        }.filterKeys { it != today.minus(DatePeriod(days = 4)).toString() }
        val p = repo.patterns.first().first { it.name == "Feather & fan lace" }
        repo.save(p.copy(progress = studio.knit.companion.startProgress(p, nowMillis()).copy(days = log)))
    }) { _ ->
        tap("Tools"); tap("Knitting stats")
        assertShown("knitted in total")
        assertShown("days in a row")
        shot("43-stats-overview")
    }
}
