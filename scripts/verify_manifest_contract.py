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

# These are optional, owner-requested evidence permissions. They are not
# requested at first launch and are not required for the core alarm.
for permission in (
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.CAMERA",
):
    if permission not in permissions:
        errors.append("Optional owner-consented evidence permission declaration missing: " + permission)

features = {
    item.get(ANDROID + "name"): item
    for item in manifest.findall("uses-feature")
}
for feature in (
    "android.hardware.bluetooth",
    "android.hardware.bluetooth_le",
    "android.hardware.sensor.accelerometer",
    "android.hardware.camera",
    "android.hardware.camera.front",
    "android.hardware.camera.flash",
    "android.hardware.fingerprint",
):
    declaration = features.get(feature)
    if declaration is None or declaration.get(ANDROID + "required") != "false":
        errors.append(feature + " must remain optional because its feature is not required for core protection")

for permission in ("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"):
    element = next((item for item in manifest.findall("uses-permission") if item.get(ANDROID + "name") == permission), None)
    if element is not None and element.get(ANDROID + "maxSdkVersion") != "28":
        errors.append(permission + " must be capped at API 28; newer Android uses app-owned MediaStore access")

for permission in (
    # RANGING is only required to create sessions; this app performs local capability
    # inspection only and deliberately does not request that dangerous permission.
    "android.permission.RANGING",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.BLUETOOTH_ADVERTISE",
    "android.permission.RECORD_AUDIO",
    "android.permission.FOREGROUND_SERVICE_CAMERA",
    "android.permission.READ_CONTACTS",
    "android.permission.SEND_SMS",
    "android.permission.READ_SMS",
    "android.permission.READ_MEDIA_IMAGES",
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
    if application.get(ANDROID + "name") != ".ThiefAlertSeQureApplication":
        errors.append("Application must run startup recovery for interrupted configuration restores")
    if application.get(ANDROID + "allowBackup") != "false":
        errors.append("Application backup must remain disabled")
    if application.get(ANDROID + "fullBackupContent") != "@xml/backup_rules":
        errors.append("Legacy Android backup must use explicit exclusion rules")
    if application.get(ANDROID + "dataExtractionRules") != "@xml/data_extraction_rules":
        errors.append("Android 12+ cloud and device-transfer backup must use explicit exclusion rules")
    backup_rules = ROOT / "app/src/main/res/xml/backup_rules.xml"
    extraction_rules = ROOT / "app/src/main/res/xml/data_extraction_rules.xml"
    if not backup_rules.is_file() or not extraction_rules.is_file():
        errors.append("Explicit no-backup rule resources are missing")
    if backup_rules.is_file() and extraction_rules.is_file():
        legacy_xml = ET.parse(backup_rules).getroot()
        extraction_xml = ET.parse(extraction_rules).getroot()
        if legacy_xml.tag != "full-backup-content" or not legacy_xml.findall("exclude"):
            errors.append("Legacy backup rules must exclude all app-owned data")
        if extraction_xml.tag != "data-extraction-rules":
            errors.append("Android 12+ extraction rules have an invalid root")
        for mode in ("cloud-backup", "device-transfer"):
            section = extraction_xml.find(mode)
            if section is None or not section.findall("exclude"):
                errors.append(f"{mode} must explicitly exclude app-owned data")
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

for name in ("SettingsActivity", "BluetoothCenterActivity", "DeviceIntelligenceActivity", "ThemeActivity", "SetupStatusActivity", "AboutActivity"):
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

# Camera evidence is one still photo from a visible Activity after explicit
# opt-in and successful owner authentication; no camera foreground service is used.
front_camera = features.get("android.hardware.camera.front")
if front_camera is None or front_camera.get(ANDROID + "required") != "false":
    errors.append("Front camera must remain optional for devices without front-facing hardware")
for (tag, name), item in components.items():
    if tag == "service" and "camera" in (item.get(ANDROID + "foregroundServiceType") or "").lower():
        errors.append("Camera foreground service is forbidden; photo capture must remain visible-Activity-only")

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
    "explicit owner-consented camera permission, API-28-capped legacy Gallery permissions and visible-Activity capture boundary, component export boundaries, "
    "foreground-service declarations, package identity, SDK levels, and 10s/8s protection timing invariants."
)
