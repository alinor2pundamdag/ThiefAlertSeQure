package com.thiefalertsequre.app

import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Shared, offline viewer for the exact versioned legal documents bundled with the app. */
internal object LegalDocumentViewer {
    fun show(
        activity: FragmentActivity,
        assetPath: String,
        title: String,
        acknowledgeLabel: String? = null,
        onAcknowledge: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val document = runCatching {
            activity.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse {
            android.widget.Toast.makeText(
                activity,
                "The selected legal document is unavailable in this installation.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val body = TextView(activity).apply {
            text = document
            textSize = 13f
            setTextColor(ContextCompat.getColor(activity, R.color.tas_text))
            setTextIsSelectable(true)
            setPadding(dp(16), dp(12), dp(16), dp(16))
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(body, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(scroll)
        if (acknowledgeLabel != null && onAcknowledge != null) {
            builder.setNegativeButton("Close", null)
                .setPositiveButton(acknowledgeLabel) { _, _ ->
                    if (!activity.isFinishing && !activity.isDestroyed) onAcknowledge()
                }
        } else {
            builder.setPositiveButton("Close", null)
        }
        builder.showThemed(activity)
    }
}
