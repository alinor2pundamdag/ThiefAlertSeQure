# Temporary CI signing identity verification

This branch exists only to verify that a pull-request build targeting `feat/custom-audio-and-reliability` restores the same cached Android debug keystore used by the v1.1.2 push build. Do not merge this diagnostic branch. Close its pull request after comparing the APK signer certificate SHA-256 with the v1.1.2 baseline.

The base-branch cache restore order is now corrected; this run verifies whether the cached v1.1.2 signing identity is preserved.

This diagnostic build uses the temporary application ID `com.thiefalertsequre.app.v113test` so it can be installed beside v1.1.2 without uninstalling or overwriting its data. It is for physical testing only and is not the production package identity.
