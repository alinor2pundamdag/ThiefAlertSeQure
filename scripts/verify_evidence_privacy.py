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
camera_vault = (ROOT / "app/src/main/java/com/thiefalertsequre/app/CameraEvidenceVault.kt").read_text(encoding="utf-8")
camera_capture = (ROOT / "app/src/main/java/com/thiefalertsequre/app/CameraEvidenceCapture.kt").read_text(encoding="utf-8")
camera_policy = (ROOT / "app/src/main/java/com/thiefalertsequre/app/CameraEvidencePolicy.kt").read_text(encoding="utf-8")
settings = (ROOT / "app/src/main/java/com/thiefalertsequre/app/SettingsActivity.kt").read_text(encoding="utf-8")
privacy = (ROOT / "docs/PRIVACY_POLICY.md").read_text(encoding="utf-8")
main = (ROOT / "app/src/main/java/com/thiefalertsequre/app/MainActivity.kt").read_text(encoding="utf-8")
history = (ROOT / "app/src/main/java/com/thiefalertsequre/app/LocalTamperHistory.kt").read_text(encoding="utf-8")
history_policy = (ROOT / "app/src/main/java/com/thiefalertsequre/app/LocalTamperHistoryPolicy.kt").read_text(encoding="utf-8")
history_activity = (ROOT / "app/src/main/java/com/thiefalertsequre/app/LocalTamperHistoryActivity.kt").read_text(encoding="utf-8")
policy_acknowledgements = (ROOT / "app/src/main/java/com/thiefalertsequre/app/PolicyAcknowledgementPolicy.kt").read_text(encoding="utf-8")
about = (ROOT / "app/src/main/java/com/thiefalertsequre/app/AboutActivity.kt").read_text(encoding="utf-8")
preference_cipher = (ROOT / "app/src/main/java/com/thiefalertsequre/app/LocalPreferenceCipher.kt").read_text(encoding="utf-8")

required = {
    "coarse and fine location are optional manifest declarations":
        {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"} <= permissions,
    "background location is not declared":
        "android.permission.ACCESS_BACKGROUND_LOCATION" not in permissions,
    "location access checks both runtime grants":
        "ACCESS_FINE_LOCATION" in location and "ACCESS_COARSE_LOCATION" in location,
    "location supports cached and bounded one-shot current fixes without continuous updates":
        "getLastKnownLocation" in location and "getCurrentLocation" in location and "requestSingleUpdate" in location and "requestLocationUpdates" not in location and "CURRENT_LOCATION_TIMEOUT_MS = 20_000L" in location,
    "freshness is based on monotonic fix age":
        "elapsedRealtimeNanos" in location and "RECENT_CACHED_FIX" in location and "STALE_CACHED_FIX" in location,
    "owner sees an explicit consent explanation before location permission request":
        "Save a location snapshot?" in settings and "requestEvidenceLocation.launch" in settings and "Request a current location?" in settings and "requestCurrentEvidenceLocation.launch" in settings,
    "vault uses AES-GCM local encryption with purpose-bound AAD":
        "LocalPreferenceCipher.encrypt(PURPOSE" in vault and "LocalPreferenceCipher.decrypt(PURPOSE" in vault,
    "vault enforces bounded records and retention":
        "MAX_RECORDS = 100" in vault and "RETENTION_MS" in vault,
    "privacy policy discloses one-shot location":
        "one cached location fix or request one current GPS/network fix" in privacy and "No background location permission" in privacy,
    "first-launch notice discloses optional location snapshot and encrypted backups":
        "current GPS/network location request" in main and "password-protected file" in main
        and "PolicyAcknowledgementPolicy.PRIVACY_PREFS" in main,
    "first launch requires current versioned Privacy Notice and Terms acknowledgements":
        "PolicyAcknowledgementPolicy.PRIVACY_VERSION" in main
        and "PolicyAcknowledgementPolicy.TERMS_VERSION" in main
        and "Terms of use and platform limitations" in main and "decision_at" in main
        and ".commit()" in main,
    "policy acknowledgement policy rejects stale or missing versions":
        "PRIVACY_VERSION = 5" in policy_acknowledgements
        and "TERMS_VERSION = 1" in policy_acknowledgements
        and "storedVersion == requiredVersion" in policy_acknowledgements,
    "Terms dialog stores its own versioned acknowledgement durably":
        'setPositiveButton("I acknowledge")' in about
        and "PolicyAcknowledgementPolicy.TERMS_PREFS" in about
        and "PolicyAcknowledgementPolicy.TERMS_VERSION" in about
        and "decision_at" in about and ".commit()" in about,
    "evidence backup uses password-derived authenticated encryption":
        "PBKDF2WithHmacSHA256" in (ROOT / "app/src/main/java/com/thiefalertsequre/app/EvidenceVaultBackup.kt").read_text(encoding="utf-8")
        and "AES/GCM/NoPadding" in (ROOT / "app/src/main/java/com/thiefalertsequre/app/EvidenceVaultBackup.kt").read_text(encoding="utf-8")
        and "Export encrypted evidence backup" in settings and "Restore encrypted evidence backup" in settings,
    "camera permission is optional and declared only for the front-camera evidence feature":
        "android.permission.CAMERA" in permissions
        and 'android:name="android.hardware.camera.front" android:required="false"' in (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        and "android.permission.FOREGROUND_SERVICE_CAMERA" not in permissions
        and 'android:foregroundServiceType="camera"' not in (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8"),
    "camera permission is requested only after explicit owner opt-in":
        "Enable front-camera photo evidence?" in settings
        and "requestCameraEvidencePermission.launch(Manifest.permission.CAMERA)" in settings
        and "CAMERA permission is requested only after you choose Enable" in settings,
    "photo capture is gated by successful authentication and a visible Activity":
        "CameraEvidenceCapture.captureAfterSuccessfulAuthentication(this)" in main
        and "OwnerAuthenticationCoordinator.complete(this, proof)" in main
        and main.index("OwnerAuthenticationCoordinator.complete(this, proof)") < main.index("CameraEvidenceCapture.captureAfterSuccessfulAuthentication(this)")
        and "Lifecycle.State.STARTED" in camera_capture
        and "authenticationSucceeded && activityVisible" in camera_policy,
    "camera photos use Android Keystore AES-GCM and authenticated metadata":
        "AndroidKeyStore" in camera_vault and "AES/GCM/NoPadding" in camera_vault
        and "updateAAD(id.toByteArray" in camera_vault and "sha256" in camera_vault,
    "camera evidence has bounded retention, count, and image size":
        "MAX_IMAGES = 20" in camera_policy and "MAX_IMAGE_BYTES = 2 * 1024 * 1024" in camera_policy
        and "RETENTION_MS = 30L" in camera_policy,
    "camera evidence can be viewed, exported and deleted by the owner":
        "showCameraEvidenceArchive" in settings and "exportCameraEvidence.launch" in settings
        and "CameraEvidenceVault.delete(this, entry.id)" in settings and "CameraEvidenceVault.deleteAll(this)" in settings,
    "camera privacy notice and policy explain the opt-in capture boundary":
        "front-camera photo evidence" in main and "CAMERA permission is requested only after that choice" in main
        and "explicitly enables front-camera photo evidence" in privacy
        and "camera_permission" in (ROOT / "app/src/main/java/com/thiefalertsequre/app/PermissionChangeMonitor.kt").read_text(encoding="utf-8"),
    "local event history is encrypted with purpose-bound Android Keystore AES-GCM":
        "LocalPreferenceCipher.encrypt(PURPOSE" in history
        and "LocalPreferenceCipher.decrypt(PURPOSE" in history
        and "local_tamper_history_v2" in history,
    "history viewer requires biometric or device credential authentication":
        "BiometricPrompt" in history_activity
        and "createConfirmDeviceCredentialIntent" in history_activity
        and "FLAG_SECURE" in history_activity,
    "history viewer supports text search and event-category filters":
        "addTextChangedListener" in history_activity
        and "matchesCategory" in history_activity
        and all(category in history_activity for category in ('"power"', '"bluetooth"', '"alarm"', '"authentication"', '"evidence"', '"system"')),
    "local event history redacts exception messages and common sensitive values":
        all(token in history_policy for token in ("exceptionMessage", "credentialAssignment", "bearerCredential", "knownToken", "jwt", "uri", "filesystemPath", "email")),
    "legacy local event details are sanitized on read and append":
        history.count("sanitizeStoredHistory") >= 2,
    "clearing event history does not silently delete the separate evidence archive":
        "LocalEvidenceVault.deleteAll" not in history[history.index("fun clear"):history.index("private fun readStored")],
    "encrypted preference decryption never creates a replacement key":
        "Cipher.DECRYPT_MODE, getExistingKey()" in preference_cipher
        and "keyStore.getKey(KEY_ALIAS, null) as? SecretKey" in preference_cipher
        and "requireExistingKey" in preference_cipher
        and "Cipher.DECRYPT_MODE, getOrCreateKey()" not in preference_cipher,
}
for description, passed in required.items():
    if not passed:
        raise SystemExit("Evidence privacy contract failed: " + description)

for forbidden in ("android.permission.RECORD_AUDIO", "android.permission.ACCESS_BACKGROUND_LOCATION"):
    if forbidden in permissions:
        raise SystemExit("Evidence Vault must not add invasive/background permission: " + forbidden)

print("Verified: owner-requested location, explicit visible front-camera consent/capture, encrypted bounded local evidence, no microphone/background location, password-protected metadata backup, and matching privacy disclosures.")
