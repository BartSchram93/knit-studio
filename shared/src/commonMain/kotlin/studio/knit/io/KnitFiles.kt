package studio.knit.io

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import studio.knit.chart.MAX_CHART_SIZE
import studio.knit.model.Cell
import studio.knit.model.Cable
import studio.knit.model.Chart
import studio.knit.model.Needle
import studio.knit.model.Pattern
import studio.knit.model.PatternCollection
import studio.knit.model.StitchDef
import studio.knit.model.Yarn
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * The platform-independent Knit Studio file formats.
 *
 *  .knit        one pattern with everything it needs (custom stitches, images, progress)
 *  .knitbackup  the whole library
 *
 * Both are UTF-8 JSON with a `format` tag and an integer `version`. Files written by a newer
 * version than this build understands are refused rather than half-read; files from older
 * versions are upgraded step by step through [MIGRATIONS] before decoding.
 */

@Serializable
data class ProjectFile(
    val format: String = KnitFiles.PATTERN_FORMAT,
    val version: Int = KnitFiles.VERSION,
    val app: String = "Knit Studio",
    val exportedAt: Long = 0,
    val pattern: Pattern,
    /** Reference images, id → base64 JPEG/PNG bytes. */
    val images: Map<String, String> = emptyMap(),
)

@Serializable
data class BackupFile(
    val format: String = KnitFiles.BACKUP_FORMAT,
    val version: Int = KnitFiles.VERSION,
    val app: String = "Knit Studio",
    val exportedAt: Long = 0,
    val patterns: List<Pattern> = emptyList(),
    val images: Map<String, String> = emptyMap(),
    val customStitches: List<StitchDef> = emptyList(),
    val collections: List<PatternCollection> = emptyList(),
    val yarnStash: List<Yarn> = emptyList(),
    val needleStash: List<Needle> = emptyList(),
    val cableStash: List<Cable> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

sealed interface ImportResult<out T> {
    /** [warnings] list anything that was repaired while reading. */
    data class Success<T>(val value: T, val images: Map<String, ByteArray>, val warnings: List<String>) : ImportResult<T>
    data class Failure(val reason: String) : ImportResult<Nothing>
}

@OptIn(ExperimentalEncodingApi::class)
object KnitFiles {
    const val PATTERN_FORMAT = "knit-studio.pattern"
    const val BACKUP_FORMAT = "knit-studio.backup"
    const val VERSION = 1
    const val PATTERN_EXTENSION = "knit"
    const val BACKUP_EXTENSION = "knitbackup"
    const val MIME = "application/json"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val pretty = Json(json) { prettyPrint = true }

    /**
     * Upgrades from version N to N+1, keyed by N. Version 1 is the first public format, so the
     * table is empty; it exists so the next format change is a one-line addition.
     */
    internal val MIGRATIONS: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun encodePattern(pattern: Pattern, images: Map<String, ByteArray> = emptyMap(), now: Long = 0): String =
        pretty.encodeToString(
            ProjectFile.serializer(),
            ProjectFile(exportedAt = now, pattern = pattern, images = images.mapValues { Base64.encode(it.value) }),
        )

    fun encodeBackup(backup: BackupFile, images: Map<String, ByteArray> = emptyMap()): String =
        json.encodeToString(BackupFile.serializer(), backup.copy(images = images.mapValues { Base64.encode(it.value) }))

    fun decodePattern(text: String): ImportResult<Pattern> {
        val obj = when (val r = open(text, PATTERN_FORMAT, "pattern")) {
            is Opened.Ok -> r.obj
            is Opened.Bad -> return ImportResult.Failure(r.reason)
        }
        val file = try {
            json.decodeFromJsonElement<ProjectFile>(obj)
        } catch (e: Exception) {
            return ImportResult.Failure("The file is damaged and could not be read (${e.message?.take(120)})")
        }
        val warnings = ArrayList<String>()
        val pattern = sanitize(file.pattern, warnings)
        return ImportResult.Success(pattern, decodeImages(file.images, warnings), warnings)
    }

    fun decodeBackup(text: String): ImportResult<BackupFile> {
        val obj = when (val r = open(text, BACKUP_FORMAT, "backup")) {
            is Opened.Ok -> r.obj
            is Opened.Bad -> return ImportResult.Failure(r.reason)
        }
        val file = try {
            json.decodeFromJsonElement<BackupFile>(obj)
        } catch (e: Exception) {
            return ImportResult.Failure("The backup is damaged and could not be read (${e.message?.take(120)})")
        }
        val warnings = ArrayList<String>()
        val patterns = file.patterns.map { sanitize(it, warnings) }
        val images = decodeImages(file.images, warnings)
        return ImportResult.Success(file.copy(patterns = patterns, images = emptyMap()), images, warnings)
    }

    /** True when [text] looks like a backup rather than a single pattern. */
    fun isBackup(text: String): Boolean = text.take(400).contains(BACKUP_FORMAT)

    private sealed interface Opened {
        data class Ok(val obj: JsonObject) : Opened
        data class Bad(val reason: String) : Opened
    }

    /**
     * Recognises common files that are picked by mistake, from their first bytes, and says what
     * they are. Null when the bytes could be a Knit Studio file.
     */
    fun identifyOtherFile(bytes: ByteArray): String? {
        fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { (bytes[it].toInt() and 0xFF) == b[it] }
        return when {
            bytes.isEmpty() -> "The file is empty. If it is stored in iCloud Drive, open it once in the Files app so it is downloaded, then try again"
            starts(0x25, 0x50, 0x44, 0x46) -> PDF_REASON
            starts(0x50, 0x4B, 0x03, 0x04) -> "This is a compressed (zip) file or an Office document. If it is a zip, unzip it first and choose the .knit file inside"
            starts(0xFF, 0xD8, 0xFF) || starts(0x89, 0x50, 0x4E, 0x47) || starts(0x47, 0x49, 0x46, 0x38) -> "This is a photo, not a Knit Studio file"
            starts(0x7B, 0x5C, 0x72, 0x74, 0x66) -> RTF_REASON
            starts(0xD0, 0xCF, 0x11, 0xE0) -> "This is an Office document, not a Knit Studio file"
            else -> null
        }
    }

    private const val PDF_REASON =
        "This is a PDF document. Knit Studio can open only .knit pattern files and .knitbackup backups made by Knit Studio — PDF patterns cannot be imported"
    private const val RTF_REASON =
        "Another app has converted this file to formatted text (RTF). Export the pattern from Knit Studio again and share it as a file"

    /** Explains, as specifically as possible, why [text] is not a Knit Studio file. */
    private fun notJsonReason(text: String): String {
        val start = text.take(30).map { if (it.isLetterOrDigit() || it in " {}[]:\",.-_#<>%") it else '·' }.joinToString("")
        return when {
            text.startsWith("%PDF") -> PDF_REASON
            text.startsWith("{\\rtf") -> RTF_REASON
            text.startsWith("<") -> "This is a web page or XML document, not a Knit Studio file"
            text.startsWith("{") -> "The file looks like a Knit Studio file but is incomplete or damaged — it may have been cut off while being copied or sent. Export it again"
            else -> "This is not a Knit Studio file (it starts with “$start”)"
        }
    }

    private fun open(text: String, expectedFormat: String, noun: String): Opened {
        // Tolerate a byte-order mark, stray NUL bytes and surrounding whitespace added by other apps.
        val clean = text.trimStart('\uFEFF', '\u0000', ' ', '\n', '\r', '\t').trimEnd('\u0000', ' ', '\n', '\r', '\t')
        if (clean.isEmpty()) return Opened.Bad("The file is empty")
        val element = try {
            json.parseToJsonElement(clean)
        } catch (e: Exception) {
            return Opened.Bad(notJsonReason(clean))
        }
        var obj = element as? JsonObject ?: return Opened.Bad("This file contains data, but not a Knit Studio pattern or backup")
        val format = (obj["format"] as? JsonPrimitive)?.content
        if (format != expectedFormat) {
            return Opened.Bad(
                when (format) {
                    PATTERN_FORMAT -> "This file is a single pattern, not a $noun"
                    BACKUP_FORMAT -> "This file is a full backup, not a $noun. Restore it from Settings › Backup"
                    else -> "This is not a Knit Studio $noun file"
                },
            )
        }
        var version = obj["version"]?.jsonPrimitive?.intOrNull ?: return Opened.Bad("The file has no version number")
        if (version > VERSION) return Opened.Bad("This file was made with a newer version of Knit Studio (format $version). Update the app to open it")
        if (version < 1) return Opened.Bad("Unsupported file version $version")
        while (version < VERSION) {
            val step = MIGRATIONS[version] ?: return Opened.Bad("Cannot upgrade file from format $version")
            obj = step(obj)
            version++
        }
        return Opened.Ok(obj)
    }

    private fun decodeImages(encoded: Map<String, String>, warnings: MutableList<String>): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        for ((id, data) in encoded) {
            try {
                out[id] = Base64.decode(data)
            } catch (e: Exception) {
                warnings += "One reference image was damaged and was skipped"
            }
        }
        return out
    }

    /**
     * Repairs structural damage so the rest of the app can rely on its invariants:
     * chart grids match their dimensions, repeat boxes lie inside the chart, ids are unique.
     * Knitting-level problems are left alone — those are the validator's job, not the importer's.
     */
    fun sanitize(pattern: Pattern, warnings: MutableList<String> = ArrayList()): Pattern {
        val chartIds = HashSet<String>()
        val charts = pattern.charts.mapNotNull { chart ->
            if (!chartIds.add(chart.id)) {
                warnings += "A duplicate chart “${chart.name}” was skipped"
                return@mapNotNull null
            }
            sanitizeChart(chart, warnings)
        }
        val sectionIds = HashSet<String>()
        val sections = pattern.sections.filter { s ->
            sectionIds.add(s.id).also { if (!it) warnings += "A duplicate section “${s.title}” was skipped" }
        }.map { s ->
            val rowIds = HashSet<String>()
            s.copy(
                rows = s.rows.filter { rowIds.add(it.id) },
                chartId = s.chartId?.takeIf { it in chartIds },
            )
        }
        val gauge = pattern.gauge?.takeIf { it.stitchesPer10cm > 0 && it.rowsPer10cm > 0 }
        if (pattern.gauge != null && gauge == null) warnings += "An invalid gauge was removed"
        return pattern.copy(charts = charts, sections = sections, gauge = gauge)
    }

    private fun sanitizeChart(chart: Chart, warnings: MutableList<String>): Chart {
        val cols = chart.cols.coerceIn(1, MAX_CHART_SIZE)
        val rows = chart.rows.coerceIn(1, MAX_CHART_SIZE)
        var cells = chart.cells
        if (cols != chart.cols || rows != chart.rows || cells.size != cols * rows) {
            warnings += "Chart “${chart.name}” had the wrong number of cells and was repaired"
            cells = List(cols * rows) { i ->
                // Re-map by coordinates so intact rows survive a size change.
                val r = i / cols
                val c = i % cols
                if (r < chart.rows && c < chart.cols) chart.cells.getOrNull(r * chart.cols + c) ?: Cell.Knit else Cell.Knit
            }
        }
        val maxColor = chart.palette.size - 1
        if (cells.any { it.color > maxColor }) {
            cells = cells.map { if (it.color > maxColor) it.copy(color = -1) else it }
            warnings += "Chart “${chart.name}” referred to colours that do not exist; those cells were uncoloured"
        }
        val repeats = chart.repeats.filter {
            it.colStart in 0 until cols && it.colEnd in it.colStart until cols && it.rowStart in 0 until rows && it.rowEnd in it.rowStart until rows
        }
        if (repeats.size != chart.repeats.size) warnings += "Chart “${chart.name}” had a repeat box outside the chart; it was removed"
        return chart.copy(cols = cols, rows = rows, cells = cells, repeats = repeats)
    }

    /** A file name safe on every platform: `Lace Scarf.knit`. */
    fun fileName(name: String, extension: String): String {
        val clean = name.trim().map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else ' ' }
            .joinToString("").replace(Regex("\\s+"), " ").trim().take(60)
        return "${clean.ifEmpty { "Pattern" }}.$extension"
    }
}
