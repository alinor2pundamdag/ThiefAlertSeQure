#!/usr/bin/env bash
set -euo pipefail

real_adb="$(command -v adb || true)"
if [[ -z "$real_adb" ]]; then
    sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
    for candidate in "$sdk_root/platform-tools/adb" \
        /usr/local/lib/android/sdk/platform-tools/adb \
        /opt/android-sdk/platform-tools/adb \
        "$HOME/Android/Sdk/platform-tools/adb"; do
        if [[ -x "$candidate" ]]; then
            real_adb="$candidate"
            break
        fi
    done
fi
if [[ -z "$real_adb" || ! -x "$real_adb" ]]; then
    echo "Android platform-tools adb was not found on this runner" >&2
    exit 1
fi
echo "Using Android platform-tools: $real_adb"
# Start the ADB server before the emulator process registers its transport.
# This avoids a known startup race where the emulator remains permanently offline.
echo "Starting ADB server before emulator launch."
"$real_adb" start-server
wrapper_dir="${RUNNER_TEMP:-/tmp}/thiefalert-adb-readiness"
mkdir -p "$wrapper_dir"

cat > "$wrapper_dir/adb" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail

REAL_ADB="${THIEFALERT_REAL_ADB:?THIEFALERT_REAL_ADB was not exported by the preparation step}"
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

case "$*" in
    *"shell input keyevent 82"*)
        # sys.boot_completed can precede InputManager readiness on cold API 30+ images.
        for attempt in {1..90}; do
            if "$REAL_ADB" -s "$serial" shell service check input 2>&1 | grep -q "found"; then
                retry_adb "$@"
                exit $?
            fi
            "$REAL_ADB" reconnect offline >/dev/null 2>&1 || true
            sleep 2
        done
        echo "Android input service did not become ready within 180 seconds" >&2
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

chmod +x "$wrapper_dir/adb"
echo "THIEFALERT_REAL_ADB=$real_adb" >> "$GITHUB_ENV"
echo "$wrapper_dir" >> "$GITHUB_PATH"
