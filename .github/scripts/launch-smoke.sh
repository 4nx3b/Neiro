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
for apk in $apks; do
  case "$apk" in
    *-prod-*)
      if unzip -l "$apk" | grep -E 'assets/dexopt/baseline\.prof'; then
        echo "BASELINE PROFILE: present in $(basename "$apk")"
      else
        echo "BASELINE PROFILE MISSING: $(basename "$apk") has no assets/dexopt/baseline.prof"
        failed=1
      fi
      ;;
  esac
done
launched=0

screen_size() {
  adb shell wm size | tr -d '\r' | awk -F': ' '/Override size/ {o=$2} /Physical size/ {p=$2} END {print (o != "" ? o : p)}'
}

interact() {
  local pkg="$1" tag="$2"
  local size w h cx top bottom bar_y i
  size="$(screen_size)"
  w="${size%x*}"
  h="${size#*x}"
  cx=$((w / 2))
  top=$((h * 3 / 10))
  bottom=$((h * 7 / 10))
  bar_y=$((h * 93 / 100))
  echo "INTERACT $tag: screen ${w}x${h}"
  for i in 1 2 3 4 5 6; do
    echo "swipe $i up+down"
    adb shell input swipe "$cx" "$bottom" "$cx" "$top" 300
    sleep 0.4
    adb shell input swipe "$cx" "$top" "$cx" "$bottom" 300
    sleep 0.4
  done
  for frac in 12 30 50 70 88; do
    echo "tap bar x=${frac}%"
    adb shell input tap $((w * frac / 100)) "$bar_y"
    sleep 1.5
    adb shell input swipe "$cx" "$bottom" "$cx" "$top" 300
    sleep 0.5
  done
  echo "tap mini player area"
  adb shell input tap "$cx" $((h * 85 / 100))
  sleep 2
  adb shell input swipe "$cx" "$top" "$cx" "$bottom" 300
  sleep 0.5
  adb shell input keyevent 4
  sleep 1
  echo "settle 10 s"
  sleep 10
}

perf_block() {
  local pkg="$1" tag="$2" label="$3"
  local activity run
  activity="$(adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$pkg" | tr -d '\r' | tail -1)"
  {
    echo "== perf $tag $label"
    for run in 1 2 3; do
      adb shell am force-stop "$pkg"
      sleep 2
      adb shell am start -W -n "$activity" | tr -d '\r' | grep -E 'TotalTime|WaitTime' | sed "s/^/cold-start run $run: /"
      sleep 8
    done
    adb shell dumpsys gfxinfo "$pkg" reset > /dev/null
    interact "$pkg" "$tag-$label" > /dev/null
    adb shell dumpsys gfxinfo "$pkg" | tr -d '\r' | grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile' | head -6
  } | tee -a "$out/perf-$tag.txt"
  adb shell am force-stop "$pkg"
}

# One launch attempt for one APK. Echoes the diagnostics the old inline block
# printed, into $out files suffixed by the attempt, and returns:
#   0 — the app is alive 25 s in with no crash or ANR signature
#   1 — runner-side flake: the launch died without any signature (an emulator
#       graphics failure — the ColorBuffer errors — or a kill under the load a
#       two-APK emulator round puts on a shared runner), or an input-dispatch
#       ANR during cold start that the process itself recovered from
#   2 — an app signature: FATAL EXCEPTION or a native Fatal signal
launch_once() {
  local pkg="$1" tag="$2" attempt="$3"
  local pid signature=0
  adb logcat -c
  adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
  sleep 25
  pid="$(adb shell pidof "$pkg" | tr -d '\r')"
  adb exec-out screencap -p > "$out/shot-$tag$attempt.png" 2>/dev/null || true
  if [ -n "$pid" ]; then
    interact "$pkg" "$tag$attempt"
    adb exec-out screencap -p > "$out/shot-$tag$attempt-interacted.png" 2>/dev/null || true
    pid="$(adb shell pidof "$pkg" | tr -d '\r')"
    if [ -z "$pid" ]; then
      echo "DIED DURING INTERACTION: $pkg"
      adb shell dumpsys activity exit-info "$pkg" 2>/dev/null | tr -d '\r' | head -40 || true
    fi
  fi
  adb logcat -d -v threadtime > "$out/logcat-$tag$attempt.txt"
  if [ -z "$pid" ]; then
    echo "DEAD: $pkg is not running after launch + interaction"
  else
    echo "ALIVE: $pkg pid $pid"
  fi
  if grep -q "FATAL EXCEPTION" "$out/logcat-$tag$attempt.txt"; then
    echo "FATAL EXCEPTION in $tag$attempt:"
    grep -A60 "FATAL EXCEPTION" "$out/logcat-$tag$attempt.txt"
    signature=1
  fi
  if grep -qE "Fatal signal|ANR in $pkg" "$out/logcat-$tag$attempt.txt"; then
    echo "NATIVE CRASH / ANR in $tag$attempt:"
    grep -E -A40 "Fatal signal|ANR in $pkg|\*\*\* \*\*\* \*\*\*" "$out/logcat-$tag$attempt.txt" | head -120
    if grep -q "Fatal signal" "$out/logcat-$tag$attempt.txt"; then
      signature=1
    fi
  fi
  echo "--- AndroidRuntime / app errors ($tag$attempt) ---"
  grep -E " E (AndroidRuntime|$pkg|System.err)| E/AndroidRuntime" "$out/logcat-$tag$attempt.txt" | head -80 || true
  adb shell am force-stop "$pkg" || true
  if [ "$signature" -eq 1 ]; then
    return 2
  fi
  if [ -z "$pid" ]; then
    return 1
  fi
  # Alive with no crash signature; an ANR line means input dispatching timed
  # out against a cold start the process then completed — runner class.
  if grep -qE "ANR in $pkg" "$out/logcat-$tag$attempt.txt"; then
    return 1
  fi
  return 0
}

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
  launch_once "$pkg" "$tag" "-1"
  verdict=$?
  if [ "$verdict" -eq 1 ]; then
    echo "RETRY: $pkg failed with no crash signature of its own (the emulator's graphics stack or the runner's load); relaunching once"
    sleep 10
    launch_once "$pkg" "$tag" "-2"
    verdict=$?
  fi
  if [ "$verdict" -ne 0 ]; then
    failed=1
  elif [ "$pkg" = "com.music.bitchord" ]; then
    perf_block "$pkg" "$tag" "as-installed" || true
    adb shell cmd package compile -m speed-profile -f "$pkg" || true
    adb shell dumpsys package dexopt | tr -d '\r' | grep -A3 "\[$pkg\]" | tee -a "$out/perf-$tag.txt" || true
    perf_block "$pkg" "$tag" "speed-profile" || true
  fi
  echo "::endgroup::"
done

if [ "$launched" -ne "$count" ]; then
  echo "LAUNCHED $launched of $count APKs"
  failed=1
fi
exit "$failed"
