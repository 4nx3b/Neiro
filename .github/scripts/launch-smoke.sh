#!/usr/bin/env bash
set -u

api="$1"
apk_dir="${2:-apks}"
out="smoke-out"
mkdir -p "$out"
failed=0

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell settings put global window_animation_scale 1 || true
adb shell input keyevent 82 || true

package_of() {
  case "$1" in
    *-dev-*) echo "com.dev.bitchord" ;;
    *) echo "com.music.bitchord" ;;
  esac
}

apks="$(find "$apk_dir" -name '*.apk' | sort)"
count="$(printf '%s\n' "$apks" | grep -c '\.apk$' || true)"
echo "APKs to launch ($count):"
printf '%s\n' "$apks"
if [ "$count" -lt 2 ] || ! printf '%s\n' "$apks" | grep -q -- '-dev-' || ! printf '%s\n' "$apks" | grep -q -- '-prod-'; then
  echo "MISSING APKS: expected one -dev- and one -prod- APK in $apk_dir, found $count"
  exit 1
fi
launched=0

for apk in $apks; do
  launched=$((launched + 1))
  pkg="$(package_of "$apk")"
  tag="$(basename "$apk" .apk)-api$api"
  echo "::group::launch $tag ($pkg)"
  if ! adb install -r -g "$apk" > "$out/install-$tag.txt" 2>&1; then
    cat "$out/install-$tag.txt"
    echo "INSTALL FAILED: $tag"
    failed=1
    echo "::endgroup::"
    continue
  fi
  installed="$(adb shell pm list packages "$pkg" | tr -d '\r')"
  if [ "$installed" != "package:$pkg" ]; then
    echo "PACKAGE MISMATCH: expected $pkg, device lists: $installed"
    failed=1
  fi
  adb logcat -c
  adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  sleep 25
  pid="$(adb shell pidof "$pkg" | tr -d '\r')"
  adb exec-out screencap -p > "$out/shot-$tag.png"
  adb logcat -d -v threadtime > "$out/logcat-$tag.txt"
  if [ -z "$pid" ]; then
    echo "DEAD: $pkg is not running 25 s after launch"
    failed=1
  else
    echo "ALIVE: $pkg pid $pid"
  fi
  if grep -q "FATAL EXCEPTION" "$out/logcat-$tag.txt"; then
    echo "FATAL EXCEPTION in $tag:"
    grep -A60 "FATAL EXCEPTION" "$out/logcat-$tag.txt"
    failed=1
  fi
  if grep -qE "Fatal signal|ANR in $pkg" "$out/logcat-$tag.txt"; then
    echo "NATIVE CRASH / ANR in $tag:"
    grep -E -A40 "Fatal signal|ANR in $pkg|\*\*\* \*\*\* \*\*\*" "$out/logcat-$tag.txt" | head -120
    failed=1
  fi
  echo "--- AndroidRuntime / app errors ($tag) ---"
  grep -E " E (AndroidRuntime|$pkg|System.err)| E/AndroidRuntime" "$out/logcat-$tag.txt" | head -80 || true
  adb shell am force-stop "$pkg" || true
  echo "::endgroup::"
done

if [ "$launched" -ne "$count" ]; then
  echo "LAUNCHED $launched of $count APKs"
  failed=1
fi
exit "$failed"
