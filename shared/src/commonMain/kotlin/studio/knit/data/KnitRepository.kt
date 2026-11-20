package studio.knit.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import studio.knit.chart.usedStitches
import studio.knit.db.KnitDatabase
import studio.knit.io.BackupFile
import studio.knit.io.ImportResult
import studio.knit.io.KnitFiles
import studio.knit.model.AttachedFile
import studio.knit.model.Cable
import studio.knit.model.Needle
import studio.knit.model.Pattern
import studio.knit.model.PatternCollection
import studio.knit.model.StitchDef
import studio.knit.model.Yarn
import studio.knit.stitch.StitchLibrary
import studio.knit.util.newId
import studio.knit.util.nowMillis

/** Outcome of importing a `.knit` file. */
sealed interface ImportOutcome {
    data class Imported(val pattern: Pattern, val warnings: List<String>) : ImportOutcome
    data class Failed(val reason: String) : ImportOutcome
}

data class RestoreSummary(val patterns: Int, val stitches: Int, val warnings: List<String>)

/**
 * The single entry point to everything stored on the device. Local first: there is no network
 * code anywhere behind this class.
 */
class KnitRepository(
    private val driver: SqlDriver,
    private val clock: () -> Long = ::nowMillis,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    /** Where attached documents (PDF patterns) are kept. */
    private val files: FileStore = InMemoryFileStore(),
) {
    private val db = KnitDatabase(driver)
    private val q = db.knitQueries
    private val json = KnitFiles.json

    /** Rows that could not be decoded. They are kept in the database, never deleted silently. */
    private val unreadable = LinkedHashSet<String>()

    // --- Patterns ----------------------------------------------------------------------------

    private fun decode(id: String, text: String): Pattern? = try {
        json.decodeFromString(Pattern.serializer(), text)
    } catch (e: Exception) {
        unreadable += id
        null
    }

    /** Every pattern, most recently changed first. */
    val patterns: Flow<List<Pattern>> =
        q.selectPatterns().asFlow().mapToList(io).map { rows -> rows.mapNotNull { decode(it.id, it.json) } }

    fun pattern(id: String): Flow<Pattern?> =
        q.selectPattern(id).asFlow().mapToList(io).map { rows -> rows.firstOrNull()?.let { decode(it.id, it.json) } }.distinctUntilChanged()

    suspend fun getPattern(id: String): Pattern? = withContext(io) {
        q.selectPattern(id).executeAsOneOrNull()?.let { decode(it.id, it.json) }
    }

    suspend fun allPatterns(): List<Pattern> = withContext(io) {
        q.selectPatterns().executeAsList().mapNotNull { decode(it.id, it.json) }
    }

    /**
     * Saves [pattern]. Stamps the modification time (unless [touch] is false) and embeds the
     * custom stitches it uses so that the stored document is self-contained.
     */
    suspend fun save(pattern: Pattern, touch: Boolean = true): Pattern = withContext(io) {
        val now = clock()
        val custom = customStitchesNow()
        val stamped = embedStitches(pattern, custom).let {
            if (touch) it.copy(updatedAt = now, createdAt = if (it.createdAt == 0L) now else it.createdAt) else it
        }
        q.upsertPattern(stamped.id, stamped.name, stamped.updatedAt, json.encodeToString(Pattern.serializer(), stamped))
        stamped
    }

    private fun embedStitches(pattern: Pattern, custom: List<StitchDef>): Pattern {
        val used = pattern.charts.flatMapTo(HashSet()) { it.usedStitches() }
        // Stitches referenced in written rows are matched by abbreviation.
        val text = pattern.sections.joinToString("\n") { s -> s.rows.joinToString("\n") { it.text } }.lowercase()
        val relevant = custom.filter { it.id in used || (it.abbr.isNotBlank() && text.contains(it.abbr.lowercase())) }
        // Keep previously embedded definitions that are no longer in the library (e.g. after import).
        val kept = pattern.customStitches.filter { old -> old.id in used && relevant.none { it.id == old.id } }
        return pattern.copy(customStitches = relevant + kept)
    }

    suspend fun create(pattern: Pattern): Pattern = save(pattern.copy(createdAt = clock()))

    suspend fun duplicate(id: String): Pattern? = withContext(io) {
        val original = getPattern(id) ?: return@withContext null
        val copyId = newId()
        // Images are copied too, so deleting either pattern never orphans the other's pictures.
        val imageMap = LinkedHashMap<String, String>()
        for (img in q.selectImagesForPattern(id).executeAsList()) {
            val newImage = newId()
            q.upsertImage(newImage, copyId, img.bytes)
            imageMap[img.id] = newImage
        }
        val fileCopies = original.files.mapNotNull { f -> files.read(f.id)?.let { bytes -> f.copy(id = newId()).also { files.write(it.id, bytes) } } }
        val copy = original.copy(
            id = copyId,
            name = "${original.displayName} copy",
            imageIds = original.imageIds.mapNotNull { imageMap[it] },
            files = fileCopies,
            favorite = false, archived = false, progress = null, createdAt = clock(),
        )
        save(copy)
    }

    suspend fun delete(id: String) = withContext(io) {
        val attached = getPattern(id)?.files.orEmpty()
        db.transaction {
            q.deleteImagesForPattern(id)
            q.deletePattern(id)
        }
        attached.forEach { files.delete(it.id) }
    }

    // --- Attached documents ---------------------------------------------------------------

    /**
     * Stores a document (e.g. a PDF pattern) and returns its description. Add the result to the
     * pattern's [Pattern.files] to keep it; [deleteFile] removes it again.
     */
    suspend fun storeFile(name: String, bytes: ByteArray, mime: String = "application/pdf"): AttachedFile = withContext(io) {
        val file = AttachedFile(id = newId(), name = name, mime = mime, sizeBytes = bytes.size.toLong(), addedAt = clock())
        files.write(file.id, bytes)
        file
    }

    suspend fun fileBytes(id: String): ByteArray? = withContext(io) { files.read(id) }
    suspend fun deleteFile(id: String) = withContext(io) { files.delete(id) }

    // --- Images ------------------------------------------------------------------------------

    suspend fun addImage(patternId: String, bytes: ByteArray): String = withContext(io) {
        val id = newId()
        q.upsertImage(id, patternId, bytes)
        id
    }

    suspend fun image(id: String): ByteArray? = withContext(io) { q.selectImage(id).executeAsOneOrNull() }
    suspend fun deleteImage(id: String) = withContext(io) { q.deleteImage(id) }

    /** Stores a photo for a stash item and returns its id. */
    suspend fun addStashPhoto(bytes: ByteArray): String = addImage(STASH_IMAGES, bytes)

    /** Removes a yarn from the stash together with its photo. */
    suspend fun deleteYarn(y: Yarn) = withContext(io) {
        db.transaction {
            y.photoId?.let { q.deleteImage(it) }
            q.deleteStash(y.id)
        }
    }

    // --- Stitch library ----------------------------------------------------------------------

    private fun customStitchesNow(): List<StitchDef> =
        q.selectStitches().executeAsList().mapNotNull { runCatching { json.decodeFromString(StitchDef.serializer(), it.json) }.getOrNull() }

    val customStitches: Flow<List<StitchDef>> = q.selectStitches().asFlow().mapToList(io).map { rows ->
        rows.mapNotNull { runCatching { json.decodeFromString(StitchDef.serializer(), it.json) }.getOrNull() }.sortedBy { it.name.lowercase() }
    }

    /** Built-in stitches plus the user's own. */
    val library: Flow<StitchLibrary> = customStitches.map { StitchLibrary(it) }

    suspend fun libraryNow(): StitchLibrary = withContext(io) { StitchLibrary(customStitchesNow()) }

    suspend fun saveStitch(def: StitchDef) = withContext(io) {
        val clean = def.copy(builtIn = false)
        q.upsertStitch(clean.id, json.encodeToString(StitchDef.serializer(), clean))
    }

    suspend fun deleteStitch(id: String) = withContext(io) { q.deleteStitch(id) }

    // --- Collections and stash ---------------------------------------------------------------

    val collections: Flow<List<PatternCollection>> =
        q.selectCollections().asFlow().mapToList(io).map { rows -> rows.map { PatternCollection(it.id, it.name, it.sort_index.toInt()) } }

    suspend fun saveCollection(c: PatternCollection) = withContext(io) { q.upsertCollection(c.id, c.name, c.sortIndex.toLong()) }

    /** Deletes a collection; its patterns stay in the library, just uncollected. */
    suspend fun deleteCollection(id: String) = withContext(io) {
        val affected = allPatterns().filter { it.collectionId == id }
        q.deleteCollection(id)
        for (p in affected) save(p.copy(collectionId = null), touch = false)
    }

    val yarnStash: Flow<List<Yarn>> = q.selectStash(KIND_YARN).asFlow().mapToList(io).map { rows ->
        rows.mapNotNull { runCatching { json.decodeFromString(Yarn.serializer(), it.json) }.getOrNull() }.sortedBy { it.displayName.lowercase() }
    }
    val needleStash: Flow<List<Needle>> = q.selectStash(KIND_NEEDLE).asFlow().mapToList(io).map { rows ->
        rows.mapNotNull { runCatching { json.decodeFromString(Needle.serializer(), it.json) }.getOrNull() }.sortedBy { it.sizeMm }
    }

    suspend fun saveYarn(y: Yarn) = withContext(io) { q.upsertStash(y.id, KIND_YARN, json.encodeToString(Yarn.serializer(), y)) }
    suspend fun saveNeedle(n: Needle) = withContext(io) { q.upsertStash(n.id, KIND_NEEDLE, json.encodeToString(Needle.serializer(), n)) }

    val cableStash: Flow<List<Cable>> = q.selectStash(KIND_CABLE).asFlow().mapToList(io).map { rows ->
        rows.mapNotNull { runCatching { json.decodeFromString(Cable.serializer(), it.json) }.getOrNull() }.sortedBy { it.lengthCm }
    }

    suspend fun saveCable(c: Cable) = withContext(io) { q.upsertStash(c.id, KIND_CABLE, json.encodeToString(Cable.serializer(), c)) }
    suspend fun deleteStash(id: String) = withContext(io) { q.deleteStash(id) }

    // --- Settings ----------------------------------------------------------------------------

    val settings: Flow<Settings> = q.selectSettings().asFlow().mapToList(io)
        .map { rows -> Settings.fromMap(rows.associate { it.key to it.value_ }) }.distinctUntilChanged()

    suspend fun settingsNow(): Settings = withContext(io) {
        Settings.fromMap(q.selectSettings().executeAsList().associate { it.key to it.value_ })
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) = withContext(io) {
        val next = transform(settingsNow())
        db.transaction { for ((k, v) in next.toMap()) q.upsertSetting(k, v) }
    }

    // --- Files -------------------------------------------------------------------------------

    /** The `.knit` file for one pattern, images included. */
    suspend fun exportPattern(id: String, includeProgress: Boolean = true): String? = withContext(io) {
        val pattern = getPattern(id) ?: return@withContext null
        val images = q.selectImagesForPattern(id).executeAsList().filter { it.id in pattern.imageIds }.associate { it.id to it.bytes } +
            pattern.files.mapNotNull { f -> files.read(f.id)?.let { f.id to it } }
        KnitFiles.encodePattern(if (includeProgress) pattern else pattern.copy(progress = null), images, clock())
    }

    /**
     * Imports a `.knit` file as a new pattern. An existing pattern with the same id is never
     * overwritten: the import gets a fresh id instead. Custom stitches the file brings along are
     * added to the library unless a stitch with that id already exists.
     */
    suspend fun importPattern(text: String): ImportOutcome = withContext(io) {
        when (val r = KnitFiles.decodePattern(text)) {
            is ImportResult.Failure -> ImportOutcome.Failed(r.reason)
            is ImportResult.Success -> {
                val warnings = r.warnings.toMutableList()
                var pattern = r.value
                val exists = q.selectPattern(pattern.id).executeAsOneOrNull() != null
                if (exists || pattern.id.isBlank()) {
                    pattern = pattern.copy(id = newId(), name = if (exists) "${pattern.displayName} (imported)" else pattern.name)
                }
                // Images get fresh ids so they can never collide with existing ones.
                val imageIds = ArrayList<String>()
                for (old in pattern.imageIds) {
                    val bytes = r.images[old]
                    if (bytes == null) { warnings += "A reference image was missing from the file"; continue }
                    val id = newId()
                    q.upsertImage(id, pattern.id, bytes)
                    imageIds += id
                }
                val known = customStitchesNow().mapTo(HashSet()) { it.id }
                for (def in pattern.customStitches) {
                    if (def.id !in known && StitchLibrary()[def.id] == null) {
                        q.upsertStitch(def.id, json.encodeToString(StitchDef.serializer(), def.copy(builtIn = false)))
                    }
                }
                val collections = q.selectCollections().executeAsList().mapTo(HashSet()) { it.id }
                // Attached documents likewise get fresh ids.
                val attached = pattern.files.mapNotNull { f ->
                    val bytes = r.images[f.id]
                    if (bytes == null) { warnings += "The attached file “${f.name}” was missing from the file"; null }
                    else f.copy(id = newId(), sizeBytes = bytes.size.toLong()).also { files.write(it.id, bytes) }
                }
                pattern = pattern.copy(
                    imageIds = imageIds,
                    files = attached,
                    collectionId = pattern.collectionId?.takeIf { it in collections },
                    archived = false,
                )
                val saved = save(pattern, touch = false).let { if (it.updatedAt == 0L) save(it) else it }
                ImportOutcome.Imported(saved, warnings)
            }
        }
    }

    /** Everything on this device as one `.knitbackup` document. */
    suspend fun exportBackup(): String = withContext(io) {
        val backup = BackupFile(
            exportedAt = clock(),
            patterns = allPatterns(),
            customStitches = customStitchesNow(),
            collections = q.selectCollections().executeAsList().map { PatternCollection(it.id, it.name, it.sort_index.toInt()) },
            yarnStash = q.selectStash(KIND_YARN).executeAsList().mapNotNull { runCatching { json.decodeFromString(Yarn.serializer(), it.json) }.getOrNull() },
            needleStash = q.selectStash(KIND_NEEDLE).executeAsList().mapNotNull { runCatching { json.decodeFromString(Needle.serializer(), it.json) }.getOrNull() },
            cableStash = q.selectStash(KIND_CABLE).executeAsList().mapNotNull { runCatching { json.decodeFromString(Cable.serializer(), it.json) }.getOrNull() },
            settings = settingsNow().toMap(),
        )
        val images = q.selectAllImages().executeAsList().associate { it.id to it.bytes } +
            backup.patterns.flatMap { it.files }.mapNotNull { f -> files.read(f.id)?.let { f.id to it } }
        val text = KnitFiles.encodeBackup(backup, images)
        db.transaction { q.upsertSetting("lastBackupAt", clock().toString()) }
        text
    }

    /**
     * Restores a backup. With [replace] the current library is cleared first; otherwise the
     * backup is merged in and patterns with the same id are replaced by the backup's version.
     * Runs in one transaction, so a failure leaves the existing data untouched.
     */
    suspend fun restoreBackup(text: String, replace: Boolean): Result<RestoreSummary> = withContext(io) {
        when (val r = KnitFiles.decodeBackup(text)) {
            is ImportResult.Failure -> Result.failure(IllegalArgumentException(r.reason))
            is ImportResult.Success -> runCatching {
                val b = r.value
                val previousFiles = if (replace) allPatterns().flatMap { it.files }.map { it.id } else emptyList()
                // Documents are written first; the database only refers to them once they exist.
                for (f in b.patterns.flatMap { it.files }) r.images[f.id]?.let { files.write(f.id, it) }
                db.transaction {
                    if (replace) {
                        q.deleteAllPatterns(); q.deleteAllImages(); q.deleteAllStitches()
                        q.deleteAllCollections(); q.deleteAllStash()
                    }
                    for (p in b.patterns) {
                        q.upsertPattern(p.id, p.name, p.updatedAt, json.encodeToString(Pattern.serializer(), p))
                        for (imageId in p.imageIds) r.images[imageId]?.let { q.upsertImage(imageId, p.id, it) }
                    }
                    // Photos of stash yarn.
                    for (y in b.yarnStash) {
                        y.photoId?.let { id -> r.images[id]?.let { q.upsertImage(id, STASH_IMAGES, it) } }
                    }
                    for (s in b.customStitches) q.upsertStitch(s.id, json.encodeToString(StitchDef.serializer(), s.copy(builtIn = false)))
                    for (c in b.collections) q.upsertCollection(c.id, c.name, c.sortIndex.toLong())
                    for (y in b.yarnStash) q.upsertStash(y.id, KIND_YARN, json.encodeToString(Yarn.serializer(), y))
                    for (n in b.needleStash) q.upsertStash(n.id, KIND_NEEDLE, json.encodeToString(Needle.serializer(), n))
                    for (c in b.cableStash) q.upsertStash(c.id, KIND_CABLE, json.encodeToString(Cable.serializer(), c))
                    if (replace) for ((k, v) in Settings.fromMap(b.settings).toMap()) q.upsertSetting(k, v)
                }
                val kept = b.patterns.flatMap { it.files }.mapTo(HashSet()) { it.id }
                previousFiles.filter { it !in kept }.forEach { files.delete(it) }
                RestoreSummary(b.patterns.size, b.customStitches.size, r.warnings)
            }
        }
    }

    suspend fun deleteEverything() = withContext(io) {
        allPatterns().flatMap { it.files }.forEach { files.delete(it.id) }
        db.transaction {
            q.deleteAllPatterns(); q.deleteAllImages(); q.deleteAllStitches()
            q.deleteAllCollections(); q.deleteAllStash()
        }
    }

    // --- Integrity ---------------------------------------------------------------------------

    /**
     * Checks the database file and every stored pattern. Returns human-readable problems;
     * an empty list means everything is readable.
     */
    suspend fun integrityCheck(): List<String> = withContext(io) {
        val problems = ArrayList<String>()
        val result = runCatching {
            driver.executeQuery(null, "PRAGMA integrity_check", { cursor ->
                val lines = ArrayList<String>()
                while (cursor.next().value) cursor.getString(0)?.let { lines += it }
                QueryResult.Value(lines)
            }, 0).value
        }.getOrElse { listOf("check failed: ${it.message}") }
        if (result != listOf("ok")) problems += result.map { "Database: $it" }
        for (row in q.selectPatterns().executeAsList()) {
            val p = decode(row.id, row.json)
            if (p == null) problems += "Pattern “${row.name}” cannot be read"
            else {
                val warnings = ArrayList<String>()
                KnitFiles.sanitize(p, warnings)
                problems += warnings.map { "“${p.displayName}”: $it" }
                for (f in p.files) if (!files.exists(f.id)) problems += "“${p.displayName}”: the attached file “${f.name}” is missing"
            }
        }
        problems
    }

    /** Repairs structurally damaged patterns in place (see [KnitFiles.sanitize]). Returns how many changed. */
    suspend fun repair(): Int = withContext(io) {
        var fixed = 0
        for (p in allPatterns()) {
            val clean = KnitFiles.sanitize(p)
            if (clean != p) { save(clean, touch = false); fixed++ }
        }
        fixed
    }

    val patternCount: Flow<Int> = patterns.map { it.size }

    /** Patterns together with the stitch library, for screens that need both. */
    val patternsWithLibrary: Flow<Pair<List<Pattern>, StitchLibrary>> = combine(patterns, library) { p, l -> p to l }

    private companion object {
        const val KIND_YARN = "yarn"
        const val KIND_NEEDLE = "needle"
        /** Owner id under which stash photos are stored in the image table. */
        const val STASH_IMAGES = "stash"
        const val KIND_CABLE = "cable"
    }
}
