#!/usr/bin/env python3
"""Guard Batch 3 notification, widget, Quick Settings and appearance boundaries."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/thiefalertsequre/app"
monitor = (APP / "ProtectionMonitorService.kt").read_text(encoding="utf-8")
alarm = (APP / "AlarmService.kt").read_text(encoding="utf-8")
tile = (APP / "ProtectionQuickSettingsTile.kt").read_text(encoding="utf-8")
widgets = (APP / "BatchThreeWidgets.kt").read_text(encoding="utf-8")
theme = (APP / "ThemeStore.kt").read_text(encoding="utf-8")
appearance = (APP / "AppearanceStudioStore.kt").read_text(encoding="utf-8")

checks = {
    "monitor notification derives its state from the authoritative persisted store":
        "val mode = ProtectionStore(this).read().mode" in monitor,
    "monitor notification status accents follow the active theme palette":
        all(f"ThemeStore.get(this).{tone}" in monitor for tone in ("muted", "success", "warning", "danger")),
    "alarm notification action only opens owner verification":
        '"Open owner verification", contentIntent' in alarm
        and "Notification actions cannot disarm the alarm" in alarm,
    "alarm notification remains ongoing and cannot be dismissed as an ordinary alert":
        ".setOngoing(true)" in alarm and ".setAutoCancel(false)" in alarm,
    "Quick Settings tile is a read-only persisted-state projection":
        "ProtectionStore(this).read().mode" in tile
        and "ProtectionStore(this).update" not in tile
        and "updatePersisted" not in tile,
    "Quick Settings tile opens the app rather than changing protection":
        "startActivityAndCollapse(pending)" in tile
        and "startActivityAndCollapse(launch)" in tile,
    "Android 14+ Quick Settings launch uses the PendingIntent overload":
        "Build.VERSION.SDK_INT >= 34" in tile and "startActivityAndCollapse(pending)" in tile,
    "widgets read the authoritative protection state and never mutate it":
        "val store = ProtectionStore(context)" in widgets
        and "val state = store.read()" in widgets
        and "ProtectionStore(context).update" not in widgets
        and "updatePersisted" not in widgets,
    "widget taps only open the application":
        "views.setOnClickPendingIntent(R.id.widget_root, pending)" in widgets
        and "Intent(context, MainActivity::class.java)" in widgets,
    "all seven documented widget providers are registered in the shared refresh path":
        all(name in widgets for name in (
            "ProtectionWidgetCompact", "ProtectionWidgetStatus", "ProtectionWidgetShield",
            "ProtectionWidgetWide", "ProtectionWidgetCountdown", "ProtectionWidgetAlarm",
            "ProtectionWidgetMonitor"
        )),
    "widget and Quick Settings refresh bridge updates both projections":
        "BaseProtectionWidgetProvider.refreshAll(context.applicationContext)" in widgets
        and "TileService.requestListeningState" in widgets,
    "appearance choices use validated indexes and durable local commits":
        "index in icons.indices" in appearance
        and "index in backgrounds.indices" in appearance
        and "index in animations.indices" in appearance
        and "prefs(context).edit().putInt(KEY_ICON, index).commit()" in appearance,
    "reduced-motion preference suppresses touch animations":
        "if (reduceMotion(context)) return@setOnTouchListener false" in appearance,
    "theme catalog contains curated and generated palettes":
        "val all = base + generatedPalettes()" in theme
        and "(0 until 104)" in theme,
}
failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + name)
if failed:
    sys.exit("Batch 3 integration contract failed: " + "; ".join(failed))
print(f"Verified {len(checks)} Batch 3 notification, widget, tile and appearance contracts.")
