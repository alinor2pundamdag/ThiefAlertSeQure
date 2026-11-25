#!/usr/bin/env python3
"""Prohibit Android instrumentation compilation/execution and all emulator/device test wiring."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Inspect every Gradle build/configuration file, not only the app module.
# This also catches instrumentation wiring hidden in root scripts, included
# build logic, or a future version catalog.
gradle_files = sorted(
    path
    for pattern in ("*.gradle", "*.gradle.kts", "*.toml")
    for path in ROOT.rglob(pattern)
    if not any(part in {".git", ".gradle", "build"} for part in path.relative_to(ROOT).parts)
)
if not gradle_files:
    raise SystemExit("No Gradle configuration files were found to audit.")

workflows = sorted((ROOT / ".github/workflows").glob("*.yml"))
forbidden_gradle = (
    "connectedCheck",
    "managedDeviceCheck",
    "instrumentationTestBuild",
    "compileDebugAndroidTest",
    "compileReleaseAndroidTest",
    "assembleAndroidTest",
    "assembleDebugAndroidTest",
    "assembleReleaseAndroidTest",
    "connectedAndroidTest",
    "connectedDebugAndroidTest",
    "connectedReleaseAndroidTest",
    "signReleaseForTests",
)
for path in gradle_files:
    source = path.read_text(encoding="utf-8")
    for token in forbidden_gradle:
        if token in source:
            raise SystemExit(
                f"Instrumentation build wiring is forbidden: {path.relative_to(ROOT)}: {token}"
            )

obsolete_helpers = (
    ROOT / ".github/scripts/run-android-instrumentation.sh",
    ROOT / ".github/scripts/prepare-adb-readiness-wrapper.sh",
    ROOT / ".github/scripts/verify-in-place-upgrade.sh",
)
for helper in obsolete_helpers:
    if helper.exists():
        raise SystemExit(f"Obsolete instrumentation/emulator helper must be removed: {helper.relative_to(ROOT)}")

for workflow in workflows:
    source = workflow.read_text(encoding="utf-8")
    forbidden_workflow = (
        "connectedCheck",
        "managedDeviceCheck",
        "android-emulator-runner",
        "am instrument",
        "assembleReleaseAndroidTest",
        "compileDebugAndroidTest",
        "compileReleaseAndroidTest",
        "assembleDebugAndroidTest",
        "connectedAndroidTest",
        "connectedDebugAndroidTest",
        "connectedReleaseAndroidTest",
        "run-android-instrumentation.sh",
        "verify-in-place-upgrade.sh",
    )
    for token in forbidden_workflow:
        if token in source:
            raise SystemExit(f"Instrumentation/emulator workflow wiring is forbidden: {workflow}: {token}")

print(
    "Verified: no Android instrumentation compilation/execution or emulator wiring "
    f"across {len(gradle_files)} Gradle configuration files and {len(workflows)} GitHub Actions workflows."
)
