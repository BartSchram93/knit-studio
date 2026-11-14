package studio.knit

import studio.knit.model.Construction
import studio.knit.model.Row
import studio.knit.model.RowKind
import studio.knit.model.Section
import studio.knit.model.Side
import studio.knit.model.SizedInt
import studio.knit.stitch.StitchLibrary

val lib = StitchLibrary()

private var counter = 0
fun id(): String = "t${counter++}"

/** Builds a section from row texts. `@rep 1-2 x3` adds a row repeat, `# text` a note. */
fun section(
    vararg rows: String,
    castOn: SizedInt? = null,
    construction: Construction = Construction.FLAT,
    firstSide: Side = Side.RS,
    chartId: String? = null,
): Section = Section(
    id = id(), title = "Test", construction = construction, firstRowSide = firstSide, castOn = castOn, chartId = chartId,
    rows = rows.map { text ->
        when {
            text.startsWith("@rep") -> {
                val m = Regex("@rep (\\d+)-(\\d+) x(.+)").find(text)!!
                Row(id(), RowKind.REPEAT_ROWS, repeatFrom = m.groupValues[1].toInt(), repeatTo = m.groupValues[2].toInt(),
                    repeatTimes = SizedInt(m.groupValues[3].split(",").map { it.trim().toInt() }))
            }
            text.startsWith("#") -> Row(id(), RowKind.NOTE, text = text.drop(1).trim())
            else -> Row(id(), text = text)
        }
    },
)
