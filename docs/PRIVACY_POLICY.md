<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# ThiefAlertSeQure Privacy Policy

**Effective date:** 18 November 2026

## 1. Overview

ThiefAlertSeQure is designed as an on-device charging anti-theft alarm. The application does not require an account or cloud backend. Internet access is used only when the owner manually checks for an official GitHub release and downloads its APK.

## 2. Information stored on the device

The app stores only the operational information needed to provide its functions:

- Protection mode and charger-removal countdown deadlines.
- Biometric failure count and temporary cooldown deadline.
- The user's privacy-notice choice and the time it was recorded.
- The previous alarm-stream volume while an alarm is active, so it can be restored after successful disarm.
- A bounded local protection event history (up to 100 entries), which the owner can review or export as CSV.
- A separate AES-GCM encrypted local evidence metadata vault (up to 100 records, 30-day retention). It stores app-observed event metadata and only cached/current location results explicitly requested by the owner. Owner-initiated metadata backup files are encrypted with a separate password-derived AES-GCM key; the backup password is not stored.
- Optional front-camera photo evidence, only after explicit Settings opt-in and CAMERA permission. New captures are saved as normal, unencrypted JPEG images in the public `Pictures/ThiefAlertSeQure` Gallery folder by default. The Settings option **Encrypt new camera photos** routes future captures to the separate Android Keystore AES-GCM private photo vault instead. The toggle controls future captures. Existing app-owned Gallery images can be moved through a separate explicit Settings migration: each photo is copied into the AES-GCM vault, read back and verified, and the Gallery original is removed only if its bytes are still unchanged and protection remains DISARMED. If verification, source recheck, deletion, or the 20-photo vault capacity prevents a safe move, the Gallery original remains. Existing encrypted-vault photos remain encrypted when future-capture storage changes. Both app-managed stores retain at most 20 photos for 30 days, with a 2 MiB per-photo limit. Encrypted-vault photos use a non-exportable Android Keystore key and SHA-256 integrity records; the key is not included in the separate event-metadata backup. Clearing app data or uninstalling can make encrypted photos unrecoverable. The owner can view/export/delete encrypted photos while protection is DISARMED. Gallery images are visible to Gallery and other apps that have photo access.
- Monitor preferences such as enablement, motion sensitivity and optional battery-alert threshold.
- Trusted Bluetooth accessory names and MAC addresses selected by the owner, plus the per-device connection-baseline and revision data used by A2. The registry payload and baselines are encrypted locally with an Android Keystore-backed AES-GCM key; identifiers are not uploaded.
- The owner's local preference for enabling or disabling the alarm flashlight.
- URI references and Android read-access grants for owner-selected alarm and secondary-alert audio. The app stores references/permissions, not a second copy of the selected audio file.
- The installed package name, app version, and signing-certificate fingerprint are read locally for About/Info transparency and official-update verification. The fingerprint is calculated on-device and is not uploaded.

This information is stored in the app's private local preferences. The app does not upload it.

## 3. Credentials and biometrics

The app does not request, read, store, or transmit the device PIN, pattern, password, fingerprint data, or face templates. Android Keyguard and Android's biometric framework perform verification and retain control of the underlying credential and biometric data.

## 4. Permissions and system functions

Depending on Android version and the user's choices, the app may use:

- Boot-completed and charger-connected/disconnected broadcasts to recover protection state and detect charging changes.
- Alarm scheduling; precise alarm access is a special Android setting on supported versions.
- Notifications and foreground services while protection monitoring or an alarm is active.
- Bluetooth Connect permission on Android 12+ when the owner selects already-paired trusted accessories for A2. The app does not scan or advertise. This workflow does not use location permission.
- Optional coarse and fine location permissions only after an explicit owner action. The owner may save one cached location fix or request one current GPS/network fix; the current request is bounded to 20 seconds and records the returned fix age, reported accuracy, provider, and source classification. No background location permission, continuous tracking, geofencing, or automatic location capture is used. If Android returns no fix, nothing is saved. The owner may export or restore the metadata evidence vault using a password-protected AES-GCM backup file; the backup password is not retained by the app.
- Optional CAMERA permission only after the owner explicitly enables front-camera photo evidence in Settings. Permission is not requested on first launch or for the flashlight. A single still photo is attempted only after successful owner PIN/password or supported strong-biometric verification disarms a latched alarm, and only while the app's authentication Activity is visible. New photos go to Gallery unencrypted by default; enabling **Encrypt new camera photos** stores future captures in the private AES-GCM vault. Existing app-owned Gallery photos can be migrated explicitly after encrypted read-back and source verification. On Android 8–9 only, Gallery mode also requires legacy READ/WRITE external-storage permissions to publish and manage the app's own Gallery folder. Android 10+ uses MediaStore scoped storage without requesting broad shared-storage permissions. Android's camera privacy indicator remains under system control. No background camera service, continuous camera preview, video, or microphone recording is used. If required permission, front-camera hardware, or visible Activity access is unavailable, the photo is skipped and alarm/authentication behavior is unaffected.
- No Android RANGING permission is requested for the optional Android 16+ capability check. The app registers only the local `RangingManager` availability callback; it does not discover peers, create a ranging session, measure distance, or access precise location. Android requires RANGING permission for session creation, but this app does not create sessions.
- Alarm-stream volume control to temporarily raise the alarm volume and restore the previous level after successful disarm.
- Android CameraManager torch control, enabled by default, to pulse a compatible device LED only while the alarm is latched. Torch control itself does not request CAMERA permission or capture images/video; the separate optional photo-evidence feature has its own explicit permission and consent flow. Flash hardware is optional and may be unavailable or temporarily busy.
- Android's Storage Access Framework document/folder picker to read audio files or an evidence-backup file explicitly selected by the owner and to write event/evidence CSV or a password-encrypted metadata backup. Encrypted-vault JPEGs can be exported individually only when the owner chooses a destination; unencrypted captures are already stored in the app's public Gallery folder.
- Internet access for an owner-initiated HTTPS request to the official ThiefAlertSeQure GitHub Releases endpoint. The app downloads the release APK and its SHA-256 checksum only after the owner taps **Check for official updates**. It verifies the checksum, package ID, version code, and pinned release signing certificate before opening Android's installer.
- Device Owner / Lock Task APIs only if the device has been separately provisioned as a fully managed device and the user explicitly enables managed mode.

The optional Bypass Charging Settings entry opens Android's own battery/settings page and displays model-specific guidance. This build does not read or change the OEM bypass-charging mode because no verified control adapter is registered; it requests no additional permission and sends no charging-mode or device data to a server.

Android settings, manufacturer power-management behavior, notification controls, exact-alarm access, and DND policies remain under Android/device-owner control.

## 5. Sharing and third parties

The app contains no configured analytics, advertising SDK, app backend, or cloud storage. The update check sends a request to GitHub for public release metadata and, only when an update is available, downloads the APK and checksum. It does not upload protection state, event history, selected audio, trusted Bluetooth names or MAC addresses, credentials, camera photos, location, or other personal data. GitHub receives ordinary network request metadata, such as the request IP address, under GitHub's own privacy terms. No update check runs until the owner initiates it. Android itself provides the credential, biometric, notification, alarm, and device-management services.

## 6. Retention and deletion

Operational preferences remain in the app's private storage until changed by the app or removed with app data/uninstallation. App-managed Gallery photos and encrypted-vault photos expire after 30 days and are capped at 20 images per storage location. Settings can delete the app's encrypted-vault photos and only the app-owned `Pictures/ThiefAlertSeQure` Gallery files; unrelated Gallery images are not deleted. Exported JPEG files are outside the app's retention controls and remain wherever the owner chooses to save them. Gallery images are shared user media and remain on the device after app uninstall; after reinstall Android may no longer attribute the old images to the new installation, so the owner should manage those remaining Gallery files directly in Gallery. The app intentionally avoids requesting broad media-library access on Android 10+. Uninstalling the app removes its private app data under normal Android behavior, including the non-exportable Android Keystore photo-vault key; encrypted photos may therefore become unrecoverable. Device Owner policies must be disabled through the app before removing the app where device-management restrictions apply.

## 7. User choice

The first-launch notice (re-presented after this privacy-notice revision) explains local data handling, alarm behavior, optional flashlight behavior, explicitly owner-requested location snapshots, the opt-in front-camera evidence feature, its unencrypted Gallery default, future-capture encryption setting, and explicit verified migration for existing app-owned Gallery photos, plus platform limitations. The optional local ranging capability check is available separately in Device Intelligence and does not request an additional permission. The owner acknowledges the notice with “I understand & continue”; that acknowledgement is stored locally. The notice can be reviewed again from Settings.

## 8. Limitations

The flashlight is an optional visual output. It may fail when the LED is unavailable, another camera resource is using it, or Android/OEM thermal and hardware policies restrict it; the audio alarm remains independent. Flashing light may affect photosensitive users, and the owner can disable it in Settings. This privacy policy does not promise that the app can defeat Android force-stop, uninstall, physical shutdown, bootloader/recovery operations, battery removal, or manufacturer-specific controls. Alarm delivery may be affected by operating-system restrictions and user-controlled permissions.

## 9. Changes

If the app's data practices change in a future release, this policy and the in-app notice must be updated before that release.
