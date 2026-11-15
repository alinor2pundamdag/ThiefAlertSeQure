#!/usr/bin/env bash
set -euo pipefail
test -n "$PREVIOUS_RELEASE_TAG"
PREVIOUS_DIR="$(mktemp -d "${TMPDIR:-/tmp}/thiefalert-previous-release.XXXXXX")"
test -d "$PREVIOUS_DIR"
gh release download "$PREVIOUS_RELEASE_TAG" --repo "$GITHUB_REPOSITORY" \
  --pattern "ThiefAlertSeQure-$PREVIOUS_RELEASE_TAG-release.apk*" --dir "$PREVIOUS_DIR"
PREVIOUS_APK="$PREVIOUS_DIR/ThiefAlertSeQure-$PREVIOUS_RELEASE_TAG-release.apk"
test -s "$PREVIOUS_APK"
EXPECTED_OLD_SHA="$(awk '{print $1}' "$PREVIOUS_DIR/ThiefAlertSeQure-$PREVIOUS_RELEASE_TAG-release.apk.sha256")"
ACTUAL_OLD_SHA="$(sha256sum "$PREVIOUS_APK" | cut -d' ' -f1)"
[[ -n "$EXPECTED_OLD_SHA" && "$ACTUAL_OLD_SHA" == "$EXPECTED_OLD_SHA" ]] || {
  echo "::error::Previous official APK checksum verification failed."
  exit 1
}
BUILD_TOOLS="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
EXPECTED_CERT="9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64"
OLD_BADGING="$("$BUILD_TOOLS/aapt" dump badging "$PREVIOUS_APK")"
OLD_PACKAGE_LINE="$(printf '%s\n' "$OLD_BADGING" | grep -m1 "^package:")"
for EXPECTED in "name='com.thiefalertsequre.app'" "versionCode='$PREVIOUS_RELEASE_VERSION_CODE'" "versionName='${PREVIOUS_RELEASE_TAG#v}'"; do
  [[ "$OLD_PACKAGE_LINE" == *"$EXPECTED"* ]] || {
    echo "::error::Previous release APK mismatch: expected $EXPECTED; got $OLD_PACKAGE_LINE"
    exit 1
  }
done
OLD_CERT="$("$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$PREVIOUS_APK" | sed -n 's/.*SHA-256 digest: //p' | head -n 1 | tr '[:upper:]' '[:lower:]')"
[[ "$OLD_CERT" == "$EXPECTED_CERT" ]] || {
  echo "::error::Previous official APK signer differs from the pinned production certificate."
  exit 1
}
TEST_APK="app/build/outputs/apk/androidTest/release/app-release-androidTest.apk"
test -s "$TEST_APK"
TEST_PACKAGE_LINE="$("$BUILD_TOOLS/aapt" dump badging "$TEST_APK" | grep -m1 "^package:")"
[[ "$TEST_PACKAGE_LINE" == *"name='com.thiefalertsequre.app.test'"* ]] || {
  echo "::error::Unexpected release instrumentation package: $TEST_PACKAGE_LINE"
  exit 1
}
TEST_CERT="$("$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$TEST_APK" | sed -n 's/.*SHA-256 digest: //p' | head -n 1 | tr '[:upper:]' '[:lower:]')"
if [[ "$TEST_CERT" != "$EXPECTED_CERT" ]]; then
  echo "Re-signing the private regression test APK with the verified production certificate."
  UNSIGNED_TEST_APK="$PREVIOUS_DIR/release-test-unsigned.apk"
  ALIGNED_TEST_APK="$PREVIOUS_DIR/release-test-aligned.apk"
  SIGNED_TEST_APK="$PREVIOUS_DIR/release-test-signed.apk"
  cp "$TEST_APK" "$UNSIGNED_TEST_APK"
  zip -q -d "$UNSIGNED_TEST_APK" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC' || true
  "$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED_TEST_APK" "$ALIGNED_TEST_APK"
  "$BUILD_TOOLS/apksigner" sign --ks "$ANDROID_KEYSTORE_PATH" --ks-key-alias "$ANDROID_KEY_ALIAS" \
    --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD \
    --out "$SIGNED_TEST_APK" "$ALIGNED_TEST_APK"
  cp "$SIGNED_TEST_APK" "$TEST_APK"
  TEST_CERT="$("$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$TEST_APK" | sed -n 's/.*SHA-256 digest: //p' | head -n 1 | tr '[:upper:]' '[:lower:]')"
fi
[[ "$TEST_CERT" == "$EXPECTED_CERT" ]] || {
  echo "::error::Unable to sign release instrumentation APK with the official certificate."
  exit 1
}
adb install "$PREVIOUS_APK"
adb install -t "$TEST_APK"
TEST_RUNNER="com.thiefalertsequre.app.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.thiefalertsequre.app.UpgradeDataPreservationTest#upgradeDataCanBeSeededOrVerifiedAcrossPackageReplacement"
adb shell am instrument -w -r -e class "$TEST_CLASS" -e upgradePhase seed "$TEST_RUNNER"
adb install -r "$APK_PATH"
adb shell am instrument -w -r -e class "$TEST_CLASS" -e upgradePhase verify "$TEST_RUNNER"
NEW_BADGING="$("$BUILD_TOOLS/aapt" dump badging "$APK_PATH")"
NEW_PACKAGE_LINE="$(printf '%s\n' "$NEW_BADGING" | grep -m1 "^package:")"
for EXPECTED in "name='com.thiefalertsequre.app'" "versionCode='$APP_VERSION_CODE'" "versionName='$APP_VERSION_NAME'"; do
  [[ "$NEW_PACKAGE_LINE" == *"$EXPECTED"* ]] || {
    echo "::error::Candidate APK mismatch: expected $EXPECTED; got $NEW_PACKAGE_LINE"
    exit 1
  }
done
echo "Verified in-place upgrade: $OLD_PACKAGE_LINE -> $NEW_PACKAGE_LINE"
echo "Verified app-private SharedPreferences survived adb install -r without uninstall."
