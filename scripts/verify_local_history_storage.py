#!/usr/bin/env python3
"""Guard encrypted local-history migration against residual plaintext copies."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/thiefalertsequre/app/LocalTamperHistory.kt"
source = SOURCE.read_text(encoding="utf-8")

checks = {
    "new encrypted history is committed before legacy plaintext cleanup":
        source.index('prefs.edit().putString(KEY_EVENTS, encrypted).commit()')
        < source.index("if (!clearLegacyPlaintext(app)) return false"),
    "record refuses success when legacy plaintext cleanup fails":
        "if (!clearLegacyPlaintext(app)) return false" in source,
    "record does not enqueue evidence archive before plaintext cleanup succeeds":
        source.index("if (!clearLegacyPlaintext(app)) return false")
        < source.index("if (writeEvidenceArchive) LocalEvidenceVault.recordAsync"),
    "existing encrypted history retries legacy plaintext cleanup before becoming healthy":
        "if (!clearLegacyPlaintext(context)) return HistoryRead(\"\", false)" in source,
    "first-use migration writes encrypted data before deleting legacy plaintext":
        source.index("if (!prefs.edit().putString(KEY_EVENTS, encrypted).commit()) return HistoryRead(\"\", false)")
        < source.index("if (!clearLegacyPlaintext(context)) return HistoryRead(\"\", false)"),
    "legacy cleanup checks the synchronous commit result":
        "val removed = legacyPrefs.edit().remove(LEGACY_KEY_EVENTS).commit()" in source,
    "legacy cleanup verifies the plaintext key is actually absent":
        "return removed && !legacyPrefs.contains(LEGACY_KEY_EVENTS)" in source,
    "cleanup failure leaves the history unhealthy rather than exposing plaintext":
        source.count('return HistoryRead("", false)') >= 3,
    "explicit clear removes both encrypted and legacy history":
        "remove(KEY_EVENTS).commit()" in source and "remove(LEGACY_KEY_EVENTS).commit()" in source,
}
failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + name)
if failed:
    sys.exit("Local history storage contract failed: " + "; ".join(failed))
print(f"Verified {len(checks)} encrypted-history migration and deletion contracts.")
