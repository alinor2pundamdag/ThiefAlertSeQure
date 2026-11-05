package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Stores user-granted content URIs. Access is limited to the exact document
 * or tree selected by the owner through Android's Storage Access Framework.
 */
object AlarmSoundStore {
    const val ALARM = "alarm"
    const val ALERT = "alert"
    private const val PREFS = "user_alarm_sounds_v1"

    fun uri(context: Context, slot: String): Uri? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(slot), null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun hasAlarmSound(context: Context): Boolean = uri(context, ALARM) != null

    fun save(context: Context, slot: String, soundUri: Uri, permissionUri: Uri): Boolean {
        require(slot == ALARM || slot == ALERT)
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                permissionUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key(slot), soundUri.toString())
                .putString("grant_uri_$slot", permissionUri.toString())
                .commit()
        }.getOrDefault(false)
    }

    fun isReadable(context: Context, slot: String): Boolean {
        val selected = uri(context, slot) ?: return false
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(selected, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    fun displayName(context: Context, slot: String): String {
        val selected = uri(context, slot) ?: return "Not selected"
        val name = runCatching {
            context.contentResolver.query(selected, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return name ?: selected.lastPathSegment ?: "Selected audio"
    }

    private fun key(slot: String) = "uri_$slot"
}
