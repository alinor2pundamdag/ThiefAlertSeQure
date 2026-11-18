package com.thiefalertsequre.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, bounded, local-only storage for owner-consented front-camera evidence. */
internal object CameraEvidenceVault {
    private const val PREFS = "camera_evidence_v1"
    private const val KEY_INDEX = "encrypted_index"
    private const val INDEX_PURPOSE = "thiefalertsequre:camera-evidence-index:v1"
    private const val KEY_ALIAS = "com.thiefalertsequre.camera_evidence_v1"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val DIRECTORY = "camera_evidence"
    private val keyLock = Any()

    data class Entry(
        val id: String,
        val timestampEpochMs: Long,
        val sha256: String,
        val sizeBytes: Int
    )

    @Synchronized
    fun save(
        context: Context,
        jpeg: ByteArray,
        timestampEpochMs: Long = System.currentTimeMillis()
    ): Entry? {
        if (!CameraEvidencePolicy.isValidImageSize(jpeg.size)) return null
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!CameraEvidencePolicy.isRetained(timestampEpochMs, now)) return null
        val entries = readIndex(app) ?: return null
        val retained = entries.filter { CameraEvidencePolicy.isRetained(it.timestampEpochMs, now) }
        val id = UUID.randomUUID().toString()
        val directory = evidenceDirectory(app)
        val file = directory.resolve("$id.aesgcm")
        val temp = directory.resolve("$id.tmp")
        val encrypted = runCatching { encrypt(id, jpeg) }.getOrNull() ?: return null
        val written = runCatching {
            FileOutputStream(temp).use { output ->
                output.write(encrypted)
                output.fd.sync()
            }
            if (!temp.renameTo(file)) throw IllegalStateException("Atomic evidence-file publish failed")
            true
        }.getOrDefault(false)
        if (!written) {
            temp.delete()
            file.delete()
            return null
        }
        val entry = Entry(id, timestampEpochMs, sha256(jpeg), jpeg.size)
        val next = (retained + entry).takeLast(CameraEvidencePolicy.MAX_IMAGES)
        if (!writeIndex(app, next)) {
            file.delete()
            return null
        }
        removeUnreferencedFiles(app, next)
        return entry
    }

    @Synchronized
    fun entries(context: Context): List<Entry> {
        val app = context.applicationContext
        pruneExpired(app)
        val now = System.currentTimeMillis()
        return (readIndex(app) ?: emptyList())
            .filter { CameraEvidencePolicy.isRetained(it.timestampEpochMs, now) }
            .takeLast(CameraEvidencePolicy.MAX_IMAGES)
            .asReversed()
    }

    fun read(context: Context, id: String): ByteArray? {
        if (!isSafeId(id)) return null
        val app = context.applicationContext
        val entry = (readIndex(app) ?: return null).firstOrNull { it.id == id } ?: return null
        if (!CameraEvidencePolicy.isRetained(entry.timestampEpochMs, System.currentTimeMillis())) return null
        val encryptedFile = evidenceDirectory(app).resolve("$id.aesgcm")
        if (!encryptedFile.isFile || encryptedFile.length() > CameraEvidencePolicy.MAX_IMAGE_BYTES + 64L) return null
        val encrypted = runCatching { encryptedFile.readBytes() }.getOrNull() ?: return null
        val plaintext = runCatching { decrypt(id, encrypted) }.getOrNull() ?: return null
        if (plaintext.size != entry.sizeBytes || sha256(plaintext) != entry.sha256 ||
            !CameraEvidencePolicy.isValidImageSize(plaintext.size)
        ) return null
        return plaintext
    }

    @Synchronized
    fun delete(context: Context, id: String): Boolean {
        if (!isSafeId(id)) return false
        val app = context.applicationContext
        val entries = readIndex(app) ?: return false
        if (entries.none { it.id == id }) return false
        val next = entries.filterNot { it.id == id }
        // Delete the ciphertext before removing its index entry. If deletion fails,
        // the index remains intact so the owner can retry instead of leaving an
        // untracked encrypted file behind.
        val file = evidenceDirectory(app).resolve("$id.aesgcm")
        val deleted = file.delete() || !file.exists()
        if (!deleted) return false
        if (next.isEmpty() && !deleteKeyIfPresent()) return false
        if (!writeIndex(app, next)) return false
        return true
    }

    @Synchronized
    fun deleteAll(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Keep the encrypted index until every ciphertext/temp file is gone.
        // A failed delete therefore remains discoverable and can be retried.
        val files = evidenceDirectory(app).listFiles() ?: return false
        val allDeleted = files.all { file ->
            runCatching { file.delete() || !file.exists() }.getOrDefault(false)
        }
        if (!allDeleted) return false
        // Keep the index if Keystore cleanup or preference commit fails so the
        // owner can retry the complete deletion operation.
        if (!deleteKeyIfPresent()) return false
        if (!prefs.edit().remove(KEY_INDEX).commit()) return false
        return true
    }

    @Synchronized
    fun verifyIntegrity(context: Context): Boolean {
        val app = context.applicationContext
        pruneExpired(app)
        val entries = readIndex(app) ?: return false
        val directoryFiles = evidenceDirectory(app).listFiles() ?: return false
        val temporaryFiles = directoryFiles.filter { it.name.endsWith(".tmp") }
        val tempCleanupSucceeded = temporaryFiles.all { it.delete() || !it.exists() }
        val indexedFiles = entries.map { "${it.id}.aesgcm" }.toSet()
        val actualFiles = directoryFiles
            .filter { it.name.endsWith(".aesgcm") }
            .map { it.name }.toSet()
        return tempCleanupSucceeded && indexedFiles == actualFiles && entries.all { read(app, it.id) != null }
    }

    private fun readIndex(context: Context): List<Entry>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = when (val value = SafePreferences.readString(prefs, KEY_INDEX, null)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return null
        } ?: return emptyList()
        return runCatching {
            val array = JSONArray(LocalPreferenceCipher.decrypt(INDEX_PURPOSE, raw))
            require(array.length() <= CameraEvidencePolicy.MAX_IMAGES)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.getString("id")
                    val at = item.getLong("at")
                    val hash = item.getString("sha256")
                    val size = item.getInt("size")
                    require(isSafeId(id))
                    require(at > 0L && hash.matches(Regex("[0-9a-f]{64}")))
                    require(CameraEvidencePolicy.isValidImageSize(size))
                    add(Entry(id, at, hash, size))
                }
            }.also { items -> require(items.map { it.id }.distinct().size == items.size) }
        }.getOrNull()
    }

    private fun writeIndex(context: Context, entries: List<Entry>): Boolean {
        if (entries.size > CameraEvidencePolicy.MAX_IMAGES || entries.any { !isSafeId(it.id) }) return false
        val encoded = JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("at", entry.timestampEpochMs)
                    put("sha256", entry.sha256)
                    put("size", entry.sizeBytes)
                })
            }
        }.toString()
        return runCatching {
            val encrypted = LocalPreferenceCipher.encrypt(INDEX_PURPOSE, encoded)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_INDEX, encrypted).commit()
        }.getOrDefault(false)
    }

    private fun encrypt(id: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size == IV_BYTES)
        return ByteArray(1 + iv.size + ciphertext.size).apply {
            this[0] = iv.size.toByte()
            System.arraycopy(iv, 0, this, 1, iv.size)
            System.arraycopy(ciphertext, 0, this, 1 + iv.size, ciphertext.size)
        }
    }

    private fun decrypt(id: String, payload: ByteArray): ByteArray {
        require(payload.size >= 1 + IV_BYTES + 16)
        val ivSize = payload[0].toInt() and 0xff
        require(ivSize == IV_BYTES)
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generator.generateKey()
        }
    }

    private fun deleteKeyIfPresent(): Boolean = runCatching {
        synchronized(keyLock) {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            !keyStore.containsAlias(KEY_ALIAS)
        }
    }.getOrDefault(false)

    private fun getExistingKey(): SecretKey = synchronized(keyLock) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Camera evidence encryption key is missing; existing photos cannot be decrypted.")
    }

    private fun pruneExpired(context: Context) {
        val entries = readIndex(context) ?: return
        val now = System.currentTimeMillis()
        val retained = entries.filter { CameraEvidencePolicy.isRetained(it.timestampEpochMs, now) }
        val indexReady = retained.size == entries.size || writeIndex(context, retained)
        if (indexReady) {
            removeUnreferencedFiles(context, retained)
            val remainingFiles = evidenceDirectory(context).listFiles() ?: return
            val noPhotoFilesRemain = remainingFiles.none {
                it.name.endsWith(".aesgcm") || it.name.endsWith(".tmp")
            }
            if (retained.isEmpty() && noPhotoFilesRemain) {
                runCatching {
                    synchronized(keyLock) {
                        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
                    }
                }
            }
        }
    }

    private fun evidenceDirectory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { if (!exists()) mkdirs() }

    private fun removeUnreferencedFiles(context: Context, entries: List<Entry>) {
        val keep = entries.map { "${it.id}.aesgcm" }.toSet()
        evidenceDirectory(context).listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
    }

    private fun isSafeId(id: String): Boolean =
        id.matches(Regex("[0-9a-fA-F-]{36}"))

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
