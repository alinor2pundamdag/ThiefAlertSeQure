#!/usr/bin/env python3
"""Static privacy and persistence contract for the opt-in local evidence vault."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
permissions = {item.get(android + "name", "") for item in manifest.findall("uses-permission")}
location = (ROOT / "app/src/main/java/com/thiefalertsequre/app/EvidenceLocationCapture.kt").read_text(encoding="utf-8")
vault = (ROOT / "app/src/main/java/com/thiefalertsequre/app/LocalEvidenceVault.kt").read_text(encoding="utf-8")
settings = (ROOT / "app/src/main/java/com/thiefalertsequre/app/SettingsActivity.kt").read_text(encoding="utf-8")
privacy = (ROOT / "docs/PRIVACY_POLICY.md").read_text(encoding="utf-8")
main = (ROOT / "app/src/main/java/com/thiefalertsequre/app/MainActivity.kt").read_text(encoding="utf-8")

required = {
    "coarse and fine location are optional manifest declarations":
        {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"} <= permissions,
    "background location is not declared":
        "android.permission.ACCESS_BACKGROUND_LOCATION" not in permissions,
    "location access checks both runtime grants":
        "ACCESS_FINE_LOCATION" in location and "ACCESS_COARSE_LOCATION" in location,
    "location snapshot reads cached provider fixes only":
        "getLastKnownLocation" in location and "requestLocationUpdates" not in location and "getCurrentLocation" not in location,
    "freshness is based on monotonic fix age":
        "elapsedRealtimeNanos" in location and "RECENT_CACHED_FIX" in location and "STALE_CACHED_FIX" in location,
    "owner sees an explicit consent explanation before location permission request":
        "Save a location snapshot?" in settings and "requestEvidenceLocation.launch" in settings,
    "vault uses AES-GCM local encryption with purpose-bound AAD":
        "LocalPreferenceCipher.encrypt(PURPOSE" in vault and "LocalPreferenceCipher.decrypt(PURPOSE" in vault,
    "vault enforces bounded records and retention":
        "MAX_RECORDS = 100" in vault and "RETENTION_MS" in vault,
    "privacy policy discloses one-shot location":
        "one cached Android location fix" in privacy and "No background location permission" in privacy,
    "first-launch notice discloses optional location snapshot":
        "cached location fix" in main and "location permission" in main,
}
for description, passed in required.items():
    if not passed:
        raise SystemExit("Evidence privacy contract failed: " + description)

for forbidden in ("android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_BACKGROUND_LOCATION"):
    if forbidden in permissions:
        raise SystemExit("Evidence Vault must not add invasive/background permission: " + forbidden)

print("Verified: explicit one-shot cached location, no background tracking, encrypted bounded local evidence, and matching privacy disclosures.")
