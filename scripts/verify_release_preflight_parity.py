#!/usr/bin/env python3
"""Ensure release preflight matches CI safety gates without instrumentation compilation."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
android = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")

required = [
    "verify_no_instrumentation.py",
    "verify_ranging_privacy.py",
    "verify_evidence_privacy.py",
    "verify_local_history_storage.py",
    "verify_local_history_auth.py",
    "verify_intro_lifecycle.py",
    "verify_batch3_contracts.py",
    "verify_batch2_contracts.py",
    "verify_bypass_charging_contract.py",
    "verify_navigation.py",
    "verify_preferences.py",
    "verify_manifest_contract.py",
    "verify_state_ownership.py",
    "verify_release_identity.py",
    "verify_legal_policy_contract.py",
    "verify_packaged_legal_assets.py",
]
missing_android = [script for script in required if f"scripts/{script}" not in android]
missing_release = [script for script in required if f"scripts/{script}" not in release]
forbidden_instrumentation_tasks = (
    "compileDebugAndroidTest",
    "compileReleaseAndroidTest",
    "assembleAndroidTest",
    "connectedAndroidTest",
    "instrumentationTestBuild",
)
checks = {
    "Android CI keeps instrumentation compilation and execution disabled": not any(
        task in android for task in forbidden_instrumentation_tasks
    ),
    "release preflight keeps instrumentation compilation and execution disabled": not any(
        task in release for task in forbidden_instrumentation_tasks
    ),
    "Android CI invokes the instrumentation-disabled guard": "scripts/verify_no_instrumentation.py" in android,
    "release preflight invokes the instrumentation-disabled guard": "scripts/verify_no_instrumentation.py" in release,
    "Android CI runs every required non-physical safety contract": not missing_android,
    "signed-release preflight runs every required non-physical safety contract": not missing_release,
}
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + name)
if missing_android:
    print("Missing from Android CI: " + ", ".join(missing_android))
if missing_release:
    print("Missing from signed-release preflight: " + ", ".join(missing_release))
if not all(checks.values()):
    sys.exit("Release preflight parity contract failed.")
print(f"Verified {len(required)} shared safety and integration checks; instrumentation compilation/execution is prohibited in both workflows.")
