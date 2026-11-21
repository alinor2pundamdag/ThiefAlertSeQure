#!/usr/bin/env python3
"""Fail CI if the immutable production package or signing certificate drifts."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
policy = (ROOT / "app/src/main/java/com/thiefalertsequre/app/AppUpdatePolicy.kt").read_text(encoding="utf-8")
release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
updater = (ROOT / "app/src/main/java/com/thiefalertsequre/app/AppUpdateManager.kt").read_text(encoding="utf-8")

def capture(pattern: str, source: str, label: str) -> str:
    match = re.search(pattern, source)
    if not match:
        raise SystemExit(f"Missing {label} in its source of truth")
    return match.group(1)

gradle_package = capture(r'applicationId\s*=\s*"([^"]+)"', gradle, "Gradle applicationId")
version_name = capture(r'val appVersionName = "([^"]+)"', gradle, "Gradle appVersionName")
assert re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,3}_Developer_Alinor", version_name), (
    f"Official versionName must retain the _Developer_Alinor suffix: {version_name}"
)
policy_package = capture(r'EXPECTED_PACKAGE\s*=\s*"([^"]+)"', policy, "updater package constant")
release_package = "com.thiefalertsequre.app"
policy_cert = capture(r'EXPECTED_CERT_SHA256\s*=\s*"([0-9a-fA-F]{64})"', policy, "updater certificate")
release_cert = capture(r'EXPECTED_CERT="([0-9a-fA-F]{64})"', release, "release workflow certificate")
assert gradle_package == policy_package == release_package, (
    f"Production package drift: Gradle={gradle_package}, updater={policy_package}, release={release_package}"
)
assert policy_cert.lower() == release_cert.lower(), (
    f"Signing certificate drift: updater={policy_cert}, release workflow={release_cert}"
)
assert "AppUpdatePolicy.requireInstalledPackage(context.packageName)" in updater
assert "AppUpdatePolicy.requireNewerVersionCode" in updater
assert "AppUpdatePolicy.hasOfficialSigner" in updater
assert 'applicationIdSuffix = ".v114test"' in gradle
assert "output.versionCode.set(appVersionCode)" in gradle
assert "output.versionName.set(appTestVersionName)" in gradle
assert 'appVersionName.substringBefore("_Developer_Alinor")' in gradle
filament_versions = set(re.findall(
    r'com\\.google\\.android\\.filament:[^"]+:(\\d+\\.\\d+\\.\\d+)', gradle
))
assert filament_versions == {"1.81.0"}, (
    f"Filament runtime/helper/material compiler artifacts must use one verified release: {filament_versions}"
)
root_gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
kotlin_plugin_versions = set(re.findall(
    r'id\\("org\\.jetbrains\\.kotlin\\.(?:android|plugin\\.compose)"\\) version "([^"]+)"',
    root_gradle,
))
assert kotlin_plugin_versions == {"2.4.21"}, (
    f"Kotlin Android and Compose compiler plugins must stay aligned: {kotlin_plugin_versions}"
)
android_workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
assert 'VERSION_NAME="${VERSION_NAME%_Developer_Alinor}-test"' in android_workflow
assert not re.search(r'^\\s*VERSION_CODE=\\d+|VERSION_NAME="[^"]+-test"', android_workflow, re.MULTILINE), (
    "CI debug version must derive from the Gradle source of truth"
)
print(f"Production identity consistent: {gradle_package}; signer SHA-256 {policy_cert.lower()}")
