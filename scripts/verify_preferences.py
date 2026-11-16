#!/usr/bin/env python3
"""Guard the SharedPreferences typed-read boundary against unsafe direct reads."""
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
TYPED_GETTER = re.compile(r"\.\s*get(?:String|Boolean|Int|Long|Float|StringSet)\s*\(")
errors = []
checked = 0

for path in sorted(JAVA.rglob("*.kt")):
    if path.name in ALLOWLIST:
        continue
    checked += 1
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if TYPED_GETTER.search(line):
            errors.append(f"{path.relative_to(ROOT)}:{number}: direct typed getter; use SafePreferences")

if errors:
    print("\n".join("ERROR: " + item for item in errors))
    sys.exit(1)

print(f"Verified typed SharedPreferences reads are centralized across {checked} Kotlin source files.")
