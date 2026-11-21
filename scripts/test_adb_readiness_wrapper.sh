#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

sdk_root="$tmp_dir/sdk"
mkdir -p "$sdk_root/platform-tools"
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
    "shell input keyevent 82")
        printf 'input-keyevent\n' >> "${FAKE_ADB_KEYEVENT_LOG:?}"
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

bash -n "$repo_root/.github/scripts/prepare-adb-readiness-wrapper.sh"
bash -n "$repo_root/.github/scripts/run-android-instrumentation.sh"
bash -n "$repo_root/scripts/test_adb_readiness_wrapper.sh"

python3 - "$repo_root/.github/workflows/android.yml" "$repo_root/.github/scripts/run-android-instrumentation.sh" <<'PY'
from pathlib import Path
import sys

workflow = Path(sys.argv[1]).read_text(encoding="utf-8")
runner = Path(sys.argv[2]).read_text(encoding="utf-8")
assert "ANDROID_I_WANT_MY_TCG: yes" in workflow, "All hosted Linux emulator jobs must explicitly allow TCG when KVM is unavailable"
assert '&& "$package_service" == "Service package: found"' in runner, "PackageManager readiness must require the exact positive service response"
assert '&& "$package_service" == *"found"*' not in runner, "A negative 'not found' service response must not pass readiness"
print("Verified hosted-emulator TCG opt-in and strict PackageManager readiness contract.")
PY

echo "ADB readiness wrapper behavior tests passed."
