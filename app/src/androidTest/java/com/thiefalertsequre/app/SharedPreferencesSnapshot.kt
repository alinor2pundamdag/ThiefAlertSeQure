package com.thiefalertsequre.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Restores the exact typed SharedPreferences values after an instrumentation test.
 * Tests must not erase the app's pre-test state as a side effect of verification.
 */
internal class SharedPreferencesSnapshot(
    context: Context,
    name: String
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val originalValues: Map<String, *> = preferences.all.mapValues { (_, value) ->
        if (value is Set<*>) value.toSet() else value
    }

    fun restore() {
        val editor = preferences.edit().clear()
        originalValues.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> error("Unsupported SharedPreferences value type for $key")
            }
        }
        check(editor.commit()) { "Could not restore original SharedPreferences values" }
    }
}
