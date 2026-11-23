package com.thiefalertsequre.app

/** Shared result rule for independent exact and inexact AlarmManager callbacks. */
internal object AlarmCallbackPolicy {
    fun hasUsableDeadlineCallback(exactScheduled: Boolean, fallbackScheduled: Boolean): Boolean =
        exactScheduled || fallbackScheduled
}
