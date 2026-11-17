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
    private const val KEY_BUILT_IN_PRESET = "built_in_alarm_preset"

    fun selectedBuiltInPreset(context: Context): AlarmTonePreset {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = when (val value = SafePreferences.readString(prefs, KEY_BUILT_IN_PRESET, AlarmTonePreset.DEFAULT_AUDIO.name)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> AlarmTonePreset.DEFAULT_AUDIO.name
        }
        return AlarmTonePreset.fromStored(raw)
    }

    fun selectNextBuiltInPreset(context: Context): AlarmTonePreset {
        val current = selectedBuiltInPreset(context)
        val choices = listOf(AlarmTonePreset.DEFAULT_AUDIO) + AlarmTonePreset.builtIns
        val next = choices[(choices.indexOf(current).coerceAtLeast(0) + 1) % choices.size]
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BUILT_IN_PRESET, next.name).commit()
        return if (saved) next else current
    }

    fun uri(context: Context, slot: String): Uri? {
        require(slot == ALARM || slot == ALERT)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = when (val stored = SafePreferences.readString(prefs, key(slot), null)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return null
        }
        return raw?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    fun hasAlarmSound(context: Context): Boolean = effectiveUri(context, ALARM) != null

    /** A custom owner-selected sound overrides the packaged default alarm. */
    fun effectiveUri(context: Context, slot: String): Uri? =
        uri(context, slot) ?: if (slot == ALARM) {
            Uri.parse("android.resource://${context.packageName}/${R.raw.default_alarm}")
        } else null

    /** Remove a selected sound only when both URI metadata reads and commit succeed. */
    fun clear(context: Context, slot: String): Boolean {
        require(slot == ALARM || slot == ALERT)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val granted = when (val stored = SafePreferences.readString(prefs, "grant_uri_$slot", null)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return false
        }
        val otherSlot = if (slot == ALARM) ALERT else ALARM
        val otherGrant = when (val stored = SafePreferences.readString(prefs, "grant_uri_$otherSlot", null)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return false
        }
        val saved = prefs.edit().remove(key(slot)).remove("grant_uri_$slot").commit()
        if (!saved) return false
        if (granted != null && granted != otherGrant) runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(granted), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        return true
    }

    fun save(context: Context, slot: String, soundUri: Uri, permissionUri: Uri): Boolean {
        require(slot == ALARM || slot == ALERT)
        var newlyGranted = false
        return runCatching {
            val alreadyGranted = context.contentResolver.persistedUriPermissions.any {
                it.uri == permissionUri && it.isReadPermission
            }
            context.contentResolver.takePersistableUriPermission(
                permissionUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            newlyGranted = !alreadyGranted
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val oldGrant = when (val stored = SafePreferences.readString(prefs, "grant_uri_$slot", null)) {
                is PreferenceRead.Value -> stored.value
                is PreferenceRead.Corrupt -> return@runCatching false
            }
            val otherSlot = if (slot == ALARM) ALERT else ALARM
            val otherGrant = when (val stored = SafePreferences.readString(prefs, "grant_uri_$otherSlot", null)) {
                is PreferenceRead.Value -> stored.value
                is PreferenceRead.Corrupt -> return@runCatching false
            }
            val newGrant = permissionUri.toString()
            val saved = prefs.edit()
                .putString(key(slot), soundUri.toString())
                .putString("grant_uri_$slot", newGrant)
                .commit()
            if (!saved) return@runCatching false
            newlyGranted = false
            if (oldGrant != null && oldGrant != newGrant && oldGrant != otherGrant) runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(oldGrant), Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            true
        }.getOrElse {
            false
        }.also { saved ->
            if (!saved && newlyGranted) runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    permissionUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    fun isReadable(context: Context, slot: String): Boolean {
        val selected = uri(context, slot) ?: return false
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(selected, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    fun displayName(context: Context, slot: String): String {
        val selected = uri(context, slot)
            ?: return if (slot == ALARM) "Built-in default alarm" else "Not selected"
        val name = runCatching {
            context.contentResolver.query(selected, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return name ?: selected.lastPathSegment ?: "Selected audio"
    }

    private fun key(slot: String) = "uri_$slot"
}
