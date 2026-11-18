#!/usr/bin/env python3
"""Static privacy and persistence contract for opt-in evidence and Gallery storage."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/thiefalertsequre/app"
android = "{http://schemas.android.com/apk/res/android}"
manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
manifest_root = ET.parse(manifest_path).getroot()
permissions = {item.get(android + "name", ""): item for item in manifest_root.findall("uses-permission")}
location = (APP / "EvidenceLocationCapture.kt").read_text(encoding="utf-8")
vault = (APP / "LocalEvidenceVault.kt").read_text(encoding="utf-8")
camera_vault = (APP / "CameraEvidenceVault.kt").read_text(encoding="utf-8")
camera_capture = (APP / "CameraEvidenceCapture.kt").read_text(encoding="utf-8")
camera_policy = (APP / "CameraEvidencePolicy.kt").read_text(encoding="utf-8")
camera_media = (APP / "CameraEvidenceMediaStore.kt").read_text(encoding="utf-8")
settings = (APP / "SettingsActivity.kt").read_text(encoding="utf-8")
camera_settings = settings[settings.index("CAMERA EVIDENCE STORAGE"):]
privacy = (ROOT / "docs/PRIVACY_POLICY.md").read_text(encoding="utf-8")
main = (APP / "MainActivity.kt").read_text(encoding="utf-8")
intro = (APP / "SeismicGenesisIntroActivity.kt").read_text(encoding="utf-8")
intro_policy = (APP / "AppLaunchIntroPolicy.kt").read_text(encoding="utf-8")
configuration = (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")

required = {
    "coarse and fine location are optional manifest declarations":
        {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"} <= permissions.keys(),
    "background location is not declared":
        "android.permission.ACCESS_BACKGROUND_LOCATION" not in permissions,
    "location access checks both runtime grants":
        "ACCESS_FINE_LOCATION" in location and "ACCESS_COARSE_LOCATION" in location,
    "location supports cached and bounded one-shot current fixes without continuous updates":
        "getLastKnownLocation" in location and "getCurrentLocation" in location and "requestSingleUpdate" in location and "requestLocationUpdates" not in location and "CURRENT_LOCATION_TIMEOUT_MS = 20_000L" in location,
    "freshness is based on monotonic fix age":
        "elapsedRealtimeNanos" in location and "RECENT_CACHED_FIX" in location and "STALE_CACHED_FIX" in location,
    "owner sees explicit consent before location permission request":
        "Save a location snapshot?" in settings and "requestEvidenceLocation.launch" in settings and "Request a current location?" in settings and "requestCurrentEvidenceLocation.launch" in settings,
    "event vault uses AES-GCM local encryption with purpose-bound AAD":
        "LocalPreferenceCipher.encrypt(PURPOSE" in vault and "LocalPreferenceCipher.decrypt(PURPOSE" in vault,
    "event vault enforces bounded records and retention":
        "MAX_RECORDS = 100" in vault and "RETENTION_MS" in vault,
    "privacy policy discloses one-shot location":
        "one cached location fix or request one current GPS/network fix" in privacy and "No background location permission" in privacy,
    "updated first-launch notice discloses Gallery default and encrypted option":
        "privacy_notice_v6" in main and "Pictures/ThiefAlertSeQure" in main and "Gallery" in main and "AES-GCM" in main,
    "evidence metadata backup uses password-derived authenticated encryption":
        "PBKDF2WithHmacSHA256" in (APP / "EvidenceVaultBackup.kt").read_text(encoding="utf-8")
        and "AES/GCM/NoPadding" in (APP / "EvidenceVaultBackup.kt").read_text(encoding="utf-8")
        and "Export encrypted evidence backup" in settings and "Restore encrypted evidence backup" in settings,
    "camera permission remains optional and no camera foreground service is declared":
        "android.permission.CAMERA" in permissions
        and 'android:name="android.hardware.camera.front" android:required="false"' in manifest_path.read_text(encoding="utf-8")
        and "android.permission.FOREGROUND_SERVICE_CAMERA" not in permissions
        and 'android:foregroundServiceType="camera"' not in manifest_path.read_text(encoding="utf-8"),
    "camera permission is requested only after explicit owner opt-in":
        "Enable front-camera photo evidence?" in settings
        and "requestCameraEvidencePermissions.launch(permissions.toTypedArray())" in settings
        and "CAMERA permission is requested only after you choose Enable" in settings,
    "photo capture is gated by successful authentication and a visible Activity":
        "CameraEvidenceCapture.captureAfterSuccessfulAuthentication(this)" in main
        and "OwnerAuthenticationCoordinator.complete(this, proof)" in main
        and main.index("OwnerAuthenticationCoordinator.complete(this, proof)") < main.index("CameraEvidenceCapture.captureAfterSuccessfulAuthentication(this)")
        and "Lifecycle.State.STARTED" in camera_capture
        and "authenticationSucceeded && activityVisible" in camera_policy,
    "camera callbacks use lifecycle state snapshots without reading Activity state off-thread":
        camera_capture.count("isActivityVisible(activity)") == 1
        and "if (!activityVisible.get())" in camera_capture
        and "val displayRotationDegrees = when (activity.windowManager.defaultDisplay.rotation)" in camera_capture
        and "val displayRotation = when (activity.windowManager.defaultDisplay.rotation)" not in camera_capture,
    "Gallery is the default and encryption is an explicit future-capture setting":
        "KEY_ENCRYPT_NEW_PHOTOS" in camera_capture
        and "SafePreferences.readBoolean" in camera_capture
        and "is PreferenceRead.Corrupt -> true" in camera_capture
        and "CameraEvidenceMediaStore.save" in camera_capture
        and "CameraEvidenceVault.save" in camera_capture
        and "Encrypt new camera photos:" in settings
        and "GALLERY (UNENCRYPTED)" in settings
        and "NEEDS GALLERY PERMISSION" in settings
        and "Grant missing permissions" in settings,
    "MediaStore writes are atomic and limited to the app-owned Gallery folder":
        "VOLUME_EXTERNAL_PRIMARY" in camera_media
        and "RELATIVE_PATH" in camera_media
        and "IS_PENDING, 1" in camera_media
        and "IS_PENDING, 0" in camera_media
        and "ThiefAlertSeQure-%" in camera_media,
    "legacy Gallery permissions are capped at API 28 and not broad on modern Android":
        permissions.get("android.permission.READ_EXTERNAL_STORAGE", {}).get(android + "maxSdkVersion") == "28"
        and permissions.get("android.permission.WRITE_EXTERNAL_STORAGE", {}).get(android + "maxSdkVersion") == "28"
        and "READ_MEDIA_IMAGES" not in permissions
        and "Build.VERSION.SDK_INT >= 29" in camera_capture,
    "private encrypted photo vault uses Android Keystore AES-GCM and authenticated metadata":
        "AndroidKeyStore" in camera_vault and "AES/GCM/NoPadding" in camera_vault
        and "updateAAD(id.toByteArray" in camera_vault and "sha256" in camera_vault,
    "both photo stores enforce bounded retention and image size":
        "MAX_IMAGES = 20" in camera_policy
        and "MAX_IMAGE_BYTES = 2 * 1024 * 1024" in camera_policy
        and "RETENTION_MS = 30L" in camera_policy
        and "CameraEvidencePolicy.isRetained" in camera_media
        and "CameraEvidencePolicy.MAX_IMAGES" in camera_media,
    "owner can view/export encrypted photos and delete both app-managed stores":
        "showCameraEvidenceArchive" in settings and "exportCameraEvidence.launch" in settings
        and "CameraEvidenceVault.delete(this, entry.id)" in settings
        and "CameraEvidenceVault.deleteAll(applicationContext)" in settings
        and "CameraEvidenceMediaStore.deleteAll(applicationContext)" in settings
        and "var allDeleted = true" in camera_media
        and "queryOwnedImages(context.applicationContext).forEach" in camera_media,
    "encrypted photo deletion retains its index until ciphertext deletion succeeds":
        camera_vault.index("val deleted = file.delete() || !file.exists()")
        < camera_vault.index("if (!writeIndex(app, next)) return false")
        and camera_vault.index("val allDeleted = files.all")
        < camera_vault.index("if (!prefs.edit().remove(KEY_INDEX).commit()) return false"),
    "encrypted photo vault enumeration and integrity checks stay off the Settings UI thread":
        "CameraEvidenceVault.entries(applicationContext)" in camera_settings
        and "CameraEvidenceVault.entries(this)" not in camera_settings
        and camera_settings.index("ioExecutor.execute {")
        < camera_settings.index("CameraEvidenceVault.entries(applicationContext)"),
    "configuration backup schema 2 includes the storage preference and accepts schema 1":
        '"camera_evidence_settings", "encrypt_new_photos"' in configuration
        and 'put("schema", 2)' in configuration
        and "schema == 1 || schema == 2" in configuration,
    "privacy notice explains unencrypted Gallery visibility and optional encryption":
        "unencrypted" in privacy.lower() and "Gallery" in privacy
        and "future captures" in privacy and "visible to Gallery and other apps" in privacy,
    "review privacy notice clears the active versioned acknowledgement":
        "PRIVACY_NOTICE_PREFS" in main
        and "MainActivity.PRIVACY_NOTICE_PREFS" in settings
        and "privacy_notice_v5" not in settings,
    "privacy acknowledgement is deferred during alarm recovery and rechecked afterward":
        "protectionModeAtLaunch != ProtectionMode.ALARM_TRIGGERED" in main
        and "privacyDecision" in main[main.index("private fun completeOwnerAuthentication"):]
        and "showPrivacyScreen()" in main[main.index("private fun completeOwnerAuthentication"):]
        and "privacyScreenVisible" in main
        and "!privacyScreenVisible" in main,
    "camera privacy notice and policy preserve explicit opt-in and no-upload boundary":
        "front-camera photo evidence" in main
        and "CAMERA permission is requested only after you choose Enable" in settings
        and "explicit Settings opt-in" in privacy
        and "camera_permission" in (APP / "PermissionChangeMonitor.kt").read_text(encoding="utf-8"),
    "cinematic intro is shown once per installed version and deferred while protection is active":
        "AppLaunchIntroPolicy.shouldShow" in main
        and "currentVersionCode > lastCompletedVersionCode" in intro_policy
        and "KEY_COMPLETED_VERSION_CODE" in intro
        and "lastCompletedVersionCode >= currentVersionCode" in intro,
}
for description, passed in required.items():
    if not passed:
        raise SystemExit("Evidence privacy contract failed: " + description)

for forbidden in (
    "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.READ_MEDIA_IMAGES",
):
    if forbidden in permissions:
        raise SystemExit("Evidence feature must not add invasive/background permission: " + forbidden)

print("Verified owner-requested location, visible post-auth camera capture, Gallery-default/un-encrypted photo routing, opt-in AES-GCM vault, bounded retention, legacy-only storage permissions, backup compatibility and privacy disclosures.")
