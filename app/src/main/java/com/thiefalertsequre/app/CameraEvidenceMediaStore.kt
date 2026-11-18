package com.thiefalertsequre.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.UUID

/** Publishes owner-consented photos into the app's dedicated Gallery folder. */
internal object CameraEvidenceMediaStore {
    private const val DIRECTORY_NAME = "ThiefAlertSeQure"

    @Synchronized
    fun save(context: Context, jpeg: ByteArray): Uri? {
        if (!CameraEvidencePolicy.isValidImageSize(jpeg.size)) return null
        val resolver = context.applicationContext.contentResolver
        val collection = imageCollectionUri()
        val now = System.currentTimeMillis()
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
        runCatching { prune(context.applicationContext, now) }
        return uri
    }

    @Synchronized
    fun enforceRetention(context: Context) {
        prune(context.applicationContext, System.currentTimeMillis())
    }

    @Synchronized
    fun count(context: Context): Int {
        enforceRetention(context)
        return queryOwnedImages(context.applicationContext).size
    }

    @Synchronized
    fun deleteAll(context: Context): Boolean {
        val resolver = context.applicationContext.contentResolver
        var allDeleted = true
        queryOwnedImages(context.applicationContext).forEach { (uri, _) ->
            val deleted = runCatching { resolver.delete(uri, null, null) == 1 }.getOrDefault(false)
            if (!deleted) allDeleted = false
        }
        return allDeleted
    }

    @Synchronized
    private fun prune(context: Context, now: Long) {
        val resolver = context.contentResolver
        queryOwnedImages(context).sortedByDescending { it.second }.forEachIndexed { index, (uri, timestamp) ->
            if (!CameraEvidencePolicy.isRetained(timestamp, now) || index >= CameraEvidencePolicy.MAX_IMAGES) {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }

    private fun imageCollectionUri(): Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun queryOwnedImages(context: Context): List<Pair<Uri, Long>> {
        val collection = imageCollectionUri()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DISPLAY_NAME)
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
        val images = mutableListOf<Pair<Uri, Long>>()
        context.contentResolver.query(collection, projection, selection, args, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val taken = cursor.getLong(takenCol)
                val added = cursor.getLong(addedCol)
                val timestamp = if (taken > 0L) taken else added * 1_000L
                val uri = collection.buildUpon().appendPath(id.toString()).build()
                images += uri to timestamp
            }
        }
        return images
    }
}
