#!/usr/bin/env python3
"""Verify full versioned legal disclosures are present in the APK being built."""
from pathlib import Path
import re
import sys
import zipfile

if len(sys.argv) != 2:
    sys.exit("Usage: verify_packaged_legal_assets.py <apk-path>")
apk_path = Path(sys.argv[1])
if not apk_path.is_file() or apk_path.stat().st_size <= 0:
    sys.exit(f"APK is missing or empty: {apk_path}")

ROOT = Path(__file__).resolve().parents[1]
policy = (ROOT / "app/src/main/java/com/thiefalertsequre/app/PolicyAcknowledgementPolicy.kt").read_text(encoding="utf-8")
privacy_version = int(re.search(r"PRIVACY_VERSION\s*=\s*(\d+)", policy).group(1))
terms_version = int(re.search(r"TERMS_VERSION\s*=\s*(\d+)", policy).group(1))

required_assets = {
    "assets/legal/privacy_policy.txt": f"Privacy Notice version: {privacy_version}",
    "assets/legal/terms_of_use.txt": f"Terms version: {terms_version}",
    "assets/licenses/Apache-2.0.txt": "END OF TERMS AND CONDITIONS",
}
with zipfile.ZipFile(apk_path) as apk:
    bad_entry = apk.testzip()
    if bad_entry is not None:
        sys.exit(f"APK ZIP integrity failed at: {bad_entry}")
    names = set(apk.namelist())
    for asset, marker in required_assets.items():
        if asset not in names:
            sys.exit(f"Packaged APK is missing required legal asset: {asset}")
        content = apk.read(asset).decode("utf-8")
        if marker not in content:
            sys.exit(f"Packaged legal asset has stale or incomplete content: {asset}")
        print(f"PASS: {asset}")
print(f"Verified {len(required_assets)} legal assets inside {apk_path.name}.")
