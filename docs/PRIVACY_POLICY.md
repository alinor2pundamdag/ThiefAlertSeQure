# ThiefAlertSeQure Privacy Policy

**Effective date:** 5 November 2026

## 1. Overview

ThiefAlertSeQure is designed as an on-device charging anti-theft alarm. The application does not require an account, cloud service, or internet connection.

## 2. Information stored on the device

The app stores only the operational information needed to provide its functions:

- Protection mode and charger-removal countdown deadlines.
- Biometric failure count and temporary cooldown deadline.
- The user's privacy-notice choice and the time it was recorded.
- The previous alarm-stream volume while an alarm is active, so it can be restored after successful disarm.

This information is stored in the app's private local preferences. The app does not upload it.

## 3. Credentials and biometrics

The app does not request, read, store, or transmit the device PIN, pattern, password, fingerprint data, or face templates. Android Keyguard and Android's biometric framework perform verification and retain control of the underlying credential and biometric data.

## 4. Permissions and system functions

Depending on Android version and the user's choices, the app may use:

- Boot-completed and charger-connected/disconnected broadcasts to recover protection state and detect charging changes.
- Alarm scheduling; precise alarm access is a special Android setting on supported versions.
- Notifications and a foreground media-playback service while an alarm is active.
- Alarm-stream volume control to temporarily raise the alarm volume and restore the previous level after successful disarm.
- Device Owner / Lock Task APIs only if the device has been separately provisioned as a fully managed device and the user explicitly enables managed mode.

Android settings, manufacturer power-management behavior, notification controls, exact-alarm access, and DND policies remain under Android/device-owner control.

## 5. Sharing and third parties

The app contains no configured analytics, advertising SDK, backend, cloud storage, or network permission. It does not sell or share app-collected personal information because it is not designed to collect or transmit such information. Android itself provides the credential, biometric, notification, alarm, and device-management services.

## 6. Retention and deletion

Operational preferences remain in the app's private storage until changed by the app or removed with app data/uninstallation. Uninstalling the app removes its private app data under normal Android behavior. Device Owner policies must be disabled through the app before removing the app where device-management restrictions apply.

## 7. User choice

The first-launch notice provides Accept & continue and Decline & continue. The choice is recorded locally and distinctly. Both choices continue to the same essential local protection functions. Declining is not represented as consent and does not disable the core local alarm. The notice can be reviewed again from Settings.

## 8. Limitations

This privacy policy does not promise that the app can defeat Android force-stop, uninstall, physical shutdown, bootloader/recovery operations, battery removal, or manufacturer-specific controls. Alarm delivery may be affected by operating-system restrictions and user-controlled permissions.

## 9. Changes

If the app's data practices change in a future release, this policy and the in-app notice must be updated before that release.
