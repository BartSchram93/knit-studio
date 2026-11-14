package studio.knit.ui.platform

import androidx.compose.runtime.Composable

class PickedFile(val name: String, val bytes: ByteArray)

/**
 * The few things only the operating system can do: hand a file to another app, save it where the
 * user chooses, and let the user pick a file or a photo.
 */
interface PlatformActions {
    /** Opens the system share sheet for a file with the given content. */
    fun shareFile(name: String, mime: String, bytes: ByteArray)

    /** Lets the user choose where to save a file (Files app / storage access framework). */
    fun saveFile(name: String, mime: String, bytes: ByteArray)

    /** Lets the user choose a file; null when cancelled or unreadable. */
    fun pickFile(onResult: (PickedFile?) -> Unit)

    /** Lets the user choose a photo; delivered as JPEG no larger than 1600 px on its long side. */
    fun pickImage(onResult: (ByteArray?) -> Unit)
}

@Composable
expect fun rememberPlatformActions(): PlatformActions

/** Keeps the display awake while [enabled] and this composable is on screen. */
@Composable
expect fun KeepScreenOn(enabled: Boolean)

expect val platformName: String

/** Decodes JPEG/PNG bytes for display; null when the data is not an image. */
expect fun decodeImage(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap?
