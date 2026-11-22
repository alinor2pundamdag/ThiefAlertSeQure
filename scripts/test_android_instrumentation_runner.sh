#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
project="$tmp_dir/project"
bin_dir="$tmp_dir/bin"
state_dir="$tmp_dir/state"
mkdir -p "$project/.github/scripts" "$bin_dir" "$state_dir" "$tmp_dir/runner"
cp "$repo_root/.github/scripts/run-android-instrumentation.sh" "$project/.github/scripts/"

cat > "$bin_dir/adb" <<'FAKE_ADB'
#!/usr/bin/env bash
set -euo pipefail
args=("$@")
if [[ "${args[0]:-}" == "-s" ]]; then
  args=("${args[@]:2}")
fi
command="${args[*]}"
printf '%s\n' "$command" >> "${FAKE_ADB_LOG:?}"
case "$command" in
  get-state) echo device ;;
  "devices -l") printf 'List of devices attached\nemulator-5554 device\n' ;;
  "reconnect offline"|reboot|root|wait-for-device) exit 0 ;;
  "shell getprop sys.boot_completed") echo 1 ;;
  "shell getprop ro.build.version.sdk") echo "${ANDROID_API_LEVEL:-37}" ;;
  "shell service check package") echo 'Service package: found' ;;
  "shell pm path android") echo 'package:/system/framework/framework-res.apk' ;;
  "shell pm grant com.thiefalertsequre.app android.permission.BLUETOOTH_CONNECT") exit 0 ;;
  "shell pm list packages")
    if [[ -f "${FAKE_ADB_STATE_DIR:?}/installed" ]]; then
      echo 'package:com.thiefalertsequre.app'
    fi
    ;;
  "shell dumpsys package com.thiefalertsequre.app")
    if [[ -f "${FAKE_ADB_STATE_DIR:?}/installed" ]]; then
      echo 'android.permission.BLUETOOTH_CONNECT: granted=true'
    else
      echo 'Unable to find package com.thiefalertsequre.app' >&2
      exit 1
    fi
    ;;
  "shell getprop"|"shell dumpsys activity processes"|"shell dumpsys activity exit-info com.thiefalertsequre.app"|"shell ls -la /data/tombstones") exit 0 ;;
  logcat\ *) exit 0 ;;
  pull\ *) exit 0 ;;
  *) exit 0 ;;
esac
FAKE_ADB
chmod +x "$bin_dir/adb"

cat > "$bin_dir/sleep" <<'FAKE_SLEEP'
#!/usr/bin/env bash
exit 0
FAKE_SLEEP
chmod +x "$bin_dir/sleep"

cat > "$project/gradlew" <<'FAKE_GRADLE'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *":app:installRelease"* ]]; then
  attempt=0
  [[ -f "${FAKE_ADB_STATE_DIR:?}/install-attempts" ]] && attempt="$(cat "$FAKE_ADB_STATE_DIR/install-attempts")"
  attempt=$((attempt + 1))
  printf '%s' "$attempt" > "$FAKE_ADB_STATE_DIR/install-attempts"
  if [[ "${FAKE_GRADLE_FAIL_INSTALL:-0}" == "1" ]]; then
    echo "Kotlin compilation failed"
    exit 1
  fi
  # Simulate a successful Gradle task followed by transient PackageManager
  # loss: the first install is not visible to pm list packages. A separate
  # mode keeps the package invisible to verify the retry limit is enforced.
  if [[ "${FAKE_PACKAGE_NEVER_VISIBLE:-0}" != "1" && "$attempt" -ge 2 ]]; then
    : > "$FAKE_ADB_STATE_DIR/installed"
  fi
  echo "Fake install attempt $attempt"
  exit 0
fi
if [[ "$*" == *":app:connectedReleaseAndroidTest"* ]]; then
  printf '1' > "$FAKE_ADB_STATE_DIR/instrumentation-runs"
  echo 'Fake connected instrumentation run passed'
  exit 0
fi
exit 0
FAKE_GRADLE
chmod +x "$project/gradlew"

(
  cd "$project"
  PATH="$bin_dir:$PATH" \
  ANDROID_API_LEVEL=37 \
  RUNNER_TEMP="$tmp_dir/runner" \
  GITHUB_SHA=runner-harness-test \
  FAKE_ADB_LOG="$state_dir/adb.log" \
  FAKE_ADB_STATE_DIR="$state_dir" \
    bash .github/scripts/run-android-instrumentation.sh
)

[[ "$(cat "$state_dir/install-attempts")" == 2 ]]
[[ "$(cat "$state_dir/instrumentation-runs")" == 1 ]]
grep -Fq 'reboot' "$state_dir/adb.log"
# Persistent PackageManager loss must exhaust exactly three attempts and stop.
persistent_state="$tmp_dir/state-persistent"
mkdir -p "$persistent_state"
if (
  cd "$project"
  PATH="$bin_dir:$PATH" \
  ANDROID_API_LEVEL=37 \
  RUNNER_TEMP="$tmp_dir/runner-persistent" \
  GITHUB_SHA=runner-harness-persistent \
  FAKE_ADB_LOG="$persistent_state/adb.log" \
  FAKE_ADB_STATE_DIR="$persistent_state" \
  FAKE_PACKAGE_NEVER_VISIBLE=1 \
    bash .github/scripts/run-android-instrumentation.sh
) > "$persistent_state/output.log" 2>&1; then
  echo "Instrumentation runner incorrectly passed with a permanently missing target package." >&2
  exit 1
fi
[[ "$(cat "$persistent_state/install-attempts")" == 3 ]]
[[ "$(grep -c '^reboot
[[ ! -f "$persistent_state/instrumentation-runs" ]]

# Real Gradle/compiler failures must not be retried as emulator infrastructure.
compile_state="$tmp_dir/state-compile-failure"
mkdir -p "$compile_state"
if (
  cd "$project"
  PATH="$bin_dir:$PATH" \
  ANDROID_API_LEVEL=37 \
  RUNNER_TEMP="$tmp_dir/runner-compile-failure" \
  GITHUB_SHA=runner-harness-compile-failure \
  FAKE_ADB_LOG="$compile_state/adb.log" \
  FAKE_ADB_STATE_DIR="$compile_state" \
  FAKE_GRADLE_FAIL_INSTALL=1 \
    bash .github/scripts/run-android-instrumentation.sh
) > "$compile_state/output.log" 2>&1; then
  echo "Instrumentation runner incorrectly passed a simulated Gradle failure." >&2
  exit 1
fi
[[ "$(cat "$compile_state/install-attempts")" == 1 ]]
if grep -Fxq 'reboot' "$compile_state/adb.log"; then
  echo "Instrumentation runner incorrectly rebooted/retried a real Gradle failure." >&2
  exit 1
fi

echo 'Android instrumentation runner verified transient recovery, bounded exhaustion, and non-infrastructure failure handling.'
 "$persistent_state/adb.log")" == 2 ]]
[[ ! -f "$persistent_state/instrumentation-runs" ]]

# Real Gradle/compiler failures must not be retried as emulator infrastructure.
compile_state="$tmp_dir/state-compile-failure"
mkdir -p "$compile_state"
if (
  cd "$project"
  PATH="$bin_dir:$PATH" \
  ANDROID_API_LEVEL=37 \
  RUNNER_TEMP="$tmp_dir/runner-compile-failure" \
  GITHUB_SHA=runner-harness-compile-failure \
  FAKE_ADB_LOG="$compile_state/adb.log" \
  FAKE_ADB_STATE_DIR="$compile_state" \
  FAKE_GRADLE_FAIL_INSTALL=1 \
    bash .github/scripts/run-android-instrumentation.sh
) > "$compile_state/output.log" 2>&1; then
  echo "Instrumentation runner incorrectly passed a simulated Gradle failure." >&2
  exit 1
fi
[[ "$(cat "$compile_state/install-attempts")" == 1 ]]
if grep -Fxq 'reboot' "$compile_state/adb.log"; then
  echo "Instrumentation runner incorrectly rebooted/retried a real Gradle failure." >&2
  exit 1
fi

echo 'Android instrumentation runner verified transient recovery, bounded exhaustion, and non-infrastructure failure handling.'
