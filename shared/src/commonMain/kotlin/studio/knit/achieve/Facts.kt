package studio.knit.achieve

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import studio.knit.chart.cell
import studio.knit.companion.position
import studio.knit.companion.tracksOf
import studio.knit.model.Category
import studio.knit.model.Construction
import studio.knit.model.Needle
import studio.knit.model.Pattern
import studio.knit.model.StitchCategory
import studio.knit.model.Yarn
import studio.knit.stitch.BuiltInStitches
import studio.knit.stitch.StitchLibrary
import studio.knit.validate.Severity
import studio.knit.written.evaluateSection

/**
 * Everything achievements are judged on, computed from what the app already keeps: the knitting
 * log per day, progress through each pattern, the patterns themselves and the stash.
 */
data class KnitFacts(
    val totalRows: Long = 0,
    val totalSeconds: Long = 0,
    /** Stitches on the needle after every row knitted, summed: what "stitches knitted" means. */
    val totalStitches: Long = 0,
    val maxRowsInDay: Int = 0,
    val longestSession: Long = 0,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    /** Weeks (Mon–Sun) with knitting on at least 3 days. */
    val knitWeeks: Int = 0,
    val knitDays: Int = 0,
    val partsFinished: Int = 0,
    val projectsFinished: Int = 0,
    val finishedCategories: Set<Category> = emptySet(),
    val socksFinished: Int = 0,
    val quickFinish: Boolean = false,
    val longHaulFinish: Boolean = false,
    val slowFinish: Boolean = false,
    val roundFinished: Boolean = false,
    val perfectFinish: Boolean = false,
    val undoneThenFinished: Boolean = false,
    val cables: Long = 0,
    val yarnOvers: Long = 0,
    val increases: Long = 0,
    val decreases: Long = 0,
    val maxColoursInRow: Int = 0,
    val charts: Int = 0,
    val writtenSections: Int = 0,
    val pdfSections: Int = 0,
    val customStitches: Int = 0,
    val renamedStitches: Int = 0,
    val yarns: Int = 0,
    val yarnPhotos: Int = 0,
    val needles: Int = 0,
    val nightRows: Int = 0,
    val earlyRows: Int = 0,
    val midnight: Boolean = false,
    val christmas: Boolean = false,
    val knitInPublicDay: Boolean = false,
    val anniversary: Boolean = false,
    val comeback: Boolean = false,
    val accentsTried: Int = 0,
    val firstDay: LocalDate? = null,
)

object FactsBuilder {

    fun build(
        patterns: List<Pattern>, library: StitchLibrary, yarns: List<Yarn> = emptyList(), needles: List<Needle> = emptyList(),
        accentsTried: Int = 0, today: LocalDate? = null,
    ): KnitFacts {
        // --- The log per day, over all patterns ---------------------------------------------
        val rowsByDay = HashMap<LocalDate, Int>()
        val secondsByDay = HashMap<LocalDate, Long>()
        var night = 0
        var early = 0
        for (p in patterns) for ((key, log) in p.progress?.days.orEmpty()) {
            val day = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            rowsByDay[day] = (rowsByDay[day] ?: 0) + log.rows
            secondsByDay[day] = (secondsByDay[day] ?: 0) + log.seconds
            for ((h, n) in log.hours) {
                if (h in 0..3) night += n
                if (h in 4..6) early += n
            }
        }
        val knitDays = (rowsByDay.keys + secondsByDay.keys).filter { (rowsByDay[it] ?: 0) > 0 || (secondsByDay[it] ?: 0) >= 60 }.distinct().sorted()
        val (best, current) = streaks(knitDays, today ?: knitDays.lastOrNull())

        // --- Rows and stitches worked, and the techniques in them ---------------------------
        var stitches = 0L; var cables = 0L; var yos = 0L; var inc = 0L; var dec = 0L; var colours = 0
        var parts = 0
        for (p in patterns) {
            val progress = p.progress ?: continue
            val tracks = tracksOf(p, progress.sizeIndex, library)
            for (t in tracks) {
                val done = progress.positions[t.id] ?: 0
                if (done <= 0) continue
                val eval = t.eval
                if (eval != null) {
                    if (!t.repeats && done >= t.length) parts++
                    for (row in eval.rows.take(done)) {
                        stitches += row.eval.produced ?: 0
                        val worked = row.eval.stitches ?: continue
                        for (w in worked) countTechnique(w.def.id, w.def.category) { kind -> when (kind) { 0 -> cables++; 1 -> yos++; 2 -> inc++; else -> dec++ } }
                        colours = maxOf(colours, worked.mapNotNullTo(HashSet()) { it.color }.size)
                    }
                } else {
                    // A chart worked on its own, possibly many times over.
                    val chart = t.chart ?: continue
                    for (r in 0 until chart.rows) {
                        val times = done / chart.rows + if (r < done % chart.rows) 1 else 0
                        if (times == 0) continue
                        var rowStitches = 0; var rc = 0; var ry = 0; var ri = 0; var rd = 0
                        val rowColours = HashSet<Int>()
                        for (c in 0 until chart.cols) {
                            val cell = chart.cell(r, c)
                            if (cell.part != 0 || cell.stitch == BuiltInStitches.NO_STITCH) continue
                            val def = library.require(cell.stitch)
                            rowStitches += def.produces
                            if (cell.color >= 0) rowColours += cell.color
                            countTechnique(def.id, def.category) { kind -> when (kind) { 0 -> rc++; 1 -> ry++; 2 -> ri++; else -> rd++ } }
                        }
                        stitches += rowStitches.toLong() * times
                        cables += rc.toLong() * times; yos += ry.toLong() * times; inc += ri.toLong() * times; dec += rd.toLong() * times
                        colours = maxOf(colours, rowColours.size)
                    }
                }
            }
        }

        // --- Finished projects ----------------------------------------------------------------
        val finished = patterns.filter { it.progress?.finishedAt != null }
        fun daysTaken(p: Pattern): Long = (p.progress!!.finishedAt!! - p.progress.startedAt) / DAY_MS
        val perfect = finished.any { p ->
            val evals = p.sections.map { evaluateSection(it, p.progress?.sizeIndex ?: 0, library) }
            evals.sumOf { it.rows.size } >= 100 && evals.all { e -> e.issues.none { it.severity != Severity.INFO } }
        }

        // --- Dates ------------------------------------------------------------------------
        val first = knitDays.firstOrNull()
        val gaps = knitDays.zipWithNext { a, b -> b.toEpochDays() - a.toEpochDays() }

        return KnitFacts(
            totalRows = rowsByDay.values.sumOf { it.toLong() },
            totalSeconds = secondsByDay.values.sum(),
            totalStitches = stitches,
            maxRowsInDay = rowsByDay.values.maxOrNull() ?: 0,
            longestSession = patterns.maxOfOrNull { it.progress?.longestSession ?: 0 } ?: 0,
            bestStreak = best,
            currentStreak = current,
            knitWeeks = knitDays.groupBy { weekOf(it) }.count { it.value.size >= 3 },
            knitDays = knitDays.size,
            partsFinished = parts,
            projectsFinished = finished.size,
            finishedCategories = finished.mapTo(HashSet()) { it.category },
            socksFinished = finished.count { it.category == Category.SOCKS },
            quickFinish = finished.any { daysTaken(it) <= 7 },
            longHaulFinish = finished.any { daysTaken(it) >= 90 },
            slowFinish = finished.any { daysTaken(it) >= 365 },
            roundFinished = finished.any { p -> p.sections.any { it.construction == Construction.ROUND } || p.charts.any { it.construction == Construction.ROUND } },
            perfectFinish = perfect,
            undoneThenFinished = finished.any { (it.progress?.undone ?: 0) >= 20 },
            cables = cables, yarnOvers = yos, increases = inc, decreases = dec, maxColoursInRow = colours,
            charts = patterns.sumOf { it.charts.size },
            writtenSections = patterns.sumOf { p -> p.sections.count { it.importedFrom == null && !it.generated && it.rows.any { r -> r.text.isNotBlank() } } },
            pdfSections = patterns.sumOf { p -> p.sections.count { it.importedFrom != null } },
            customStitches = library.custom.size,
            renamedStitches = BuiltInStitches.all.count { library.isRenamed(it.id) },
            yarns = yarns.size,
            yarnPhotos = yarns.count { it.photoId != null },
            needles = needles.size,
            nightRows = night,
            earlyRows = early,
            midnight = patterns.any { "midnight" in (it.progress?.moments ?: emptySet()) },
            christmas = knitDays.any { it.month.ordinal == 11 && it.day in 24..26 },
            knitInPublicDay = knitDays.any { isKnitInPublicDay(it) },
            anniversary = first != null && knitDays.any { it.toEpochDays() - first.toEpochDays() >= 365 },
            comeback = gaps.any { it >= 30 },
            accentsTried = accentsTried,
            firstDay = first,
        )
    }

    /** 0 cable, 1 yarn over, 2 increase, 3 decrease. */
    private inline fun countTechnique(id: String, category: StitchCategory, count: (Int) -> Unit) {
        when {
            category == StitchCategory.CABLE -> count(0)
            id == "yo" -> count(1)
            category == StitchCategory.INCREASE -> count(2)
            category == StitchCategory.DECREASE && id != "bo" -> count(3)
        }
    }

    /**
     * Longest and current streak of knitting days. One rest day in a row is forgiven, at most
     * once in any 7 days, so a busy day does not break a streak. The current streak still counts
     * when the last knitting day was yesterday (or the day before, using the rest day).
     */
    fun streaks(days: List<LocalDate>, today: LocalDate?): Pair<Int, Int> {
        if (days.isEmpty()) return 0 to 0
        var best = 1
        var run = 1
        var lastRest: Long? = null
        for (i in 1 until days.size) {
            val prev = days[i - 1].toEpochDays()
            val cur = days[i].toEpochDays()
            val gap = cur - prev
            when {
                gap == 1L -> run++
                gap == 2L && (lastRest == null || prev + 1 - lastRest!! >= 7) -> { run++; lastRest = prev + 1 }
                else -> { run = 1; lastRest = null }
            }
            best = maxOf(best, run)
        }
        val sinceLast = (today ?: days.last()).toEpochDays() - days.last().toEpochDays()
        val restAvailable = lastRest == null || days.last().toEpochDays() + 1 - lastRest!! >= 7
        val current = if (sinceLast <= 1 || (sinceLast == 2L && restAvailable)) run else 0
        return best to current
    }

    private fun weekOf(d: LocalDate): Long = (d.toEpochDays() - d.dayOfWeek.ordinal) // Monday of that week

    /** World Wide Knit in Public Day: the second Saturday of June. */
    private fun isKnitInPublicDay(d: LocalDate): Boolean =
        d.month.ordinal == 5 && d.dayOfWeek == DayOfWeek.SATURDAY && d.day in 8..14

    const val DAY_MS = 86_400_000L
}
