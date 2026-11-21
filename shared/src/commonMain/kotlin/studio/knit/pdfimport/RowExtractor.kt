package studio.knit.pdfimport

import studio.knit.model.Construction
import studio.knit.model.Row
import studio.knit.model.RowKind
import studio.knit.model.Section
import studio.knit.model.Side
import studio.knit.model.SizedInt
import studio.knit.written.RowParser

/*
 * Finds the row-by-row instructions in the text of a knitting pattern PDF.
 *
 * Rule-based, no AI: it recognises the ways patterns commonly write rows ("Row 1 (RS): …",
 * "Rnd 3: …", "Next round: …", "Set-up row: …", "Rows 2, 3, 4: …", "Row 4: as Row 2"), the usual
 * repeat sentences ("Repeat Rows 1 to 4 a total of 4 times", "Work rows 1 and 2 a total of
 * 58 (71) 92 times", "Repeat these 4 rows 15 times"), section headings and cast-on counts.
 *
 * The row text is kept in the designer's own words. Anything it is unsure about is listed in
 * [PdfImport.warnings] so the knitter can check it before importing.
 */

sealed interface ImportItem {
    /** One instruction row. [number] is the number printed in the pattern, if any. */
    data class Instruction(val number: Int?, val side: Side?, val text: String, val note: String = "") : ImportItem

    /** Work instruction rows [from]..[to] (positions in this section) [more] more times. */
    data class Repeat(val from: Int, val to: Int, val more: SizedInt, val source: String) : ImportItem

    data class Note(val text: String) : ImportItem
}

data class ImportedSection(
    val title: String,
    val round: Boolean,
    val firstSide: Side,
    val castOn: SizedInt?,
    val description: String,
    val items: List<ImportItem>,
    /** Markers after the cast-on (stitches before each), from a sentence such as "2 sts, pm, 2 sts, pm, 2 sts". */
    val markers: List<Int> = emptyList(),
    /** Worked "until" a length or count is reached, so the count at its end is not known. */
    val openEnded: Boolean = false,
) {
    val rowCount: Int get() = items.count { it is ImportItem.Instruction }
    val repeatCount: Int get() = items.count { it is ImportItem.Repeat }
}

data class PdfImport(
    val sections: List<ImportedSection>,
    /** Number of sizes the pattern writes its counts for, e.g. 3 for "10 (12, 14)". */
    val sizeCount: Int,
    val warnings: List<String>,
) {
    val rowCount: Int get() = sections.sumOf { it.rowCount }
    val isEmpty: Boolean get() = rowCount == 0
}

object RowExtractor {

    // --- Patterns ------------------------------------------------------------------------------

    private const val MAX_TRAILING_NOTES = 3
    private const val SIZED = """\d+(?:\s*\(\s*\d+(?:\s*,\s*\d+)*\s*\))?"""
    private const val UNIT = """(?:row|rnd|round)"""
    private val NUMBER_WORDS = mapOf(
        "once" to 1, "twice" to 2, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )

    /** "Row 1 (RS):", "Rnd 12:", "Rows 5-6:", "Round 3 (WS) -", "Short Row 2:". */
    private val ROW_START = Regex(
        """^(?:short\s+)?($UNIT)s?\s+(\d+)(?:\s*(?:-|–|to)\s*(\d+))?\s*(?:\((rs|ws)\))?(\s+and\s+all\s+(?:following\s+)?(?:even|odd|following|other|ws|rs|wrong[- ]side|right[- ]side)\s+(?:rows|rounds|rnds)(?:\s+that\s+follow)?(?:\s+unless\s+otherwise\s+(?:noted|stated|specified))?)?\s*(?:\((rs|ws)\))?\s*[:.]\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    /** "Rows 2, 3, 4: …" and "Rows 2 and 4: …". */
    private val ROW_LIST = Regex("""^($UNIT)s\s+(\d+(?:\s*(?:,|and|&)\s*\d+)+)\s*(?:\((rs|ws)\))?\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)
    /** "Next row (RS):", "Next round:", "Set-up row (WS):", "Foundation round:". */
    private val ROW_NEXT = Regex("""^(next|set[- ]?up|foundation|first|last|final)\s+($UNIT)\s*(?:\((rs|ws)\))?\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val AS_ROW = Regex("""^(?:work\s+)?(?:as|same\s+as|rep(?:eat)?)\s+$UNIT\s+(\d+)\.?$""", RegexOption.IGNORE_CASE)

    private val CAST_ON = Regex("""(?:cast\s+on|\bCO)\s+($SIZED)\s*(?:sts?|stitches)""", RegexOption.IGNORE_CASE)
    /** "Knit 10 rounds.", "Purl 2 rows using yarn B." */
    private val PLAIN_ROWS = Regex("""^(knit|purl)\s+($SIZED|one|two|three|four|five|six|seven|eight|nine|ten)\s+(?:more\s+)?(rows|rounds|rnds)(?:\s+(?:using|with|in)\s+(?:yarn|colou?r)\s+([A-Z]))?\.?$""", RegexOption.IGNORE_CASE)
    /** Copyright, shop and social media lines are not instructions. */
    private val BOILERPLATE = Regex("""copyright|copied|reproduc|redistribut|resold|resell|\bsell\b|translated|instagram|facebook|ravelry|#\w|www\.|https?:|e-?mail|website|happy\s+knitting|enjoy|congratulations|thank\s+you""", RegexOption.IGNORE_CASE)
    private val FIRST_IS_WS = Regex("""first\s+$UNIT\s+is\s+a\s+(?:ws|wrong[- ]side)""", RegexOption.IGNORE_CASE)
    private val TIMES = """($SIZED|once|twice|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)"""

    /** Repeat sentences naming the rows: "Repeat Rows 1 to 4 a total of 4 times", "Rep rows 2–3 5 more times". */
    private val REPEAT_NAMED = Regex(
        """(?:repeat|rep|work)\s+(?:the\s+)?${UNIT}s?\s+(\d+)(?:\s*(?:-|–|to|and|&)\s*(\d+))?\s+(a\s+total\s+of\s+)?$TIMES\s*(more\s+)?(?:times?)?""",
        RegexOption.IGNORE_CASE,
    )
    /** "Repeat these 4 rows 15 times", "Repeat the last 2 rounds 3 more times". */
    private val REPEAT_LAST = Regex(
        """(?:repeat|rep|work)\s+(?:these|the\s+last|the\s+previous|last)\s+(\d+|two|three|four|five|six|eight)\s+${UNIT}s\s+(a\s+total\s+of\s+)?$TIMES\s*(more\s+)?(?:times?)?""",
        RegexOption.IGNORE_CASE,
    )
    /** "Repeat the last row 3 more times". */
    private val REPEAT_LAST_ONE = Regex(
        """(?:repeat|rep)\s+(?:this|the\s+last|the\s+previous|last)\s+$UNIT\s+(a\s+total\s+of\s+)?$TIMES\s*(more\s+)?(?:times?)?""",
        RegexOption.IGNORE_CASE,
    )
    /** "Chart 1", "Chart #4-A", "Part 2", "Section B". */
    private val NUMBERED_PART = Regex("""^(?:chart|part|section|step|panel|tier|motif)\s*#?\s*[\w-]{1,6}$""", RegexOption.IGNORE_CASE)
    private val SPLIT_HEADER = Regex("""^(?:short\s+)?(?:row|rnd|round)s?\s+\d+""", RegexOption.IGNORE_CASE)
    /** "RS rows: sl1 st wyf, k1, k3, work chart, k3, sl1 st wyf." — the edge worked around every chart row. */
    private val FRAME = Regex("""\b(RS|WS)\s+rows?:\s*(.+?),\s*work\s+(?:the\s+)?(?:[a-z-]+\s+)?chart(?:\s+\S+)?,\s*(.+?)\.?$""", RegexOption.IGNORE_CASE)
    private val WHOLE_ROW_TEXT = Regex("""^(knit the knits and purl the purls|work (?:all )?(?:the )?(?:sts|stitches) as they appear|work in pattern|work as set)\.?$""", RegexOption.IGNORE_CASE)
    private val UNTIL = Regex("""\buntil\b|\bdesired\b|\bmeasures\b""", RegexOption.IGNORE_CASE)

    /** Headings that introduce instructions but are not part names. */
    private val GENERIC_HEADING = Regex("""^(work\s+)?(as\s+follows|follows|pattern|instructions|abbreviations|notes?|materials|sizes?|gauge|tension|needles?|yarn)$""", RegexOption.IGNORE_CASE)

    /** Remarks in brackets that are not stitches: "(4 sts have been increased)", "(center sts)". */
    private val REMARK = Regex("""\(([^()]*\b(?:have\s+been|has\s+been|cent(?:er|re)|increased|decreased|total|remain(?:ing)?|i\.e\.|see\s|optional|note|exceptions?|ws|rs)\b[^()]*)\)""", RegexOption.IGNORE_CASE)

    /** "58 (71) 92" — the "small (medium) large" style — becomes "58 (71, 92)". */
    private val SIZES_PETITE = Regex("""(?<![\d(,])(\d+)\s*\((\d+)\)\s*(\d+)(?![\d)])""")
    private val SIZES_STANDARD = Regex("""\b\d+\s*\(\s*\d+(?:\s*,\s*\d+)+\s*\)""")

    // --- Entry point ---------------------------------------------------------------------------

    fun extract(rawText: String): PdfImport {
        val warnings = ArrayList<String>()
        val lines = clean(rawText)

        val sections = ArrayList<SectionBuilder>()
        var section: SectionBuilder? = null
        var pendingHeading: String? = null
        var pendingCastOn: SizedInt? = null
        var pendingWsFirst = false
        var pendingMarkers: List<Int> = emptyList()
        val pendingFrames = HashMap<Side, Pair<String, String>>()
        val prose = StringBuilder()
        var row: RowBuilder? = null

        fun current(): SectionBuilder {
            val s = section
            if (s != null && (pendingHeading == null || s.items.isEmpty())) {
                pendingHeading?.let { if (s.items.isEmpty()) s.title = it }
                pendingHeading = null
                return s
            }
            val created = SectionBuilder(pendingHeading ?: "Part ${sections.size + 1}")
            pendingHeading = null
            created.castOn = pendingCastOn; pendingCastOn = null
            created.markers = pendingMarkers; pendingMarkers = emptyList()
            created.frames.putAll(pendingFrames); pendingFrames.clear()
            if (pendingWsFirst) { created.firstSide = Side.WS; pendingWsFirst = false }
            sections += created
            section = created
            return created
        }

        fun flushRow() {
            val r = row ?: return
            row = null
            r.finish(current(), warnings)
        }

        fun flushProse() {
            val text = prose.toString().trim()
            prose.clear()
            if (text.isEmpty()) return
            for (sentence in splitSentences(text)) handleSentence(sentence, section, warnings, { section = it; it.also { s -> if (s !in sections) sections += s } },
                onCastOn = { c -> if (section == null || section!!.items.isNotEmpty()) pendingCastOn = c else section!!.castOn = c },
                onFrame = { side, pre, post ->
                    val target = section?.takeIf { it.items.isEmpty() }
                    if (target != null) target.frames[side] = pre to post else pendingFrames[side] = pre to post
                },
                onMarkers = { m -> if (section == null || section!!.items.isNotEmpty()) pendingMarkers = m else section!!.markers = m },
                onWsFirst = { if (section == null || section!!.items.isNotEmpty()) pendingWsFirst = true else section!!.firstSide = Side.WS })
        }

        for (line in lines) {
            val start = parseRowStart(line)
            if (start != null) {
                flushRow(); flushProse()
                // Row 1 again means a new part of the pattern.
                val s = section
                if (s != null && start.first() <= (s.lastNumber ?: 0) && start.first() == 1 && pendingHeading == null) {
                    pendingHeading = "Part ${sections.size + 1}"
                }
                if (pendingHeading != null && section?.items?.isNotEmpty() == true) section = null
                current()
                row = start
                continue
            }
            val r = row
            val heading = looksLikeHeading(line)
            val soft = !heading && looksLikeSoftHeading(line)
            if (r != null && (r.text.isBlank() || !heading && (line.first().isLowerCase() || line.startsWith("(") || r.openBracket() || !r.complete() && !soft))) {
                r.text.append(' ').append(line)
                continue
            }
            flushRow()
            if (heading || soft) {
                flushProse()
                val title = line.trimEnd(':').trim().let { t -> if (t == t.uppercase()) t.lowercase().replaceFirstChar { it.uppercase() } else t }
                if (!GENERIC_HEADING.matches(title)) {
                    pendingHeading = title
                    if (section?.items?.isNotEmpty() == true) section = null
                }
                continue
            }
            prose.append(' ').append(line)
            if (line.endsWith(".")) flushProse()
        }
        flushRow(); flushProse()

        val kept = sections.filter { it.items.any { i -> i is ImportItem.Instruction } }
        kept.forEach { s -> s.alternateWarning()?.let { warnings += "${s.title}: $it" } }
        val built = kept.map { it.build() }
        val sizeCount = SIZES_STANDARD.findAll(rawText.replace(SIZES_PETITE) { m -> "${m.groupValues[1]} (${m.groupValues[2]}, ${m.groupValues[3]})" })
            .map { m -> m.value.count { it == ',' } + 2 }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 1
        val fromCounts = built.flatMap { sec -> listOfNotNull(sec.castOn) + sec.items.filterIsInstance<ImportItem.Repeat>().map { it.more } }
            .maxOfOrNull { it.values.size } ?: 1
        return PdfImport(built, maxOf(sizeCount, fromCounts), warnings.distinct())
    }

    // --- Lines ---------------------------------------------------------------------------------

    /** Splits into trimmed lines, removes page furniture (headers, footers, page numbers). */
    private fun clean(raw: String): List<String> {
        val text = raw.replace('\u000C', '\n').replace(' ', ' ').replace("ﬁ", "fi").replace("ﬂ", "fl").replace('\t', ' ').replace('\r', '\n')
        val lines = text.split('\n').map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        val counts = lines.groupingBy { it }.eachCount()
        return joinSplitHeaders(lines).filter { l ->
            // A line repeated on many pages is a header or footer, unless it is stitch text.
            !((counts[l] ?: 1) >= 3 && parseRowStart(l) == null && !l.contains(":") && !l.contains(",") && !l.first().isLowerCase()) &&
                !Regex("""^(page\s+)?\d+(\s*(of|/)\s*\d+)?$""", RegexOption.IGNORE_CASE).matches(l)
        }
    }

    /** "Row 138 and all WS rows unless otherwise" + "noted: knit the knits…" → one line. */
    private fun joinSplitHeaders(lines: List<String>): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            val next = lines.getOrNull(i + 1)
            if (next != null && !l.contains(':') && SPLIT_HEADER.containsMatchIn(l) && parseRowStart(l) == null &&
                next.indexOf(':') in 0..40 && parseRowStart("$l $next") != null
            ) {
                out += "$l $next"; i += 2
            } else { out += l; i++ }
        }
        return out
    }

    private fun looksLikeHeading(line: String): Boolean =
        line.endsWith(":") && line.length <= 50 && !line.first().isLowerCase() && parseRowStart(line) == null && !line.contains(",")

    /** A short title line without punctuation, such as "Scarf", "Wash and blocking" or "SHAPE CROWN". */
    private fun looksLikeSoftHeading(line: String): Boolean =
        line.length <= 40 && line.first().isUpperCase() &&
            (line.none { it.isDigit() } && line.last().isLetter() || NUMBERED_PART.matches(line)) &&
            line.split(' ').size <= 5 && parseRowStart(line) == null &&
            !Regex("""^(knit|purl|work|rep|repeat|cont|continue|k|p|change|cut|break)\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("""(?<=[.!])\s+(?=[A-Z(])""")).map { it.trim() }.filter { it.isNotEmpty() }

    // --- Rows ----------------------------------------------------------------------------------

    private class RowBuilder(
        val numbers: List<Int>?, val side: Side?, val round: Boolean,
        val allFollowing: Boolean, first: String,
    ) {
        val text = StringBuilder(first)
        fun openBracket(): Boolean = text.count { it == '(' } > text.count { it == ')' }
        fun first(): Int = numbers?.firstOrNull() ?: 0
        /** A row is complete once it ends with a full stop or a stitch count. */
        fun complete(): Boolean {
            val t = text.toString().trimEnd()
            if (t.endsWith(",") || t.endsWith(":") || t.endsWith(";")) return false
            return t.endsWith(".") || Regex("""(?<![A-Za-z])\d+\s*(?:sts?|stitches)\.?\)?$""", RegexOption.IGNORE_CASE).containsMatchIn(t) || t.endsWith(")")
        }

        fun finish(section: SectionBuilder, warnings: MutableList<String>) {
            if (round) section.round = true
            // "noted: … knit the knits": a lone ellipsis before the text is punctuation, not a stitch.
            var body = text.toString().trim().replace(Regex("""^(?:…|\.\.\.)\s+"""), "")
            // "as Row 2" refers to the text of another row.
            AS_ROW.matchEntire(body)?.let { m ->
                val ref = m.groupValues[1].toInt()
                val target = section.textOf(ref)
                if (target != null) body = target
                else warnings += "${label()} says “${body.trimEnd('.')}”, but row $ref was not found — check this row"
            }
            val (cleanText, note) = splitRemarks(normalizeSizes(body))
            val nums: List<Int?> = numbers ?: listOf(null)
            for (n in nums) section.addRow(n, side, cleanText, note)
            if (allFollowing && nums.size == 1 && nums[0] != null) section.fillAlternate(nums[0]!!, side, cleanText, note, label())
        }

        fun label(): String = numbers?.let { "Row ${it.joinToString(", ")}" } ?: "A row"
    }

    /** Recognises the start of a row and returns a builder for it, or null. */
    private fun parseRowStart(line: String): RowBuilder? {
        ROW_START.matchEntire(line)?.let { m ->
            val unit = m.groupValues[1].lowercase()
            val from = m.groupValues[2].toInt()
            val to = m.groupValues[3].toIntOrNull()
            val side = (m.groupValues[4].ifEmpty { m.groupValues[6] }).uppercase().let { if (it == "RS") Side.RS else if (it == "WS") Side.WS else null }
            val numbers = if (to != null && to > from && to - from < 50) (from..to).toList() else listOf(from)
            return RowBuilder(numbers, side, unit != "row", m.groupValues[5].isNotEmpty(), m.groupValues[7])
        }
        ROW_LIST.matchEntire(line)?.let { m ->
            val numbers = Regex("""\d+""").findAll(m.groupValues[2]).map { it.value.toInt() }.toList()
            val side = m.groupValues[3].uppercase().let { if (it == "RS") Side.RS else if (it == "WS") Side.WS else null }
            return RowBuilder(numbers, side, m.groupValues[1].lowercase() != "row", false, m.groupValues[4])
        }
        ROW_NEXT.matchEntire(line)?.let { m ->
            val side = m.groupValues[3].uppercase().let { if (it == "RS") Side.RS else if (it == "WS") Side.WS else null }
            return RowBuilder(null, side, m.groupValues[2].lowercase() != "row", false, m.groupValues[4])
        }
        return null
    }

    private fun normalizeSizes(text: String): String =
        text.replace(SIZES_PETITE) { m -> "${m.groupValues[1]} (${m.groupValues[2]}, ${m.groupValues[3]})" }

    /** Moves remarks such as "(4 sts have been increased)" out of the instructions into a note. */
    private fun splitRemarks(text: String): Pair<String, String> {
        val notes = REMARK.findAll(text).map { it.groupValues[1].trim() }.toList()
        val cleaned = text.replace(REMARK, "").replace(Regex("""\s+([,.])"""), "$1").replace(Regex("""\s{2,}"""), " ").trim()
        return cleaned to notes.joinToString("; ")
    }

    // --- Sentences -----------------------------------------------------------------------------

    private fun times(word: String): SizedInt? =
        NUMBER_WORDS[word.lowercase()]?.let { SizedInt(it) } ?: RowParser.parseSized(word)

    private fun handleSentence(
        sentence: String, section: SectionBuilder?, warnings: MutableList<String>,
        @Suppress("UNUSED_PARAMETER") replaceSection: (SectionBuilder) -> SectionBuilder,
        onCastOn: (SizedInt) -> Unit, onWsFirst: () -> Unit, onMarkers: (List<Int>) -> Unit,
        onFrame: (Side, String, String) -> Unit,
    ) {
        val s = normalizeSizes(sentence)
        FRAME.find(s)?.let { m -> onFrame(if (m.groupValues[1].equals("RS", true)) Side.RS else Side.WS, m.groupValues[2].trim(), m.groupValues[3].trim()) }
        CAST_ON.find(s)?.let { m -> RowParser.parseSized(m.groupValues[1])?.let(onCastOn) }
        if (FIRST_IS_WS.containsMatchIn(s)) onWsFirst()
        markerSetup(s)?.let(onMarkers)
        if (section == null || section.items.isEmpty()) {
            section?.let { if (it.description.length < 400) it.description = (it.description + " " + s).trim() }
            return
        }
        PLAIN_ROWS.matchEntire(s.trim())?.let { m ->
            val n = times(m.groupValues[2])
            if (n != null && n.values.size == 1) {
                val stitch = if (m.groupValues[1].equals("knit", true)) "k" else "p"
                val text = (m.groupValues[4].takeIf { it.isNotEmpty() }?.let { "Using yarn $it, " } ?: "") + "$stitch to end."
                section.round = section.round || !m.groupValues[3].lowercase().startsWith("row")
                section.addRow(null, null, text, s)
                val count = n.values[0]
                if (count > 1) { val pos = section.instructionCount(); section.items += ImportItem.Repeat(pos, pos, SizedInt(count - 1), s) }
                return
            }
        }
        val repeat = parseRepeat(s, section, warnings)
        if (repeat != null) { section.items += repeat; return }
        // Short notes between rows are kept; long prose is left out.
        if (s.length <= 200) section.items += ImportItem.Note(s)
    }

    /** "2 sts, place a marker, 2 sts, place a marker, 2 sts" → [2, 4]. */
    private fun markerSetup(s: String): List<Int>? {
        if (!Regex("""marker|\bpm\b""", RegexOption.IGNORE_CASE).containsMatchIn(s)) return null
        // Stitches as "2 sts", "work 13" or "K1"; markers as "place (a) marker" or "pm".
        val tokens = Regex("""(\d+)\s*(?:sts?|stitches)\b|\b(?:k|p|work)\s*(\d+)\b(?!\s*(?:sts?|stitches))|place\s+(?:a\s+)?(?:stitch\s+)?marker|\bpm\b""", RegexOption.IGNORE_CASE).findAll(s).toList()
        var count = 0
        var numbers = 0
        val markers = ArrayList<Int>()
        for (t in tokens) {
            val n = t.groupValues[1].toIntOrNull() ?: t.groupValues[2].toIntOrNull()
            if (n != null) { count += n; numbers++ } else markers += count
        }
        return markers.takeIf { it.isNotEmpty() && numbers >= 2 && numbers > markers.size - 1 }
    }

    private fun parseRepeat(s: String, section: SectionBuilder, warnings: MutableList<String>): ImportItem.Repeat? {
        val rows = section.instructionCount()
        fun more(totalWord: Boolean, countText: String, moreWord: Boolean): SizedInt? {
            val n = times(countText) ?: return null
            return when {
                moreWord -> n
                totalWord -> SizedInt(n.values.map { it - 1 })
                else -> {
                    warnings += "“${s.trimEnd('.')}” was read as ${n} times in total — check whether the pattern means ${n} more times"
                    SizedInt(n.values.map { it - 1 })
                }
            }
        }
        REPEAT_NAMED.find(s)?.let { m ->
            val from = section.positionOf(m.groupValues[1].toInt())
            val to = m.groupValues[2].toIntOrNull()?.let { section.positionOf(it) } ?: from
            val n = more(m.groupValues[3].isNotEmpty(), m.groupValues[4], m.groupValues[5].isNotEmpty()) ?: return null
            if (from == null || to == null || to < from) {
                warnings += "“${s.trimEnd('.')}” refers to rows that were not found — add this repeat yourself"
                return null
            }
            if (UNTIL.containsMatchIn(s)) { warnings += "“${s.trimEnd('.')}” depends on a measurement — check the number of repeats" }
            return n.takeIf { it.values.any { v -> v > 0 } }?.let { ImportItem.Repeat(from, to, it, s) }
        }
        REPEAT_LAST.find(s)?.let { m ->
            val count = NUMBER_WORDS[m.groupValues[1].lowercase()] ?: m.groupValues[1].toIntOrNull() ?: return null
            val n = more(m.groupValues[2].isNotEmpty(), m.groupValues[3], m.groupValues[4].isNotEmpty()) ?: return null
            if (count > rows) { warnings += "“${s.trimEnd('.')}” needs $count rows but only $rows were found — check it"; return null }
            return n.takeIf { it.values.any { v -> v > 0 } }?.let { ImportItem.Repeat(rows - count + 1, rows, it, s) }
        }
        REPEAT_LAST_ONE.find(s)?.let { m ->
            val n = more(m.groupValues[1].isNotEmpty(), m.groupValues[2], m.groupValues[3].isNotEmpty()) ?: return null
            if (rows == 0) return null
            return n.takeIf { it.values.any { v -> v > 0 } }?.let { ImportItem.Repeat(rows, rows, it, s) }
        }
        if (Regex("""^(repeat|rep|work|continue)\b""", RegexOption.IGNORE_CASE).containsMatchIn(s) && UNTIL.containsMatchIn(s)) {
            section.openEnded = true
            warnings += "“${s.trimEnd('.').take(90)}” — repeat until a length is reached: set the number of repeats yourself"
        }
        return null
    }

    // --- Section assembly ----------------------------------------------------------------------

    private class SectionBuilder(var title: String) {
        var round = false
        var firstSide = Side.RS
        var castOn: SizedInt? = null
        var description = ""
        var markers: List<Int> = emptyList()
        var openEnded = false
        /** Edge stitches worked before and after every row of a chart, per side. */
        val frames = HashMap<Side, Pair<String, String>>()
        val items = ArrayList<ImportItem>()
        var lastNumber: Int? = null
        private val numberToPosition = HashMap<Int, Int>()
        private var sideSeen = false

        fun instructionCount() = items.count { it is ImportItem.Instruction }
        fun positionOf(number: Int): Int? = numberToPosition[number]
        fun textOf(number: Int): String? = positionOf(number)?.let { pos -> items.filterIsInstance<ImportItem.Instruction>().getOrNull(pos - 1)?.text }

        /** "Row 2 and all WS rows: …" — the text used for every later row of the same parity the pattern skips. */
        private var alternate: ImportItem.Instruction? = null
        private var alternateLabel = ""
        var filled = 0

        fun addRow(number: Int?, side: Side?, text: String, note: String) {
            val alt = alternate
            val last = lastNumber
            if (alt != null && number != null && last != null && number - last in 2..50) {
                for (k in last + 1 until number) if ((k - alt.number!!) % 2 == 0) { add(ImportItem.Instruction(k, alt.side, alt.text, alt.note)); filled++ }
            }
            if (!sideSeen && side != null && instructionCount() == 0) { firstSide = side; sideSeen = true }
            add(ImportItem.Instruction(number, side, text, note))
        }

        private fun add(row: ImportItem.Instruction) {
            items += row
            row.number?.let { numberToPosition.getOrPut(it) { instructionCount() }; lastNumber = it }
        }

        fun fillAlternate(number: Int, side: Side?, text: String, note: String, label: String) {
            alternate = ImportItem.Instruction(number, side, text, note)
            alternateLabel = label
        }

        fun alternateWarning(): String? = when {
            alternate == null -> null
            filled == 0 -> "$alternateLabel applies to several rows; it was added once — add the other rows or a repeat if needed"
            else -> "$filled rows were filled in from “$alternateLabel and all … rows” — check them"
        }

        fun build(): ImportedSection {
            // Drop boilerplate, and keep at most a few notes after the last row: what follows is usually finishing prose.
            val kept = items.filterNot { it is ImportItem.Note && BOILERPLATE.containsMatchIn(it.text) }
            val lastWork = kept.indexOfLast { it !is ImportItem.Note }
            var trimmed = kept.take(lastWork + 1) + kept.drop(lastWork + 1).take(MAX_TRAILING_NOTES)
            if (frames.isNotEmpty() && !round) {
                val firstNumber = trimmed.filterIsInstance<ImportItem.Instruction>().firstNotNullOfOrNull { it.number } ?: 1
                trimmed = trimmed.map { item ->
                    if (item !is ImportItem.Instruction || item.number == null) return@map item
                    val side = item.side ?: if ((item.number - firstNumber) % 2 == 0) firstSide else firstSide.flipped()
                    val (pre, post) = frames[side] ?: return@map item
                    item.copy(text = framed(item.text, pre, post), note = listOf(item.note, "includes the edge stitches").filter { it.isNotBlank() }.joinToString("; "))
                }
            }
            return ImportedSection(title, round, if (round) Side.RS else firstSide, castOn, description.trim(), trimmed, markers, openEnded)
        }
    }

    // --- Conversion ----------------------------------------------------------------------------

    /**
     * Turns the import into ordinary written sections of a pattern. With a [library], a part
     * that does not state its own cast-on starts with the stitches (and markers) the part before
     * it ended with, so its rows can be checked too.
     */
    fun toSections(
        import: PdfImport, pdfName: String,
        library: studio.knit.stitch.StitchLibrary? = null,
        newId: () -> String,
    ): List<Section> {
        val out = ArrayList<Section>()
        for (s in import.sections) {
            var section = Section(
                id = newId(),
                title = s.title,
                description = s.description.take(300),
                construction = if (s.round) Construction.ROUND else Construction.FLAT,
                firstRowSide = s.firstSide,
                castOn = s.castOn,
                importedFrom = pdfName,
                markers = s.markers,
                rows = s.items.map { item ->
                    when (item) {
                        is ImportItem.Instruction -> Row(newId(), RowKind.INSTRUCTION, text = item.text, note = item.note)
                        is ImportItem.Repeat -> Row(newId(), RowKind.REPEAT_ROWS, repeatFrom = item.from, repeatTo = item.to, repeatTimes = item.more)
                        is ImportItem.Note -> Row(newId(), RowKind.NOTE, text = item.text)
                    }
                },
            )
            val prev = out.lastOrNull()
            val prevOpen = import.sections.getOrNull(out.size - 1)?.openEnded == true
            if (library != null && prev != null && section.castOn == null && !prevOpen) {
                // Every size is followed separately, as the counts differ per size.
                val ends = (0 until import.sizeCount).map { studio.knit.written.evaluateSection(prev, it, library) }
                val counts = ends.map { it.endCount }
                if (counts.all { it != null && it > 0 }) {
                    section = section.copy(castOn = SizedInt(counts.map { it!! }.let { c -> if (c.distinct().size == 1) listOf(c[0]) else c }))
                    val last = ends[0].rows.lastOrNull()
                    val markers = last?.eval?.markersOut
                    if (section.markers.isEmpty() && markers != null && import.sizeCount >= 1) {
                        val total = counts[0]!!
                        section = section.copy(markers = if (prev.construction == Construction.ROUND) markers else markers.map { total - it }.sorted())
                    }
                }
            }
            out += section
        }
        return out
    }

    /** Puts the edge stitches around a chart row: "pre, row, post", keeping a stated count at the end. */
    private fun framed(text: String, pre: String, post: String): String {
        val m = Regex("""^(.*?)(?:[.,]?\s*(\(?\d+(?:\s*\([\d,\s]+\))?\s*sts?\.?\)?))?\.?\s*$""", RegexOption.IGNORE_CASE).find(text.trim())
        var body = m?.groupValues?.get(1)?.trim()?.trimEnd('.', ',') ?: text
        val tail = m?.groupValues?.get(2).orEmpty()
        if (WHOLE_ROW_TEXT.matches(body)) {
            // "Knit the knits and purl the purls" runs up to the edge stitches at the end.
            val lib = studio.knit.stitch.StitchLibrary()
            val edge = studio.knit.written.RowEvaluator().evaluate(RowParser(lib).parse(post), null, compact = true).consumed
            body = if (edge != null && edge > 0) "work to last $edge sts" else "work to end"
        }
        return "$pre, $body, $post." + if (tail.isNotEmpty()) " $tail" else ""
    }
}
