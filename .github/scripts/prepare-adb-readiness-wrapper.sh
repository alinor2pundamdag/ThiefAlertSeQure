#!/usr/bin/env bash
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
    *"shell settings put"*)
        # SettingsProvider may restart briefly after a slow emulator boot.
        for attempt in {1..90}; do
            if "$REAL_ADB" -s "$serial" shell settings get global window_animation_scale >/dev/null 2>&1; then
                retry_adb "$@"
                exit $?
            fi
            "$REAL_ADB" reconnect offline >/dev/null 2>&1 || true
            sleep 2
        done
        echo "Android settings provider did not become ready within 180 seconds" >&2
        exit 1
        ;;
esac

exec "$REAL_ADB" "$@"
WRAPPER

chmod +x "$real_adb"
"$real_adb" start-server
"$real_adb" version
echo "ADB server started and in-place readiness wrapper is ready."
