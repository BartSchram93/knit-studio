package studio.knit.written

import studio.knit.model.Construction
import studio.knit.model.Row
import studio.knit.model.RowKind
import studio.knit.model.Section
import studio.knit.model.Side
import studio.knit.model.StitchDef
import studio.knit.stitch.StitchLibrary
import studio.knit.validate.Severity

/** One stitch actually worked, in knitting order. */
data class Worked(val def: StitchDef, val color: String? = null, val inRepeat: Boolean = false)

data class EvalIssue(val severity: Severity, val message: String)

/**
 * The arithmetic of one row.
 *
 * @property consumed stitches taken from the left needle; null when it cannot be known
 *   (an open-ended instruction without a known starting count).
 * @property produced stitches on the right needle afterwards.
 * @property stitches every stitch in working order, or null when unresolved.
 * @property repeatsWorked how often a "to end" repeat was worked.
 */
data class RowEval(
    val consumed: Int?,
    val produced: Int?,
    val stitches: List<Worked>?,
    val issues: List<EvalIssue>,
    val repeatsWorked: Int? = null,
    /**
     * For rows whose count is open ("rep from * to end" with no cast-on set): the row works over
     * a multiple of [multiple] stitches plus [extra].
     */
    val multiple: Int? = null,
    val extra: Int? = null,
) {
    val hasErrors: Boolean get() = issues.any { it.severity == Severity.ERROR }
    val delta: Int? get() = if (consumed != null && produced != null) produced - consumed else null
}

/**
 * Works through a parsed row stitch by stitch, exactly as a knitter would, keeping track of how
 * many stitches remain on the left needle.
 */
class RowEvaluator(private val sizeIndex: Int = 0) {

    private class State(var remaining: Int?) {
        var consumed = 0
        var produced = 0
        val list = ArrayList<Worked>()
        var color: String? = null
        var unresolved = false
        var repeats: Int? = null
        val issues = ArrayList<EvalIssue>()
        fun error(msg: String) { issues += EvalIssue(Severity.ERROR, msg) }
    }

    /**
     * @param stitchesIn stitches on the needle before the row, or null if unknown.
     * @param compact work every "to end" repeat exactly once and flag its stitches
     *   ([Worked.inRepeat]); used to draw a chart of one repeat.
     */
    fun evaluate(parsed: ParsedRow, stitchesIn: Int?, compact: Boolean = false): RowEval {
        val st = State(stitchesIn)
        for (e in parsed.errors) st.error(e.message)
        run(parsed.nodes, st, compact, inRepeat = false)

        if (st.unresolved) {
            if (compact) {
                st.issues += EvalIssue(Severity.WARNING, "“To end” instructions outside a repeat need a known stitch count")
                return RowEval(null, null, null, st.issues)
            }
            // Without a starting count the row cannot be checked, but a repeat still tells us
            // which stitch counts it works over.
            val once = State(null)
            run(parsed.nodes, once, compact = true, inRepeat = false)
            val per = once.list.filter { it.inRepeat }.sumOf { it.def.consumes }
            return if (!once.unresolved && per > 0) {
                val edge = once.consumed - per
                st.issues += EvalIssue(Severity.INFO, "Works over a multiple of $per sts" + if (edge > 0) " plus $edge" else "")
                RowEval(null, null, null, st.issues, multiple = per, extra = edge)
            } else {
                st.issues += EvalIssue(Severity.INFO, "Stitch count not known here — set the cast-on count to have this row checked")
                RowEval(null, null, null, st.issues)
            }
        }
        if (compact) return RowEval(st.consumed, st.produced, st.list, st.issues, st.repeats)

        if (stitchesIn != null && parsed.nodes.isNotEmpty() && st.consumed != stitchesIn && st.issues.none { it.severity == Severity.ERROR }) {
            if (st.consumed < stitchesIn) {
                val left = stitchesIn - st.consumed
                st.error("Works ${st.consumed} of the $stitchesIn sts on the needle — $left ${plural(left)} left over")
            } else {
                val over = st.consumed - stitchesIn
                st.error("Needs ${st.consumed} sts but only $stitchesIn are on the needle — $over too many")
            }
        }
        parsed.stated?.let { stated ->
            val expected = stated.forSize(sizeIndex)
            if (expected != st.produced) st.error("Row says $expected sts but works out to ${st.produced}")
        }
        return RowEval(st.consumed, st.produced, st.list, st.issues, st.repeats)
    }

    private fun run(nodes: List<Node>, st: State, compact: Boolean, inRepeat: Boolean) {
        for (node in nodes) {
            when (node) {
                is Node.Color -> st.color = node.name
                is Node.Unknown -> Unit // already reported by the parser
                is Node.Stitch -> stitch(node, st, compact, inRepeat)
                is Node.Group -> group(node, st, compact, inRepeat)
            }
        }
    }

    private fun stitch(node: Node.Stitch, st: State, compact: Boolean, inRepeat: Boolean) {
        val def = node.def
        val n = when (val c = node.count) {
            is Count.Fixed -> c.n.forSize(sizeIndex)
            is Count.ToEnd -> {
                val rem = st.remaining
                if (rem == null || compact) { st.unresolved = true; return }
                val leave = c.leaving.forSize(sizeIndex)
                val avail = rem - leave
                when {
                    def.consumes == 0 -> { st.error("“${def.abbr}” uses no stitches, so it cannot be worked “to end”"); return }
                    avail < 0 -> { st.error("“${def.abbr} to last $leave sts” needs at least $leave sts, but only $rem remain"); return }
                    avail % def.consumes != 0 -> {
                        st.error("“${def.abbr}” to ${if (leave == 0) "end" else "last $leave sts"}: $avail sts remain, which is not a multiple of ${def.consumes}")
                        avail / def.consumes
                    }
                    else -> avail / def.consumes
                }
            }
        }
        apply(def, n, st, inRepeat)
    }

    private fun apply(def: StitchDef, n: Int, st: State, inRepeat: Boolean) {
        if (n <= 0) return
        st.consumed += n * def.consumes
        st.produced += n * def.produces
        st.remaining = st.remaining?.minus(n * def.consumes)
        if (st.list.size < MAX_STITCHES) repeat(n) { st.list += Worked(def, st.color, inRepeat) }
    }

    private fun group(node: Node.Group, st: State, compact: Boolean, inRepeat: Boolean) {
        when (val t = node.times) {
            is Count.Fixed -> repeat(t.n.forSize(sizeIndex).coerceIn(0, MAX_REPEATS)) { run(node.body, st, compact, inRepeat) }
            is Count.ToEnd -> {
                if (compact) {
                    run(node.body, st, compact, inRepeat = true)
                    st.repeats = 1
                    return
                }
                val leave = t.leaving.forSize(sizeIndex)
                val start = st.remaining
                if (start == null) { st.unresolved = true; return }
                if (start < leave) {
                    st.error("Repeat “to last $leave sts” needs at least $leave sts, but only $start remain")
                    return
                }
                var reps = 0
                while (reps < MAX_REPEATS) {
                    val rem = st.remaining ?: break
                    if (rem - leave <= 0) break
                    // Snapshot so an overshooting pass can be undone.
                    val c0 = st.consumed; val p0 = st.produced; val l0 = st.list.size; val col0 = st.color
                    run(node.body, st, compact = false, inRepeat = inRepeat)
                    val used = st.consumed - c0
                    if (st.unresolved) return
                    if (used <= 0) {
                        st.error("The repeat uses no stitches, so it can never reach the end of the row")
                        break
                    }
                    val after = st.remaining ?: break
                    if (after < leave) {
                        // Undo the partial pass and report.
                        st.consumed = c0; st.produced = p0; st.remaining = rem; st.color = col0
                        while (st.list.size > l0) st.list.removeAt(st.list.size - 1)
                        val space = rem - leave
                        st.error("Repeat of $used sts does not fit: $space ${plural(space)} left before the ${if (leave == 0) "end" else "last $leave"}")
                        break
                    }
                    reps++
                }
                st.repeats = (st.repeats ?: 0) + reps
            }
        }
    }

    private fun plural(n: Int) = if (n == 1) "st" else "sts"

    companion object {
        const val MAX_REPEATS = 5000
        const val MAX_STITCHES = 20000
    }
}

// ---------------------------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------------------------

/** One row as it will actually be knitted, after row repeats have been unrolled. */
data class ExpandedRow(
    /** Zero-based position in the section. */
    val index: Int,
    /** Row number shown to the knitter, starting at 1. */
    val number: Int,
    val side: Side,
    /** The instruction row this came from. */
    val source: Row,
    /** The REPEAT_ROWS entry that produced it, if any, with "iteration of total". */
    val viaRepeat: Row? = null,
    val repeatIteration: Int = 0,
    val repeatTotal: Int = 0,
    val parsed: ParsedRow,
    val stitchesBefore: Int?,
    val eval: RowEval,
)

data class SectionIssue(val rowId: String?, val severity: Severity, val message: String)

data class SectionEval(
    val rows: List<ExpandedRow>,
    val issues: List<SectionIssue>,
    val startCount: Int?,
    val endCount: Int?,
) {
    /** First expanded row for a source row id (for showing counts next to the editor row). */
    fun firstFor(rowId: String): ExpandedRow? = rows.firstOrNull { it.source.id == rowId && it.viaRepeat == null }
    val totalStitches: Int? get() = if (rows.any { it.eval.produced == null }) null else rows.sumOf { it.eval.produced ?: 0 }
}

fun rowLabel(construction: Construction, number: Int, side: Side): String =
    if (construction == Construction.ROUND) "Rnd $number" else "Row $number (${side.name})"

/**
 * Unrolls a section into the rows that are actually knitted and checks the stitch count from
 * row to row for one size.
 */
fun evaluateSection(section: Section, sizeIndex: Int, library: StitchLibrary): SectionEval {
    val parser = RowParser(library)
    val evaluator = RowEvaluator(sizeIndex)
    val issues = ArrayList<SectionIssue>()

    data class Pending(val source: Row, val parsed: ParsedRow, val via: Row?, val iteration: Int, val total: Int)

    val seq = ArrayList<Pending>()
    for (row in section.rows) {
        when (row.kind) {
            RowKind.NOTE -> Unit
            RowKind.INSTRUCTION -> seq += Pending(row, parser.parse(row.text), null, 0, 0)
            RowKind.REPEAT_ROWS -> {
                val from = row.repeatFrom
                val to = row.repeatTo
                val times = row.repeatTimes.forSize(sizeIndex)
                when {
                    from < 1 || to < from || to > seq.size ->
                        issues += SectionIssue(row.id, Severity.ERROR, "Repeat refers to rows $from–$to, but only rows 1–${seq.size} exist above it")
                    times < 0 -> issues += SectionIssue(row.id, Severity.ERROR, "Repeat count cannot be negative")
                    seq.size + (to - from + 1).toLong() * times > MAX_ROWS ->
                        issues += SectionIssue(row.id, Severity.ERROR, "Repeat is too long to expand (more than $MAX_ROWS rows)")
                    else -> {
                        val block = seq.subList(from - 1, to).toList()
                        for (i in 1..times) for (b in block) seq += Pending(b.source, b.parsed, row, i, times)
                    }
                }
            }
        }
    }

    val start = section.castOn?.forSize(sizeIndex)
    var current = start
    val out = ArrayList<ExpandedRow>(seq.size)
    var side = section.firstRowSide
    seq.forEachIndexed { index, p ->
        val rowSide = if (section.construction == Construction.ROUND) Side.RS else side
        val raw = if (p.parsed.isEmpty) null else evaluator.evaluate(p.parsed, current)
        val eval = if (raw != null && section.importedFrom != null && p.parsed.errors.isNotEmpty()) {
            // Imported rows in the designer's own words: follow them, but do not flag them as wrong.
            raw.copy(issues = listOf(EvalIssue(Severity.INFO, "Not checked — the app does not understand: " + p.parsed.errors.joinToString("; ") { it.fragment })))
        } else if (p.parsed.isEmpty) {
            RowEval(0, current ?: 0, emptyList(), listOf(EvalIssue(Severity.WARNING, "This row has no instructions yet")))
        } else if (section.importedFrom != null) {
            // A count that does not add up in an imported row is worth a look, but the designer may
            // count differently (edge stitches, charts without the border): a warning, not an error.
            raw!!.copy(issues = raw.issues.map { if (it.severity == Severity.ERROR) it.copy(severity = Severity.WARNING) else it })
        } else raw!!
        out += ExpandedRow(index, index + 1, rowSide, p.source, p.via, p.iteration, p.total, p.parsed, current, eval)
        // Report each distinct problem once, on the row (or repeat) that caused it.
        if (p.via == null || p.iteration == 1) {
            for (i in eval.issues) {
                // Hints such as "multiple of 18" are worth saying once per section, not on every row.
                if (i.severity == Severity.INFO && issues.any { it.severity == Severity.INFO && it.message == i.message }) continue
                issues += SectionIssue(p.source.id, i.severity, i.message)
            }
        } else if (eval.hasErrors && out.none { it !== out.last() && it.source.id == p.source.id && it.eval.issues == eval.issues }) {
            for (i in eval.issues) issues += SectionIssue(p.via.id, i.severity, "Repeat ${p.iteration} of ${p.total}, row ${index + 1}: ${i.message}")
        }
        // A row the app could not fully read gives no reliable count for the rows after it.
        if (!p.parsed.isEmpty) current = if (p.parsed.errors.isEmpty()) eval.produced else null
        side = side.flipped()
    }
    return SectionEval(out, issues, start ?: out.firstOrNull()?.eval?.consumed, current)
}

private const val MAX_ROWS = 20000
