#!/usr/bin/env python3
"""Enforce the owner-directed non-instrumentation build policy."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
workflows = list((ROOT / ".github/workflows").glob("*.yml"))
forbidden_gradle = (
    "instrumentationTestBuild",
    "testInstrumentationRunner",
    "androidTestImplementation",
    "assembleAndroidTest",
    "compileReleaseAndroidTest",
)
for token in forbidden_gradle:
    if token in gradle:
        raise SystemExit(f"Instrumentation build wiring is forbidden: {token}")

for workflow in workflows:
    source = workflow.read_text(encoding="utf-8")
    forbidden_workflow = (
        "android-emulator-runner",
        "am instrument",
        "assembleReleaseAndroidTest",
        "compileReleaseAndroidTest",
        "run-android-instrumentation.sh",
        "verify-in-place-upgrade.sh",
    )
    for token in forbidden_workflow:
        if token in source:
            raise SystemExit(f"Instrumentation/emulator workflow wiring is forbidden: {workflow}: {token}")

print("Verified: no instrumentation runner/dependencies/build tasks or emulator execution in Gradle or GitHub Actions workflows.")
