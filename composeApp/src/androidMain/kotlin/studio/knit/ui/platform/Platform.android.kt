package studio.knit.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

private class AndroidActions(private val context: Context) : PlatformActions {
    var pendingSave: ByteArray? = null
    var onFile: ((PickedFile?) -> Unit)? = null
    var onImage: ((ByteArray?) -> Unit)? = null
    var launchSave: (String, String) -> Unit = { _, _ -> }
    var launchOpen: () -> Unit = {}
    var launchImage: () -> Unit = {}

    override fun shareFile(name: String, mime: String, bytes: ByteArray) {
        // The file lives in the app's private cache and is exposed read-only through a FileProvider.
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, name).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun saveFile(name: String, mime: String, bytes: ByteArray) {
        pendingSave = bytes
        launchSave(name, mime)
    }

    override fun pickFile(onResult: (PickedFile?) -> Unit) { onFile = onResult; launchOpen() }
    override fun pickImage(onResult: (ByteArray?) -> Unit) { onImage = onResult; launchImage() }

    fun written(uri: Uri?) {
        val bytes = pendingSave
        pendingSave = null
        if (uri == null || bytes == null) return
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
    }

    fun opened(uri: Uri?) {
        val callback = onFile
        onFile = null
        if (uri == null) { callback?.invoke(null); return }
        val result = runCatching {
            var name = "file"
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            PickedFile(name, bytes)
        }.getOrNull()
        callback?.invoke(result)
    }

    fun imagePicked(uri: Uri?) {
        val callback = onImage
        onImage = null
        if (uri == null) { callback?.invoke(null); return }
        callback?.invoke(runCatching { loadScaledJpeg(uri) }.getOrNull())
    }

    /** Decodes at reduced resolution and re-encodes as JPEG so stored images stay small. */
    private fun loadScaledJpeg(uri: Uri, maxSide: Int = 1600): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide > maxSide) {
            val scale = maxSide.toFloat() / longSide
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
            out.toByteArray()
        }
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    val context = LocalContext.current
    val actions = remember(context) { AndroidActions(context) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { actions.written(it) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { actions.opened(it) }
    val image = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { actions.imagePicked(it) }
    actions.launchSave = { name, _ -> runCatching { save.launch(name) } }
    actions.launchOpen = { runCatching { open.launch(arrayOf("*/*")) } }
    actions.launchImage = { runCatching { image.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
    return actions
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

actual val platformName: String = "Android"

actual fun decodeImage(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
