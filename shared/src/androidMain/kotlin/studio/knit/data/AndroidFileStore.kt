package studio.knit.data

import android.content.Context
import java.io.File

/** Attached documents in the app's private files directory (kept across updates). */
class AndroidFileStore(context: Context) : FileStore {
    private val dir = File(context.applicationContext.filesDir, "documents").apply { mkdirs() }
    private fun file(id: String) = File(dir, safeFileName(id))
    override fun write(id: String, bytes: ByteArray) {
        // Write to a temporary file first so a crash can never leave half a document behind.
        val tmp = File(dir, safeFileName(id) + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file(id))) { file(id).writeBytes(bytes); tmp.delete() }
    }
    override fun read(id: String): ByteArray? = file(id).takeIf { it.exists() }?.readBytes()
    override fun delete(id: String) { file(id).delete() }
    override fun exists(id: String): Boolean = file(id).exists()
}
