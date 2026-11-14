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
}

@Composable
actual fun rememberPlatformActions(): PlatformActions = DesktopPlatform

@Composable
actual fun KeepScreenOn(enabled: Boolean) = Unit

actual val platformName: String = "Desktop"

actual fun decodeImage(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        org.jetbrains.skia.Bitmap.makeFromImage(org.jetbrains.skia.Image.makeFromEncoded(bytes)).asComposeImageBitmap()
    }.getOrNull()
