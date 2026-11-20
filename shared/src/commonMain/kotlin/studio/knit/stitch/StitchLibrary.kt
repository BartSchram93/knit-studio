package studio.knit.stitch

import studio.knit.model.StitchCategory
import studio.knit.model.StitchCategory.BASIC
import studio.knit.model.StitchCategory.CABLE
import studio.knit.model.StitchCategory.DECREASE
import studio.knit.model.StitchCategory.INCREASE
import studio.knit.model.StitchCategory.MARKER
import studio.knit.model.StitchCategory.SPECIAL
import studio.knit.model.StitchCategory.TWISTED
import studio.knit.model.StitchDef
import studio.knit.model.WorksOn

/**
 * The set of stitches known to the engine: the built-in standard stitches plus the user's own.
 * Lookups by id are used by charts; lookups by abbreviation are used by the instruction parser.
 */
class StitchLibrary(custom: List<StitchDef> = emptyList()) {
    /** Built-ins first, then custom stitches. A custom stitch never shadows a built-in id. */
    val all: List<StitchDef> = BuiltInStitches.all + custom.filter { c -> BuiltInStitches.all.none { it.id == c.id } }

    private val byId: Map<String, StitchDef> = all.associateBy { it.id }

    /** Every spelling the parser accepts, lower-case, mapped to its stitch. Longest first. */
    val spellings: List<Pair<String, StitchDef>> = buildList {
        val seen = HashSet<String>()
        for (def in all) {
            for (s in listOfNotNull(def.abbr, def.abbrUk) + def.aliases + def.name) {
                val key = s.trim().lowercase()
                if (key.isNotEmpty() && seen.add(key)) add(key to def)
            }
        }
    }.sortedByDescending { it.first.length }

    operator fun get(id: String): StitchDef? = byId[id]
    fun require(id: String): StitchDef = byId[id] ?: Unknown.copy(id = id, name = "Unknown stitch “$id”")

    /** The stitch whose chart symbol represents working [worked] on a wrong-side row. */
    fun symbolForWorkedOnWs(worked: StitchDef): StitchDef? =
        if (worked.ws == worked.id) worked else all.firstOrNull { it.ws == worked.id && it.id != worked.id }

    val custom: List<StitchDef> get() = all.filter { !it.builtIn }

    fun with(custom: List<StitchDef>): StitchLibrary = StitchLibrary(custom)

    companion object {
        /** Stand-in for ids that are not in the library. Behaves like a plain stitch. */
        val Unknown = StitchDef(id = "?", name = "Unknown stitch", abbr = "?", symbol = "text:?")
    }
}

object BuiltInStitches {
    const val KNIT = "k"
    const val PURL = "p"
    const val NO_STITCH = "none"

    private fun s(
        id: String, name: String, abbr: String, cat: StitchCategory, symbol: String,
        consumes: Int = 1, produces: Int = 1, width: Int = 1, ws: String? = null,
        aliases: List<String> = emptyList(), abbrUk: String? = null, mirror: String? = null,
        worksOn: WorksOn = WorksOn.ANY, description: String = "", instructions: String = "",
    ) = StitchDef(
        id = id, name = name, abbr = abbr, abbrUk = abbrUk, description = description,
        instructions = instructions, consumes = consumes, produces = produces, width = width,
        symbol = symbol, ws = ws, category = cat, worksOn = worksOn, aliases = aliases,
        builtIn = true, mirror = mirror,
    )

    val all: List<StitchDef> = listOf(
        // --- Basic -------------------------------------------------------------------------
        s("k", "Knit", "k", BASIC, "blank", ws = "p", aliases = listOf("knit", "k1"),
            description = "The basic knit stitch. Smooth “V” on the right side.",
            instructions = "Insert the right needle into the next stitch from left to right, wrap the yarn and pull it through."),
        s("p", "Purl", "p", BASIC, "dot", ws = "k", aliases = listOf("purl", "p1"),
            description = "The purl stitch. A bump on the right side.",
            instructions = "With yarn in front, insert the right needle from right to left, wrap and pull through."),
        s("yo", "Yarn over", "yo", INCREASE, "circle", consumes = 0, produces = 1, ws = "yo",
            aliases = listOf("yarn over", "yfwd", "yon", "yrn", "yf", "yarn forward"),
            description = "Makes a new stitch and a decorative hole. Increases 1.",
            instructions = "Bring the yarn over the right needle from front to back before working the next stitch."),
        s("none", "No stitch", "no stitch", SPECIAL, "shade", consumes = 0, produces = 0, ws = "none",
            description = "A placeholder that keeps chart columns aligned. Skip it when knitting."),

        // --- Decreases ---------------------------------------------------------------------
        s("k2tog", "Knit 2 together", "k2tog", DECREASE, "slash_r", consumes = 2, ws = "p2tog", mirror = "ssk",
            description = "Right-leaning decrease. Decreases 1.",
            instructions = "Insert the right needle through the next two stitches together and knit them as one."),
        s("ssk", "Slip, slip, knit", "ssk", DECREASE, "slash_l", consumes = 2, ws = "ssp", mirror = "k2tog",
            description = "Left-leaning decrease. Decreases 1.",
            instructions = "Slip two stitches knitwise one at a time, return them to the left needle and knit them together through the back loops."),
        // Same result and chart symbol as ssk, but worked differently, so patterns name it apart.
        s("skpo", "Slip 1, knit 1, pass over", "skpo", DECREASE, "slash_l", consumes = 2, ws = "ssp", mirror = "k2tog",
            aliases = listOf("skp", "sl1 k1 psso", "sl1 k1 psso1"),
            description = "Left-leaning decrease. Decreases 1. Gives the same result as ssk and uses the same chart symbol; some knitters find it looks slightly looser.",
            instructions = "Slip 1 stitch knitwise, knit 1, then lift the slipped stitch over the knitted stitch and off the needle."),
        s("p2tog", "Purl 2 together", "p2tog", DECREASE, "slash_r_dot", consumes = 2, ws = "k2tog", mirror = "ssp",
            description = "Purl decrease, leans right on the right side. Decreases 1.",
            instructions = "Purl the next two stitches together."),
        s("ssp", "Slip, slip, purl", "ssp", DECREASE, "slash_l_dot", consumes = 2, ws = "ssk", mirror = "p2tog",
            aliases = listOf("p2tog tbl"),
            description = "Purl decrease, leans left on the right side. Decreases 1.",
            instructions = "Slip two stitches knitwise one at a time, return them to the left needle and purl them together through the back loops."),
        s("k3tog", "Knit 3 together", "k3tog", DECREASE, "triple_r", consumes = 3, ws = "p3tog", mirror = "sk2p",
            description = "Right-leaning double decrease. Decreases 2.",
            instructions = "Knit the next three stitches together."),
        s("p3tog", "Purl 3 together", "p3tog", DECREASE, "triple_r_dot", consumes = 3, ws = "k3tog",
            description = "Purl double decrease. Decreases 2.",
            instructions = "Purl the next three stitches together."),
        s("sk2p", "Slip 1, k2tog, pass over", "sk2p", DECREASE, "triple_l", consumes = 3, mirror = "k3tog",
            aliases = listOf("sk2po", "sl1 k2tog psso", "sssk"),
            description = "Left-leaning double decrease. Decreases 2.",
            instructions = "Slip 1 knitwise, knit 2 together, pass the slipped stitch over."),
        s("cdd", "Centred double decrease", "cdd", DECREASE, "caret", consumes = 3, mirror = "cdd",
            aliases = listOf("s2kp", "s2kpo", "s2kp2", "sl2 k1 p2sso"),
            description = "Symmetrical double decrease with the centre stitch on top. Decreases 2.",
            instructions = "Slip 2 together knitwise, knit 1, pass the two slipped stitches over."),
        s("bo", "Bind off", "BO", DECREASE, "arc", consumes = 1, produces = 0, ws = "bo", abbrUk = "cast off",
            aliases = listOf("bind off", "cast off", "bo1"),
            description = "Removes a stitch from the needles for good.",
            instructions = "Work the stitch, then pass the previous stitch on the right needle over it."),

        // --- Increases ---------------------------------------------------------------------
        s("m1", "Make 1", "m1", INCREASE, "m_one", consumes = 0, produces = 1, ws = "m1p", mirror = "m1",
            aliases = listOf("make 1", "make one"),
            description = "Nearly invisible increase worked into the strand between stitches. Increases 1.",
            instructions = "Lift the strand between the needles and knit into it twisted."),
        s("m1l", "Make 1 left", "m1l", INCREASE, "m_left", consumes = 0, produces = 1, mirror = "m1r",
            aliases = listOf("m1-l", "make 1 left"),
            description = "Left-leaning lifted increase. Increases 1.",
            instructions = "Lift the strand from front to back with the left needle and knit it through the back loop."),
        s("m1r", "Make 1 right", "m1r", INCREASE, "m_right", consumes = 0, produces = 1, mirror = "m1l",
            aliases = listOf("m1-r", "make 1 right"),
            description = "Right-leaning lifted increase. Increases 1.",
            instructions = "Lift the strand from back to front with the left needle and knit it through the front loop."),
        s("m1p", "Make 1 purlwise", "m1p", INCREASE, "m_purl", consumes = 0, produces = 1, ws = "m1",
            aliases = listOf("make 1 purl"),
            description = "Lifted increase worked as a purl. Increases 1.",
            instructions = "Lift the strand between the needles and purl into it twisted."),
        s("kfb", "Knit front and back", "kfb", INCREASE, "wye", consumes = 1, produces = 2, ws = "pfb",
            aliases = listOf("k1fb", "kf&b", "k1f&b", "inc"),
            description = "Bar increase. Turns one stitch into two.",
            instructions = "Knit into the front of the stitch without dropping it, then knit into the back of the same stitch."),
        s("pfb", "Purl front and back", "pfb", INCREASE, "wye_dot", consumes = 1, produces = 2, ws = "kfb",
            aliases = listOf("p1fb", "pf&b"),
            description = "Purl bar increase. Turns one stitch into two.",
            instructions = "Purl into the front of the stitch without dropping it, then purl into the back of the same stitch."),
        s("co", "Cast on", "CO", INCREASE, "plus", consumes = 0, produces = 1, ws = "co",
            aliases = listOf("cast on"),
            description = "Adds a new stitch at this point (for example with a backward-loop cast on)."),

        // --- Twisted & slipped ---------------------------------------------------------------
        s("ktbl", "Knit through back loop", "ktbl", TWISTED, "loop", ws = "ptbl",
            aliases = listOf("k1tbl", "k tbl", "k1 tbl"),
            description = "A twisted knit stitch.",
            instructions = "Knit the stitch through its back loop."),
        s("ptbl", "Purl through back loop", "ptbl", TWISTED, "loop_dot", ws = "ktbl",
            aliases = listOf("p1tbl", "p tbl", "p1 tbl"),
            description = "A twisted purl stitch.",
            instructions = "Purl the stitch through its back loop."),
        s("sl", "Slip stitch (yarn in back)", "sl", TWISTED, "vee", ws = "slwyif",
            aliases = listOf("sl1", "s1", "slip", "slip 1", "sl1p", "sl1 wyib", "sl wyib", "sl1 pwise", "s1 pwise", "sl st"),
            description = "Moves a stitch across without working it, yarn held at the back.",
            instructions = "Slip the stitch purlwise with the yarn at the back of the work."),
        s("slwyif", "Slip stitch (yarn in front)", "sl wyif", TWISTED, "vee_bar", ws = "sl",
            aliases = listOf("sl1 wyif", "slip 1 wyif", "sl1wyif"),
            description = "Moves a stitch across without working it, with the strand showing on the right side.",
            instructions = "Slip the stitch purlwise with the yarn at the front of the work."),

        // --- Cables --------------------------------------------------------------------------
        s("c2f", "1/1 left cross", "1/1 LC", CABLE, "cable_l", consumes = 2, produces = 2, width = 2, mirror = "c2b",
            aliases = listOf("c2f", "t2f", "lt", "left twist"), worksOn = WorksOn.RS_ONLY,
            description = "Two stitches crossed to the left.",
            instructions = "Slip 1 to a cable needle and hold in front, k1, then k1 from the cable needle."),
        s("c2b", "1/1 right cross", "1/1 RC", CABLE, "cable_r", consumes = 2, produces = 2, width = 2, mirror = "c2f",
            aliases = listOf("c2b", "t2b", "rt", "right twist"), worksOn = WorksOn.RS_ONLY,
            description = "Two stitches crossed to the right.",
            instructions = "Slip 1 to a cable needle and hold in back, k1, then k1 from the cable needle."),
        s("c4f", "2/2 left cross", "C4F", CABLE, "cable_l", consumes = 4, produces = 4, width = 4, mirror = "c4b",
            aliases = listOf("2/2 lc", "c4l"), worksOn = WorksOn.RS_ONLY,
            description = "Four-stitch cable crossing to the left.",
            instructions = "Slip 2 to a cable needle and hold in front, k2, then k2 from the cable needle."),
        s("c4b", "2/2 right cross", "C4B", CABLE, "cable_r", consumes = 4, produces = 4, width = 4, mirror = "c4f",
            aliases = listOf("2/2 rc", "c4r"), worksOn = WorksOn.RS_ONLY,
            description = "Four-stitch cable crossing to the right.",
            instructions = "Slip 2 to a cable needle and hold in back, k2, then k2 from the cable needle."),
        s("c6f", "3/3 left cross", "C6F", CABLE, "cable_l", consumes = 6, produces = 6, width = 6, mirror = "c6b",
            aliases = listOf("3/3 lc", "c6l"), worksOn = WorksOn.RS_ONLY,
            description = "Six-stitch cable crossing to the left.",
            instructions = "Slip 3 to a cable needle and hold in front, k3, then k3 from the cable needle."),
        s("c6b", "3/3 right cross", "C6B", CABLE, "cable_r", consumes = 6, produces = 6, width = 6, mirror = "c6f",
            aliases = listOf("3/3 rc", "c6r"), worksOn = WorksOn.RS_ONLY,
            description = "Six-stitch cable crossing to the right.",
            instructions = "Slip 3 to a cable needle and hold in back, k3, then k3 from the cable needle."),
        s("t3f", "2/1 left purl cross", "T3F", CABLE, "cable_l_p", consumes = 3, produces = 3, width = 3, mirror = "t3b",
            aliases = listOf("2/1 lpc"), worksOn = WorksOn.RS_ONLY,
            description = "Two knit stitches travel left over one purl stitch.",
            instructions = "Slip 2 to a cable needle and hold in front, p1, then k2 from the cable needle."),
        s("t3b", "2/1 right purl cross", "T3B", CABLE, "cable_r_p", consumes = 3, produces = 3, width = 3, mirror = "t3f",
            aliases = listOf("2/1 rpc"), worksOn = WorksOn.RS_ONLY,
            description = "Two knit stitches travel right over one purl stitch.",
            instructions = "Slip 1 to a cable needle and hold in back, k2, then p1 from the cable needle."),

        // --- Special -------------------------------------------------------------------------
        s("mb", "Make bobble", "MB", SPECIAL, "bobble", aliases = listOf("bobble", "make bobble"),
            description = "A raised bobble worked into one stitch.",
            instructions = "(K1, yo, k1, yo, k1) into the same stitch, turn, p5, turn, k5, then pass 4 stitches over the first."),

        // --- Markers (never charted, never counted) -------------------------------------------
        s("pm", "Place marker", "pm", MARKER, "blank", consumes = 0, produces = 0, width = 0, ws = "pm",
            aliases = listOf("place marker", "pmk")),
        s("sm", "Slip marker", "sm", MARKER, "blank", consumes = 0, produces = 0, width = 0, ws = "sm",
            aliases = listOf("slip marker", "slm")),
        s("rm", "Remove marker", "rm", MARKER, "blank", consumes = 0, produces = 0, width = 0, ws = "rm",
            aliases = listOf("remove marker")),
    )

    /** Stitches offered first in the chart editor's palette. */
    val quickPalette: List<String> = listOf("k", "p", "yo", "k2tog", "ssk", "cdd", "sl", "c4f", "c4b", "none")
}
