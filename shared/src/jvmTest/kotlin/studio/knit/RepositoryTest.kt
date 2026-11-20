package studio.knit

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import studio.knit.chart.blankChart
import studio.knit.chart.place
import studio.knit.data.ImportOutcome
import studio.knit.data.KnitRepository
import studio.knit.data.PaperSize
import studio.knit.data.ThemeMode
import studio.knit.data.jvmDriver
import studio.knit.db.KnitDatabase
import studio.knit.model.Needle
import studio.knit.model.Pattern
import studio.knit.model.PatternCollection
import studio.knit.model.StitchDef
import studio.knit.model.Yarn
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {
    private var time = 1_000L
    private fun repo(path: String? = null) = KnitRepository(jvmDriver(path), clock = { time++ }, io = Dispatchers.Unconfined)

    @Test fun saveLoadEditDelete() = runTest {
        val r = repo()
        val saved = r.create(Pattern(id = "a", name = "First"))
        assertEquals(saved.createdAt, 1_000L)
        assertEquals(saved, r.getPattern("a"))

        val edited = r.save(saved.copy(name = "Renamed", notes = "n"))
        assertTrue(edited.updatedAt > saved.updatedAt)
        assertEquals(saved.createdAt, edited.createdAt)
        assertEquals("Renamed", r.getPattern("a")!!.name)
        assertEquals(listOf("Renamed"), r.patterns.first().map { it.name })

        r.delete("a")
        assertNull(r.getPattern("a"))
        assertTrue(r.patterns.first().isEmpty())
    }

    @Test fun listIsOrderedByMostRecentChange() = runTest {
        val r = repo()
        r.create(Pattern(id = "a", name = "A"))
        r.create(Pattern(id = "b", name = "B"))
        r.save(r.getPattern("a")!!.copy(notes = "touched"))
        assertEquals(listOf("A", "B"), r.patterns.first().map { it.name })
    }

    @Test fun duplicateIsIndependentAndCopiesImages() = runTest {
        val r = repo()
        val original = r.create(richPattern().copy(imageIds = emptyList()))
        val img = r.addImage(original.id, byteArrayOf(9, 8, 7))
        r.save(original.copy(imageIds = listOf(img)))

        val copy = assertNotNull(r.duplicate(original.id))
        assertNotEquals(original.id, copy.id)
        assertTrue(copy.name.endsWith("copy"))
        assertNull(copy.progress)
        assertEquals(original.charts, copy.charts)
        assertNotEquals(img, copy.imageIds.single())
        assertContentEquals(byteArrayOf(9, 8, 7), r.image(copy.imageIds.single()))

        // Deleting the original leaves the copy and its image intact.
        r.delete(original.id)
        assertNull(r.image(img))
        assertContentEquals(byteArrayOf(9, 8, 7), r.image(copy.imageIds.single()))
        assertEquals(1, r.patterns.first().size)
    }

    @Test fun dataSurvivesReopeningTheDatabaseFile() = runTest {
        val file = File.createTempFile("knit", ".db").also { it.delete() }
        try {
            repo(file.path).create(richPattern())
            val reopened = repo(file.path)
            assertEquals(richPattern().charts, reopened.getPattern("pattern-1")!!.charts)
            assertTrue(reopened.integrityCheck().isEmpty())
        } finally { file.delete() }
    }

    /**
     * The cross-platform guarantee: both apps run this same shared code, so a file exported by one
     * and imported by the other must come back identical. Two separate databases stand in for the
     * two phones; the file is passed along as text and then sent back.
     */
    @Test fun exportOnOneDeviceImportOnAnotherAndBack() = runTest {
        val android = repo()
        val ios = repo()
        val custom = richPattern().customStitches.single()
        android.saveStitch(custom)
        // Use the custom stitch in the chart so it travels with the pattern.
        val withNupp = richPattern().let { p ->
            val library = android.libraryNow()
            p.copy(imageIds = emptyList(), charts = p.charts.map { it.place(3, 0, library.require(custom.id), null, library) })
        }
        val original = android.create(withNupp)
        val img = android.addImage(original.id, ByteArray(64) { it.toByte() })
        val source = android.save(original.copy(imageIds = listOf(img)))

        val file = assertNotNull(android.exportPattern(source.id))
        val onIos = assertIs<ImportOutcome.Imported>(ios.importPattern(file))
        assertTrue(onIos.warnings.isEmpty(), onIos.warnings.toString())
        // Everything except device-local ids (images) and the collection link is identical.
        fun Pattern.comparable() = copy(imageIds = emptyList(), collectionId = null, customStitches = customStitches.map { it.copy(builtIn = false) })
        assertEquals(source.comparable(), onIos.pattern.comparable())
        assertContentEquals(ByteArray(64) { it.toByte() }, ios.image(onIos.pattern.imageIds.single()))
        assertEquals(listOf("Nupp"), ios.customStitches.first().map { it.name })
        assertEquals(source.progress, onIos.pattern.progress)

        // And back again.
        val back = assertNotNull(ios.exportPattern(onIos.pattern.id))
        val onAndroid = assertIs<ImportOutcome.Imported>(android.importPattern(back))
        assertEquals(source.comparable().copy(id = onAndroid.pattern.id, name = onAndroid.pattern.name), onAndroid.pattern.comparable())
        // The original was not overwritten: the returning file became a second pattern.
        assertNotEquals(source.id, onAndroid.pattern.id)
        assertTrue(onAndroid.pattern.name.endsWith("(imported)"))
        assertEquals(2, android.patterns.first().size)
    }

    @Test fun importRejectsGarbageWithoutTouchingTheLibrary() = runTest {
        val r = repo()
        r.create(Pattern(id = "a", name = "Keep"))
        assertIs<ImportOutcome.Failed>(r.importPattern("not json"))
        assertIs<ImportOutcome.Failed>(r.importPattern("{\"format\":\"knit-studio.pattern\",\"version\":7,\"pattern\":{\"id\":\"z\"}}"))
        assertEquals(listOf("Keep"), r.patterns.first().map { it.name })
    }

    @Test fun backupAndRestore() = runTest {
        val a = repo()
        a.create(richPattern().copy(imageIds = emptyList()))
        a.create(Pattern(id = "second", name = "Second"))
        a.saveStitch(StitchDef(id = "nupp", name = "Nupp", abbr = "nupp"))
        a.saveCollection(PatternCollection("col-1", "Gifts"))
        a.saveYarn(Yarn(id = "y", brand = "Holst", name = "Supersoft"))
        a.saveNeedle(Needle(id = "n", sizeMm = 3.5))
        a.saveCable(studio.knit.model.Cable(id = "c", brand = "ChiaoGoo", lengthCm = 80.0, connector = "[S]", count = 2))
        a.updateSettings { it.copy(imperial = true, paper = PaperSize.LETTER) }
        a.addImage("second", byteArrayOf(1, 2))
        val backup = a.exportBackup()
        assertTrue(a.settingsNow().lastBackupAt > 0)

        val b = repo()
        b.create(Pattern(id = "old", name = "Old"))
        val merged = b.restoreBackup(backup, replace = false).getOrThrow()
        assertEquals(2, merged.patterns)
        assertEquals(setOf("Old", "Second", richPattern().name), b.patterns.first().map { it.name }.toSet())
        assertEquals(false, b.settingsNow().imperial)

        val replaced = b.restoreBackup(backup, replace = true).getOrThrow()
        assertEquals(2, replaced.patterns)
        assertEquals(2, b.patterns.first().size)
        assertEquals(true, b.settingsNow().imperial)
        assertEquals(listOf("Gifts"), b.collections.first().map { it.name })
        assertEquals(listOf("Holst Supersoft"), b.yarnStash.first().map { it.displayName })
        assertEquals(listOf(3.5), b.needleStash.first().map { it.sizeMm })
        assertEquals(listOf(Triple(80.0, "[S]", 2)), b.cableStash.first().map { Triple(it.lengthCm, it.connector, it.count) })
        assertEquals(listOf("Nupp"), b.customStitches.first().map { it.name })

        // A bad backup changes nothing.
        assertTrue(b.restoreBackup("{}", replace = true).isFailure)
        assertEquals(2, b.patterns.first().size)
    }

    @Test fun settingsPersistAndDefaultSafely() = runTest {
        val r = repo()
        assertEquals(ThemeMode.SYSTEM, r.settingsNow().theme)
        r.updateSettings { it.copy(theme = ThemeMode.DARK, ukTerms = true, haptics = false) }
        val s = r.settings.first()
        assertEquals(ThemeMode.DARK, s.theme); assertTrue(s.ukTerms); assertEquals(false, s.haptics)
    }

    @Test fun customStitchesAreEmbeddedInPatternsThatUseThem() = runTest {
        val r = repo()
        r.saveStitch(StitchDef(id = "nupp", name = "Nupp", abbr = "nupp", symbol = "text:N"))
        r.saveStitch(StitchDef(id = "unused", name = "Unused", abbr = "zzq"))
        val lib = r.libraryNow()
        val chart = blankChart("c", 3, 1).place(0, 1, lib.require("nupp"), null, lib)
        val saved = r.create(Pattern(id = "p", name = "P", charts = listOf(chart)))
        assertEquals(listOf("nupp"), saved.customStitches.map { it.id })
    }

    @Test fun deletingACollectionKeepsItsPatterns() = runTest {
        val r = repo()
        r.saveCollection(PatternCollection("c", "Socks"))
        r.create(Pattern(id = "p", name = "P", collectionId = "c"))
        r.deleteCollection("c")
        assertNull(r.getPattern("p")!!.collectionId)
        assertTrue(r.collections.first().isEmpty())
    }

    @Test fun unreadableRowsAreReportedNotLost() = runTest {
        val driver = jvmDriver()
        val r = KnitRepository(driver, io = Dispatchers.Unconfined)
        r.create(Pattern(id = "good", name = "Good"))
        KnitDatabase(driver).knitQueries.upsertPattern("bad", "Broken one", 5, "{ this is not json")
        assertEquals(listOf("Good"), r.patterns.first().map { it.name })
        val problems = r.integrityCheck()
        assertTrue(problems.single().contains("Broken one"), problems.toString())
        // The damaged row is still in the database for manual recovery.
        assertEquals(2L, KnitDatabase(driver).knitQueries.countPatterns().executeAsOne())
    }

    @Test fun repairFixesDamagedCharts() = runTest {
        val driver = jvmDriver()
        val r = KnitRepository(driver, io = Dispatchers.Unconfined)
        val damaged = Pattern(id = "p", name = "P", charts = listOf(blankChart("c", 4, 2).copy(cols = 5)))
        KnitDatabase(driver).knitQueries.upsertPattern("p", "P", 1, studio.knit.io.KnitFiles.json.encodeToString(Pattern.serializer(), damaged))
        assertEquals(1, r.integrityCheck().size)
        assertEquals(1, r.repair())
        assertTrue(r.integrityCheck().isEmpty())
        assertEquals(10, r.getPattern("p")!!.charts.single().cells.size)
    }

    /** Opens a database created with schema version 1 and upgrades it without losing data. */
    @Test fun migratesVersion1DatabaseToCurrentSchema() = runTest {
        val file = File.createTempFile("knit-v1", ".db").also { it.delete() }
        try {
            val v1 = JdbcSqliteDriver("jdbc:sqlite:${file.path}")
            listOf(
                "CREATE TABLE pattern (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, updated_at INTEGER NOT NULL, json TEXT NOT NULL)",
                "CREATE TABLE custom_stitch (id TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL)",
                "CREATE TABLE setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
                "CREATE TABLE image (id TEXT NOT NULL PRIMARY KEY, pattern_id TEXT NOT NULL, bytes BLOB NOT NULL)",
                "INSERT INTO pattern VALUES ('old', 'From v1', 42, '{\"id\":\"old\",\"name\":\"From v1\"}')",
                "INSERT INTO setting VALUES ('imperial', 'true')",
                "PRAGMA user_version = 1",
            ).forEach { v1.execute(null, it, 0) }
            v1.close()

            assertEquals(2L, KnitDatabase.Schema.version)
            val r = KnitRepository(jvmDriver(file.path), io = Dispatchers.Unconfined)
            assertEquals("From v1", r.getPattern("old")!!.name)
            assertTrue(r.settingsNow().imperial)
            // Tables added in version 2 are usable.
            r.saveCollection(PatternCollection("c", "New in v2"))
            r.saveYarn(Yarn(id = "y", name = "Stash"))
            assertEquals(1, r.collections.first().size)
            assertTrue(r.integrityCheck().isEmpty())
        } finally { file.delete() }
    }

    @Test fun backupsMadeBeforeCablesExistedStillRestore() = runTest {
        val old = """{"format":"knit-studio.backup","version":1,"patterns":[],"needleStash":[{"id":"n","sizeMm":4.0,"notes":"ChiaoGoo"}]}"""
        val r = repo()
        r.restoreBackup(old, replace = true).getOrThrow()
        assertEquals(listOf("ChiaoGoo"), r.needleStash.first().map { it.notes })
        assertTrue(r.cableStash.first().isEmpty())
    }
}
