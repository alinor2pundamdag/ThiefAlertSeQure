#!/usr/bin/env python3
"""Static privacy boundary for the optional Android 16+ local ranging capability query."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/com/thiefalertsequre/app/DeviceIntelligenceActivity.kt").read_text(encoding="utf-8")
query = (ROOT / "app/src/main/java/com/thiefalertsequre/app/RangingCapabilityQuery.kt").read_text(encoding="utf-8")
privacy = (ROOT / "docs/PRIVACY_POLICY.md").read_text(encoding="utf-8")
kotlin_sources = list((ROOT / "app/src/main").rglob("*.kt"))
all_kotlin = "\n".join(path.read_text(encoding="utf-8") for path in kotlin_sources)

required = {
    "owner explicitly starts the capability check from Device Intelligence": 'button("Check Android ranging capabilities (optional)") { requestRangingCapabilityCheck() }' in activity,
    "owner explicitly starts the capability check from Bluetooth Center": 'button("Check actual ranging hardware capabilities") { checkRangingPermission() }' in (ROOT / "app/src/main/java/com/thiefalertsequre/app/BluetoothCenterActivity.kt").read_text(encoding="utf-8"),
    "both screens use the shared local-only capability query": "RangingCapabilityQuery(this)" in activity and "RangingCapabilityQuery(this)" in (ROOT / "app/src/main/java/com/thiefalertsequre/app/BluetoothCenterActivity.kt").read_text(encoding="utf-8"),
    "capability query registers and unregisters only the availability callback": "registerCapabilitiesCallback" in query and "unregisterCapabilitiesCallback" in query,
    "privacy policy states no RANGING permission is requested for local capability inspection": "No Android RANGING permission is requested" in privacy,
}
for description, passed in required.items():
    if not passed:
        raise SystemExit(f"Ranging privacy contract failed: {description}")

if "android.permission.RANGING" in manifest or "Manifest.permission.RANGING" in all_kotlin or '"android.permission.RANGING"' in all_kotlin:
    raise SystemExit("Local capability inspection must not declare or request the dangerous RANGING permission.")
for forbidden in ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION"):
    if forbidden in manifest:
        raise SystemExit(f"Ranging capability inspection must not add location permission: {forbidden}")
for forbidden in ("Manifest.permission.ACCESS_FINE_LOCATION", "Manifest.permission.ACCESS_COARSE_LOCATION", "Manifest.permission.ACCESS_BACKGROUND_LOCATION"):
    if forbidden in all_kotlin:
        raise SystemExit(f"Ranging capability inspection must not request location permission: {forbidden}")
if "createRangingSession(" in all_kotlin:
    raise SystemExit("Ranging capability inspection must not start a peer ranging session.")

print("Verified: both screens use the shared owner-initiated local availability callback; no RANGING permission, no location permission, and no ranging session.")
