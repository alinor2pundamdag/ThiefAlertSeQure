<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# ThiefAlertSeQure Privacy Policy

**Effective date:** 8 November 2026

## 1. Overview

ThiefAlertSeQure is designed as an on-device charging anti-theft alarm. The application does not require an account or cloud backend. Internet access is used only when the owner manually checks for an official GitHub release and downloads its APK.

## 2. Information stored on the device

The app stores only the operational information needed to provide its functions:

- Protection mode and charger-removal countdown deadlines.
- Biometric failure count and temporary cooldown deadline.
- The user's privacy-notice choice and the time it was recorded.
- The previous alarm-stream volume while an alarm is active, so it can be restored after successful disarm.
- A bounded local protection event history (up to 100 entries), which the owner can review or export as CSV.
- Monitor preferences such as enablement, motion sensitivity and optional battery-alert threshold.
- The owner's local preference for enabling or disabling the alarm flashlight.
- URI references and Android read-access grants for owner-selected alarm and secondary-alert audio. The app stores references/permissions, not a second copy of the selected audio file.

This information is stored in the app's private local preferences. The app does not upload it.

## 3. Credentials and biometrics

The app does not request, read, store, or transmit the device PIN, pattern, password, fingerprint data, or face templates. Android Keyguard and Android's biometric framework perform verification and retain control of the underlying credential and biometric data.

## 4. Permissions and system functions

Depending on Android version and the user's choices, the app may use:

- Boot-completed and charger-connected/disconnected broadcasts to recover protection state and detect charging changes.
- Alarm scheduling; precise alarm access is a special Android setting on supported versions.
- Notifications and a foreground media-playback service while an alarm is active.
- Alarm-stream volume control to temporarily raise the alarm volume and restore the previous level after successful disarm.
- Android CameraManager torch control, enabled by default, to pulse a compatible device LED only while the alarm is latched. This API does not request CAMERA permission, open the camera for capture, or collect images/video. Flash hardware is optional and may be unavailable or temporarily busy.
- Android's Storage Access Framework document/folder picker to read audio files explicitly selected by the owner and to write an event-history CSV only when the owner chooses an export destination.
- Internet access for an owner-initiated HTTPS request to the official ThiefAlertSeQure GitHub Releases endpoint. The app downloads the release APK and its SHA-256 checksum only after the owner taps **Check for official updates**. It verifies the checksum, package ID, version code, and pinned release signing certificate before opening Android's installer.
- Device Owner / Lock Task APIs only if the device has been separately provisioned as a fully managed device and the user explicitly enables managed mode.

Android settings, manufacturer power-management behavior, notification controls, exact-alarm access, and DND policies remain under Android/device-owner control.

## 5. Sharing and third parties

The app contains no configured analytics, advertising SDK, app backend, or cloud storage. The update check sends a request to GitHub for public release metadata and, only when an update is available, downloads the APK and checksum. It does not upload protection state, event history, selected audio, credentials, device identifiers, or other personal data. No update check runs until the owner initiates it. Android itself provides the credential, biometric, notification, alarm, and device-management services.

## 6. Retention and deletion

Operational preferences remain in the app's private storage until changed by the app or removed with app data/uninstallation. Uninstalling the app removes its private app data under normal Android behavior. Device Owner policies must be disabled through the app before removing the app where device-management restrictions apply.

## 7. User choice

The first-launch notice explains local data handling, alarm behavior, optional flashlight behavior and platform limitations. The owner acknowledges the notice with “I understand & continue”; that acknowledgement is stored locally. The notice can be reviewed again from Settings.

## 8. Limitations

The flashlight is an optional visual output. It may fail when the LED is unavailable, another camera resource is using it, or Android/OEM thermal and hardware policies restrict it; the audio alarm remains independent. Flashing light may affect photosensitive users, and the owner can disable it in Settings. This privacy policy does not promise that the app can defeat Android force-stop, uninstall, physical shutdown, bootloader/recovery operations, battery removal, or manufacturer-specific controls. Alarm delivery may be affected by operating-system restrictions and user-controlled permissions.

## 9. Changes

If the app's data practices change in a future release, this policy and the in-app notice must be updated before that release.
