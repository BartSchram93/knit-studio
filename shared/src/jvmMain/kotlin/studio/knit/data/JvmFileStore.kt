package studio.knit.data

import java.io.File

/** Attached documents in a directory; used by tests and the desktop preview. */
class JvmFileStore(private val dir: File) : FileStore {
    init { dir.mkdirs() }
    private fun file(id: String) = File(dir, safeFileName(id))
    override fun write(id: String, bytes: ByteArray) = file(id).writeBytes(bytes)
    override fun read(id: String): ByteArray? = file(id).takeIf { it.exists() }?.readBytes()
    override fun delete(id: String) { file(id).delete() }
    override fun exists(id: String): Boolean = file(id).exists()
}
