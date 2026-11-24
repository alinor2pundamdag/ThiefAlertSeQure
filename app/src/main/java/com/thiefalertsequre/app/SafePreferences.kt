package com.thiefalertsequre.app

import android.content.SharedPreferences
import android.util.Log

/**
 * Typed SharedPreferences reads that distinguish valid values from corrupt
 * values. A mismatch is logged without deleting or rewriting the source key.
 */
sealed class PreferenceRead<out T> {
    data class Value<T>(val value: T) : PreferenceRead<T>()
    data class Corrupt(val key: String) : PreferenceRead<Nothing>()
}

object SafePreferences {
    private const val TAG = "ThiefAlertPreferences"

    fun readString(prefs: SharedPreferences, key: String, defaultValue: String? = null): PreferenceRead<String?> =
        read(prefs, key) { prefs.getString(key, defaultValue) }

    fun readBoolean(prefs: SharedPreferences, key: String, defaultValue: Boolean): PreferenceRead<Boolean> =
        read(prefs, key) { prefs.getBoolean(key, defaultValue) }

    fun readInt(prefs: SharedPreferences, key: String, defaultValue: Int): PreferenceRead<Int> =
        read(prefs, key) { prefs.getInt(key, defaultValue) }

    fun readLong(prefs: SharedPreferences, key: String, defaultValue: Long): PreferenceRead<Long> =
        read(prefs, key) { prefs.getLong(key, defaultValue) }

    fun string(prefs: SharedPreferences, key: String, defaultValue: String? = null): String? =
        valueOrDefault(readString(prefs, key, defaultValue), defaultValue)

    fun boolean(prefs: SharedPreferences, key: String, defaultValue: Boolean): Boolean =
        valueOrDefault(readBoolean(prefs, key, defaultValue), defaultValue)

    fun int(prefs: SharedPreferences, key: String, defaultValue: Int): Int =
        valueOrDefault(readInt(prefs, key, defaultValue), defaultValue)

    fun long(prefs: SharedPreferences, key: String, defaultValue: Long): Long =
        valueOrDefault(readLong(prefs, key, defaultValue), defaultValue)

    private inline fun <T> read(
        prefs: SharedPreferences,
        key: String,
        getter: () -> T
    ): PreferenceRead<T> = try {
        PreferenceRead.Value(getter())
    } catch (error: ClassCastException) {
        // Keep the malformed entry intact for diagnosis and explicit recovery.
        Log.e(TAG, "SharedPreferences type mismatch for key '$key'; value retained", error)
        PreferenceRead.Corrupt(key)
    }

    private fun <T> valueOrDefault(result: PreferenceRead<T>, defaultValue: T): T =
        when (result) {
            is PreferenceRead.Value -> result.value
            is PreferenceRead.Corrupt -> defaultValue
        }
}
