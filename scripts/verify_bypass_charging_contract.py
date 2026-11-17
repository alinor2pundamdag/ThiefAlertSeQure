#!/usr/bin/env python3
"""Guard the Bypass Charging integration against unsafe or misleading controls."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
controller=(ROOT/"app/src/main/java/com/thiefalertsequre/app/BypassChargingController.kt").read_text()
settings=(ROOT/"app/src/main/java/com/thiefalertsequre/app/SettingsActivity.kt").read_text()
manifest=(ROOT/"app/src/main/AndroidManifest.xml").read_text()
tests=ROOT/"app/src/test/java/com/thiefalertsequre/app/BypassChargingControllerTest.kt"

for token in ("interface BypassChargingAdapter","adapter.readEnabled()",
              "BypassChargingStatus.STATE_UNVERIFIED","BypassChargingStatus.ACTIVATION_FAILED",
              "return emptyList()"):
    assert token in controller,f"Missing fail-closed contract: {token}"
for forbidden in ("ProcessBuilder(","Runtime.getRuntime().exec","WRITE_SECURE_SETTINGS",
                  "DEVICE_POWER","setprop ","sysfs","service call"):
    assert forbidden not in controller,f"Unsafe undocumented control path: {forbidden}"
assert "isEnabled = snapshot.canToggle" in settings
assert settings.count("addBypassChargingSection()") >= 2, "Bypass Charging section must be connected to Settings rendering."
assert "Settings.ACTION_SETTINGS" in settings
assert "BypassChargingGuidance.instructions" in settings
assert tests.is_file()
for permission in ("android.permission.WRITE_SECURE_SETTINGS",
                   "android.permission.DEVICE_POWER","android.permission.BATTERY_STATS"):
    assert permission not in manifest,f"Unexpected privileged permission: {permission}"
print("Bypass Charging privilege boundary and truthful-state checks passed.")
