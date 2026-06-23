package sg.act.domain.data.local

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import sg.act.domain.core.CrashReporting
import sg.act.domain.data.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists conversations to an AES-256 encrypted file in app-private storage.
 *
 * The encryption key is held in the Android Keystore (hardware-backed where
 * available) via [MasterKey], so conversation history is unreadable at rest even
 * with filesystem access. Combined with the backup opt-out in the manifest, chat
 * data never leaves the device in any form.
 *
 * IMPORTANT: [EncryptedFile] binds the ciphertext to the file's *name* (it uses
 * the filename as the AEAD associated data). So a file written under one name and
 * then renamed to another can no longer be decrypted — the name it's read under
 * won't match the name it was sealed with. We therefore always write to the
 * canonical [FILE_NAME] directly (never write-temp-then-rename), keep a raw
 * ciphertext backup for crash safety, and recover histories left unreadable by an
 * older build that did rename across names.
 */
class ConversationStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** Raw ciphertext backup of the last good [file], for crash-safe writes. */
    private val backupFile: File get() = File(context.filesDir, "$FILE_NAME.bak")

    /**
     * The temp name an older build sealed data under before renaming it over
     * [file]. Reused here to (a) recover that legacy data and (b) read a temp left
     * by an interrupted old save.
     */
    private val legacyTempFile: File get() = File(context.filesDir, "$FILE_NAME.tmp")

    private fun encryptedFile(target: File): EncryptedFile =
        EncryptedFile.Builder(
            context,
            target,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

    /** Decrypt [target] (named so its AEAD associated-data matches). Null on any failure. */
    private fun readEncrypted(target: File): List<Conversation>? {
        if (!target.exists()) return null
        return runCatching {
            encryptedFile(target).openFileInput().use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty()) {
                    emptyList()
                } else {
                    json.decodeFromString<List<Conversation>>(bytes.decodeToString())
                }
            }
        }.onFailure { CrashReporting.record(it) }.getOrNull()
    }

    suspend fun load(): List<Conversation> = withContext(Dispatchers.IO) {
        // 1. Canonical read — the normal path for correctly-written files.
        readEncrypted(file)?.let { return@withContext it }

        // 2. Crash-safety backup: a prior save left a raw copy of the last good
        //    ciphertext (sealed under the canonical name). Restore the name so the
        //    associated-data matches, then read.
        if (backupFile.exists() && !file.exists()) {
            backupFile.renameTo(file)
            readEncrypted(file)?.let { return@withContext it }
        }

        // 3. Legacy recovery: an older build wrote a temp file and renamed it over
        //    the canonical name, so the committed bytes were sealed under the temp
        //    name and can't be read as the canonical name. Re-create the temp name
        //    from the canonical bytes and read it back — recovering lost history.
        if (file.exists()) {
            runCatching { file.copyTo(legacyTempFile, overwrite = true) }
            readEncrypted(legacyTempFile)?.let { recovered ->
                legacyTempFile.delete()
                CrashReporting.log("Recovered ${recovered.size} conversations from legacy-named file")
                return@withContext recovered
            }
            legacyTempFile.delete()
        }

        // 4. A temp left by an interrupted *old* save is self-consistent (its name
        //    matches what it was sealed with), so try it directly as a last resort.
        readEncrypted(legacyTempFile)?.let { return@withContext it }

        emptyList()
    }

    suspend fun save(conversations: List<Conversation>) = withContext(Dispatchers.IO) {
        // Back up the previous good file (raw ciphertext copy) before touching it,
        // so a failed write can't destroy existing history.
        if (file.exists()) runCatching { file.copyTo(backupFile, overwrite = true) }
        // EncryptedFile refuses to overwrite, and a stale temp would shadow recovery.
        if (file.exists()) file.delete()
        legacyTempFile.delete()
        try {
            // Write directly to the canonical name so it's sealed under the same
            // name it will later be read with.
            encryptedFile(file).openFileOutput().use { output ->
                output.write(json.encodeToString(conversations).encodeToByteArray())
            }
            // Verify it reads back before discarding the backup.
            checkNotNull(readEncrypted(file)) { "post-write verification failed" }
            backupFile.delete()
        } catch (e: Exception) {
            CrashReporting.record(e)
            // Restore the previous good file so we never leave the user with nothing.
            if (backupFile.exists()) {
                file.delete()
                backupFile.renameTo(file)
            }
            throw e
        }
    }

    private companion object {
        const val FILE_NAME = "conversations.enc"
    }
}
