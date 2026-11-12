#!/usr/bin/env python3
"""Static contract checks for app-internal Activity navigation and Material dialogs."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"

manifest = ET.parse(MANIFEST).getroot()
declared = {
    element.get(ANDROID + "name", "").rsplit(".", 1)[-1]
    for element in manifest.findall("application/activity")
}
sources = list(JAVA.rglob("*.kt"))
defined = set()
targets = set()
dialog_errors = []

class_pattern = re.compile(r"\bclass\s+([A-Z][A-Za-z0-9_]*Activity)\b")
target_pattern = re.compile(r"\b([A-Z][A-Za-z0-9_]*Activity)::class\.java")
builder_pattern = re.compile(r"MaterialAlertDialogBuilder\s*\(")
themed_pattern = re.compile(r"\.showThemed\s*\(")

for path in sources:
    text = path.read_text(encoding="utf-8")
    defined.update(class_pattern.findall(text))
    targets.update(target_pattern.findall(text))
    builders = len(builder_pattern.findall(text))
    themed = len(themed_pattern.findall(text))
    if "fun MaterialAlertDialogBuilder.showThemed" in text:
        themed -= 1  # Exclude the shared extension declaration itself.
    if builders != themed:
        dialog_errors.append(
            f"{path.relative_to(ROOT)}: {builders} MaterialAlertDialogBuilder calls, "
            f"but {themed} themed show calls"
        )

missing_declarations = sorted(targets - declared)
missing_sources = sorted(declared - defined)
errors = []
if missing_declarations:
    errors.append("Internal navigation targets missing from manifest: " + ", ".join(missing_declarations))
if missing_sources:
    errors.append("Manifest Activities missing Kotlin classes: " + ", ".join(missing_sources))
errors.extend(dialog_errors)

if errors:
    print("\n".join("ERROR: " + item for item in errors))
    sys.exit(1)

print(f"Verified {len(declared)} declared Activities, {len(targets)} internal navigation targets, "
      "and themed Material dialogs in all affected screens.")
