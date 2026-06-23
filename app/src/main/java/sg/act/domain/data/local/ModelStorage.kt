package sg.act.domain.data.local

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Owns where on-device model files live and validates that a file is actually a
 * GGUF model. Models are kept in app-private storage (`filesDir/models`), which is
 * excluded from backup — model bytes never leave the device through Android's
 * backup framework.
 */
class ModelStorage(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun fileFor(name: String): File = File(dir, name.sanitized())

    /** Temp path a download streams into before validation/rename. */
    fun tempFor(name: String): File = File(dir, "${name.sanitized()}.part")

    fun listModels(): List<File> = dir.listFiles()?.filter { it.isFile }.orEmpty()

    fun freeSpaceBytes(): Long = dir.usableSpace

    /** Validate a fully-downloaded temp file and atomically promote it to [name]. */
    fun finalize(temp: File, name: String): File {
        require(isGguf(temp)) { temp.delete(); "Downloaded file is not a valid GGUF model." }
        val target = fileFor(name)
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Could not finalize downloaded model." }
        return target
    }

    /**
     * Copy [source] into model storage under [name], validating the GGUF magic.
     * The copy goes to a temp file first and is renamed atomically on success.
     */
    fun importFrom(source: InputStream, name: String): File {
        val target = fileFor(name)
        val tmp = File(dir, "${target.name}.part")
        tmp.outputStream().use { out -> source.copyTo(out) }
        require(isGguf(tmp)) { tmp.delete(); "Not a valid GGUF model file." }
        if (target.exists()) target.delete()
        check(tmp.renameTo(target)) { "Could not finalize imported model." }
        return target
    }

    /** GGUF files begin with the ASCII magic "GGUF" (0x47 0x47 0x55 0x46). */
    fun isGguf(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 &&
                magic[0] == 0x47.toByte() && magic[1] == 0x47.toByte() &&
                magic[2] == 0x55.toByte() && magic[3] == 0x46.toByte()
        }
    }.getOrDefault(false)

    private fun String.sanitized(): String {
        val base = substringAfterLast('/').ifBlank { "model.gguf" }
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
