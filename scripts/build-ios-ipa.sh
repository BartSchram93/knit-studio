#!/usr/bin/env bash
# Builds Knit Studio for iPhone and packages an UNSIGNED .ipa for sideloading with AltStore.
#
# AltStore signs the app on your iPhone with your own (free) Apple ID, so no certificates,
# provisioning profiles or Apple credentials are needed here. The script then checks that the
# IPA has the structure AltStore expects and that the bundle identifier has not changed —
# a changed identifier would install as a separate app and leave your patterns behind.
#
# Usage (macOS with Xcode):  scripts/build-ios-ipa.sh [build-number] [output-dir]
set -euo pipefail

cd "$(dirname "$0")/.."

# The identifier Knit Studio has on every iPhone. Changing it turns an update into a new,
# empty app. Never change it.
EXPECTED_BUNDLE_ID="studio.knit.app"

BUILD_NUMBER="${1:-${GITHUB_RUN_NUMBER:-1}}"
OUT_DIR="${2:-dist}"
# One version for both platforms: taken from the Android build file.
VERSION="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' androidApp/build.gradle.kts | head -1)"
[ -n "$VERSION" ] || { echo "error: could not read versionName from androidApp/build.gradle.kts"; exit 1; }
# Every iPhone build gets its own version, major.minor.<build>, so AltStore can tell a newer
# build from an older one and offer it as an update (it compares version numbers).
VERSION_BASE="$(echo "$VERSION" | cut -d. -f1-2)"
IOS_VERSION="$VERSION_BASE.$BUILD_NUMBER"

CONFIGURED_ID="$(sed -n 's/^BUNDLE_ID *= *//p' iosApp/Configuration/Config.xcconfig | tr -d '[:space:]')"
if [ "$CONFIGURED_ID" != "$EXPECTED_BUNDLE_ID" ]; then
  echo "error: Config.xcconfig has BUNDLE_ID=$CONFIGURED_ID but Knit Studio must stay $EXPECTED_BUNDLE_ID"
  echo "       (changing it would make the next install a separate app without your data)"
  exit 1
fi

DERIVED="build/ios"
APP="$DERIVED/Build/Products/Release-iphoneos/KnitStudio.app"
echo "Building Knit Studio $IOS_VERSION (build $BUILD_NUMBER) for iPhone, unsigned…"

xcodebuild \
  -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath "$DERIVED" \
  MARKETING_VERSION="$IOS_VERSION" CURRENT_PROJECT_VERSION="$BUILD_NUMBER" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" CODE_SIGN_ENTITLEMENTS="" \
  build

# --- Package ----------------------------------------------------------------------------------
STAGE="build/ipa"
rm -rf "$STAGE"
mkdir -p "$STAGE/Payload" "$OUT_DIR"
cp -R "$APP" "$STAGE/Payload/"
NAME="KnitStudio-$IOS_VERSION.ipa"
# An .ipa is a zip with Payload/<App>.app at its root; -y keeps symlinks intact.
(cd "$STAGE" && zip -qry "$NAME" Payload)
mv "$STAGE/$NAME" "$OUT_DIR/$NAME"
cp "$OUT_DIR/$NAME" "$OUT_DIR/KnitStudio.ipa"

# --- Check what AltStore will receive ----------------------------------------------------------
PLIST="$APP/Info.plist"
read_plist() { /usr/libexec/PlistBuddy -c "Print :$1" "$PLIST" 2>/dev/null || echo ""; }
fail=0
check() { if [ "$2" = "ok" ]; then echo "  ✓ $1"; else echo "  ✗ $1"; fail=1; fi; }

BUNDLE_ID="$(read_plist CFBundleIdentifier)"
EXE="$(read_plist CFBundleExecutable)"
SHORT="$(read_plist CFBundleShortVersionString)"
BUILD="$(read_plist CFBundleVersion)"
MIN_OS="$(read_plist MinimumOSVersion)"
DISPLAY="$(read_plist CFBundleDisplayName)"
ARCHS="$(lipo -archs "$APP/$EXE" 2>/dev/null || echo "?")"
SIZE="$(du -h "$OUT_DIR/$NAME" | cut -f1)"

echo
echo "IPA checks"
check "bundle identifier is $EXPECTED_BUNDLE_ID (found: $BUNDLE_ID)" "$([ "$BUNDLE_ID" = "$EXPECTED_BUNDLE_ID" ] && echo ok)"
check "version $SHORT, build $BUILD" "$([ "$SHORT" = "$IOS_VERSION" ] && [ "$BUILD" = "$BUILD_NUMBER" ] && echo ok)"
check "display name is plain ASCII ($DISPLAY) — AltStore rejects other names" "$(printf '%s' "$DISPLAY" | LC_ALL=C grep -q '[^ -~]' || echo ok)"
check "executable $EXE present and built for arm64 ($ARCHS)" "$([ -f "$APP/$EXE" ] && [[ "$ARCHS" == *arm64* ]] && echo ok)"
check "Payload/KnitStudio.app at the root of the IPA" "$(unzip -l "$OUT_DIR/$NAME" | grep -q ' Payload/KnitStudio.app/Info.plist$' && echo ok)"
check "no app extensions (each would use one of the 10 free App IDs)" "$([ ! -d "$APP/PlugIns" ] && [ ! -d "$APP/Extensions" ] && echo ok)"
check "no embedded provisioning profile (AltStore supplies its own)" "$([ ! -f "$APP/embedded.mobileprovision" ] && echo ok)"
check "no entitlements requiring a paid account (iCloud, push, app groups)" "$( (codesign -d --entitlements :- "$APP" 2>/dev/null || true) | grep -qE 'icloud|aps-environment|application-groups' || echo ok)"
ICON="$(ls "$APP"/AppIcon*.png 2>/dev/null | head -1 | xargs -I{} basename {} || true)"
echo "  · app icon: ${ICON:-$( [ -f "$APP/Assets.car" ] && echo "in Assets.car" || echo "not found (the app works, but shows a blank icon)")}"
EMBEDDED="$(find "$APP" -maxdepth 2 \( -name '*.framework' -o -name '*.dylib' \) | sed "s|$APP/||" | tr '\n' ' ')"
echo "  · embedded frameworks: ${EMBEDDED:-none (Kotlin code is linked statically)}"
echo "  · minimum iOS: $MIN_OS · size: $SIZE"

# A small report that travels with the artifact.
cat > "$OUT_DIR/ipa-info.txt" <<INFO
Knit Studio for iPhone (unsigned, for AltStore)
file:           $NAME
version:        $SHORT (build $BUILD)
bundle id:      $BUNDLE_ID
minimum iOS:    $MIN_OS
architectures:  $ARCHS
size:           $SIZE
commit:         ${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}
built:          $(date -u +%Y-%m-%dT%H:%MZ)
INFO

# Machine-readable facts for the AltStore source (scripts/make-altstore-source.py).
cat > "$OUT_DIR/ipa-meta.json" <<META
{"file": "$NAME", "version": "$SHORT", "build": "$BUILD", "bundleId": "$BUNDLE_ID", "minOS": "$MIN_OS", "size": $(stat -f%z "$OUT_DIR/$NAME" 2>/dev/null || stat -c%s "$OUT_DIR/$NAME")}
META

if [ "$fail" -ne 0 ]; then
  echo "error: the IPA failed one or more checks above"
  exit 1
fi
echo
echo "Created $OUT_DIR/$NAME (also copied to $OUT_DIR/KnitStudio.ipa)"
