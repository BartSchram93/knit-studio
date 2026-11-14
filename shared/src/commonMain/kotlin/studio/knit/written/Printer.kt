package studio.knit.written

import studio.knit.model.SizedInt
import studio.knit.model.StitchDef

/**
 * Turns the structured form of a row back into conventional pattern text.
 * `print(parse(x))` is stable: parsing the output yields the same structure again.
 */
class RowPrinter(private val uk: Boolean = false) {

    fun print(nodes: List<Node>, stated: SizedInt? = null, capitalize: Boolean = true): String {
        val body = seq(nodes)
        val text = if (stated != null) "$body ($stated sts)" else body
        if (!capitalize) return text
        // Capitalise the first letter, which may follow a "*" or "(".
        val i = text.indexOfFirst { it.isLetter() }
        return if (i < 0) text else text.substring(0, i) + text[i].uppercase() + text.substring(i + 1)
    }

    private fun seq(nodes: List<Node>): String {
        val sb = StringBuilder()
        var pendingColor: String? = null
        var first = true
        for (n in nodes) {
            if (n is Node.Color) { pendingColor = n.name; continue }
            val text = one(n)
            if (text.isEmpty()) continue
            if (!first) sb.append(", ")
            if (pendingColor != null) { sb.append("with ").append(pendingColor).append(' '); pendingColor = null }
            sb.append(text)
            first = false
        }
        return sb.toString()
    }

    private fun one(n: Node): String = when (n) {
        is Node.Color -> ""
        is Node.Unknown -> n.text
        is Node.Stitch -> stitch(n.def, n.count)
        is Node.Group -> group(n)
    }

    private fun stitch(def: StitchDef, count: Count): String {
        val abbr = def.abbrFor(uk)
        return when (count) {
            is Count.ToEnd -> {
                val leave = count.leaving
                val verb = when (def.id) { "k" -> "knit"; "p" -> "purl"; else -> abbr }
                if (leave.values.all { it == 0 }) "$verb to end" else "$verb to last ${sts(leave)}"
            }
            is Count.Fixed -> {
                val n = count.n
                val one = !n.isSized && n.values[0] == 1
                when {
                    def.id == "k" || def.id == "p" -> "$abbr$n"
                    def.id == "sl" -> "sl$n"
                    def.id == "slwyif" -> "sl$n wyif"
                    def.id == "ktbl" -> "k${n}tbl".takeIf { !n.isSized } ?: "ktbl $n times"
                    def.id == "ptbl" -> "p${n}tbl".takeIf { !n.isSized } ?: "ptbl $n times"
                    def.id == "bo" || def.id == "co" -> "$abbr $n ${if (one) "st" else "sts"}"
                    one -> abbr
                    !n.isSized && n.values[0] == 2 -> "$abbr twice"
                    else -> "$abbr $n times"
                }
            }
        }
    }

    private fun group(g: Node.Group): String {
        val body = seq(g.body)
        return if (g.star) {
            when (val t = g.times) {
                is Count.ToEnd ->
                    if (t.leaving.values.all { it == 0 }) "*$body; rep from * to end"
                    else "*$body; rep from * to last ${sts(t.leaving)}"
                is Count.Fixed -> {
                    val more = SizedInt(t.n.values.map { it - 1 })
                    when {
                        !more.isSized && more.values[0] <= 0 -> body
                        !more.isSized && more.values[0] == 1 -> "*$body; rep from * once more"
                        else -> "*$body; rep from * $more more times"
                    }
                }
            }
        } else {
            when (val t = g.times) {
                is Count.ToEnd ->
                    if (t.leaving.values.all { it == 0 }) "($body) to end" else "($body) to last ${sts(t.leaving)}"
                is Count.Fixed -> when {
                    !t.n.isSized && t.n.values[0] == 1 -> "($body)"
                    !t.n.isSized && t.n.values[0] == 2 -> "($body) twice"
                    else -> "($body) ${t.n} times"
                }
            }
        }
    }

    private fun sts(n: SizedInt): String = if (!n.isSized && n.values[0] == 1) "1 st" else "$n sts"
}
