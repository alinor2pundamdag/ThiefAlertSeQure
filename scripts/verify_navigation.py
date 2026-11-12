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
for required in ("Not needed — optional A2 is OFF", "Not needed — optional dashboard is OFF", "optional owner-initiated API 36 capability query"):
    if required not in device_activity:
        errors.append("DeviceIntelligenceActivity missing optional-feature context: " + required)

bluetooth_activity = (APP / "BluetoothCenterActivity.kt").read_text(encoding="utf-8")
if "Monitor runtime:" not in bluetooth_activity or "Monitoring runs only while A2 is ON" not in bluetooth_activity:
    errors.append("Bluetooth center must distinguish enabled preference from confirmed runtime")
if "AndroidDeviceCompatibility.appInfoIntent(packageName)" not in device_activity:
    errors.append("DeviceIntelligenceActivity must provide a fallback for app Settings navigation")

for screen in (
    "MainActivity.kt", "SettingsActivity.kt", "BluetoothCenterActivity.kt",
    "DeviceIntelligenceActivity.kt", "ThemeActivity.kt", "SetupStatusActivity.kt"
):
    source = (APP / screen).read_text(encoding="utf-8")
    if "applySystemBarInsets" not in source:
        errors.append(screen + " must apply system-bar/cutout insets")
    if "override fun onResume" not in source or "ThemeRuntime.apply" not in source:
        errors.append(screen + " must reapply the persisted/preview theme when returning to the screen")

settings_activity = (APP / "SettingsActivity.kt").read_text(encoding="utf-8")
if "startActivity(installIntent)" in settings_activity:
    errors.append("Package installer launch must use guarded Android Settings/activity fallback")
if "startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in settings_activity:
    errors.append("Battery optimization request must use guarded Android Settings/activity fallback")
if "AndroidDeviceCompatibility.open(" not in settings_activity:
    errors.append("Settings external destinations must use the centralized guarded launcher")
main_activity = (APP / "MainActivity.kt").read_text(encoding="utf-8")
if "device_credential_launch_failed" not in main_activity:
    errors.append("Device credential launch failures must be handled without terminating the active alarm")
if "Android access not granted" not in settings_activity or "Open access settings" not in settings_activity:
    errors.append("Optional full-screen dashboard toggle must disclose missing Android access and provide a settings route")

main_activity = (APP / "MainActivity.kt").read_text(encoding="utf-8")
if "minHeight = dp(46)" in main_activity:
    errors.append("MainActivity contains a primary touch target below 48dp")

for screen in ("BluetoothCenterActivity.kt", "DeviceIntelligenceActivity.kt"):
    source = (APP / screen).read_text(encoding="utf-8")
    if "minHeight = dp(48)" not in source:
        errors.append(screen + " button helper must enforce a 48dp minimum touch target")

setup_activity = (APP / "SetupStatusActivity.kt").read_text(encoding="utf-8")
for action_label in (
    "Open exact-alarm access", "Open full-screen access",
    "Review battery restrictions", "Open notification settings",
    "Open Android screen-lock settings"
):
    if action_label not in setup_activity:
        errors.append("Setup status action label missing or ambiguous: " + action_label)
for required in (
    "AndroidDeviceCompatibility.open", "AndroidDeviceCompatibility.applySystemBarInsets",
    "openStatusAction", "Toast.LENGTH_LONG"
):
    if required not in setup_activity:
        errors.append("SetupStatusActivity missing safe action/inset contract: " + required)

full_screen_policy = (APP / "FullScreenSetupStatusPolicy.kt").read_text(encoding="utf-8")
if "!ownerEnabledDashboard" not in full_screen_policy or "SetupState.NEUTRAL" not in full_screen_policy:
    errors.append("Full-screen intent access must remain optional until the owner enables the dashboard")

setup_snapshot = (APP / "SetupStatusSnapshot.kt").read_text(encoding="utf-8")
if "FullScreenSetupStatusPolicy.evaluate" not in setup_snapshot:
    errors.append("Setup status must use the owner-aware full-screen access policy")

setup_activity = (APP / "SetupStatusActivity.kt").read_text(encoding="utf-8")
if "item.state == SetupState.ACTION_REQUIRED || item.state == SetupState.UNKNOWN" not in setup_activity:
    errors.append("Setup status must not show action buttons for neutral/unsupported/verified items")

bluetooth_policy = (APP / "BluetoothSetupStatusPolicy.kt").read_text(encoding="utf-8")
if "!a2Enabled" not in bluetooth_policy:
    errors.append("Bluetooth permission and adapter must be optional while A2 is disabled")

setup_snapshot = (APP / "SetupStatusSnapshot.kt").read_text(encoding="utf-8")
if "BluetoothSetupStatusPolicy.permission" not in setup_snapshot or "BluetoothSetupStatusPolicy.adapter" not in setup_snapshot:
    errors.append("Setup status must use owner-aware Bluetooth readiness policy")

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
