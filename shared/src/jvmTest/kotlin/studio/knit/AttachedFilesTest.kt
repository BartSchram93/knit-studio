package studio.knit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import studio.knit.data.ImportOutcome
import studio.knit.data.JvmFileStore
import studio.knit.data.KnitRepository
import studio.knit.data.jvmDriver
import studio.knit.model.Pattern
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachedFilesTest {
    private fun dir(): File = Files.createTempDirectory("knit-files").toFile()
    private fun repo(d: File = dir()) = KnitRepository(jvmDriver(), io = Dispatchers.Unconfined, files = JvmFileStore(d))
    /** A 3 MB "PDF": larger than Android's ~2 MB database row limit, which is why files live on disk. */
    private val pdf = "%PDF-1.7\n".encodeToByteArray() + Random(3).nextBytes(3_000_000)

    private suspend fun KnitRepository.withPdf(): Pattern {
        val f = storeFile("Fjord sjaal.pdf", pdf)
        return create(Pattern(id = "p", name = "Fjord sjaal", files = listOf(f)))
    }

    @Test fun storedPdfIsReadBackExactly() = runTest {
        val r = repo()
        val p = r.withPdf()
        assertEquals(pdf.size.toLong(), p.files.single().sizeBytes)
        assertContentEquals(pdf, r.fileBytes(p.files.single().id))
        assertTrue(r.integrityCheck().isEmpty())
    }

    @Test fun patternFileCarriesThePdfToAnotherDevice() = runTest {
        val a = repo(); val b = repo()
        val p = a.withPdf()
        val imported = assertIs<ImportOutcome.Imported>(b.importPattern(a.exportPattern(p.id)!!)).pattern
        val f = imported.files.single()
        assertEquals("Fjord sjaal.pdf", f.name)
        assertNotEquals(p.files.single().id, f.id)
        assertContentEquals(pdf, b.fileBytes(f.id))
    }

    @Test fun duplicateCopiesAndDeleteRemovesTheFile() = runTest {
        val d = dir()
        val r = repo(d)
        val p = r.withPdf()
        val copy = r.duplicate(p.id)!!
        assertNotEquals(p.files.single().id, copy.files.single().id)
        r.delete(p.id)
        assertNull(r.fileBytes(p.files.single().id))
        assertContentEquals(pdf, r.fileBytes(copy.files.single().id))
        r.delete(copy.id)
        assertTrue(d.listFiles()!!.isEmpty(), "no orphaned files on disk")
    }

    @Test fun backupRestoresThePdf() = runTest {
        val a = repo()
        val p = a.withPdf()
        val backup = a.exportBackup()
        val b = repo()
        b.create(Pattern(id = "other", name = "Other", files = listOf(b.storeFile("old.pdf", byteArrayOf(1)))))
        b.restoreBackup(backup, replace = true).getOrThrow()
        assertEquals(listOf("Fjord sjaal"), b.patterns.first().map { it.name })
        assertContentEquals(pdf, b.fileBytes(p.files.single().id))
        assertTrue(b.integrityCheck().isEmpty())
    }

    @Test fun missingFileIsReported() = runTest {
        val d = dir()
        val r = repo(d)
        val p = r.withPdf()
        File(d, p.files.single().id).delete()
        assertTrue(r.integrityCheck().single().contains("Fjord sjaal.pdf"))
    }

    @Test fun patternsSavedBeforeAttachmentsStillLoad() {
        val old = """{"id":"x","name":"Old"}"""
        val p = studio.knit.io.KnitFiles.json.decodeFromString(Pattern.serializer(), old)
        assertTrue(p.files.isEmpty())
    }
}
