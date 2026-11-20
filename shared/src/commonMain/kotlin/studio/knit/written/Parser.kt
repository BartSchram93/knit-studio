package studio.knit.written

import studio.knit.model.SizedInt
import studio.knit.model.StitchDef
import studio.knit.stitch.StitchLibrary

/*
 * Structured representation of one written row and the parser that produces it.
 *
 * "K2, *p2, k2; rep from * to last 2 sts, k2 (24 sts)" becomes
 *
 *   Stitch(k ×2), Group(star)[Stitch(p ×2), Stitch(k ×2)] to-end(leaving 2), Stitch(k ×2), stated = 24
 *
 * The text the user typed stays the source of truth; this tree is what the engine reasons about.
 */

/** How many times a stitch or group is worked. */
sealed interface Count {
    /** A fixed number, possibly different per size. */
    data class Fixed(val n: SizedInt) : Count {
        constructor(n: Int) : this(SizedInt(n))
    }

    /** As many as fit until [leaving] stitches remain on the left needle ("to last 2 sts"). */
    data class ToEnd(val leaving: SizedInt = SizedInt(0)) : Count
}

sealed interface Node {
    /** A stitch worked [count] times: `k3`, `yo`, `k to end`. */
    data class Stitch(val def: StitchDef, val count: Count = Count.Fixed(1)) : Node

    /**
     * A repeated group. [star] distinguishes `*…; rep from *` from bracketed `(…) 3 times`.
     */
    data class Group(val body: List<Node>, val times: Count, val star: Boolean = false) : Node

    /** Change of working colour for the stitches that follow: `with B`. */
    data class Color(val name: String) : Node

    /** Text the parser could not interpret. Always accompanied by a [ParseError]. */
    data class Unknown(val text: String) : Node
}

data class ParseError(val message: String, val fragment: String)

data class ParsedRow(
    val nodes: List<Node>,
    /** Stitch count the author wrote at the end of the row, e.g. `(24 sts)`. */
    val stated: SizedInt? = null,
    val errors: List<ParseError> = emptyList(),
) {
    val isEmpty: Boolean get() = nodes.isEmpty()
    val hasOpenEnded: Boolean get() = nodes.any { it.isOpenEnded() }
}

fun Node.isOpenEnded(): Boolean = when (this) {
    is Node.Stitch -> count is Count.ToEnd
    is Node.Group -> times is Count.ToEnd || body.any { it.isOpenEnded() }
    else -> false
}

class RowParser(private val library: StitchLibrary) {

    fun parse(input: String): ParsedRow {
        var text = input.trim()
        if (text.isEmpty()) return ParsedRow(emptyList())
        text = stripLabel(text)
        val (body, stated) = extractStated(text)
        val p = Cursor(normalize(body))
        val errors = ArrayList<ParseError>()
        val nodes = parseSequence(p, closing = null, errors = errors)
        return ParsedRow(nodes, stated, errors)
    }

    // -----------------------------------------------------------------------------------------

    private class Cursor(val s: String) {
        var i = 0
        val atEnd get() = i >= s.length
        fun peek(): Char = s[i]
        fun rest(): String = s.substring(i)
        fun skipSpace() { while (!atEnd && s[i].isWhitespace()) i++ }
    }

    private fun normalize(s: String): String =
        s.lowercase()
            .replace('–', '-').replace('—', '-').replace('×', 'x')
            .replace('{', '(').replace('}', ')')
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Removes a leading `Row 3 (RS):` / `Rnd 2:` / `Round 4 -` label if the user typed one. */
    private fun stripLabel(s: String): String =
        LABEL.find(s)?.let { s.substring(it.range.last + 1).trim() } ?: s

    /** Splits off a trailing stitch count such as `(24 sts)`, `— 24 sts.` or `. 20 (24, 28) sts`. */
    private fun extractStated(s: String): Pair<String, SizedInt?> {
        val m = STATED.find(s) ?: return s to null
        val sized = parseSized(m.groupValues[1]) ?: return s to null
        if (s.substring(0, m.range.first).trimEnd().endsWith("last", ignoreCase = true)) return s to null
        return s.substring(0, m.range.first).trim().trimEnd(',', ';', '.', '-', '–', '—').trim() to sized
    }

    private fun parseSequence(p: Cursor, closing: Char?, errors: MutableList<ParseError>): List<Node> {
        val out = ArrayList<Node>()
        while (true) {
            // Skip separators.
            while (!p.atEnd && (p.peek().isWhitespace() || p.peek() == ',' || p.peek() == ';' || p.peek() == '.')) p.i++
            if (p.atEnd) break
            val c = p.peek()
            if (closing != null && c == closing) break
            when {
                c == ')' || c == ']' -> {
                    errors += ParseError("Unmatched “$c”", c.toString()); p.i++
                }
                c == '(' || c == '[' -> {
                    val close = if (c == '(') ')' else ']'
                    p.i++
                    val body = parseSequence(p, close, errors)
                    if (!p.atEnd && p.peek() == close) p.i++
                    else errors += ParseError("Missing closing “$close”", c.toString())
                    val times = parseMultiplier(p)
                    if (body.isEmpty()) errors += ParseError("Empty brackets", "$c$close")
                    else out += Node.Group(body, times ?: Count.Fixed(1), star = false)
                }
                c == '*' -> {
                    while (!p.atEnd && p.peek() == '*') p.i++
                    val body = parseStarBody(p, closing, errors)
                    val times = parseRepFrom(p)
                    if (body.isEmpty()) errors += ParseError("Nothing to repeat after “*”", "*")
                    else out += Node.Group(body, times ?: Count.ToEnd(), star = true)
                }
                else -> {
                    // "rep from * …" without an opening star repeats everything written so far.
                    if (REP_FROM.find(p.rest())?.range?.first == 0) {
                        val times = parseRepFrom(p) ?: Count.ToEnd()
                        if (out.isEmpty()) errors += ParseError("Nothing to repeat", "rep from *")
                        else {
                            val body = out.toList()
                            out.clear()
                            out += Node.Group(body, times, star = true)
                        }
                    } else {
                        parseChunk(p, out, errors)
                    }
                }
            }
        }
        return out
    }

    /** Body of a star repeat: everything up to `rep from *`, the closing bracket or the end. */
    private fun parseStarBody(p: Cursor, closing: Char?, errors: MutableList<ParseError>): List<Node> {
        val out = ArrayList<Node>()
        while (true) {
            while (!p.atEnd && (p.peek().isWhitespace() || p.peek() == ',' || p.peek() == ';' || p.peek() == '.')) p.i++
            if (p.atEnd) break
            if (closing != null && p.peek() == closing) break
            if (REP_FROM.find(p.rest())?.range?.first == 0) break
            val c = p.peek()
            when (c) {
                '(', '[' -> {
                    val close = if (c == '(') ')' else ']'
                    p.i++
                    val body = parseSequence(p, close, errors)
                    if (!p.atEnd && p.peek() == close) p.i++ else errors += ParseError("Missing closing “$close”", c.toString())
                    val times = parseMultiplier(p)
                    if (body.isNotEmpty()) out += Node.Group(body, times ?: Count.Fixed(1))
                }
                ')', ']', '*' -> { errors += ParseError("Unexpected “$c” inside a repeat", c.toString()); p.i++ }
                else -> parseChunk(p, out, errors)
            }
        }
        return out
    }

    /** `rep from * to end`, `repeat from * to last 3 sts`, `rep from * 3 more times`, … */
    private fun parseRepFrom(p: Cursor): Count? {
        p.skipSpace()
        val m = REP_FROM.find(p.rest()) ?: return null
        if (m.range.first != 0) return null
        p.i += m.range.last + 1
        return parseMultiplier(p, afterRep = true) ?: Count.ToEnd()
    }

    /**
     * What follows a group: `3 times`, `x 3`, `twice`, `to end`, `to last 2 sts`, `4 more times`.
     * Returns null (consuming nothing) when there is no multiplier.
     */
    private fun parseMultiplier(p: Cursor, afterRep: Boolean = false): Count? {
        p.skipSpace()
        // Designers often write "(k1, p1) repeat to end" / "(…) rep 3 times": the word
        // "repeat" belongs to the multiplier that follows it.
        if (!afterRep) {
            val lead = REPEAT_WORD.find(p.rest())
            if (lead != null && lead.range.first == 0) {
                val saved = p.i
                p.i += lead.range.last + 1
                parseMultiplier(p, afterRep = false)?.let { return it }
                p.i = saved
            }
        }
        val rest = p.rest()
        for ((regex, make) in MULTIPLIERS) {
            val m = regex.find(rest) ?: continue
            if (m.range.first != 0) continue
            val count = make(m, afterRep) ?: continue
            p.i += m.range.last + 1
            return count
        }
        return null
    }

    /** One comma-separated instruction that is not a group: `k3`, `k to last 2 sts`, `with B k2`. */
    private fun parseChunk(p: Cursor, out: MutableList<Node>, errors: MutableList<ParseError>) {
        val start = p.i
        while (!p.atEnd) {
            val c = p.peek()
            if (c == ',' || c == ';' || c == ')' || c == ']' || c == '*' || c == '[') break
            if (c == '(') {
                // `k10 (12, 14)` — a size list belongs to this chunk; anything else starts a group.
                val close = p.s.indexOf(')', p.i)
                val inner = if (close > 0) p.s.substring(p.i + 1, close) else ""
                val prev = p.s.substring(start, p.i).trimEnd()
                if (close > 0 && inner.isNotBlank() && SIZE_LIST.matches(inner) && prev.lastOrNull()?.isDigit() == true) {
                    p.i = close + 1
                    continue
                }
                break
            }
            // A sentence-ending full stop (but not one inside a number).
            if (c == '.' && !(p.i + 1 < p.s.length && p.s[p.i + 1].isDigit())) break
            // Stop before an inline "rep from *".
            if (c == 'r' && REP_FROM.find(p.s, p.i)?.range?.first == p.i && p.i > start) break
            p.i++
        }
        var chunk = p.s.substring(start, p.i).trim()
        if (chunk.isEmpty()) { if (!p.atEnd && p.i == start) p.i++; return }

        // Colour prefix.
        COLOR.find(chunk)?.let { m ->
            out += Node.Color(m.groupValues[2].uppercase())
            chunk = chunk.substring(m.range.last + 1).trim()
        }
        if (chunk.isEmpty()) return

        if (chunk == "turn" || chunk.startsWith("w&t") || chunk.startsWith("wrap and turn")) {
            out += Node.Unknown(chunk)
            errors += ParseError("Short rows (“$chunk”) are not counted automatically", chunk)
            return
        }

        val node = parseStitch(chunk)
        if (node != null) out += node
        else {
            out += Node.Unknown(chunk)
            errors += ParseError("Unknown instruction “$chunk”", chunk)
        }
    }

    private fun parseStitch(chunkIn: String): Node? {
        var chunk = chunkIn
        // k2tbl / p3tbl → ktbl ×2
        TBL.matchEntire(chunk)?.let { m ->
            val def = library[if (m.groupValues[1] == "k") "ktbl" else "ptbl"] ?: return null
            return Node.Stitch(def, Count.Fixed(m.groupValues[2].toInt()))
        }
        // sl2 / slip 2 → sl ×2 (plain "sl1" is covered by aliases)
        SLIP_N.matchEntire(chunk)?.let { m ->
            val def = library[if (m.groupValues[2].contains("wyif")) "slwyif" else "sl"] ?: return null
            return Node.Stitch(def, Count.Fixed(m.groupValues[1].toInt()))
        }
        // "work 3 sts in pattern" style text is not interpretable.
        chunk = chunk.removeSuffix(" sts").removeSuffix(" st").removeSuffix(" stitches").trim()

        if (chunk == "knit" || chunk == "purl") {
            // A bare "Knit." means the whole row.
            return library[chunk.substring(0, 1)]?.let { Node.Stitch(it, Count.ToEnd()) }
        }
        for ((spelling, def) in library.spellings) {
            if (!chunk.startsWith(spelling)) continue
            val rest = chunk.substring(spelling.length)
            // Do not split inside a number or a word: "k12" is k ×12, never "k1" + "2".
            if (rest.isNotEmpty()) {
                val boundaryOk = when {
                    rest[0].isDigit() -> !spelling.last().isDigit()
                    rest[0].isLetter() -> false
                    else -> true
                }
                if (!boundaryOk) continue
            }
            val count = parseCount(rest.trim()) ?: continue
            return Node.Stitch(def, count)
        }
        return null
    }

    /** The part after an abbreviation: ``, `3`, `10 (12, 14)`, ` to end`, ` to last 2 sts`, ` twice`. */
    private fun parseCount(rest: String): Count? {
        if (rest.isEmpty()) return Count.Fixed(1)
        parseSized(rest)?.let { return Count.Fixed(it) }
        TO_END.matchEntire(rest)?.let { return Count.ToEnd() }
        TO_LAST.matchEntire(rest)?.let { m -> return parseSized(m.groupValues[1])?.let { Count.ToEnd(it) } }
        TIMES.matchEntire(rest)?.let { m -> return Count.Fixed(m.groupValues[1].toInt()) }
        return when (rest) {
            "once" -> Count.Fixed(1)
            "twice" -> Count.Fixed(2)
            else -> null
        }
    }

    companion object {
        private val LABEL = Regex("^(row|rnd|round|rows|rnds)\\s*\\d+\\s*(\\((rs|ws)\\))?\\s*[:\\-–—.]", RegexOption.IGNORE_CASE)
        private const val SIZED = "\\d+(?:\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)*\\s*\\))?"
        private val STATED = Regex(
            "(?:\\(|\\[|[-–—.:]\\s*)\\s*($SIZED)\\s*(?:sts?|stitches)\\s*(?:total|remain|remaining|rem)?\\s*[)\\]]?\\s*\\.?\\s*$",
            RegexOption.IGNORE_CASE,
        )
        private val SIZE_LIST = Regex("\\s*\\d+(\\s*,\\s*\\d+)*\\s*")
        private val REPEAT_WORD = Regex("^(?:rep|repeat)\\s+")
        private val REP_FROM = Regex("(?:and\\s+)?(?:rep|repeat)\\s+(?:from\\s+)?\\*+")
        private val COLOR = Regex("^(with|using|in)\\s+(mc|cc\\d?|[a-h])\\b[\\s:,]*")
        private val TBL = Regex("^([kp])\\s*(\\d+)\\s*tbl$")
        private val SLIP_N = Regex("^(?:sl|slip)\\s*(\\d+)((?:\\s*(?:wyib|wyif|pwise|purlwise))*)$")
        private val TO_END = Regex("(?:to|until)\\s+(?:the\\s+)?end(?:\\s+of\\s+(?:the\\s+)?(?:row|rnd|round))?|across|around|all")
        private val TO_LAST = Regex("(?:to|until)\\s+(?:the\\s+)?last\\s+($SIZED)\\s*(?:sts?|stitches)?")
        private val TIMES = Regex("(?:x\\s*)?(\\d+)(?:\\s*(?:times|x))?")

        /** Parses `10` or `10 (12, 14)`. */
        fun parseSized(s: String): SizedInt? {
            val m = Regex("^\\s*(\\d+)\\s*(?:\\(([^)]*)\\))?\\s*$").find(s) ?: return null
            val first = m.groupValues[1].toIntOrNull() ?: return null
            val others = m.groupValues[2]
            if (others.isBlank()) return SizedInt(first)
            val rest = others.split(',').map { it.trim().toIntOrNull() ?: return null }
            return SizedInt(listOf(first) + rest)
        }

        private fun sized(s: String) = parseSized(s)

        /**
         * After a bracketed group a number is the total ("(k2tog, yo) 3 times" = 3 in all).
         * After "rep from *" it counts additional repeats ("rep from * twice" = 3 in all).
         */
        private fun fixed(n: SizedInt, afterRep: Boolean): Count =
            Count.Fixed(if (afterRep) SizedInt(n.values.map { it + 1 }) else n)

        private val MULTIPLIERS: List<Pair<Regex, (MatchResult, Boolean) -> Count?>> = listOf(
            Regex("^(?:to|until)\\s+(?:the\\s+)?last\\s+($SIZED)\\s*(?:sts?|stitches)?") to { m, _ -> sized(m.groupValues[1])?.let { Count.ToEnd(it) } },
            Regex("^(?:to|until)\\s+(?:the\\s+)?end(?:\\s+of\\s+(?:the\\s+)?(?:row|rnd|round))?") to { _, _ -> Count.ToEnd() },
            Regex("^(?:across|around)\\b") to { _, _ -> Count.ToEnd() },
            Regex("^($SIZED)\\s+more\\s+times?") to { m, after -> sized(m.groupValues[1])?.let { fixed(it, after) } },
            Regex("^once\\s+more") to { _, after -> fixed(SizedInt(1), after) },
            Regex("^twice\\s+more") to { _, after -> fixed(SizedInt(2), after) },
            Regex("^(?:x\\s*)?($SIZED)\\s*(?:times|x)\\b") to { m, after -> sized(m.groupValues[1])?.let { fixed(it, after) } },
            Regex("^x\\s*($SIZED)") to { m, after -> sized(m.groupValues[1])?.let { fixed(it, after) } },
            Regex("^twice\\b") to { _, after -> fixed(SizedInt(2), after) },
            Regex("^once\\b") to { _, after -> fixed(SizedInt(1), after) },
        )
    }
}
