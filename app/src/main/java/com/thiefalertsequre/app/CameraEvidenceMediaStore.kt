package com.thiefalertsequre.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Publishes owner-consented photos into the app's dedicated Gallery folder. */
internal object CameraEvidenceMediaStore {
    private const val DIRECTORY_NAME = "ThiefAlertSeQure"
    private val OWNED_IMAGE_NAME = Regex("^ThiefAlertSeQure-[0-9]{10,13}-[0-9a-f]{8}\\.jpg$")
    private val MIGRATION_LOCK = Any()
    private const val LEGACY_INDEX_PREFS = "camera_gallery_ownership_v1"
    private const val KEY_LEGACY_OWNED_IMAGES = "owned_images"
    private const val LEGACY_INDEX_PURPOSE = "thiefalertsequre:legacy-gallery-ownership:v1"

    private data class LegacyOwnershipEntry(val id: String, val name: String)

    data class MigrationResult(
        val enumerationSucceeded: Boolean,
        val copiedToVault: Int = 0,
        val originalsRemoved: Int = 0,
        val copyFailures: Int = 0,
        val removalFailures: Int = 0,
        val capacityLimited: Int = 0,
        val vaultIntegrityVerified: Boolean = false,
        val blockedByProtection: Boolean = false,
        val blockedByEncryptionSetting: Boolean = false,
        val unverifiedLegacyPhotos: Int = 0
    )

    @Synchronized
    fun save(context: Context, jpeg: ByteArray): Uri? {
        if (!CameraEvidencePolicy.isValidImageSize(jpeg.size)) return null
        val app = context.applicationContext
        val resolver = app.contentResolver
        val collection = imageCollectionUri()
        val now = System.currentTimeMillis()
        // Reserve one Gallery slot before capture. This keeps the hard 20-photo
        // cap even if protection becomes active before post-insert retention runs.
        if (ProtectionStore(app).read().mode == ProtectionMode.DISARMED) {
            runCatching { prune(app, now, maxImages = CameraEvidencePolicy.MAX_IMAGES - 1) }
        }
        val currentGallery = queryOwnedImages(app)
        if (!currentGallery.success ||
            currentGallery.images.size + currentGallery.unverifiedLegacyCount >= CameraEvidencePolicy.MAX_IMAGES
        ) return null
        val fileName = "ThiefAlertSeQure-$now-${UUID.randomUUID().toString().take(8)}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME/")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DIRECTORY_NAME)
                if (!directory.exists() && !directory.mkdirs()) return null
                put(MediaStore.Images.Media.DATA, File(directory, fileName).absolutePath)
            }
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri, "w")?.use { it.write(jpeg); it.flush() }
                ?: throw IllegalStateException("Gallery output stream unavailable")
            if (Build.VERSION.SDK_INT >= 29) {
                val published = resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                if (published <= 0) throw IllegalStateException("Gallery image could not be published")
            }
            true
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        if (Build.VERSION.SDK_INT < 29 && !rememberLegacyImage(app, uri, fileName)) {
            val removed = runCatching { resolver.delete(uri, null, null) == 1 }.getOrDefault(false)
            LocalTamperHistory.record(
                app, "legacy_gallery_ownership_index_failed",
                "API 26–28 Gallery ownership could not be recorded securely; the just-created photo was ${if (removed) "removed" else "left visible but unmanaged"}"
            )
            return null
        }
        runCatching { prune(context.applicationContext, now) }
        return uri
    }

    @Synchronized
    fun enforceRetention(context: Context) {
        prune(context.applicationContext, System.currentTimeMillis())
    }

    /**
     * Copies app-owned Gallery photos into the encrypted vault, verifies every
     * ciphertext by reading it back, then removes the original only if the
     * source bytes are still identical and protection remains DISARMED.
     */
    fun migrateAllToEncryptedVault(context: Context): MigrationResult =
        synchronized(this) {
            synchronized(MIGRATION_LOCK) { migrateAllToEncryptedVaultLocked(context) }
        }

    private fun migrateAllToEncryptedVaultLocked(context: Context): MigrationResult {
        val app = context.applicationContext
        if (ProtectionStore(app).read().mode != ProtectionMode.DISARMED) {
            return MigrationResult(true, blockedByProtection = true)
        }
        if (!CameraEvidenceCapture.isEncryptionEnabled(app)) {
            return MigrationResult(true, blockedByEncryptionSetting = true)
        }
        enforceRetention(app)
        val query = queryOwnedImages(app)
        if (!query.success) return MigrationResult(false)
        if (!CameraEvidenceVault.verifyIntegrity(app)) {
            return MigrationResult(true, copyFailures = query.images.size, vaultIntegrityVerified = false)
        }
        if (query.images.isEmpty()) {
            return MigrationResult(
                true, vaultIntegrityVerified = true,
                unverifiedLegacyPhotos = query.unverifiedLegacyCount
            )
        }

        var availableCapacity = (CameraEvidencePolicy.MAX_IMAGES - CameraEvidenceVault.entries(app).size)
            .coerceAtLeast(0)
        // Inspect every app-owned Gallery photo so a retry can recognize an
        // already-copied item even when the encrypted vault is currently full.
        val selected = query.images.sortedByDescending { it.second }
        var copied = 0
        var removed = 0
        var copyFailures = 0
        var removalFailures = 0
        var capacityLimitedDuringMigration = 0
        var blocked = false
        val pendingDeletions = mutableListOf<Pair<Uri, ByteArray>>() // URI plus SHA-256 digest, not the full photo bytes

        // Copy and verify every selected item first. Do not remove any Gallery
        // source until the complete encrypted vault passes its integrity scan.
        for ((uri, timestamp) in selected) {
            // Stop creating new encrypted copies as soon as protection is armed.
            // Already verified copies remain safe; their Gallery originals stay
            // untouched because source deletion is separately DISARMED-only.
            if (ProtectionStore(app).read().mode != ProtectionMode.DISARMED) {
                blocked = true
                break
            }
            val original = readBoundedImage(app, uri)
            if (original == null || !CameraEvidencePolicy.isValidImageSize(original.size)) {
                copyFailures += 1
                continue
            }
            val sourceDigest = sha256(original)
            val sourceDigestHex = sourceDigest.joinToString("") { "%02x".format(it) }
            // A previous run may have completed the encrypted copy but been
            // interrupted before deleting its Gallery source. Reuse that exact
            // timestamp+content entry instead of consuming another vault slot.
            val existingCopy = CameraEvidenceVault.entries(app).firstOrNull {
                it.timestampEpochMs == timestamp &&
                    it.sizeBytes == original.size &&
                    it.sha256.equals(sourceDigestHex, ignoreCase = true)
            }
            if (existingCopy == null && availableCapacity <= 0) {
                capacityLimitedDuringMigration += 1
                continue
            }
            val entry = existingCopy ?: CameraEvidenceVault.saveForMigration(app, original, timestamp)
            if (entry == null) {
                if (ProtectionStore(app).read().mode != ProtectionMode.DISARMED) {
                    blocked = true
                    break
                }
                if (CameraEvidenceVault.entries(app).size >= CameraEvidencePolicy.MAX_IMAGES) {
                    availableCapacity = 0
                    capacityLimitedDuringMigration += 1
                } else {
                    copyFailures += 1
                }
                continue
            }
            if (existingCopy == null) availableCapacity = (availableCapacity - 1).coerceAtLeast(0)
            val verified = CameraEvidenceVault.read(app, entry.id)
            if (verified == null || !MessageDigest.isEqual(original, verified)) {
                if (existingCopy == null) runCatching { CameraEvidenceVault.delete(app, entry.id) }
                copyFailures += 1
                continue
            }
            copied += 1
            pendingDeletions += uri to sourceDigest
        }

        val copiedVaultHealthy = CameraEvidenceVault.verifyIntegrity(app)
        if (!copiedVaultHealthy) {
            return MigrationResult(
                enumerationSucceeded = true,
                copiedToVault = copied,
                copyFailures = copyFailures,
                capacityLimited = capacityLimitedDuringMigration,
                vaultIntegrityVerified = false,
                blockedByProtection = blocked,
                unverifiedLegacyPhotos = query.unverifiedLegacyCount
            )
        }

        // Remove a source only after a fresh byte-for-byte source check and a
        // state-locked DISARMED check. If protection activates, stop immediately.
        for ((uri, originalDigest) in pendingDeletions) {
            val currentSource = readBoundedImage(app, uri)
            if (currentSource == null || !MessageDigest.isEqual(originalDigest, sha256(currentSource))) {
                removalFailures += 1
                continue
            }
            val deleteResult = ProtectionStore(app).runIfDisarmed {
                runCatching { app.contentResolver.delete(uri, null, null) == 1 }.getOrDefault(false)
            }
            when (deleteResult) {
                true -> removed += 1
                false -> removalFailures += 1
                null -> {
                    blocked = true
                    break
                }
            }
        }

        val vaultHealthy = CameraEvidenceVault.verifyIntegrity(app)
        return MigrationResult(
            enumerationSucceeded = true,
            copiedToVault = copied,
            originalsRemoved = removed,
            copyFailures = copyFailures,
            removalFailures = removalFailures,
            capacityLimited = capacityLimitedDuringMigration,
            vaultIntegrityVerified = vaultHealthy,
            blockedByProtection = blocked,
            unverifiedLegacyPhotos = query.unverifiedLegacyCount
        )
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun readBoundedImage(context: Context, uri: Uri): ByteArray? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= CameraEvidencePolicy.MAX_IMAGE_BYTES) {
                    "Gallery image exceeds the encrypted evidence size limit"
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }.getOrNull()


    @Synchronized
    fun count(context: Context): Int {
        enforceRetention(context)
        val query = queryOwnedImages(context.applicationContext)
        return if (query.success) query.images.size + query.unverifiedLegacyCount else -1
    }

    @Synchronized
    fun deleteAll(context: Context): Boolean {
        val resolver = context.applicationContext.contentResolver
        val query = queryOwnedImages(context.applicationContext)
        if (!query.success) return false
        var allDeleted = query.unverifiedLegacyCount == 0
        val removedLegacyIds = mutableListOf<String>()
        query.images.forEach { (uri, _) ->
            val deleted = ProtectionStore(context.applicationContext).runIfDisarmed {
                runCatching { resolver.delete(uri, null, null) == 1 }.getOrDefault(false)
            }
            if (deleted == true && Build.VERSION.SDK_INT < 29) {
                uri.lastPathSegment?.let(removedLegacyIds::add)
            } else if (deleted != true) {
                allDeleted = false
            }
        }
        if (removedLegacyIds.isNotEmpty() && !removeLegacyImages(context.applicationContext, removedLegacyIds)) {
            LocalTamperHistory.record(
                context.applicationContext, "legacy_gallery_index_cleanup_failed",
                "Gallery originals were deleted, but their encrypted ownership index could not be fully cleaned; missing rows remain harmless stale references"
            )
        }
        return allDeleted
    }

    @Synchronized
    private fun prune(
        context: Context,
        now: Long,
        maxImages: Int = CameraEvidencePolicy.MAX_IMAGES
    ) {
        val resolver = context.contentResolver
        val query = queryOwnedImages(context)
        if (!query.success) return
        val allowedManagedImages = (maxImages - query.unverifiedLegacyCount).coerceAtLeast(0)
        val removedLegacyIds = mutableListOf<String>()
        query.images.sortedByDescending { it.second }.forEachIndexed { index, (uri, timestamp) ->
            if (!CameraEvidencePolicy.isRetained(timestamp, now) || index >= allowedManagedImages) {
                val deleted = ProtectionStore(context).runIfDisarmed {
                    runCatching { resolver.delete(uri, null, null) == 1 }.getOrDefault(false)
                }
                if (deleted == true && Build.VERSION.SDK_INT < 29) {
                    uri.lastPathSegment?.let(removedLegacyIds::add)
                }
            }
        }
        if (removedLegacyIds.isNotEmpty()) removeLegacyImages(context, removedLegacyIds)
    }

    private fun imageCollectionUri(): Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private data class QueryResult(
        val success: Boolean,
        val images: List<Pair<Uri, Long>>,
        val unverifiedLegacyCount: Int = 0
    )

    private fun readLegacyIndex(context: Context): Set<LegacyOwnershipEntry>? {
        val prefs = context.getSharedPreferences(LEGACY_INDEX_PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LEGACY_OWNED_IMAGES)) return emptySet()
        val encoded = runCatching { prefs.getString(KEY_LEGACY_OWNED_IMAGES, null) }.getOrNull() ?: return null
        return runCatching {
            val array = org.json.JSONArray(
                LocalPreferenceCipher.decrypt(LEGACY_INDEX_PURPOSE, encoded)
            )
            require(array.length() <= CameraEvidencePolicy.MAX_IMAGES)
            buildSet {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.getString("id")
                    val name = item.getString("name")
                    require(id.matches(Regex("^[0-9]+$")) && OWNED_IMAGE_NAME.matches(name))
                    add(LegacyOwnershipEntry(id, name))
                }
            }
        }.getOrNull()
    }

    private fun writeLegacyIndex(
        context: Context,
        entries: Set<LegacyOwnershipEntry>
    ): Boolean = runCatching {
        val prefs = context.getSharedPreferences(LEGACY_INDEX_PREFS, Context.MODE_PRIVATE)
        val editorFactory = {
            if (entries.isEmpty()) {
                prefs.edit().remove(KEY_LEGACY_OWNED_IMAGES)
            } else {
                val array = org.json.JSONArray()
                entries.forEach { entry ->
                    array.put(
                        org.json.JSONObject()
                            .put("id", entry.id)
                            .put("name", entry.name)
                    )
                }
                val encrypted = LocalPreferenceCipher.encrypt(LEGACY_INDEX_PURPOSE, array.toString())
                prefs.edit().putString(KEY_LEGACY_OWNED_IMAGES, encrypted)
            }
        }
        editorFactory().commit() || editorFactory().commit()
    }.getOrDefault(false)

    private fun rememberLegacyImage(context: Context, uri: Uri, name: String): Boolean {
        val id = uri.lastPathSegment?.takeIf { it.matches(Regex("^[0-9]+$")) } ?: return false
        val current = readLegacyIndex(context) ?: return false
        val entry = LegacyOwnershipEntry(id, name)
        val existing = current.firstOrNull { it.id == id }
        if (existing != null && existing != entry) return false
        if (existing == null && current.size >= CameraEvidencePolicy.MAX_IMAGES) return false
        return writeLegacyIndex(context, current + entry)
    }

    private fun removeLegacyImages(context: Context, ids: Collection<String>): Boolean {
        if (ids.isEmpty()) return true
        val current = readLegacyIndex(context) ?: return false
        return writeLegacyIndex(context, current.filterNot { it.id in ids }.toSet())
    }

    private fun queryOwnedImages(context: Context): QueryResult {
        val collection = imageCollectionUri()
        val legacyIndex = if (Build.VERSION.SDK_INT < 29) {
            readLegacyIndex(context) ?: return QueryResult(false, emptyList())
        } else emptySet()
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        }.toTypedArray()
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) {
            selection = "(${MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${MediaStore.Images.Media.RELATIVE_PATH} = ?) AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            args = arrayOf(
                "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME",
                "${Environment.DIRECTORY_PICTURES}/$DIRECTORY_NAME/",
                "ThiefAlertSeQure-%"
            )
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DIRECTORY_NAME)
            selection = "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            args = arrayOf(directory.absolutePath + "/%", "ThiefAlertSeQure-%")
        }
        return try {
            val cursor = context.contentResolver.query(
                collection, projection, selection, args, "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            ) ?: return QueryResult(false, emptyList())
            val images = mutableListOf<Pair<Uri, Long>>()
            var unverifiedLegacyCount = 0
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val takenCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val addedCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val ownerCol = if (Build.VERSION.SDK_INT >= 29) {
                    it.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
                } else -1
                while (it.moveToNext()) {
                    val id = it.getLong(idCol).toString()
                    val displayName = it.getString(nameCol).orEmpty()
                    val generatedName = OWNED_IMAGE_NAME.matches(displayName)
                    val ownerMatches = if (Build.VERSION.SDK_INT >= 29) {
                        it.getString(ownerCol) == context.packageName
                    } else {
                        legacyIndex.any { entry -> entry.id == id && entry.name == displayName }
                    }
                    if (Build.VERSION.SDK_INT < 29 && generatedName && !ownerMatches) {
                        unverifiedLegacyCount += 1
                    }
                    if (generatedName && ownerMatches) {
                        val taken = it.getLong(takenCol)
                        val added = it.getLong(addedCol)
                        val timestamp = if (taken > 0L) taken else added * 1_000L
                        val uri = collection.buildUpon().appendPath(id).build()
                        images += uri to timestamp
                    }
                }
            }
            QueryResult(true, images, unverifiedLegacyCount)
        } catch (error: Exception) {
            android.util.Log.e("CameraEvidenceMediaStore", "Could not enumerate app-owned Gallery evidence", error)
            QueryResult(false, emptyList())
        }
    }
}
