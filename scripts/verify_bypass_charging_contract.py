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
              "firmwareFingerprint: String","firmwareFingerprints: Set<String>",
              "firmwareFingerprints.any { it == deviceFingerprint }",
              "firmwareFingerprints.none {", "it != it.trim()",
              "it.equals(\"unknown\", ignoreCase = true)",
              "deviceFingerprint == deviceFingerprint.trim()",
              "!deviceFingerprint.contains(\"*\")", "isKnownIdentity(device.model)",
              
              "duplicated certification is ambiguous","registeredId","actualId == registeredId",
              "return emptyList()"):
    assert token in controller,f"Missing fail-closed contract: {token}"
for forbidden in ("ProcessBuilder(","Runtime.getRuntime().exec","WRITE_SECURE_SETTINGS",
                  "DEVICE_POWER","setprop ","sysfs","service call"):
    assert forbidden not in controller,f"Unsafe undocumented control path: {forbidden}"
assert "isEnabled = snapshot.canToggle" in settings
assert "android.R.color.darker_gray" in settings, "Unsupported adapter state must use the visibly disabled dark panel"
assert "UNAVAILABLE — this device has no certified" in settings
assert "UNKNOWN does not mean OFF" in settings
assert "Supported OEM control detected" in settings
assert "Build.FINGERPRINT" in settings
assert "Intent(Settings.ACTION_SETTINGS)" in settings
assert "ACTION_BATTERY_SAVER_SETTINGS" not in settings
assert "BypassChargingGuidance.instructions" in settings
assert tests.is_file()
test_source=tests.read_text(encoding="utf-8")
for test_name in ("adapterCertificationRequiresExactModelApiAndFirmwareFingerprint",
                  "duplicateAdapterCertificationFailsClosed",
                  "factoryCannotBorrowAnotherCertificationAdapterId",
                  "adapterIdGetterFailureIsContainedAndFailsClosed"):
    assert test_name in test_source, f"Missing bypass registry regression test: {test_name}"
for test_name in ("certificationRejectsUnknownCaseVariantsAndWildcardIdentities",):
    assert test_name in tests.read_text(encoding="utf-8"), f"Missing strict identity regression: {test_name}"
for permission in ("android.permission.WRITE_SECURE_SETTINGS",
                   "android.permission.DEVICE_POWER","android.permission.BATTERY_STATS"):
    assert permission not in manifest,f"Unexpected privileged permission: {permission}"
print("Bypass Charging privilege boundary and truthful-state checks passed.")
