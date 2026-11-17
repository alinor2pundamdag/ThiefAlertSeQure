package com.thiefalertsequre.app

import android.widget.TextView
import androidx.core.view.ViewCompat

/**
 * Marks the primary title of a screen as a semantic heading for TalkBack
 * and other Android accessibility services without changing its appearance.
 */
fun TextView.markAsPageHeading(): TextView = apply {
    ViewCompat.setAccessibilityHeading(this, true)
}
