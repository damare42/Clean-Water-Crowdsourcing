#!/usr/bin/env bash
# Builds the desktop installers for the current OS with jlink + jpackage.
#
#   tools/package.sh <version>        e.g. tools/package.sh 1.0.0
#
# Run from the repository root with a JDK 21 (jlink/jpackage on PATH or in $JAVA_HOME/bin).
# Linux needs fakeroot for the .deb; Windows needs Git Bash and the WiX Toolset 3 for the .msi.
# Output: cwc-desktop/target/dist/  (installers, portable archive)
#   Linux:   clean-water_<v>_amd64.deb, CleanWater-<v>-linux-x64.tar.gz
#   macOS:   Clean Water-<v>.dmg (renamed CleanWater-<v>-macos-<arch>.dmg), CleanWater-<v>-macos-<arch>.zip
#   Windows: Clean Water-<v>.msi (renamed CleanWater-<v>-windows-x64.msi), CleanWater-<v>-windows-x64.zip
#
# How JavaFX gets into the runtime: the JavaFX jars from Maven (platform classifier,
# chosen by Maven for the build OS) are modular jars, so jlink links them into the runtime.
# jlink does not copy the native libraries inside those jars to the runtime's library
# folder, so this script extracts them there itself; without that, JavaFX would unpack them
# into ~/.openjfx/cache on first start. The media natives (GStreamer, FFmpeg plugins) are
# left out: the app plays no audio or video, and the FFmpeg plugins would make the .deb
# depend on one Ubuntu release's FFmpeg libraries.
set -euo pipefail

VERSION="${1:?usage: tools/package.sh <version, e.g. 1.0.0>}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must be X.Y.Z, got $VERSION" >&2; exit 2; }

ROOT="$(pwd)"
D="$ROOT/cwc-desktop"
WORK="$D/target/package"
DIST="$D/target/dist"
PKG="$D/src/packaging"
JBIN=""
if [ -n "${JAVA_HOME:-}" ]; then
  JH="$JAVA_HOME"
  if command -v cygpath >/dev/null 2>&1; then JH="$(cygpath -u "$JH")"; fi   # Windows: C:\... -> /c/...
  JBIN="$JH/bin/"
fi
APP_NAME="Clean Water"
VENDOR="Clean Water Crowdsourcing contributors"
COPYRIGHT="Copyright (c) 2016-2026 Clean Water Crowdsourcing contributors"
DESCRIPTION="Crowdsourced map of water sources and water quality reports"
MAIN_CLASS="fxapp.Main"

case "$(uname -s)" in
  Linux*)  OS=linux;   NAT='*.so';    NATDIR=lib; APP_NAME="CleanWater" ;;
  Darwin*) OS=macos;   NAT='*.dylib'; NATDIR=lib ;;
  # JavaFX looks in java.home/bin/javafx on Windows (lib elsewhere) when it runs from
  # a jlinked runtime (NativeLibLoader.libDirForJRT).
  MINGW*|MSYS*|CYGWIN*) OS=windows; NAT='*.dll'; NATDIR=bin/javafx ;;
  *) echo "unsupported OS $(uname -s)" >&2; exit 2 ;;
esac
case "$(uname -m)" in
  x86_64|amd64) ARCH=x64 ;;
  arm64|aarch64) ARCH=arm64 ;;
  *) ARCH="$(uname -m)" ;;
esac
BASE="CleanWater-$VERSION-$OS-$ARCH"
MAC_OPTS=()
if [ "$OS" = macos ]; then
  # macOS bundle versions (CFBundleShortVersionString) must not start with 0; jpackage rejects them.
  [ "${VERSION%%.*}" -ge 1 ] || { echo "macOS needs a version >= 1.0.0, got $VERSION" >&2; exit 2; }
  MAC_OPTS=(--mac-package-identifier org.cleanwater.desktop --mac-package-name "Clean Water")
fi

rm -rf "$WORK" "$DIST"
mkdir -p "$WORK/deps" "$WORK/input" "$WORK/javafx" "$WORK/natives" "$DIST"

# 1. Build the jars with the release version and collect the runtime dependencies.
./mvnw -B -q -Drevision="$VERSION" -pl cwc-desktop -am package -DskipTests \
  dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$WORK/deps"
cp "$D/target/cwc-desktop-$VERSION.jar" "$WORK/input/"
for j in "$WORK"/deps/*.jar; do
  name="$(basename "$j")"
  case "$name" in
    javafx-*-linux.jar|javafx-*-mac*.jar|javafx-*-win*.jar)
      cp "$j" "$WORK/javafx/" ;;                                  # platform jars: module path
    javafx-*) ;;                                                  # empty placeholder jars: skip
    *) cp "$j" "$WORK/input/" ;;                                  # everything else: class path
  esac
done
ls "$WORK"/javafx/javafx-graphics-*.jar >/dev/null    # fail early if Maven picked no platform jars
cp "$ROOT/LICENSE" "$ROOT/THIRD-PARTY-NOTICES.md" "$WORK/input/"
# Every shipped third-party jar must be listed in THIRD-PARTY-NOTICES.md by its exact artifact
# name in backticks (e.g. `sqlite-jdbc`), so a partial name can't satisfy the check.
for j in "$WORK"/input/*.jar "$WORK"/javafx/*.jar; do
  artifact="$(basename "$j" | sed -E 's/-[0-9][0-9.]*(-[a-z0-9-]+)?\.jar$//')"
  case "$artifact" in cwc-*) continue ;; esac
  grep -qF -- "\`$artifact\`" "$ROOT/THIRD-PARTY-NOTICES.md" || { echo "THIRD-PARTY-NOTICES.md does not mention $artifact" >&2; exit 1; }
done

# 2. Runtime: JDK modules the app needs (jdeps) + JavaFX; natives into the runtime.
"${JBIN}jlink" --module-path "$WORK/javafx" \
  --add-modules javafx.controls,javafx.fxml,javafx.web,java.sql,java.net.http,java.logging,java.scripting,jdk.jsobject,jdk.xml.dom,jdk.crypto.ec,jdk.charsets,jdk.unsupported,jdk.localedata \
  --include-locales=en --strip-debug --strip-native-commands --no-header-files --no-man-pages \
  --compress=zip-6 --output "$WORK/runtime"
# (jar from the JDK, not unzip: unzip is not on every Windows runner)
for j in "$WORK"/javafx/*.jar; do
  mkdir -p "$WORK/unpacked" && (cd "$WORK/unpacked" && "${JBIN}jar" xf "$j")
done
find "$WORK/unpacked" -maxdepth 1 -type f -name "$NAT" -exec cp {} "$WORK/natives/" \;
rm -f "$WORK"/natives/*avplugin* "$WORK"/natives/*gstreamer* "$WORK"/natives/*jfxmedia* \
      "$WORK"/natives/*fxplugins* "$WORK"/natives/*glib-lite*
mkdir -p "$WORK/runtime/$NATDIR"
cp "$WORK"/natives/* "$WORK/runtime/$NATDIR/"
mkdir -p "$WORK/runtime/legal/openjfx"
cp -R "$PKG"/legal/openjfx/. "$WORK/runtime/legal/openjfx/"

# 3. Application image, then the OS installers.
COMMON=(--name "$APP_NAME" --app-version "$VERSION" --vendor "$VENDOR" --copyright "$COPYRIGHT"
        --description "$DESCRIPTION")
case "$OS" in
  linux)   ICON="$PKG/icons/cleanwater-256.png" ;;
  macos)   ICON="$PKG/icons/cleanwater.icns" ;;
  windows) ICON="$PKG/icons/cleanwater.ico" ;;
esac
"${JBIN}jpackage" --type app-image "${COMMON[@]}" --icon "$ICON" \
  --input "$WORK/input" --main-jar "cwc-desktop-$VERSION.jar" --main-class "$MAIN_CLASS" \
  --runtime-image "$WORK/runtime" --dest "$WORK/image" ${MAC_OPTS[@]+"${MAC_OPTS[@]}"}

case "$OS" in
  linux)
    "${JBIN}jpackage" --type deb "${COMMON[@]}" --app-image "$WORK/image/$APP_NAME" \
      --linux-package-name clean-water --linux-deb-maintainer maintainers@cleanwater.invalid \
      --linux-menu-group Education --linux-app-category misc --linux-shortcut \
      --license-file "$ROOT/LICENSE" --dest "$DIST"
    tar -C "$WORK/image" -czf "$DIST/$BASE.tar.gz" "$APP_NAME"
    ;;
  macos)
    APP="$WORK/image/$APP_NAME.app"
    # Unsigned release: make sure every binary carries at least an ad-hoc signature
    # (Apple silicon refuses to run unsigned native code).
    codesign --force --deep --sign - "$APP"
    codesign --verify --deep --strict "$APP"
    "${JBIN}jpackage" --type dmg "${COMMON[@]}" --app-image "$APP" "${MAC_OPTS[@]}" \
      --license-file "$ROOT/LICENSE" --dest "$DIST"
    mv "$DIST/$APP_NAME-$VERSION.dmg" "$DIST/$BASE.dmg"
    ditto -c -k --keepParent "$APP" "$DIST/$BASE.zip"
    ;;
  windows)
    "${JBIN}jpackage" --type msi "${COMMON[@]}" --app-image "$WORK/image/$APP_NAME" \
      --win-menu --win-menu-group "Clean Water" --win-shortcut --win-shortcut-prompt \
      --win-per-user-install --win-dir-chooser \
      --win-upgrade-uuid 7f0c6c2e-3b0a-4f0e-9a51-2a1d7c3e5b91 \
      --license-file "$ROOT/LICENSE" --dest "$DIST"
    mv "$DIST/$APP_NAME-$VERSION.msi" "$DIST/$BASE.msi"
    (cd "$WORK/image" && powershell -NoProfile -Command "Compress-Archive -Path '$APP_NAME' -DestinationPath '$(cygpath -w "$DIST/$BASE.zip")'")
    ;;
esac

# The image must carry the licence files.
find "$WORK/image" -name LICENSE -path '*app*' | grep -q . || { echo "LICENSE missing from the image" >&2; exit 1; }
find "$WORK/image" -name THIRD-PARTY-NOTICES.md | grep -q . || { echo "notices missing from the image" >&2; exit 1; }
ls -l "$DIST"
