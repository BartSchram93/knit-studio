package studio.knit.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.io.File

/**
 * Desktop stand-in used for previews and automated UI tests: files are written to a folder and
 * recorded so tests can inspect them, and pickers answer from [nextFile] / [nextImage].
 */
object DesktopPlatform : PlatformActions {
    var outputDir: File = File(System.getProperty("java.io.tmpdir"), "knit-studio-out")
    val written = ArrayList<Pair<String, ByteArray>>()
    var nextFile: PickedFile? = null
    var nextImage: ByteArray? = null

    private fun write(name: String, bytes: ByteArray) {
        outputDir.mkdirs()
        File(outputDir, name).writeBytes(bytes)
        written += name to bytes
    }

    override fun shareFile(name: String, mime: String, bytes: ByteArray) = write(name, bytes)
    override fun saveFile(name: String, mime: String, bytes: ByteArray) = write(name, bytes)
    override fun pickFile(onResult: (PickedFile?) -> Unit) = onResult(nextFile)
    override fun pickImage(onResult: (ByteArray?) -> Unit) = onResult(nextImage)
    override suspend fun pdfText(bytes: ByteArray): String? = pdfTextOf(bytes)
}

/** Text of a PDF with Apache PDFBox. */
fun pdfTextOf(bytes: ByteArray): String? = runCatching {
    org.apache.pdfbox.Loader.loadPDF(bytes).use { doc -> org.apache.pdfbox.text.PDFTextStripper().getText(doc) }
}.getOrNull()

@Composable
actual fun rememberPlatformActions(): PlatformActions = DesktopPlatform

@Composable
actual fun KeepScreenOn(enabled: Boolean) = Unit

actual val platformName: String = "Desktop"

actual fun decodeImage(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        org.jetbrains.skia.Bitmap.makeFromImage(org.jetbrains.skia.Image.makeFromEncoded(bytes)).asComposeImageBitmap()
    }.getOrNull()

@Composable
actual fun appVersion(): String = "development build"

/** Desktop builds are only used for tests and previews; they show a placeholder. */
@Composable
actual fun PdfView(bytes: ByteArray, modifier: androidx.compose.ui.Modifier) {
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.Text("PDF · ${bytes.size / 1024} KB\n(shown on the phone)", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
