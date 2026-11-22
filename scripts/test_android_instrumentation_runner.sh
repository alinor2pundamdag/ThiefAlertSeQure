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

cat > "$project/gradlew" <<'FAKE_GRADLE'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *":app:installRelease"* ]]; then
  attempt=0
  [[ -f "${FAKE_ADB_STATE_DIR:?}/install-attempts" ]] && attempt="$(cat "$FAKE_ADB_STATE_DIR/install-attempts")"
  attempt=$((attempt + 1))
  printf '%s' "$attempt" > "$FAKE_ADB_STATE_DIR/install-attempts"
  # Simulate a successful Gradle task followed by a transient PackageManager
  # loss: the first install is not visible to pm list packages.
  if [[ "$attempt" -ge 2 ]]; then
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
echo 'Android instrumentation runner recovered a transient post-install PackageManager failure.'
