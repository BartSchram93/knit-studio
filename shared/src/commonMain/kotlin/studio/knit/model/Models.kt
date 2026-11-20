package studio.knit.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/*
 * The complete, serializable data model of a knitting pattern.
 *
 * Everything here is immutable and platform independent. The same classes are stored in the local
 * database, written to `.knit` project files and rendered by the UI and the PDF writer.
 */

@Serializable
enum class Category(val label: String) {
    SWEATER("Sweater"), CARDIGAN("Cardigan"), SCARF("Scarf"), SHAWL("Shawl"), SOCKS("Socks"),
    HAT("Hat"), GLOVES("Gloves"), BLANKET("Blanket"), OTHER("Other"),
}

@Serializable
enum class Difficulty(val label: String) {
    BEGINNER("Beginner"), EASY("Easy"), INTERMEDIATE("Intermediate"), ADVANCED("Advanced"),
}

/** How a piece of fabric is worked. */
@Serializable
enum class Construction(val label: String) { FLAT("Flat (rows)"), ROUND("In the round") }

/** Right side (public side) or wrong side of the fabric. */
@Serializable
enum class Side { RS, WS; fun flipped() = if (this == RS) WS else RS }

/** Reading direction of a chart row, as seen on the chart. */
@Serializable
enum class Direction { RIGHT_TO_LEFT, LEFT_TO_RIGHT; fun flipped() = if (this == RIGHT_TO_LEFT) LEFT_TO_RIGHT else RIGHT_TO_LEFT }

/**
 * An integer that may differ per size, e.g. `10 (12, 14)`.
 * A single value applies to every size.
 */
@Serializable
data class SizedInt(val values: List<Int>) {
    constructor(value: Int) : this(listOf(value))

    val isSized: Boolean get() = values.size > 1
    fun forSize(index: Int): Int = values.getOrElse(index) { values.last() }

    /** `10` or `10 (12, 14)`. */
    override fun toString(): String =
        if (values.size == 1) values[0].toString()
        else "${values[0]} (${values.drop(1).joinToString(", ")})"
}

@Serializable
data class Gauge(
    val stitchesPer10cm: Double,
    val rowsPer10cm: Double,
    /** Stitch pattern the gauge was measured over, e.g. "stockinette". */
    val over: String = "stockinette stitch",
    val needleMm: Double? = null,
)

@Serializable
enum class YarnWeight(val label: String, val ukLabel: String, val typicalGauge: String) {
    LACE("Lace", "Lace / 2-ply", "33–40 sts"),
    FINGERING("Fingering", "4-ply", "27–32 sts"),
    SPORT("Sport", "5-ply / baby", "23–26 sts"),
    DK("DK", "DK / 8-ply", "21–24 sts"),
    WORSTED("Worsted", "Aran", "16–20 sts"),
    BULKY("Bulky", "Chunky", "12–15 sts"),
    SUPER_BULKY("Super bulky", "Super chunky", "7–11 sts"),
    JUMBO("Jumbo", "Jumbo", "6 sts or fewer"),
    UNKNOWN("Not set", "Not set", ""),
}

@Serializable
data class Yarn(
    val id: String,
    /** Role in the pattern, e.g. "MC", "A", "B". */
    val label: String = "",
    val brand: String = "",
    val name: String = "",
    val weight: YarnWeight = YarnWeight.UNKNOWN,
    val fiber: String = "",
    val color: String = "",
    /** Display colour, ARGB. */
    val colorArgb: Long? = null,
    val dyeLot: String = "",
    val metersPerSkein: Double? = null,
    val gramsPerSkein: Double? = null,
    val skeins: Double? = null,
    val notes: String = "",
    /** Photo of the yarn (stash only), stored in the image table. */
    val photoId: String? = null,
) {
    val displayName: String
        get() = listOf(brand, name).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Untitled yarn" }
    val totalMeters: Double? get() = if (metersPerSkein != null && skeins != null) metersPerSkein * skeins else null
}

@Serializable
enum class NeedleType(val label: String) {
    STRAIGHT("Straight"), CIRCULAR("Circular"), DPN("Double-pointed"),
    INTERCHANGEABLE("Interchangeable"), CABLE("Cable needle"), CROCHET_HOOK("Crochet hook"),
}

@Serializable
data class Needle(
    val id: String,
    val sizeMm: Double = 4.0,
    val type: NeedleType = NeedleType.CIRCULAR,
    val material: String = "",
    val lengthCm: Double? = null,
    val notes: String = "",
    /** Maker, e.g. "ChiaoGoo". Used to group the stash into folders. */
    val brand: String = "",
)

/**
 * A cable for interchangeable needle tips. Cables belong to a brand rather than to one needle:
 * any tip of the brand (with a matching connector) fits on it.
 */
@Serializable
data class Cable(
    val id: String,
    val brand: String = "",
    /** Length as printed on the cable or its packaging. */
    val lengthCm: Double = 80.0,
    /** Connector or cable size where a brand has several, e.g. "[S]" or "small". */
    val connector: String = "",
    val count: Int = 1,
    val material: String = "",
    val notes: String = "",
)

/** A named body or garment measurement in centimetres, one value per size. */
@Serializable
data class Measurement(
    val id: String,
    val name: String,
    val valuesCm: List<Double> = emptyList(),
)

// ---------------------------------------------------------------------------------------------
// Charts
// ---------------------------------------------------------------------------------------------

/**
 * One chart cell.
 *
 * @property stitch id of the [StitchDef] shown in this cell.
 * @property color index into [Chart.palette], or -1 for the uncoloured background.
 * @property part for stitches wider than one cell (cables): 0 for the left-most cell of the
 *   stitch, 1, 2… for the cells to its right.
 */
@Serializable(with = CellSerializer::class)
data class Cell(val stitch: String = "k", val color: Int = -1, val part: Int = 0) {
    companion object {
        val Knit = Cell()
    }
}

/** Compact text form used in files: `k`, `k#2` (colour 2), `c4f+1` (second cell of a cable). */
object CellSerializer : KSerializer<Cell> {
    override val descriptor = PrimitiveSerialDescriptor("studio.knit.Cell", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Cell) = encoder.encodeString(encode(value))
    override fun deserialize(decoder: Decoder): Cell = decode(decoder.decodeString())

    fun encode(c: Cell): String = buildString {
        append(c.stitch)
        if (c.color >= 0) append('#').append(c.color)
        if (c.part > 0) append('+').append(c.part)
    }

    fun decode(s: String): Cell {
        var rest = s
        var part = 0
        var color = -1
        val plus = rest.lastIndexOf('+')
        if (plus > 0) {
            part = rest.substring(plus + 1).toIntOrNull() ?: 0
            rest = rest.substring(0, plus)
        }
        val hash = rest.lastIndexOf('#')
        if (hash > 0) {
            color = rest.substring(hash + 1).toIntOrNull() ?: -1
            rest = rest.substring(0, hash)
        }
        return Cell(rest.ifEmpty { "k" }, color, part)
    }
}

@Serializable
data class ChartColor(val name: String, val argb: Long)

/**
 * A boxed repeat on a chart. Columns and rows are zero-based chart coordinates
 * (column 0 = left edge, row 0 = bottom row), both ends inclusive.
 */
@Serializable
data class ChartRepeat(
    val id: String,
    val colStart: Int,
    val colEnd: Int,
    val rowStart: Int,
    val rowEnd: Int,
    val label: String = "",
)

/**
 * A knitting chart. Cells are stored row-major from the **bottom** row upwards, each row from the
 * visual **left** to the right — i.e. exactly as the chart is drawn, never in knitting order.
 * Knitting order is derived from [construction], [firstRowSide] and [rsDirection].
 */
@Serializable
data class Chart(
    val id: String,
    val name: String = "Chart",
    val cols: Int,
    val rows: Int,
    val cells: List<Cell>,
    val construction: Construction = Construction.FLAT,
    /** Side of the first (bottom) row when worked flat. */
    val firstRowSide: Side = Side.RS,
    /** Direction in which right-side rows are read. Left-handed knitters use LEFT_TO_RIGHT. */
    val rsDirection: Direction = Direction.RIGHT_TO_LEFT,
    val firstRowNumber: Int = 1,
    val repeats: List<ChartRepeat> = emptyList(),
    val palette: List<ChartColor> = emptyList(),
    val notes: String = "",
)

// ---------------------------------------------------------------------------------------------
// Written instructions
// ---------------------------------------------------------------------------------------------

@Serializable
enum class RowKind { INSTRUCTION, REPEAT_ROWS, NOTE }

/**
 * One entry of a written section.
 *
 * - [RowKind.INSTRUCTION]: [text] holds the stitches of one row/round, e.g. `k2, *p2, k2; rep from * to end`.
 * - [RowKind.REPEAT_ROWS]: work rows [repeatFrom]..[repeatTo] again, [repeatTimes] more times.
 * - [RowKind.NOTE]: free text between rows; does not take a row number.
 */
@Serializable
data class Row(
    val id: String,
    val kind: RowKind = RowKind.INSTRUCTION,
    val text: String = "",
    val repeatFrom: Int = 1,
    val repeatTo: Int = 1,
    val repeatTimes: SizedInt = SizedInt(1),
    val note: String = "",
)

@Serializable
data class Section(
    val id: String,
    val title: String = "Section",
    val description: String = "",
    val construction: Construction = Construction.FLAT,
    val firstRowSide: Side = Side.RS,
    /** Stitches on the needle before row 1, if known. */
    val castOn: SizedInt? = null,
    val rows: List<Row> = emptyList(),
    /** Chart this section mirrors, if any. */
    val chartId: String? = null,
    /** True while the rows are exactly what was generated from [chartId]. */
    val generated: Boolean = false,
)

// ---------------------------------------------------------------------------------------------
// Knitting progress
// ---------------------------------------------------------------------------------------------

@Serializable
data class Counter(
    val id: String,
    val name: String = "Counter",
    val value: Int = 0,
    val target: Int? = null,
    /** When set, the counter resets to 0 after reaching this value and bumps [laps]. */
    val resetAt: Int? = null,
    val laps: Int = 0,
    /** Advance together with the main row counter. */
    val linked: Boolean = false,
)

@Serializable
data class ProjectNote(val id: String, val text: String, val createdAt: Long, val context: String = "")

/** Where the knitter is in a pattern. Stored with the pattern so it travels with exports. */
@Serializable
data class Progress(
    val startedAt: Long,
    val lastKnitAt: Long = startedAt,
    val finishedAt: Long? = null,
    val sizeIndex: Int = 0,
    /** Id of the section or chart currently being worked. */
    val activeTrackId: String? = null,
    /** Rows completed per section/chart id. */
    val positions: Map<String, Int> = emptyMap(),
    val counters: List<Counter> = emptyList(),
    val notes: List<ProjectNote> = emptyList(),
)

// ---------------------------------------------------------------------------------------------
// Pattern
// ---------------------------------------------------------------------------------------------

@Serializable
data class Pattern(
    val id: String,
    val name: String = "",
    val description: String = "",
    val category: Category = Category.OTHER,
    val difficulty: Difficulty = Difficulty.EASY,
    /** Size names, smallest first. Empty means the pattern has a single size. */
    val sizes: List<String> = emptyList(),
    val gauge: Gauge? = null,
    val yarns: List<Yarn> = emptyList(),
    val needles: List<Needle> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val notes: String = "",
    val imageIds: List<String> = emptyList(),
    /** Attached documents such as PDF patterns. */
    val files: List<AttachedFile> = emptyList(),
    val charts: List<Chart> = emptyList(),
    val sections: List<Section> = emptyList(),
    /** Snapshot of the user-defined stitches this pattern uses, so the file is self-contained. */
    val customStitches: List<StitchDef> = emptyList(),
    val draft: Boolean = true,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val collectionId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val progress: Progress? = null,
) {
    val sizeCount: Int get() = maxOf(1, sizes.size)
    val displayName: String get() = name.ifBlank { "Untitled pattern" }
}

/** A document kept with a pattern, e.g. the PDF of a bought pattern. The bytes live in a [studio.knit.data.FileStore]. */
@Serializable
data class AttachedFile(
    val id: String,
    val name: String,
    val mime: String = "application/pdf",
    val sizeBytes: Long = 0,
    val addedAt: Long = 0,
)

@Serializable
data class PatternCollection(val id: String, val name: String, val sortIndex: Int = 0)

// ---------------------------------------------------------------------------------------------
// Stitch definitions
// ---------------------------------------------------------------------------------------------

@Serializable
enum class StitchCategory(val label: String) {
    BASIC("Basic"), INCREASE("Increases"), DECREASE("Decreases"), TWISTED("Twisted & slipped"),
    CABLE("Cables"), SPECIAL("Special"), MARKER("Markers"), CUSTOM("My stitches"),
}

@Serializable
enum class WorksOn(val label: String) { ANY("Any row"), RS_ONLY("Right-side rows only"), WS_ONLY("Wrong-side rows only") }

/**
 * A stitch, structured so the engine can reason about it.
 *
 * @property consumes stitches taken from the left needle.
 * @property produces stitches left on the right needle.
 * @property width chart cells occupied; 0 for actions that are never charted (markers).
 * @property symbol glyph key, see `Glyphs`; `text:XY` draws the letters XY.
 * @property ws id of the stitch actually worked when this symbol sits on a wrong-side row
 *   (blank "knit" symbol → purl). Equal to [id] when identical on both sides; null when unknown.
 */
@Serializable
data class StitchDef(
    val id: String,
    val name: String,
    val abbr: String,
    val abbrUk: String? = null,
    val description: String = "",
    val instructions: String = "",
    val consumes: Int = 1,
    val produces: Int = 1,
    val width: Int = 1,
    val symbol: String = "blank",
    val ws: String? = null,
    val category: StitchCategory = StitchCategory.CUSTOM,
    val worksOn: WorksOn = WorksOn.ANY,
    val colorArgb: Long? = null,
    val aliases: List<String> = emptyList(),
    val builtIn: Boolean = false,
    /** Id of the mirror-image stitch (k2tog ↔ ssk), used when flipping a chart. */
    val mirror: String? = null,
) {
    val delta: Int get() = produces - consumes
    fun abbrFor(uk: Boolean): String = if (uk) abbrUk ?: abbr else abbr
}
