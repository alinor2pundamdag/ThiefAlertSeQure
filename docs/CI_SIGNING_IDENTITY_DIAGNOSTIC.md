# Temporary CI signing identity verification

This branch exists only to verify that a pull-request build targeting `feat/custom-audio-and-reliability` restores the same cached Android debug keystore used by the v1.1.2 push build. Do not merge this diagnostic branch. Close its pull request after comparing the APK signer certificate SHA-256 with the v1.1.2 baseline.
