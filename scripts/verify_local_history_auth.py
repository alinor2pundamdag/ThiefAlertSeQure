#!/usr/bin/env python3
"""Guard the local-history authentication and lifecycle security boundary."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/thiefalertsequre/app"
activity = (APP / "LocalTamperHistoryActivity.kt").read_text(encoding="utf-8")
crypto = (APP / "LocalHistoryBiometricCrypto.kt").read_text(encoding="utf-8")

checks = {
    "strong biometric is required when biometric prompt is used":
        "BiometricManager.Authenticators.BIOMETRIC_STRONG" in activity,
    "biometric prompt receives an Android Keystore CryptoObject":
        "BiometricPrompt.CryptoObject(cryptoCipher)" in activity,
    "successful callback uses the returned CryptoObject cipher":
        "result.cryptoObject?.cipher" in activity
        and "LocalHistoryBiometricCrypto.verify" in activity,
    "access is granted only after the cryptographic proof succeeds":
        "if (proofValid)" in activity
        and "if (proofValid) {\n                        grantAccess()" in activity,
    "failed proof keeps history locked":
        "Secure biometric verification could not complete its Android Keystore operation" in activity,
    "device-credential fallback remains Android Keyguard-mediated":
        "createConfirmDeviceCredentialIntent" in activity,
    "fresh random challenge is generated":
        "SecureRandom().nextBytes(it)" in crypto
        and "CHALLENGE_BYTES = 32" in crypto,
    "authentication key requires per-operation user authentication":
        ".setUserAuthenticationRequired(true)" in crypto,
    "Android 11+ explicitly restricts the key to strong biometrics":
        "setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)" in crypto,
    "Android 8–10 use auth-per-operation key semantics":
        "setUserAuthenticationValidityDurationSeconds(-1)" in crypto,
    "proof is an AES-GCM operation":
        'Cipher.getInstance("AES/GCM/NoPadding")' in crypto
        and "cipher.doFinal(challenge)" in crypto,
    "biometric prompt is cancelled when Activity is destroyed":
        "biometricPrompt?.cancelAuthentication()" in activity,
    "challenge bytes are cleared on teardown and authentication completion":
        "biometricChallenge?.fill(0)" in activity
        and "clearBiometricChallenge()" in activity,
    "session expiry callback is detached on Activity teardown":
        "root.removeCallbacks(expiry)" in activity,
}
failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + name)
if failed:
    sys.exit("Local history authentication contract failed: " + "; ".join(failed))
print(f"Verified {len(checks)} local-history authentication and lifecycle contracts.")
