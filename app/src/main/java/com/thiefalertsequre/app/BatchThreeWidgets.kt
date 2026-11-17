package com.thiefalertsequre.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Seven read-only widget choices share one authoritative state projection. */
abstract class BaseProtectionWidgetProvider : AppWidgetProvider() {
    protected abstract val widgetTitle: String
    protected abstract val widgetLayout: Int

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        ids.forEach { id -> manager.updateAppWidget(id, render(context, widgetTitle)) }
    }

    private fun render(context: Context, title: String): RemoteViews {
        val store = ProtectionStore(context)
        val integrity = store.integritySnapshot()
        val state = store.read()
        val monitorEnabled = SafePreferences.boolean(
            context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE),
            ProtectionMonitorService.KEY_ENABLED,
            ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val monitor = monitorEnabled && ProtectionMonitorService.isRunning
        val now = System.currentTimeMillis()
        val modeLabel = when (state.mode) {
            ProtectionMode.DISARMED -> "NOT ARMED"
            ProtectionMode.ARMED -> if (monitor) "PROTECTED" else "ARMED • MONITOR UNKNOWN"
            ProtectionMode.COUNTDOWN -> "CHARGER COUNTDOWN"
            ProtectionMode.ALARM_TRIGGERED -> "ALARM ACTIVE"
        }
        val detail = when (state.mode) {
            ProtectionMode.DISARMED -> "Protection is disarmed"
            ProtectionMode.ARMED -> if (monitor) "Local protection monitor is confirmed running" else "Protection is armed; monitor is not confirmed"
            ProtectionMode.COUNTDOWN -> {
                val remainingMs = ProtectionEngine.remainingMs(
                    state, now, ProtectionClock.elapsedRealtime(), ProtectionClock.bootCount(context)
                )
                val seconds = ((remainingMs + 999L) / 1_000L).coerceAtLeast(0L)
                "Removal grace period • ${seconds}s remaining"
            }
            ProtectionMode.ALARM_TRIGGERED -> "Owner authentication required"
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val committed = prefs.edit().putLong(KEY_LAST_UPDATE, now).commit()
        val stale = !integrity.protectionStateHealthy || !committed
        val views = RemoteViews(context.packageName, widgetLayout)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_status, modeLabel)
        views.setTextViewText(R.id.widget_detail, detail)
        views.setTextViewText(R.id.widget_freshness,
            if (stale) "Status needs review • Open app" else "Live local status")
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, title.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            views.setContentDescription(R.id.widget_root, "$title. $modeLabel. $detail. Tap to open ThiefAlertSeQure.")
        }
        return views
    }

    companion object {
        private const val PREFS = "batch_three_widget_state_v1"
        private const val KEY_LAST_UPDATE = "last_update_epoch_ms"

        fun refreshAll(context: Context) {
            val providers = listOf(
                ProtectionWidgetCompact::class.java, ProtectionWidgetStatus::class.java,
                ProtectionWidgetShield::class.java, ProtectionWidgetWide::class.java,
                ProtectionWidgetCountdown::class.java, ProtectionWidgetAlarm::class.java,
                ProtectionWidgetMonitor::class.java
            )
            val manager = AppWidgetManager.getInstance(context)
            providers.forEach { provider ->
                val component = ComponentName(context, provider)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isNotEmpty()) {
                    context.sendBroadcast(Intent(context, provider)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
                }
            }
        }
    }
}

class ProtectionWidgetCompact : BaseProtectionWidgetProvider() {
    override val widgetTitle = "Protection"
    override val widgetLayout = R.layout.widget_protection_compact
}
class ProtectionWidgetStatus : BaseProtectionWidgetProvider() {
    override val widgetTitle = "Protection Status"
    override val widgetLayout = R.layout.widget_protection_status
}
class ProtectionWidgetShield : BaseProtectionWidgetProvider() {
    override val widgetTitle = "Shield Status"
    override val widgetLayout = R.layout.widget_protection_compact
}
class ProtectionWidgetWide : BaseProtectionWidgetProvider() {
    override val widgetTitle = "Protection Overview"
    override val widgetLayout = R.layout.widget_protection_large
}
class ProtectionWidgetCountdown : BaseProtectionWidgetProvider() {
    override val widgetTitle = "Charger Countdown"
    override val widgetLayout = R.layout.widget_protection_status
}
class ProtectionWidgetAlarm : BaseProtectionWidgetProvider() {
    override val widgetTitle = "Alarm Status"
    override val widgetLayout = R.layout.widget_protection_status
}
class ProtectionWidgetMonitor : BaseProtectionWidgetProvider() {
    override val widgetTitle = "Monitor Status"
    override val widgetLayout = R.layout.widget_protection_compact
}

object BatchThreeUiBridge {
    fun refresh(context: Context) {
        runCatching { BaseProtectionWidgetProvider.refreshAll(context.applicationContext) }
        runCatching {
            android.service.quicksettings.TileService.requestListeningState(
                context.applicationContext,
                ComponentName(context, ProtectionQuickSettingsTile::class.java)
            )
        }
    }
}
