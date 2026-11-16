#!/usr/bin/env python3
"""Static privacy boundary for the optional Android 16+ ranging capability query."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
activity = (ROOT / "app/src/main/java/com/thiefalertsequre/app/DeviceIntelligenceActivity.kt").read_text(encoding="utf-8")
query = (ROOT / "app/src/main/java/com/thiefalertsequre/app/RangingCapabilityQuery.kt").read_text(encoding="utf-8")
privacy = (ROOT / "docs/PRIVACY_POLICY.md").read_text(encoding="utf-8")
kotlin_sources = list((ROOT / "app/src/main").rglob("*.kt"))
all_kotlin = "\n".join(path.read_text(encoding="utf-8") for path in kotlin_sources)

required = {
    "manifest declares the optional RANGING permission": 'android.permission.RANGING' in manifest,
    "RANGING is requested through an explicit permission launcher": "ActivityResultContracts.RequestPermission()" in activity and "rangingPermissionLauncher.launch(Manifest.permission.RANGING)" in activity,
    "permission request is behind the owner confirmation dialog": '.setPositiveButton("Continue")' in activity,
    "capability query uses the availability callback": "registerCapabilitiesCallback" in query and "unregisterCapabilitiesCallback" in query,
    "privacy policy discloses the optional RANGING permission": "Android RANGING permission" in privacy,
}
for description, passed in required.items():
    if not passed:
        raise SystemExit(f"Ranging privacy contract failed: {description}")

for forbidden in ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION"):
    if forbidden in manifest:
        raise SystemExit(f"Ranging capability check must not add location permission: {forbidden}")
if "createRangingSession(" in all_kotlin:
    raise SystemExit("Ranging capability inspection must not start a peer ranging session.")

print("Verified: RANGING is opt-in after owner disclosure; only local capability availability is queried; no location permission or ranging session is used.")
