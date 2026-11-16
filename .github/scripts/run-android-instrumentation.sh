#!/usr/bin/env bash
set -euo pipefail

api_level="${ANDROID_API_LEVEL:-unknown}"
diagnostics_dir="${RUNNER_TEMP:-/tmp}/android-diagnostics-api-${api_level}"
mkdir -p "$diagnostics_dir"

# Wait for a stable ADB transport AND Android framework services. A boot-complete
# property alone is insufficient: PackageManager can still be unavailable, causing
# DDMLib to fail APK installation with "Can't find service: package".
if [[ "$api_level" =~ ^[0-9]+$ ]]; then
  device_ready=false
  stable_checks=0
  for attempt in {1..180}; do
    adb_state="$(adb get-state 2>/dev/null || true)"
    if [[ "$adb_state" == "device" ]]; then
      boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      actual_api="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
      package_service="$(adb shell service check package 2>&1 || true)"
      package_path="$(adb shell pm path android 2>/dev/null | head -n 1 | tr -d '\r' || true)"
      if [[ "$boot_completed" == "1" && "$actual_api" == "$api_level" \
            && "$package_service" == *"found"* && "$package_path" == package:* ]]; then
        stable_checks=$((stable_checks + 1))
        if [[ "$stable_checks" -ge 3 ]]; then
          device_ready=true
          break
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
  if [[ "$device_ready" != "true" ]]; then
    echo "::error::Emulator did not expose a stable PackageManager at expected API $api_level."
    adb devices -l || true
    adb shell getprop sys.boot_completed || true
    adb shell service check package || true
    adb shell cmd package path android || true
    exit 1
  fi
  echo "Verified stable ADB transport, PackageManager, and API $api_level before instrumentation."
fi

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
      'FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|SIGBUS|backtrace:|tombstone|crash_dump|tombstoned|AndroidRuntime|Instrumentation|com\\.thiefalertsequre|linker|libc|art runtime|am_crash|am_proc_died' \
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
./gradlew :app:connectedReleaseAndroidTest -PsignReleaseForTests=true -PinstrumentationTestBuild=true --no-daemon --stacktrace
