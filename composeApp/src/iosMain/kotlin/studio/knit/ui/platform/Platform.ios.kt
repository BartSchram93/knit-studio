package studio.knit.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

private fun topController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

private fun tempFile(name: String, bytes: ByteArray): NSURL? {
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
    return if (bytes.toNSData().writeToURL(url, atomically = true)) url else null
}

@OptIn(ExperimentalForeignApi::class)
private class IosActions : PlatformActions {
    // Delegates are held here because UIKit keeps only weak references to them.
    private var documentDelegate: NSObject? = null
    private var photoDelegate: NSObject? = null

    override fun shareFile(name: String, mime: String, bytes: ByteArray) {
        val url = tempFile(name, bytes) ?: return
        val host = topController() ?: return
        val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        // Required on iPad, harmless on iPhone.
        sheet.popoverPresentationController?.sourceView = host.view
        sheet.popoverPresentationController?.sourceRect = host.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0) }
        host.presentViewController(sheet, animated = true, completion = null)
    }

    override fun saveFile(name: String, mime: String, bytes: ByteArray) {
        val url = tempFile(name, bytes) ?: return
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        topController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun pickFile(onResult: (PickedFile?) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypeData, UTTypeItem), asCopy = true)
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                val data = url?.let { NSData.dataWithContentsOfURL(it) }
                onResult(if (url != null && data != null) PickedFile(url.lastPathComponent ?: "file", data.toByteArray()) else null)
                documentDelegate = null
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                onResult(null)
                documentDelegate = null
            }
        }
        documentDelegate = delegate
        picker.delegate = delegate
        topController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun pickImage(onResult: (ByteArray?) -> Unit) {
        val config = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration = config)
        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, completion = null)
                photoDelegate = null
                val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
                if (provider == null) { onResult(null); return }
                provider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
                    val jpeg = data?.let { UIImage.imageWithData(it) }?.let { scaledJpeg(it) }
                    dispatch_async(dispatch_get_main_queue()) { onResult(jpeg) }
                }
            }
        }
        photoDelegate = delegate
        picker.delegate = delegate
        topController()?.presentViewController(picker, animated = true, completion = null)
    }

    /** Redraws at no more than [maxSide] points on the long side and encodes as JPEG. */
    private fun scaledJpeg(image: UIImage, maxSide: Double = 1600.0): ByteArray? {
        val (w, h) = image.size.useContents { width to height }
        if (w <= 0.0 || h <= 0.0) return null
        val scale = minOf(1.0, maxSide / maxOf(w, h))
        val target = CGSizeMake(w * scale, h * scale)
        UIGraphicsBeginImageContextWithOptions(target, true, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, w * scale, h * scale))
        val scaled = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return (scaled ?: image).let { UIImageJPEGRepresentation(it, 0.86) }?.toByteArray()
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions = remember { IosActions() }

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

actual val platformName: String = "iOS"

actual fun decodeImage(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        org.jetbrains.skia.Bitmap.makeFromImage(org.jetbrains.skia.Image.makeFromEncoded(bytes)).asComposeImageBitmap()
    }.getOrNull()

@Composable
actual fun appVersion(): String {
    val info = platform.Foundation.NSBundle.mainBundle.infoDictionary
    val version = info?.get("CFBundleShortVersionString") as? String ?: "?"
    val build = info?.get("CFBundleVersion") as? String
    return if (build != null) "$version ($build)" else version
}
