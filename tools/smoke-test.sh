#!/usr/bin/env bash
# Starts the packaged app (from the app image built by tools/package.sh) and checks that it
# opens its database within 60 s and loads JavaFX's native libraries from its own runtime
# (no ~/.openjfx cache). Uses a throwaway home folder and database; no network lookup.
#
#   tools/smoke-test.sh            (run from the repository root after tools/package.sh)
set -euo pipefail
WORK="$(pwd)/cwc-desktop/target/package"
TMP="$(pwd)/cwc-desktop/target/smoke"
rm -rf "$TMP"; mkdir -p "$TMP/home"
case "$(uname -s)" in
  Linux*)  LAUNCH=("$WORK/image/CleanWater/bin/CleanWater"); WRAP=(xvfb-run -a -s "-screen 0 1280x800x24") ;;
  Darwin*) LAUNCH=("$WORK/image/Clean Water.app/Contents/MacOS/Clean Water"); WRAP=() ;;
  *)       LAUNCH=("$WORK/image/Clean Water/Clean Water.exe"); WRAP=() ;;
esac
DB="$TMP/smoke.db"
# Paths as the JVM sees them (Windows: C:/... instead of Git Bash's /c/...). They go into
# JAVA_TOOL_OPTIONS, which is split at spaces.
native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s\n' "$1"; fi; }
case "$TMP" in *" "*) echo "FAIL: the repository path must not contain spaces ($TMP)"; exit 1 ;; esac
JHOME="$(native_path "$TMP/home")"
JDB="$(native_path "$DB")"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Duser.home=$JHOME -Dcwc.db.path=$JDB -Dcwc.geo.url=off"
${WRAP[@]+"${WRAP[@]}"} "${LAUNCH[@]}" > "$TMP/app.log" 2>&1 &
PID=$!
for _ in $(seq 1 60); do
  [ -s "$DB" ] && break
  kill -0 "$PID" 2>/dev/null || break
  sleep 1
done
ok=true
[ -s "$DB" ] || { echo "FAIL: the app did not create its database"; ok=false; }
kill -0 "$PID" 2>/dev/null || { echo "FAIL: the app exited early"; ok=false; }
if find "$TMP/home" -path '*openjfx*' | grep -q .; then
  echo "FAIL: JavaFX unpacked its native libraries into the home folder"; ok=false
fi
# Stop the app itself; on Linux, xvfb-run then exits and removes its X server.
case "$(uname -s)" in
  Linux*|Darwin*) pkill -f "^$WORK/image/" 2>/dev/null || true ;;   # the launcher only, not xvfb-run
  *) taskkill //F //IM "Clean Water.exe" >/dev/null 2>&1 || true ;;
esac
wait "$PID" 2>/dev/null || true
cat "$TMP/app.log"
$ok && echo "PASS: packaged app started, opened $DB, natives loaded from the runtime"
$ok
