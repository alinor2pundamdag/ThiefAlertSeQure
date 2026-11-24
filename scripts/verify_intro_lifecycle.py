#!/usr/bin/env python3
"""Guard the Seismic Genesis intro against background execution and audio leaks."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/thiefalertsequre/app/SeismicGenesisIntroActivity.kt"
source = SOURCE.read_text(encoding="utf-8")

checks = {
    "animation pauses when the Activity stops": "override fun onStop()" in source and "animator?.pause()" in source,
    "animation resumes when the Activity becomes visible again": "override fun onStart()" in source and "animator?.resume()" in source,
    "replay close action has an accurate accessibility description": "if (replay) \"Close the introduction and return to ThiefAlertSeQure\"" in source,
    "intro audio is released on stop": "override fun onStop()" in source and "releaseIntroAudio()" in source,
    "pending tone-release callback is cancelled before manual release": "toneReleaseRunnable?.let(mainHandler::removeCallbacks)" in source,
    "ToneGenerator reference is cleared before release": "toneGenerator = null" in source and "runCatching { generator.release() }" in source,
    "Activity destruction cancels all callbacks owned by its Handler": "mainHandler.removeCallbacksAndMessages(null)" in source,
    "tone release is tracked rather than using an anonymous Handler callback": "toneReleaseRunnable = release" in source and "mainHandler.postDelayed(release, 180L)" in source,
    "untracked per-call Handler callback is absent": "Handler(Looper.getMainLooper()).postDelayed" not in source,
}
failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + name)
if failed:
    sys.exit("Intro lifecycle contract failed: " + "; ".join(failed))
print(f"Verified {len(checks)} intro lifecycle and resource-cleanup contracts.")
