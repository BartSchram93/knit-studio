package studio.knit.stitch

/**
 * Chart symbols as resolution-independent drawing primitives.
 *
 * A glyph is described once, in a unit coordinate system (x: 0..width, y: 0..1, y pointing down),
 * and rendered by both the on-screen chart canvas and the PDF writer. That guarantees the printed
 * chart shows exactly the symbols the editor shows.
 */
sealed interface Prim {
    /** Stroked line. */
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Prim

    /** Stroked (or filled) polyline. */
    data class Poly(val points: List<Float>, val closed: Boolean = false, val filled: Boolean = false) : Prim

    /** Circle, stroked or filled. */
    data class Dot(val cx: Float, val cy: Float, val r: Float, val filled: Boolean) : Prim

    /** Short text centred in the cell. */
    data class Label(val text: String) : Prim

    /** Grey wash over the whole cell ("no stitch"). */
    data object Shade : Prim
}

object Glyphs {
    /** Symbols offered when the user designs a custom stitch. */
    val pickable: List<String> = listOf(
        "blank", "dot", "dash", "bar", "circle", "ring_dot", "slash_r", "slash_l", "cross", "caret",
        "vee", "wye", "loop", "arc", "plus", "diamond", "triangle", "square", "bobble", "star",
    )

    fun isText(key: String) = key.startsWith("text:")
    fun textOf(key: String) = key.removePrefix("text:")

    /** Primitives for [key] drawn across [width] cells. Unknown keys render as a question mark. */
    fun prims(key: String, width: Int = 1): List<Prim> {
        val w = width.toFloat()
        val cx = w / 2f
        if (isText(key)) return listOf(Prim.Label(textOf(key).take(4)))
        if (key.startsWith("cable_")) return cable(key, w)
        return when (key) {
            "blank" -> emptyList()
            "dot" -> listOf(Prim.Dot(cx, .5f, .13f, true))
            "dash" -> listOf(Prim.Line(cx - .28f, .5f, cx + .28f, .5f))
            "bar" -> listOf(Prim.Line(cx, .2f, cx, .8f))
            "circle" -> listOf(Prim.Dot(cx, .5f, .27f, false))
            "ring_dot" -> listOf(Prim.Dot(cx, .5f, .29f, false), Prim.Dot(cx, .5f, .09f, true))
            "slash_r" -> listOf(Prim.Line(cx - .28f, .8f, cx + .28f, .2f))
            "slash_l" -> listOf(Prim.Line(cx - .28f, .2f, cx + .28f, .8f))
            "slash_r_dot" -> listOf(Prim.Line(cx - .28f, .8f, cx + .28f, .2f), Prim.Dot(cx - .22f, .3f, .08f, true))
            "slash_l_dot" -> listOf(Prim.Line(cx - .28f, .2f, cx + .28f, .8f), Prim.Dot(cx + .22f, .3f, .08f, true))
            // Three stitches leaning right / left.
            "triple_r" -> listOf(
                Prim.Line(cx - .3f, .8f, cx + .3f, .2f),
                Prim.Line(cx - .02f, .8f, cx + .3f, .2f),
            )
            "triple_l" -> listOf(
                Prim.Line(cx - .3f, .2f, cx + .3f, .8f),
                Prim.Line(cx - .3f, .2f, cx + .02f, .8f),
            )
            "triple_r_dot" -> prims("triple_r", width) + Prim.Dot(cx - .22f, .3f, .08f, true)
            // Centred double decrease.
            "caret" -> listOf(
                Prim.Poly(listOf(cx - .28f, .8f, cx, .2f, cx + .28f, .8f)),
                Prim.Line(cx, .2f, cx, .8f),
            )
            "cross" -> listOf(Prim.Line(cx - .25f, .25f, cx + .25f, .75f), Prim.Line(cx - .25f, .75f, cx + .25f, .25f))
            "vee" -> listOf(Prim.Poly(listOf(cx - .25f, .25f, cx, .78f, cx + .25f, .25f)))
            "vee_bar" -> listOf(
                Prim.Poly(listOf(cx - .25f, .25f, cx, .78f, cx + .25f, .25f)),
                Prim.Line(cx - .3f, .5f, cx + .3f, .5f),
            )
            "wye" -> listOf(
                Prim.Poly(listOf(cx - .25f, .2f, cx, .52f, cx + .25f, .2f)),
                Prim.Line(cx, .52f, cx, .82f),
            )
            "wye_dot" -> prims("wye", width) + Prim.Dot(cx + .27f, .68f, .07f, true)
            // Twisted stitch: a loop with crossed legs.
            "loop" -> listOf(
                Prim.Poly(listOf(cx - .24f, .84f, cx + .15f, .42f, cx + .12f, .24f, cx, .16f, cx - .12f, .24f, cx - .15f, .42f, cx + .24f, .84f)),
            )
            "loop_dot" -> prims("loop", width) + Prim.Dot(cx, .7f, .07f, true)
            "arc" -> listOf(Prim.Poly(listOf(cx - .32f, .62f, cx - .2f, .42f, cx, .34f, cx + .2f, .42f, cx + .32f, .62f)))
            "plus" -> listOf(Prim.Line(cx - .25f, .5f, cx + .25f, .5f), Prim.Line(cx, .25f, cx, .75f))
            "diamond" -> listOf(Prim.Poly(listOf(cx, .2f, cx + .28f, .5f, cx, .8f, cx - .28f, .5f), closed = true))
            "triangle" -> listOf(Prim.Poly(listOf(cx, .22f, cx + .28f, .76f, cx - .28f, .76f), closed = true))
            "square" -> listOf(Prim.Poly(listOf(cx - .24f, .26f, cx + .24f, .26f, cx + .24f, .74f, cx - .24f, .74f), closed = true))
            "bobble" -> listOf(Prim.Dot(cx, .5f, .28f, true))
            "star" -> listOf(
                Prim.Line(cx, .2f, cx, .8f),
                Prim.Line(cx - .26f, .35f, cx + .26f, .65f),
                Prim.Line(cx - .26f, .65f, cx + .26f, .35f),
            )
            "m_left" -> listOf(Prim.Label("ML"))
            "m_right" -> listOf(Prim.Label("MR"))
            "m_one" -> listOf(Prim.Label("M"))
            "m_purl" -> listOf(Prim.Label("MP"))
            "shade" -> listOf(Prim.Shade)
            else -> listOf(Prim.Label("?"))
        }
    }

    /**
     * Cable crossings. Key: `cable_l` / `cable_r` (front strands lean left / right),
     * optionally suffixed `_p` when the back strands are purled.
     * The front strands are drawn unbroken; the strands passing behind are drawn interrupted.
     */
    private fun cable(key: String, w: Float): List<Prim> {
        val left = key.startsWith("cable_l")
        val purl = key.endsWith("_p")
        val m = .12f
        val top = .14f
        val bot = .86f
        val mid = w / 2f
        // Left cross: front strands travel from bottom-right to top-left, drawn as "\".
        val front = listOf(
            Prim.Line(m, top, mid, bot),
            Prim.Line(mid, top, w - m, bot),
        )
        // Back strands, broken where they pass behind.
        val back = listOf(
            Prim.Line(m, bot, m + (mid - m) * .34f, bot - (bot - top) * .34f),
            Prim.Line(w - m, top, w - m - (mid - m) * .34f, top + (bot - top) * .34f),
        )
        val all = front + back + if (purl) listOf(Prim.Dot(m + .1f, .5f + .22f, .06f, true)) else emptyList()
        return if (left) all else all.map { mirror(it, w) }
    }

    private fun mirror(p: Prim, w: Float): Prim = when (p) {
        is Prim.Line -> Prim.Line(w - p.x1, p.y1, w - p.x2, p.y2)
        is Prim.Dot -> p.copy(cx = w - p.cx)
        is Prim.Poly -> p.copy(points = p.points.mapIndexed { i, v -> if (i % 2 == 0) w - v else v })
        else -> p
    }
}
