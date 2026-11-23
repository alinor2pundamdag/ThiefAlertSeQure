#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

sdk_root="$tmp_dir/sdk"
bin_dir="$tmp_dir/bin"
mkdir -p "$sdk_root/platform-tools" "$bin_dir"
export FAKE_ADB_CALL_LOG="$tmp_dir/adb-calls.log"
export FAKE_ADB_KEYEVENT_LOG="$tmp_dir/keyevents.log"
: > "$FAKE_ADB_CALL_LOG"
: > "$FAKE_ADB_KEYEVENT_LOG"

cat > "$sdk_root/platform-tools/adb" <<'FAKE_ADB'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "${FAKE_ADB_CALL_LOG:?}"
args=("$@")
if [[ "${args[0]:-}" == "-s" ]]; then
    args=("${args[@]:2}")
fi
command="${args[*]}"

case "$command" in
    start-server|reconnect\ offline)
        exit 0
        ;;
    version)
        echo "Android Debug Bridge version fake"
        exit 0
        ;;
    "shell service check input")
        printf 'Service input: %s\n' "${FAKE_ADB_INPUT_SERVICE_STATUS:-not found}"
        exit 0
        ;;
    "shell service check settings")
        printf 'Service settings: %s\n' "${FAKE_ADB_SETTINGS_SERVICE_STATUS:-not found}"
        exit 0
        ;;
    "shell input keyevent 82")
        printf 'input-keyevent\n' >> "${FAKE_ADB_KEYEVENT_LOG:?}"
        exit 0
        ;;
    "shell settings put global window_animation_scale 0.0"|\
    "shell settings put global transition_animation_scale 0.0"|\
    "shell settings put global animator_duration_scale 0.0"|\
    "shell settings put secure spell_checker_enabled 0")
        if [[ "${FAKE_ADB_SETTINGS_PUT_FAIL:-0}" == "1" ]]; then
            echo "Simulated settings write failure" >&2
            exit 1
        fi
        exit 0
        ;;
    "shell getprop sys.boot_completed")
        printf '0\n'
        exit 0
        ;;
    *)
        echo "Unexpected fake ADB invocation: $command" >&2
        exit 2
        ;;
esac
FAKE_ADB
chmod +x "$sdk_root/platform-tools/adb"

cat > "$bin_dir/sleep" <<'FAKE_SLEEP'
#!/usr/bin/env bash
exit 0
FAKE_SLEEP
chmod +x "$bin_dir/sleep"
export PATH="$bin_dir:$PATH"

ANDROID_HOME="$sdk_root" bash "$repo_root/.github/scripts/prepare-adb-readiness-wrapper.sh"
wrapper="$sdk_root/platform-tools/adb"

# A successful shell exit is not enough: "not found" must never satisfy readiness.
: > "$FAKE_ADB_KEYEVENT_LOG"
if FAKE_ADB_INPUT_SERVICE_STATUS='not found' \
    ADB_READINESS_INPUT_ATTEMPTS=2 \
    ADB_READINESS_INPUT_INTERVAL_SECONDS=0 \
    "$wrapper" -s emulator-5554 shell input keyevent 82 > "$tmp_dir/not-found.log" 2>&1; then
    echo "ADB wrapper incorrectly accepted the 'Service input: not found' response." >&2
    exit 1
fi
if [[ -s "$FAKE_ADB_KEYEVENT_LOG" ]]; then
    echo "ADB wrapper sent the key event before InputManager was ready." >&2
    exit 1
fi
grep -Fq "did not become ready within 2 checks" "$tmp_dir/not-found.log"

# The exact positive response must release the key event.
FAKE_ADB_INPUT_SERVICE_STATUS='found' \
    ADB_READINESS_INPUT_ATTEMPTS=2 \
    ADB_READINESS_INPUT_INTERVAL_SECONDS=0 \
    "$wrapper" -s emulator-5554 shell input keyevent 82 > "$tmp_dir/found.log" 2>&1
grep -Fxq "input-keyevent" "$FAKE_ADB_KEYEVENT_LOG"

# Boot polling belongs to android-emulator-runner and must pass through immediately.
: > "$FAKE_ADB_CALL_LOG"
"$wrapper" -s emulator-5554 shell getprop sys.boot_completed > "$tmp_dir/boot-probe.log"
[[ "$(wc -l < "$FAKE_ADB_CALL_LOG" | tr -d ' ')" == "1" ]]
grep -Fxq "0" "$tmp_dir/boot-probe.log"

# Missing SettingsProvider only disables optional emulator UI tuning; it must not
# fail the job or send a settings write to an unavailable service.
: > "$FAKE_ADB_CALL_LOG"
FAKE_ADB_SETTINGS_SERVICE_STATUS='not found' \
    ADB_READINESS_SETTINGS_ATTEMPTS=2 \
    ADB_READINESS_SETTINGS_INTERVAL_SECONDS=0 \
    "$wrapper" -s emulator-5554 shell settings put global window_animation_scale 0.0 \
    > "$tmp_dir/settings-unavailable.log" 2>&1
grep -Fq "Skipping optional emulator UI-tuning command" "$tmp_dir/settings-unavailable.log"
if grep -Fq "shell settings put global window_animation_scale 0.0" "$FAKE_ADB_CALL_LOG"; then
    echo "ADB wrapper sent an optional settings write while SettingsProvider was unavailable." >&2
    exit 1
fi

# Cache the unavailable state so additional optional tuning commands do not each
# consume another readiness timeout.
: > "$FAKE_ADB_CALL_LOG"
FAKE_ADB_SETTINGS_SERVICE_STATUS='found' \
    "$wrapper" -s emulator-5554 shell settings put global transition_animation_scale 0.0 \
    > "$tmp_dir/settings-cached.log" 2>&1
grep -Fq "SettingsProvider was previously unavailable" "$tmp_dir/settings-cached.log"
if grep -Fq "shell service check settings" "$FAKE_ADB_CALL_LOG" ||
    grep -Fq "shell settings put global transition_animation_scale 0.0" "$FAKE_ADB_CALL_LOG"; then
    echo "ADB wrapper did not reuse the cached unavailable SettingsProvider state." >&2
    exit 1
fi

# A ready SettingsProvider must receive the optional write.
settings_marker="#!/usr/bin/env bash
set -euo pipefail

# android-emulator-runner prepends ANDROID_HOME/platform-tools to PATH after
# ordinary GITHUB_PATH entries. Install the wrapper at that exact path, and do
# so through pre-emulator-launch-script (after SDK installation) so sdkmanager
# cannot overwrite it.
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
real_adb="$sdk_root/platform-tools/adb"
if [[ ! -x "$real_adb" ]]; then
    echo "Expected Android platform-tools adb at $real_adb; refusing to wrap a different binary." >&2
    exit 1
fi
backup_adb="${real_adb}.thiefalert-real"
if [[ -e "$backup_adb" ]]; then
    echo "ADB backup already exists at $backup_adb; refusing to replace it." >&2
    exit 1
fi

echo "Installing ADB readiness wrapper at the SDK path used by android-emulator-runner: $real_adb"
mv "$real_adb" "$backup_adb"

cat > "$real_adb" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail

REAL_ADB="${0}.thiefalert-real"
if [[ ! -x "$REAL_ADB" ]]; then
    echo "Original Android platform-tools adb is missing: $REAL_ADB" >&2
    exit 1
fi
args=("$@")
serial=""
for ((i = 0; i < ${#args[@]} - 1; i++)); do
    if [[ "${args[i]}" == "-s" ]]; then
        serial="${args[i + 1]}"
        break
    fi
done

if [[ -z "$serial" ]]; then
    exec "$REAL_ADB" "$@"
fi

retry_adb() {
    local attempt
    for attempt in {1..12}; do
        if "$REAL_ADB" "$@"; then
            return 0
        fi
        echo "Transient ADB/service failure (attempt $attempt/12): $*" >&2
        "$REAL_ADB" reconnect offline >/dev/null 2>&1 || true
        sleep 3
    done
    echo "ADB command did not recover after 12 attempts: $*" >&2
    return 1
}

# Leave sys.boot_completed polling to android-emulator-runner. Its configured
# emulator-boot-timeout is the authoritative boot deadline; a long retry loop here
# would block the runner's own polling and can turn a missing emulator into a timeout.
case "$*" in
    *"shell input keyevent 82"*)
        input_wait_attempts="${ADB_READINESS_INPUT_ATTEMPTS:-90}"
        input_wait_interval="${ADB_READINESS_INPUT_INTERVAL_SECONDS:-2}"
        if ! [[ "$input_wait_attempts" =~ ^[1-9][0-9]*$ &&
                "$input_wait_interval" =~ ^(0|[1-9][0-9]*)$ ]]; then
            echo "Invalid ADB input-service readiness retry configuration." >&2
            exit 2
        fi

        # Require the positive service-check response exactly. A substring search
        # for "found" also matches "not found" and can release setup too early.
        for ((attempt = 1; attempt <= input_wait_attempts; attempt++)); do
            service_output="$("$REAL_ADB" -s "$serial" shell service check input 2>&1 || true)"
            service_output="${service_output//$'\r'/}"
            if [[ "$service_output" == "Service input: found" ]]; then
                retry_adb "$@"
                exit $?
            fi
            "$REAL_ADB" reconnect offline >/dev/null 2>&1 || true
            if [[ "$attempt" -lt "$input_wait_attempts" ]]; then
                sleep "$input_wait_interval"
            fi
        done
        echo "Android input service did not become ready within $input_wait_attempts checks" >&2
        exit 1
        ;;
    *"shell settings put global window_animation_scale 0"*|\
    *"shell settings put global transition_animation_scale 0"*|\
    *"shell settings put global animator_duration_scale 0"*|\
    *"shell settings put secure spell_checker_enabled 0"*)
        # These whitelisted writes are android-emulator-runner UI optimizations,
        # not test prerequisites. A missing SettingsProvider must not turn a
        # booted, otherwise usable emulator into a false-negative CI failure.
        settings_unavailable_marker="${REAL_ADB}.thiefalert-settings-unavailable"
        if [[ -e "$settings_unavailable_marker" ]]; then
            echo "Skipping optional emulator UI-tuning command; SettingsProvider was previously unavailable." >&2
            exit 0
        fi

        settings_wait_attempts="${ADB_READINESS_SETTINGS_ATTEMPTS:-30}"
        settings_wait_interval="${ADB_READINESS_SETTINGS_INTERVAL_SECONDS:-2}"
        if ! [[ "$settings_wait_attempts" =~ ^[1-9][0-9]*$ &&
                "$settings_wait_interval" =~ ^(0|[1-9][0-9]*)$ ]]; then
            echo "Invalid ADB SettingsProvider readiness retry configuration." >&2
            exit 2
        fi

        for ((attempt = 1; attempt <= settings_wait_attempts; attempt++)); do
            service_output="$("$REAL_ADB" -s "$serial" shell service check settings 2>&1 || true)"
            service_output="${service_output//$'\r'/}"
            if [[ "$service_output" == "Service settings: found" ]]; then
                retry_adb "$@"
                exit $?
            fi
            "$REAL_ADB" reconnect offline >/dev/null 2>&1 || true
            if [[ "$attempt" -lt "$settings_wait_attempts" ]]; then
                sleep "$settings_wait_interval"
            fi
        done

        : > "$settings_unavailable_marker"
        echo "::warning::Skipping optional emulator UI-tuning command; SettingsProvider did not become ready within $settings_wait_attempts checks. Instrumentation continues with default animation/spellchecker settings." >&2
        exit 0
        ;;
esac

exec "$REAL_ADB" "$@"
WRAPPER

chmod +x "$real_adb"
"$real_adb" start-server
"$real_adb" version
echo "ADB server started and in-place readiness wrapper is ready."
.thiefalert-real.thiefalert-settings-unavailable"
rm -f "$settings_marker"
: > "$FAKE_ADB_CALL_LOG"
FAKE_ADB_SETTINGS_SERVICE_STATUS='found' \
    "$wrapper" -s emulator-5554 shell settings put global window_animation_scale 0.0
grep -Fq "shell settings put global window_animation_scale 0.0" "$FAKE_ADB_CALL_LOG"

# Once readiness is confirmed, a real settings-write failure must still fail CI.
: > "$FAKE_ADB_CALL_LOG"
if FAKE_ADB_SETTINGS_SERVICE_STATUS='found' \
    FAKE_ADB_SETTINGS_PUT_FAIL=1 \
    "$wrapper" -s emulator-5554 shell settings put global window_animation_scale 0.0 \
    > "$tmp_dir/settings-write-failure.log" 2>&1; then
    echo "ADB wrapper incorrectly swallowed a real SettingsProvider write failure." >&2
    exit 1
fi
[[ "$(grep -c 'shell settings put global window_animation_scale 0.0' "$FAKE_ADB_CALL_LOG")" == 12 ]]

bash -n "$repo_root/.github/scripts/prepare-adb-readiness-wrapper.sh"
bash -n "$repo_root/.github/scripts/run-android-instrumentation.sh"
bash -n "$repo_root/scripts/test_adb_readiness_wrapper.sh"

python3 - "$repo_root/.github/workflows/android.yml" "$repo_root/.github/scripts/run-android-instrumentation.sh" "$repo_root/.github/scripts/prepare-adb-readiness-wrapper.sh" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1]).read_text(encoding="utf-8")
runner = Path(sys.argv[2]).read_text(encoding="utf-8")
wrapper = Path(sys.argv[3]).read_text(encoding="utf-8")
assert "ANDROID_I_WANT_MY_TCG: yes" in workflow, "All hosted Linux emulator jobs must explicitly allow TCG when KVM is unavailable"
assert "disable-animations: true" in workflow and "disable-spellchecker: true" in workflow, "Expected optional emulator UI tuning must remain configured"
assert '&& "$package_service" == "Service package: found"' in runner, "PackageManager readiness must require the exact positive service response"
assert '&& "$package_service" == *"found"*' not in runner, "A negative 'not found' service response must not pass readiness"
assert 'Service settings: found' in wrapper, "SettingsProvider readiness must use an exact positive service response"
assert "Skipping optional emulator UI-tuning command" in wrapper, "Unavailable optional UI tuning must not fail instrumentation setup"
print("Verified hosted-emulator TCG opt-in, strict PackageManager readiness, and optional SettingsProvider handling.")
PY

echo "ADB readiness wrapper behavior tests passed."

bash "$repo_root/scripts/test_android_instrumentation_runner.sh"
