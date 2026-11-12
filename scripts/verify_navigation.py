#!/usr/bin/env python3
"""Static interaction contracts for internal navigation, dialogs, theme preview and setup actions."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java"
APP = JAVA / "com" / "thiefalertsequre" / "app"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"

manifest = ET.parse(MANIFEST).getroot()
declared = {
    element.get(ANDROID + "name", "").rsplit(".", 1)[-1]
    for element in manifest.findall("application/activity")
}
sources = list(JAVA.rglob("*.kt"))
defined = set()
targets = set()
errors = []

class_pattern = re.compile(r"\bclass\s+([A-Z][A-Za-z0-9_]*Activity)\b")
target_pattern = re.compile(r"\b([A-Z][A-Za-z0-9_]*Activity)::class\.java")
builder_pattern = re.compile(r"MaterialAlertDialogBuilder\s*\(")
themed_pattern = re.compile(r"\.showThemed\s*\(")
empty_click_pattern = re.compile(r"setOnClickListener\s*\{\s*(?:/\*.*?\*/\s*)?\}", re.S)
empty_click_value_pattern = re.compile(r"setOnClickListener\s*\{\s*Unit\s*\}")

for path in sources:
    source = path.read_text(encoding="utf-8")
    defined.update(class_pattern.findall(source))
    targets.update(target_pattern.findall(source))
    builders = len(builder_pattern.findall(source))
    themed = len(themed_pattern.findall(source))
    if "fun MaterialAlertDialogBuilder.showThemed" in source:
        themed -= 1
    if builders != themed:
        errors.append(
            f"{path.relative_to(ROOT)}: {builders} MaterialAlertDialogBuilder calls, "
            f"but {themed} themed show calls"
        )
    if empty_click_pattern.search(source) or empty_click_value_pattern.search(source):
        errors.append(f"{path.relative_to(ROOT)}: empty/no-op click listener detected")

missing_declarations = sorted(targets - declared)
missing_sources = sorted(declared - defined)
if missing_declarations:
    errors.append("Internal navigation targets missing from manifest: " + ", ".join(missing_declarations))
if missing_sources:
    errors.append("Manifest Activities missing Kotlin classes: " + ", ".join(missing_sources))

theme_activity = (APP / "ThemeActivity.kt").read_text(encoding="utf-8")
for required in (
    "LIVE APP PREVIEW", "SETTINGS", "PROTECTION READINESS",
    "SAMPLE CONFIRMATION DIALOG", "ThemeStore.save",
    "AndroidDeviceCompatibility.applySystemBarInsets"
):
    if required not in theme_activity:
        errors.append("ThemeActivity missing required preview/inset contract: " + required)

bluetooth_activity = (APP / "BluetoothCenterActivity.kt").read_text(encoding="utf-8")
if "AndroidDeviceCompatibility.open(this, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)" not in bluetooth_activity:
    errors.append("BluetoothCenterActivity must provide a fallback for Bluetooth Settings navigation")

device_activity = (APP / "DeviceIntelligenceActivity.kt").read_text(encoding="utf-8")
if "AndroidDeviceCompatibility.appInfoIntent(packageName)" not in device_activity:
    errors.append("DeviceIntelligenceActivity must provide a fallback for app Settings navigation")

setup_activity = (APP / "SetupStatusActivity.kt").read_text(encoding="utf-8")
for required in (
    "AndroidDeviceCompatibility.open", "AndroidDeviceCompatibility.applySystemBarInsets",
    "openStatusAction", "Toast.LENGTH_LONG"
):
    if required not in setup_activity:
        errors.append("SetupStatusActivity missing safe action/inset contract: " + required)

a2_policy = (APP / "A2SetupStatusPolicy.kt").read_text(encoding="utf-8")
if "connectedBaselineCount < trustedDeviceCount" not in a2_policy:
    errors.append("A2 setup readiness must require a fresh baseline for every registered device")

registry = (APP / "TrustedBluetoothRegistry.kt").read_text(encoding="utf-8")
service = (APP / "ProtectionMonitorService.kt").read_text(encoding="utf-8")
if "fun deviceRevision(" not in registry or "TrustedBluetoothRegistry.deviceRevision(prefs, address)" not in service:
    errors.append("A2 disconnect timers must use per-device registry revisions")
if "TrustedBluetoothRegistry.revision(prefs)" not in service:
    errors.append("A2 disconnect timers must retain global monitor-reset invalidation")

if errors:
    print("\n".join("ERROR: " + item for item in errors))
    sys.exit(1)

print(
    f"Verified {len(declared)} declared Activities, {len(targets)} internal navigation targets, "
    "themed Material dialogs, no obvious empty click listeners, theme-preview/inset contracts, "
    "safe setup-action fallbacks, and A2 readiness/timer invalidation contracts."
)
