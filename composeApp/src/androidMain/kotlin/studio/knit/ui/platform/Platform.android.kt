package studio.knit.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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

@Composable
actual fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("?")
    }
}

/**
 * Renders the pages with android.graphics.pdf.PdfRenderer, one bitmap per page at screen width,
 * in a scrolling list with pinch-to-zoom.
 */
@Composable
actual fun PdfView(bytes: ByteArray, modifier: androidx.compose.ui.Modifier) {
    val context = LocalContext.current
    // PdfRenderer needs a file; the bytes are copied to the private cache for viewing.
    val renderer = remember(bytes) {
        runCatching {
            val file = File(context.cacheDir, "viewer.pdf").apply { writeBytes(bytes) }
            android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY))
        }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    if (renderer == null) {
        androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("This PDF cannot be shown. It may be damaged or password-protected.")
        }
        return
    }
    val lock = remember(renderer) { Any() }
    var scale by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var offset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                }
            },
    ) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        androidx.compose.foundation.lazy.LazyColumn(
            androidx.compose.ui.Modifier.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            items(renderer.pageCount) { index ->
                val page by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, renderer, index, widthPx) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        // PdfRenderer can only have one page open at a time.
                        synchronized(lock) {
                            runCatching {
                                renderer.openPage(index).use { p ->
                                    // Render at up to twice the screen width so zooming stays sharp.
                                    val w = (widthPx * 2).coerceAtMost(2400)
                                    val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    p.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmap.asImageBitmap()
                                }
                            }.getOrNull()
                        }
                    }
                }
                val image = page
                if (image != null) {
                    androidx.compose.foundation.Image(image, "Page ${index + 1}", androidx.compose.ui.Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                } else {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().aspectRatio(0.707f).background(androidx.compose.ui.graphics.Color.White))
                }
            }
        }
    }
}
