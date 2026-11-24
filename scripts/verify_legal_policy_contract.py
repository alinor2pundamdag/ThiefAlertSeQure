#!/usr/bin/env python3
"""Keep in-app legal disclosures, policy versions and public documentation aligned."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
policy = (ROOT / "app/src/main/java/com/thiefalertsequre/app/PolicyAcknowledgementPolicy.kt").read_text(encoding="utf-8")
main = (ROOT / "app/src/main/java/com/thiefalertsequre/app/MainActivity.kt").read_text(encoding="utf-8")
about = (ROOT / "app/src/main/java/com/thiefalertsequre/app/AboutActivity.kt").read_text(encoding="utf-8")
viewer = (ROOT / "app/src/main/java/com/thiefalertsequre/app/LegalDocumentViewer.kt").read_text(encoding="utf-8")
readme = (ROOT / "README.md").read_text(encoding="utf-8")
privacy_doc = (ROOT / "docs/PRIVACY_POLICY.md").read_text(encoding="utf-8")
terms_doc = (ROOT / "docs/TERMS_OF_USE.md").read_text(encoding="utf-8")
privacy_asset = (ROOT / "app/src/main/assets/legal/privacy_policy.txt").read_text(encoding="utf-8")
terms_asset = (ROOT / "app/src/main/assets/legal/terms_of_use.txt").read_text(encoding="utf-8")

privacy_version = int(re.search(r"PRIVACY_VERSION\s*=\s*(\d+)", policy).group(1))
terms_version = int(re.search(r"TERMS_VERSION\s*=\s*(\d+)", policy).group(1))

checks = {
    "privacy policy asset carries the current app privacy version":
        f"Privacy Notice version: {privacy_version}" in privacy_asset,
    "terms asset carries the current app terms version":
        f"Terms version: {terms_version}" in terms_asset,
    "privacy asset and repository policy agree on the effective date":
        "Effective date: 17 November 2026" in privacy_doc and
        "Effective date: 17 November 2026" in privacy_asset,
    "first-launch screen exposes the full privacy document":
        'LegalDocumentViewer.show(' in main and '"legal/privacy_policy.txt"' in main,
    "first-launch screen exposes the full terms document":
        '"legal/terms_of_use.txt"' in main,
    "first-launch acknowledgement persists both policy versions separately":
        "PolicyAcknowledgementPolicy.PRIVACY_PREFS" in main and
        "PolicyAcknowledgementPolicy.TERMS_PREFS" in main and
        "privacySaved && termsSaved" in main,
    "first launch requires both current policy versions":
        "!privacyAcknowledged || !termsAcknowledged" in main,
    "About screen provides full privacy and terms documents":
        '"legal/privacy_policy.txt"' in about and '"legal/terms_of_use.txt"' in about,
    "About terms acknowledgement is saved only after explicit confirmation":
        'acknowledgeLabel = "I acknowledge"' in about and
        'putInt("version", PolicyAcknowledgementPolicy.TERMS_VERSION)' in about,
    "shared legal viewer fails closed when a bundled document is missing":
        "The selected legal document is unavailable" in viewer,
    "legal document viewer bounds scroll height on small screens":
        "heightPixels * 0.65f" in viewer and "dp(520)" in viewer,
    "README accurately describes both first-launch acknowledgements":
        "records both acknowledgements in separate versioned app-private preferences" in readme,
    "repository privacy policy matches current in-app privacy version":
        f"Privacy Notice version {privacy_version}" in privacy_doc,
    "repository terms document matches current in-app terms version":
        f"version:** {terms_version}" in terms_doc,
}
failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + name)
if failed:
    sys.exit("Legal policy integration contract failed: " + "; ".join(failed))
print(f"Verified {len(checks)} legal document, UI and acknowledgement contracts.")
