#!/usr/bin/env bash
set -euo pipefail

api_level="${ANDROID_API_LEVEL:-unknown}"
diagnostics_dir="${RUNNER_TEMP:-/tmp}/android-diagnostics-api-${api_level}"
mkdir -p "$diagnostics_dir"

# Wait for a stable ADB transport AND Android framework services. A boot-complete
# property alone is insufficient: PackageManager can still be unavailable, causing
# DDMLib to fail APK installation with "Can't find service: package".
wait_for_stable_emulator() {
  local stable_checks=0 attempt
  local adb_state boot_completed actual_api package_service package_path
  for attempt in {1..180}; do
    adb_state="$(adb get-state 2>/dev/null || true)"
    if [[ "$adb_state" == "device" ]]; then
      boot_completed="$(timeout 10s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      actual_api="$(timeout 10s adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
      package_service="$(timeout 10s adb shell service check package 2>&1 | tr -d '\r' || true)"
      package_path="$(timeout 10s adb shell pm path android 2>/dev/null | head -n 1 | tr -d '\r' || true)"
      if [[ "$boot_completed" == "1" && "$actual_api" == "$api_level" \
            && "$package_service" == "Service package: found" && "$package_path" == package:* ]]; then
        stable_checks=$((stable_checks + 1))
        if [[ "$stable_checks" -ge 3 ]]; then
          echo "Verified stable ADB transport, PackageManager, and API $api_level."
          return 0
        fi
      else
        stable_checks=0
      fi
    else
      stable_checks=0
      adb reconnect offline >/dev/null 2>&1 || true
    fi
    sleep 2
  done
  echo "::error::Emulator did not expose a stable PackageManager at expected API $api_level."
  adb devices -l || true
  timeout 10s adb shell getprop sys.boot_completed || true
  timeout 10s adb shell service check package || true
  timeout 10s adb shell pm path android || true
  return 1
}

capture_android_diagnostics() {
  local test_exit_code=$?
  trap - EXIT
  set +e

  printf 'API level: %s\nCommit: %s\nTest exit code: %s\n' \
    "$api_level" "${GITHUB_SHA:-unknown}" "$test_exit_code" \
    > "$diagnostics_dir/run-context.txt"

  adb devices -l > "$diagnostics_dir/adb-devices.txt" 2>&1
  adb logcat -b crash -d -v threadtime \
    > "$diagnostics_dir/logcat-crash-buffer.txt" 2>&1
  adb logcat -b all -d -v threadtime 2>&1 | \
    grep -E -i -C 3 \
      'FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|SIGBUS|backtrace:|tombstone|crash_dump|tombstoned|AndroidRuntime|Instrumentation|com\.thiefalertsequre|linker|libc|art runtime|am_crash|am_proc_died' \
    > "$diagnostics_dir/logcat-crash-context.txt" || true

  adb shell getprop > "$diagnostics_dir/device-properties.txt" 2>&1
  adb shell dumpsys activity processes \
    > "$diagnostics_dir/activity-processes.txt" 2>&1
  adb shell dumpsys activity exit-info com.thiefalertsequre.app \
    > "$diagnostics_dir/application-exit-info.txt" 2>&1
  adb shell ls -la /data/tombstones \
    > "$diagnostics_dir/tombstone-directory.txt" 2>&1

  mkdir -p "$diagnostics_dir/tombstones"
  if adb root > "$diagnostics_dir/adb-root.txt" 2>&1; then
    timeout 15s adb wait-for-device >/dev/null 2>&1
    adb pull /data/tombstones "$diagnostics_dir/tombstones" \
      > "$diagnostics_dir/tombstone-pull.txt" 2>&1
  fi

  if [ -d app/build/reports/androidTests/connected/release ]; then
    cp -R app/build/reports/androidTests/connected/release \
      "$diagnostics_dir/android-test-report"
  fi
  if [ -d app/build/outputs/androidTest-results/connected/release ]; then
    cp -R app/build/outputs/androidTest-results/connected/release \
      "$diagnostics_dir/android-test-results"
  fi

  exit "$test_exit_code"
}

trap capture_android_diagnostics EXIT

# Install the target package first so the disposable emulator can grant the
# Bluetooth runtime permission before AndroidX starts the instrumentation process.
# This is test-harness setup only; the app never auto-grants or auto-requests it here.
is_transient_package_manager_failure() {
  local output="$1"
  grep -Eiq \
    'Can.t find service: package|Failure calling service package|PackageManager.*(not ready|unavailable)|device offline|device .+ not found|Broken pipe|ShellCommandUnresponsiveException' \
    <<< "$output"
}

recover_emulator_for_setup_retry() {
  adb reconnect offline >/dev/null 2>&1 || true
  adb -s emulator-5554 reboot >/dev/null 2>&1 || true
  sleep 5
  wait_for_stable_emulator
}

# One install attempt. The caller owns the bounded retry so installation,
# permission grant, and PackageManager verification are retried as one unit.
install_instrumentation_target() {
  local install_attempt="$1"
  local log_file="$diagnostics_dir/install-attempt-$install_attempt.log"
  local install_exit_code
  set +e
  ./gradlew :app:installRelease -PsignReleaseForTests=true -PinstrumentationTestBuild=true --no-daemon --stacktrace 2>&1 | tee "$log_file"
  install_exit_code="${PIPESTATUS[0]}"
  set -e

  if [[ "$install_exit_code" -eq 0 ]]; then
    return 0
  fi

  # A real Gradle/configuration/ABI failure is never retried as an emulator issue.
  if grep -Eq \
      'ShellCommandUnresponsiveException|Unknown API Level|device offline|Can.t find service: package|Failed to install on any devices|Skipping device .*Could not find build of variant which supports density' \
      "$log_file"; then
    echo "::warning::Emulator/PackageManager infrastructure failed during target installation."
    return 2
  fi
  return 1
}

prepare_instrumentation_permissions() {
  local setup_attempt=1
  local install_status grant_status package_status permission_dump_status
  local grant_output package_output permission_dump permission_state package_present
  local transient_failure

  while [[ "$setup_attempt" -le 3 ]]; do
    if install_instrumentation_target "$setup_attempt"; then
      install_status=0
    else
      install_status=$?
    fi

    if [[ "$install_status" -ne 0 ]]; then
      if [[ "$install_status" -ne 2 || "$setup_attempt" -ge 3 ]]; then
        echo "::error::Unable to install the release instrumentation target after $setup_attempt attempt(s)."
        return 1
      fi
      echo "::warning::Rebooting the emulator after a PackageManager-only installation failure (attempt $setup_attempt/3)."
      if ! recover_emulator_for_setup_retry; then
        echo "::error::Emulator framework did not recover before retrying target installation."
        return 1
      fi
      setup_attempt=$((setup_attempt + 1))
      continue
    fi

    if grant_output="$(adb shell pm grant com.thiefalertsequre.app android.permission.BLUETOOTH_CONNECT 2>&1)"; then
      grant_status=0
    else
      grant_status=$?
    fi
    if package_output="$(adb shell pm list packages 2>&1)"; then
      package_status=0
    else
      package_status=$?
    fi
    package_output="${package_output//$'\r'/}"
    if printf '%s\n' "$package_output" | grep -Fxq 'package:com.thiefalertsequre.app'; then
      package_present=true
    else
      package_present=false
    fi

    permission_dump=""
    permission_state=""
    permission_dump_status=0
    if [[ "$package_present" == true ]]; then
      if permission_dump="$(adb shell dumpsys package com.thiefalertsequre.app 2>&1)"; then
        permission_dump_status=0
      else
        permission_dump_status=$?
      fi
      permission_dump="${permission_dump//$'\r'/}"
      permission_state="$(printf '%s\n' "$permission_dump" | grep -F 'android.permission.BLUETOOTH_CONNECT: granted=true' | tail -n 1 || true)"
    fi

    if [[ "$grant_status" -eq 0 && "$package_status" -eq 0 &&
          "$package_present" == true && "$permission_dump_status" -eq 0 &&
          -n "$permission_state" ]]; then
      echo "Verified BLUETOOTH_CONNECT grant: $permission_state"
      return 0
    fi

    transient_failure=false
    if [[ "$package_status" -ne 0 || "$package_present" == false ]]; then
      transient_failure=true
    fi
    if is_transient_package_manager_failure "$grant_output" ||
        is_transient_package_manager_failure "$package_output" ||
        is_transient_package_manager_failure "$permission_dump"; then
      transient_failure=true
    fi

    if [[ "$transient_failure" == true && "$setup_attempt" -lt 3 ]]; then
      echo "::warning::PackageManager lost the installed target or permission state after installation; rebooting and retrying the complete setup (attempt $setup_attempt/3)."
      if ! recover_emulator_for_setup_retry; then
        echo "::error::Emulator framework did not recover before retrying instrumentation setup."
        return 1
      fi
      setup_attempt=$((setup_attempt + 1))
      continue
    fi

    echo "::error::Instrumentation target package/permission verification failed after $setup_attempt attempt(s)."
    echo "Package present: $package_present; pm grant exit: $grant_status; package-list exit: $package_status; permission-dump exit: $permission_dump_status"
    printf 'pm grant output: %s\n' "$grant_output"
    printf 'pm list packages output: %s\n' "$package_output"
    printf 'permission dump: %s\n' "$permission_dump"
    return 1
  done

  echo "::error::Instrumentation target setup exhausted its bounded retry budget."
  return 1
}

if [[ "$api_level" =~ ^[0-9]+$ ]]; then
  wait_for_stable_emulator || exit 1
fi
if [[ "$api_level" =~ ^[0-9]+$ ]]; then
  prepare_instrumentation_permissions || exit 1
fi

run_instrumentation_attempt() {
  local attempt="$1"
  local log_file="$diagnostics_dir/instrumentation-attempt-$attempt.log"
  local test_exit_code
  set +e
  ./gradlew :app:connectedReleaseAndroidTest -PsignReleaseForTests=true -PinstrumentationTestBuild=true --no-daemon --stacktrace 2>&1 | tee "$log_file"
  test_exit_code="${PIPESTATUS[0]}"
  set -e
  return "$test_exit_code"
}

attempt=1
while true; do
  if run_instrumentation_attempt "$attempt"; then
    exit 0
  else
    test_exit_code="$?"
  fi

  # Never retry assertion failures. Retry only known emulator/framework failures:
  # DDMLib can temporarily lose API identity or PackageManager while cold images settle.
  attempt_log="$diagnostics_dir/instrumentation-attempt-$attempt.log"
  if [[ "$attempt" -ge 3 ]] || ! grep -Eq \
      'No compatible devices connected|Found [0-9]+ connected device|Unknown API Level|Can.t find service: package|Failed to install split APK|Failed to commit install session|Failure calling service package|Broken pipe|ShellCommandUnresponsiveException|device offline|Skipping device .*Could not find build of variant which supports density|Failed to install on any devices' \
      "$attempt_log"; then
    exit "$test_exit_code"
  fi

  echo "::warning::Android emulator/framework infrastructure failed during instrumentation; rebooting the emulator and retrying this infrastructure-only failure."
  adb reconnect offline >/dev/null 2>&1 || true
  adb -s emulator-5554 reboot >/dev/null 2>&1 || true
  sleep 5
  if ! wait_for_stable_emulator; then
    echo "::error::Emulator framework did not recover after reboot; preserving diagnostics."
    exit "$test_exit_code"
  fi
  attempt=$((attempt + 1))
done
