#!/usr/bin/env bash
set -u

apk="$1"
label="$2"
pkg=io.github.neoruaa.glasmio.sample
activity="$pkg/.MainActivity"
out="sample-smoke-out/$label"
mkdir -p "$out/tombstones" "$out/dumps"
crashes=0

routes=(Home MaterialTab SettingsTab About Controls Navigation Materials Search Messages Sheets)
caps=(0 30)

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
adb root >/dev/null 2>&1 || true
sleep 3
adb wait-for-device
adb shell 'rm -f /data/tombstones/*' >/dev/null 2>&1 || true
adb shell settings put global window_animation_scale 1 || true
adb shell input keyevent 82 || true
adb install -r -t "$apk" >/dev/null
adb logcat -c || true

tombstone_count() { adb shell 'ls /data/tombstones 2>/dev/null | grep -v "\.pb$" | wc -l' | tr -d '\r '; }
seen_tombstones=$(tombstone_count)

check_alive() {
  local where="$1"
  local now
  now=$(tombstone_count)
  local died=0
  if [ -z "$(adb shell pidof "$pkg" | tr -d '\r')" ]; then died=1; fi
  if [ "$now" != "$seen_tombstones" ]; then died=1; fi
  if adb logcat -d -s AndroidRuntime:E | grep -q "FATAL EXCEPTION"; then died=1; fi
  if adb logcat -d | grep -q "ANR in $pkg"; then died=1; fi
  if [ "$died" = 1 ]; then
    crashes=$((crashes + 1))
    echo "::error::$label: the sample died at: $where"
    local newest
    newest=$(adb shell 'ls -t /data/tombstones 2>/dev/null | grep -v "\.pb$" | head -1' | tr -d '\r')
    if [ -n "$newest" ]; then
      adb pull "/data/tombstones/$newest" "$out/tombstones/$crashes-$newest" >/dev/null 2>&1 || true
      echo "---- tombstone $newest (first 200 lines) ----"
      head -200 "$out/tombstones/$crashes-$newest" 2>/dev/null || true
    fi
    echo "---- exit-info ----"
    adb shell dumpsys activity exit-info "$pkg" | head -60
    adb logcat -d > "$out/logcat-crash-$crashes.txt" || true
    adb logcat -d -s AndroidRuntime:E | tail -60
    adb logcat -c || true
    seen_tombstones=$now
    return 1
  fi
  return 0
}

launch() {
  local cap="$1" route="$2"
  adb shell am force-stop "$pkg"
  adb shell am start -W -n "$activity" --ei glasmio_cap "$cap" --es glasmio_route "$route" ${EXTRA_ARGS:-} >/dev/null
  sleep 3
}

walk_route() {
  local cap="$1" route="$2"
  local dump="$out/dumps/cap$cap-$route.xml"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 0
  adb pull /sdcard/ui.xml "$dump" >/dev/null 2>&1 || return 0
  python3 - "$dump" "$pkg" > "$out/actions-cap$cap-$route.txt" <<'PY'
import re, sys, xml.etree.ElementTree as ET
dump, pkg = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(dump).getroot()
except ET.ParseError:
    sys.exit(0)
seen = set()
for node in root.iter("node"):
    if node.get("package") != pkg:
        continue
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        continue
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 - x1 < 8 or y2 - y1 < 8:
        continue
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    if node.get("scrollable") == "true":
        print(f"scroll {cx} {cy} {y1} {y2}")
    if node.get("clickable") == "true" and (cx, cy) not in seen:
        seen.add((cx, cy))
        print(f"tap {cx} {cy}")
PY
  while read -r kind x y a b <&3; do
    case "$kind" in
      tap)
        adb shell input tap "$x" "$y"
        sleep 0.7
        check_alive "cap=$cap route=$route tap $x,$y" || { launch "$cap" "$route"; continue; }
        if [ "$(adb shell dumpsys window | grep -c "mCurrentFocus.*$pkg")" = "0" ]; then
          adb shell input keyevent BACK; sleep 0.5
        fi
        ;;
      scroll)
        local top=$((a + (b - a) / 5)) bottom=$((b - (b - a) / 5))
        for _ in 1 2 3 4 5 6; do adb shell input swipe "$x" "$bottom" "$x" "$top" 250; done
        for _ in 1 2 3 4 5 6; do adb shell input swipe "$x" "$top" "$x" "$bottom" 250; done
        sleep 0.5
        check_alive "cap=$cap route=$route scroll at $x,$y" || launch "$cap" "$route"
        ;;
    esac
  done 3< "$out/actions-cap$cap-$route.txt"
  launch "$cap" "$route"
}

for cap in "${caps[@]}"; do
  for route in "${routes[@]}"; do
    echo "== $label cap=$cap route=$route"
    launch "$cap" "$route"
    check_alive "cap=$cap route=$route launch" || continue
    walk_route "$cap" "$route"
  done
  for seed in 1 2 3; do
    echo "== $label cap=$cap monkey seed $seed"
    launch "$cap" Home
    adb shell monkey -p "$pkg" -s "$seed" --throttle 120 --pct-touch 45 --pct-motion 45 \
      --pct-syskeys 0 --pct-appswitch 0 2000 > "$out/monkey-cap$cap-seed$seed.txt" 2>&1 || true
    check_alive "cap=$cap monkey seed $seed" || true
  done
done

adb logcat -d > "$out/logcat-final.txt" || true
echo "$label: $crashes crash(es)"
echo "$crashes" > "$out/crash-count"
[ "$crashes" = "0" ]
