#!/usr/bin/env python3
"""Enforce one owner for persisted protection-state construction and transitions."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
CONSTRUCTOR = re.compile(r"\bProtectionState\s*\(")
ALLOWED = {
    "ProtectionEngine.kt",  # Pure authoritative state-transition rules.
    "ProtectionState.kt",   # State model and persisted-state decoder.
    "ProtectionStore.kt",   # Fail-closed state used for corrupt persistence.
}
errors = []
checked = 0

for path in sorted(JAVA.rglob("*.kt")):
    if path.name in ALLOWED:
        continue
    checked += 1
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if CONSTRUCTOR.search(line):
            errors.append(
                f"{path.relative_to(ROOT)}:{number}: direct ProtectionState construction; "
                "route the transition through ProtectionEngine"
            )

if errors:
    print("\n".join("ERROR: " + item for item in errors))
    sys.exit(1)

print(
    f"Verified ProtectionEngine/ProtectionState/ProtectionStore own state construction "
    f"across {checked} other Kotlin source files."
)
