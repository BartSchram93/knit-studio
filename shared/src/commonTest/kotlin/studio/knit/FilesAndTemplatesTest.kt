package studio.knit

import studio.knit.chart.CellRect
import studio.knit.chart.addColor
import studio.knit.chart.addRepeat
import studio.knit.chart.blankChart
import studio.knit.chart.paintColor
import studio.knit.chart.place
import studio.knit.io.BackupFile
import studio.knit.io.ImportResult
import studio.knit.io.KnitFiles
import studio.knit.model.Category
import studio.knit.model.Counter
import studio.knit.model.Difficulty
import studio.knit.model.Gauge
import studio.knit.model.Measurement
import studio.knit.model.Needle
import studio.knit.model.Pattern
import studio.knit.model.PatternCollection
import studio.knit.model.Progress
import studio.knit.model.ProjectNote
import studio.knit.model.SizedInt
import studio.knit.model.StitchCategory
import studio.knit.model.StitchDef
import studio.knit.model.Yarn
import studio.knit.model.YarnWeight
import studio.knit.stitch.StitchLibrary
import studio.knit.template.Templates
import studio.knit.validate.Severity
import studio.knit.validate.Validator
import studio.knit.written.evaluateSection
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A pattern that exercises every field of the model. */
fun richPattern(): Pattern {
    var chart = blankChart("chart-1", 8, 4, "Lace & cable")
    chart = chart.place(0, 0, lib.require("yo"), null, lib).place(0, 1, lib.require("k2tog"), null, lib)
        .place(2, 2, lib.require("c4b"), null, lib).addColor(0xFFC2553D).paintColor(1, 3, 0)
        .addRepeat("rep-1", CellRect.of(0, 0, 3, 7))
    val custom = StitchDef(id = "custom-nupp", name = "Nupp", abbr = "nupp", consumes = 1, produces = 1, symbol = "text:N",
        category = StitchCategory.CUSTOM, description = "Estonian nupp", ws = "p5tog-fake")
    val sec = section("K2, *p2, k2; rep from * to end", "P2, *k2, p2; rep from * to end", "@rep 1-2 x3, 5", "# Bind off",
        castOn = SizedInt(listOf(22, 26)), chartId = "chart-1")
    return Pattern(
        id = "pattern-1", name = "Søren's “Fjord” Scarf — test ✓", description = "Line one\nLine two",
        category = Category.SCARF, difficulty = Difficulty.INTERMEDIATE, sizes = listOf("S", "L"),
        gauge = Gauge(22.5, 30.0, "pattern stitch", 4.0),
        yarns = listOf(Yarn("y1", "MC", "Brand", "Merino DK", YarnWeight.DK, "100% merino", "Teal", 0xFF2F5D62, "L42", 112.0, 50.0, 3.0, "soft")),
        needles = listOf(Needle("n1", 4.0, material = "Bamboo", lengthCm = 80.0)),
        measurements = listOf(Measurement("m1", "Width", listOf(20.0, 24.0))),
        notes = "Block firmly.", imageIds = listOf("img-1"),
        charts = listOf(chart), sections = listOf(sec), customStitches = listOf(custom),
        draft = false, favorite = true, archived = false, collectionId = "col-1", createdAt = 1_700_000_000_000, updatedAt = 1_700_000_500_000,
        progress = Progress(
            startedAt = 1_700_000_100_000, sizeIndex = 1, activeTrackId = sec.id, positions = mapOf(sec.id to 5, "chart-1" to 2),
            counters = listOf(Counter("c1", "Repeats", 3, target = 10, resetAt = 4, laps = 2, linked = true)),
            notes = listOf(ProjectNote("pn1", "Dropped a stitch on row 3", 1_700_000_200_000, "Row 3")),
        ),
    )
}

class FilesAndTemplatesTest {

    @Test fun patternFileRoundTripsEveryField() {
        val original = richPattern()
        val image = ByteArray(300) { (it * 7).toByte() }
        val text = KnitFiles.encodePattern(original, mapOf("img-1" to image), now = 123)
        val result = assertIs<ImportResult.Success<Pattern>>(KnitFiles.decodePattern(text))
        assertEquals(original, result.value)
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
        assertContentEquals(image, result.images["img-1"])
        assertTrue(text.contains("\"format\": \"knit-studio.pattern\""))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test fun exportedTextIsStableAcrossRepeatedExports() {
        // Byte-identical output is what makes Android → iOS → Android lossless.
        val a = KnitFiles.encodePattern(richPattern(), now = 1)
        val again = KnitFiles.encodePattern((KnitFiles.decodePattern(a) as ImportResult.Success).value, now = 1)
        assertEquals(a, again)
    }

    @Test fun rejectsForeignNewerAndCorruptFiles() {
        fun reason(text: String) = assertIs<ImportResult.Failure>(KnitFiles.decodePattern(text)).reason
        assertTrue(reason("hello").contains("not a Knit Studio"))
        assertTrue(reason("{\"format\":\"something-else\",\"version\":1}").contains("not a Knit Studio"))
        assertTrue(reason("{\"format\":\"knit-studio.pattern\",\"version\":99,\"pattern\":{\"id\":\"x\"}}").contains("newer version"))
        assertTrue(reason("{\"format\":\"knit-studio.pattern\",\"version\":1}").contains("damaged"))
        assertTrue(reason("{\"format\":\"knit-studio.backup\",\"version\":1}").contains("full backup"))
        assertTrue(reason("{\"format\":\"knit-studio.pattern\"}").contains("version"))
    }

    @Test fun unknownFieldsFromFutureMinorVersionsAreIgnored() {
        val text = "{\"format\":\"knit-studio.pattern\",\"version\":1,\"futureField\":true,\"pattern\":{\"id\":\"x\",\"name\":\"N\",\"shiny\":1}}"
        assertEquals("N", assertIs<ImportResult.Success<Pattern>>(KnitFiles.decodePattern(text)).value.name)
    }

    @Test fun damagedChartIsRepairedAndReported() {
        val text = """{"format":"knit-studio.pattern","version":1,"pattern":{"id":"x","charts":[
            {"id":"c","name":"Broken","cols":3,"rows":2,"cells":["k","p","k#4","yo"],
             "repeats":[{"id":"r","colStart":1,"colEnd":9,"rowStart":0,"rowEnd":0}]}]}}"""
        val r = assertIs<ImportResult.Success<Pattern>>(KnitFiles.decodePattern(text))
        val chart = r.value.charts.single()
        assertEquals(6, chart.cells.size)
        assertEquals(listOf("k", "p", "k", "yo", "k", "k"), chart.cells.map { it.stitch })
        assertTrue(chart.cells.all { it.color == -1 })
        assertTrue(chart.repeats.isEmpty())
        assertEquals(3, r.warnings.size)
        assertTrue(Validator.validateChart(chart, lib).none { it.code == "chart.dimensions" })
    }

    @Test fun backupRoundTrips() {
        val backup = BackupFile(
            exportedAt = 5, patterns = listOf(richPattern(), Pattern(id = "p2", name = "Second")),
            customStitches = richPattern().customStitches, collections = listOf(PatternCollection("col-1", "Gifts", 0)),
            yarnStash = richPattern().yarns, needleStash = richPattern().needles, settings = mapOf("units" to "imperial"),
        )
        val text = KnitFiles.encodeBackup(backup, mapOf("img-1" to byteArrayOf(1, 2, 3)))
        assertTrue(KnitFiles.isBackup(text))
        val r = assertIs<ImportResult.Success<BackupFile>>(KnitFiles.decodeBackup(text))
        assertEquals(backup, r.value)
        assertContentEquals(byteArrayOf(1, 2, 3), r.images["img-1"])
        assertIs<ImportResult.Failure>(KnitFiles.decodeBackup(KnitFiles.encodePattern(richPattern())))
    }

    @Test fun fileNamesAreSafe() {
        assertEquals("Lace Scarf.knit", KnitFiles.fileName("Lace Scarf", "knit"))
        assertEquals("a b c.knit", KnitFiles.fileName("a/b\\c:", "knit"))
        assertEquals("Pattern.pdf", KnitFiles.fileName("  ", "pdf"))
    }

    // --- Templates -----------------------------------------------------------------------------

    @Test fun everyTemplateBuildsAPatternWithoutErrorsOrWarnings() {
        for (t in Templates.all) {
            for (gauge in listOf(t.defaultGauge, Gauge(14.0, 20.0), Gauge(30.0, 42.0))) {
                val p = Templates.build(t, gauge, emptyMap(), lib, ::id, 0)
                val issues = Validator.validate(p, lib).filter { it.severity != Severity.INFO }
                assertTrue(issues.isEmpty(), "${t.id} @ ${gauge.stitchesPer10cm}: $issues")
                assertTrue(p.sections.isNotEmpty(), t.id)
            }
        }
    }

    @Test fun scarfCastOnMatchesGaugeAndRib() {
        val t = Templates.byId("scarf")!!
        val p = Templates.build(t, Gauge(22.0, 28.0), mapOf("width" to 20.0, "length" to 150.0), lib, ::id, 0)
        val castOn = p.sections.single().castOn!!.forSize(0)
        assertEquals(46, castOn)                 // 44 exact → nearest multiple of 4 plus 2
        assertEquals(2, castOn % 4)
        val eval = evaluateSection(p.sections.single(), 0, lib)
        assertEquals(420, eval.rows.size)        // 150 cm at 28 rows per 10 cm
        assertEquals(castOn, eval.endCount)
    }

    @Test fun hatCrownDecreasesToEightStitches() {
        val p = Templates.build(Templates.byId("hat")!!, Gauge(20.0, 28.0), mapOf("head" to 56.0, "height" to 21.0), lib, ::id, 0)
        val (brim, body, crown) = p.sections
        assertEquals(104, brim.castOn!!.forSize(0)) // 56 cm × 0.9 × 2 sts/cm = 100.8 → 104
        assertEquals(104, evaluateSection(body, 0, lib).endCount)
        val crownEval = evaluateSection(crown, 0, lib)
        assertEquals(8, crownEval.endCount)
        val total = evaluateSection(brim, 0, lib).rows.size + evaluateSection(body, 0, lib).rows.size + crownEval.rows.size
        assertEquals(59, total)                     // 21 cm at 28 rounds per 10 cm
    }

    @Test fun clothGrowsAndShrinksSymmetrically() {
        val p = Templates.build(Templates.byId("cloth")!!, Gauge(18.0, 36.0), mapOf("side" to 22.0), lib, ::id, 0)
        val inc = evaluateSection(p.sections[0], 0, lib)
        val dec = evaluateSection(p.sections[1], 0, lib)
        assertEquals(56, inc.endCount)
        assertEquals(4, dec.endCount)
        assertEquals(inc.rows.size, dec.rows.size)
    }

    @Test fun chartedTemplatesHaveLinkedGeneratedRows() {
        val lace = Templates.build(Templates.byId("lace")!!, Gauge(20.0, 28.0), emptyMap(), lib, ::id, 0)
        assertEquals(lace.charts.single().id, lace.sections.single().chartId)
        assertEquals(
            listOf("*K18; rep from * to end", "*P18; rep from * to end",
                "*K2tog 3 times, yo, k1, yo, k1, yo, k1, yo, k1, yo, k1, yo, k1, k2tog 3 times; rep from * to end",
                "*K18; rep from * to end"),
            lace.sections.single().rows.map { it.text },
        )
        val cable = Templates.build(Templates.byId("cable")!!, Gauge(20.0, 28.0), emptyMap(), lib, ::id, 0)
        assertEquals("P2, C4B, p2", cable.sections.single().rows[2].text)
        assertEquals("K2, p4, k2", cable.sections.single().rows[1].text)
    }

    @Test fun customStitchesJoinTheLibraryWithoutShadowingBuiltIns() {
        val custom = listOf(
            StitchDef(id = "k", name = "Evil knit", abbr = "zz", consumes = 5),
            StitchDef(id = "nupp", name = "Nupp", abbr = "nupp", symbol = "text:N", consumes = 1, produces = 1),
        )
        val library = StitchLibrary(custom)
        assertEquals(1, library.require("k").consumes)
        assertEquals("Nupp", library.require("nupp").name)
        val e = studio.knit.written.RowEvaluator().evaluate(studio.knit.written.RowParser(library).parse("k2, nupp, k2"), 5)
        assertTrue(!e.hasErrors, e.issues.toString())
        assertEquals("Unknown stitch “zzz”", library.require("zzz").name)
    }

    // --- What people pick by mistake ---------------------------------------------------------

    @Test fun commonWrongFilesAreNamed() {
        fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() } + ByteArray(20)
        assertTrue(KnitFiles.identifyOtherFile("%PDF-1.4 rest".encodeToByteArray())!!.contains("PDF"))
        assertTrue(KnitFiles.identifyOtherFile(bytes(0x50, 0x4B, 0x03, 0x04))!!.contains("zip"))
        assertTrue(KnitFiles.identifyOtherFile(bytes(0xFF, 0xD8, 0xFF, 0xE0))!!.contains("photo"))
        assertTrue(KnitFiles.identifyOtherFile(ByteArray(0))!!.contains("empty"))
        assertEquals(null, KnitFiles.identifyOtherFile(KnitFiles.encodePattern(richPattern()).encodeToByteArray()))
    }

    @Test fun unreadableTextExplainsWhatItIs() {
        fun reason(text: String) = assertIs<ImportResult.Failure>(KnitFiles.decodePattern(text)).reason
        val good = KnitFiles.encodePattern(richPattern())
        assertTrue(reason(good.take(good.length / 2)).contains("incomplete or damaged"))
        assertTrue(reason("{\\rtf1\\ansi {\\fonttbl}").contains("RTF"))
        assertTrue(reason("Hello knitting").contains("starts with “Hello knitting”"))
        assertTrue(reason("").contains("empty"))
    }

    @Test fun byteOrderMarkAndTrailingNulsAreTolerated() {
        val original = richPattern()
        val text = "\uFEFF\n" + KnitFiles.encodePattern(original) + "\u0000\u0000"
        assertEquals(original, assertIs<ImportResult.Success<Pattern>>(KnitFiles.decodePattern(text)).value)
    }
}
