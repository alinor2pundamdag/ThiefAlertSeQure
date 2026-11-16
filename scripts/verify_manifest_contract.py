#!/usr/bin/env python3
"""Verify Android manifest, identity, permission and protection-timing invariants."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE = ROOT / "app/build.gradle.kts"
APP = ROOT / "app/src/main/java/com/thiefalertsequre/app"
errors = []
manifest = ET.parse(MANIFEST).getroot()
application = manifest.find("application")
permissions = {
    item.get(ANDROID + "name", "")
    for item in manifest.findall("uses-permission")
}

required_permissions = {
    "android.permission.INTERNET",
    "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_ADMIN",
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.RANGING",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.WAKE_LOCK",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    "android.permission.VIBRATE",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.USE_FULL_SCREEN_INTENT",
}
for permission in sorted(required_permissions - permissions):
    errors.append("Required permission missing: " + permission)

features = {
    item.get(ANDROID + "name"): item
    for item in manifest.findall("uses-feature")
}
for feature in ("android.hardware.bluetooth", "android.hardware.bluetooth_le"):
    declaration = features.get(feature)
    if declaration is None or declaration.get(ANDROID + "required") != "false":
        errors.append(feature + " must remain optional so Bluetooth-less devices can install")

for permission in (
    "android.permission.CAMERA",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.RECORD_AUDIO",
    "android.permission.READ_CONTACTS",
    "android.permission.SEND_SMS",
    "android.permission.READ_SMS",
):
    if permission in permissions:
        errors.append("Unapproved sensitive permission declared: " + permission)

legacy_bluetooth = {
    item.get(ANDROID + "name"): item
    for item in manifest.findall("uses-permission")
    if item.get(ANDROID + "name") in {
        "android.permission.BLUETOOTH",
        "android.permission.BLUETOOTH_ADMIN",
    }
}
for name, element in legacy_bluetooth.items():
    if element.get(ANDROID + "maxSdkVersion") != "30":
        errors.append(name + " must remain capped at API 30")

if application is None:
    errors.append("Manifest application element is missing")
else:
    if application.get(ANDROID + "allowBackup") != "false":
        errors.append("Application backup must remain disabled")
    if application.get(ANDROID + "usesCleartextTraffic") != "false":
        errors.append("Cleartext traffic must remain disabled")

components = {}
if application is not None:
    for tag in ("activity", "service", "receiver", "provider"):
        for item in application.findall(tag):
            name = item.get(ANDROID + "name", "").removeprefix(".")
            components[(tag, name)] = item

def component(tag, name):
    item = components.get((tag, name))
    if item is None:
        errors.append(f"Missing manifest {tag}: {name}")
    return item

main = component("activity", "MainActivity")
if main is not None and main.get(ANDROID + "exported") != "true":
    errors.append("MainActivity must remain exported for the launcher")

for name in ("SettingsActivity", "BluetoothCenterActivity", "DeviceIntelligenceActivity", "ThemeActivity", "SetupStatusActivity"):
    item = component("activity", name)
    if item is not None and item.get(ANDROID + "exported") != "false":
        errors.append(name + " must remain non-exported")

admin = component("receiver", "ThiefAlertAdminReceiver")
if admin is not None:
    if admin.get(ANDROID + "exported") != "true":
        errors.append("Device Admin receiver must remain exported for Android policy binding")
    if admin.get(ANDROID + "permission") != "android.permission.BIND_DEVICE_ADMIN":
        errors.append("Device Admin receiver must require BIND_DEVICE_ADMIN")

for name in ("BootReceiver", "A2BluetoothAclReceiver", "AlarmDeadlineReceiver"):
    item = component("receiver", name)
    if item is not None and item.get(ANDROID + "exported") != "false":
        errors.append(name + " must remain non-exported")

alarm = component("service", "AlarmService")
if alarm is not None:
    if alarm.get(ANDROID + "exported") != "false":
        errors.append("AlarmService must remain non-exported")
    if alarm.get(ANDROID + "foregroundServiceType") != "mediaPlayback":
        errors.append("AlarmService must retain its mediaPlayback foreground-service type")

monitor = component("service", "ProtectionMonitorService")
if monitor is not None:
    if monitor.get(ANDROID + "exported") != "false":
        errors.append("ProtectionMonitorService must remain non-exported")
    if monitor.get(ANDROID + "foregroundServiceType") != "specialUse":
        errors.append("ProtectionMonitorService must retain its specialUse foreground-service type")
    subtype = monitor.find("property")
    if subtype is None or subtype.get(ANDROID + "name") != "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE":
        errors.append("ProtectionMonitorService must document its special-use FGS subtype")

provider = component("provider", "androidx.core.content.FileProvider")
if provider is not None:
    if provider.get(ANDROID + "exported") != "false" or provider.get(ANDROID + "grantUriPermissions") != "true":
        errors.append("Update FileProvider must remain non-exported and grant URI permissions")

gradle = GRADLE.read_text(encoding="utf-8")
for expression, expected, label in (
    (r"applicationId\s*=\s*\"([^\"]+)\"", "com.thiefalertsequre.app", "applicationId"),
    (r"minSdk\s*=\s*(\d+)", "26", "minSdk"),
    (r"compileSdk\s*=\s*(\d+)", "36", "compileSdk"),
    (r"targetSdk\s*=\s*(\d+)", "36", "targetSdk"),
):
    match = re.search(expression, gradle)
    if not match or match.group(1) != expected:
        errors.append(f"{label} must remain {expected}")

engine = (APP / "ProtectionEngine.kt").read_text(encoding="utf-8")
if not re.search(r"const val COUNTDOWN_MS\s*=\s*10_000L", engine):
    errors.append("Charger-removal countdown must remain exactly 10 seconds")

service = (APP / "ProtectionMonitorService.kt").read_text(encoding="utf-8")
if not re.search(r"const val BLUETOOTH_DISCONNECT_GRACE_MS\s*=\s*8_000L", service):
    errors.append("Trusted Bluetooth disconnect grace must remain exactly 8 seconds")

if errors:
    print("\n".join("ERROR: " + item for item in errors))
    sys.exit(1)

print(
    f"Verified {len(required_permissions)} required permissions, sensitive-permission exclusions, "
    "component export boundaries, foreground-service declarations, package identity, SDK levels, "
    "and 10s/8s protection timing invariants."
)
