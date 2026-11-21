package studio.knit.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * Attached documents in `Library/Application Support/documents/` inside the app's container —
 * next to the database, kept across updates and AltStore refreshes, included in phone backups.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class IosFileStore : FileStore {
    private val dir: String = run {
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        "$support/documents".also { NSFileManager.defaultManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null) }
    }
    private fun path(id: String) = "$dir/${safeFileName(id)}"

    override fun write(id: String, bytes: ByteArray) {
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        check(data.writeToFile(path(id), atomically = true)) { "could not save the document" }
    }

    override fun read(id: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(path(id)) ?: return null
        val out = ByteArray(data.length.toInt())
        if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return out
    }

    override fun delete(id: String) { NSFileManager.defaultManager.removeItemAtPath(path(id), null) }
    override fun exists(id: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path(id))
}
