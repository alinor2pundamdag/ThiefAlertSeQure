#!/usr/bin/env python3
"""Guard SharedPreferences typed reads while ignoring unrelated JSON/Android getters."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java"
# Typed preference access is centralized in SafePreferences. These files contain
# narrowly reviewed Android/framework reads that share the same method names:
# ManagedProtectionController validates every persisted rollback field before
# restoration; AlarmSoundStore reads a ContentResolver Cursor; ProtectionClock
# reads Settings.Global, not SharedPreferences.
ALLOWLIST = {
    "SafePreferences.kt",
    "ManagedProtectionController.kt",
    "AlarmSoundStore.kt",
    "ProtectionClock.kt",
}
# Match conventional SharedPreferences receiver names and direct
# getSharedPreferences(...).getX(...) chains. Do not match JSONObject/JSONArray
# accessors such as item.getString(), item.getLong(), or item.getInt().
GETTER = r"get(?:String|Boolean|Int|Long|Float|StringSet)"
PREFERENCE_READ = re.compile(
    rf"(?:\b(?:prefs|preferences|sharedPrefs|sharedPreferences)\s*\.\s*{GETTER}\s*\("
    rf"|\bgetSharedPreferences\s*\([^\n]*\)\s*\.\s*{GETTER}\s*\()"
)
errors = []
checked = 0

for path in sorted(JAVA.rglob("*.kt")):
    if path.name in ALLOWLIST:
        continue
    checked += 1
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if PREFERENCE_READ.search(line):
            errors.append(f"{path.relative_to(ROOT)}:{number}: direct typed preference getter; use SafePreferences")

if errors:
    print("\n".join("ERROR: " + item for item in errors))
    sys.exit(1)

print(f"Verified typed SharedPreferences reads are centralized across {checked} Kotlin source files.")
