#!/usr/bin/env bash
# 310 冻结探针 v4（probe-freeze-* 分支专用）：验证 fix3「进态错峰」修复。
# 前情（probe v1–v3 实证）：进态合成爆发与 IME 首秀同窗 → 模拟器进程级
# 死亡（logcat 无宾客侧 FATAL）；冷 IME、深色首帧各为一个加重因子；
# 应用中心单独聚焦存活；IME 已热 + 浅色 + 冷进程进态存活（v3 C1）。
# v4 按加重程度从高到低打（设备死了后续自动跳过）：
#  D0 深色（开机前设定）+ 冷进程 + 冷 IME —— 最大加重，修复的首要考场；
#  D1 深色 + force-stop 冷启再进（v3 C9 的点火配置复打）；
#  L1 浅色 + 冷启进态（v3 C1 的存活配置，防修法回退）；
#  A2 同进程第二次进/退。
PKG="com.bobot.ailauncher"
APK="apk/app-debug.apk"
OUT="probe-out"
mkdir -p "$OUT"
PASS=0
FAIL=0

log()  { echo "[probe] $*"; }
ok()   { PASS=$((PASS+1)); log "PASS: $1"; }
ng()   { FAIL=$((FAIL+1)); log "FAIL: $1"; }

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP || true
sleep 2
adb logcat -c > /dev/null 2>&1 || true
adb logcat -v time > "$OUT/logcat-live.txt" 2>/dev/null &
LOGCAT_PID=$!

state() { adb get-state 2>/dev/null | tr -d '\r'; }
curpid() { adb shell pidof "$PKG" 2>/dev/null | tr -d '\r'; }
devok() { [ "$(state)" = "device" ]; }
alive() { devok && [ -n "$(curpid)" ]; }

if [ -f "$APK" ]; then
  if adb install -r -g "$APK"; then ok "install"; else ng "install"; fi
else
  ng "apk-missing($APK)"
fi
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" "<map>" \
  '    <boolean name="battery_asked" value="true" />' \
  '    <boolean name="onboarded" value="true" />' "</map>" > "$OUT/ai_launcher.xml"
adb push "$OUT/ai_launcher.xml" /data/local/tmp/ai_launcher.xml > /dev/null 2>&1
adb shell "run-as $PKG mkdir -p shared_prefs" > /dev/null 2>&1
adb shell "run-as $PKG cp /data/local/tmp/ai_launcher.xml shared_prefs/ai_launcher.xml" > /dev/null 2>&1
adb shell "run-as $PKG chmod 660 shared_prefs/ai_launcher.xml" > /dev/null 2>&1
if adb shell "run-as $PKG cat shared_prefs/ai_launcher.xml" 2>/dev/null | grep -q 'name="onboarded" value="true"'; then
  ok "seed-prefs"
else
  ng "seed-prefs"
fi
adb shell cmd package set-home-activity "$PKG/.MainActivity" > /dev/null 2>&1 || true

center_rid() {
  local F="$1" RID="$2" NODE NUMS X1 Y1 X2 Y2
  [ -f "$F" ] || return 1
  NODE=$(grep -o "<node[^>]*resource-id=\"$RID\"[^>]*>" "$F" | head -1)
  [ -n "$NODE" ] || return 1
  NUMS=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
  X1=$(echo "$NUMS" | sed -n 1p); Y1=$(echo "$NUMS" | sed -n 2p)
  X2=$(echo "$NUMS" | sed -n 3p); Y2=$(echo "$NUMS" | sed -n 4p)
  [ -n "$X1" ] && [ -n "$Y2" ] || return 1
  [ "$X2" -gt "$X1" ] && [ "$Y2" -gt "$Y1" ] || return 1
  echo "$(( (X1 + X2) / 2 )) $(( (Y1 + Y2) / 2 ))"
}
tap_rid() {
  local F="$1" RID="$2" CTR
  CTR=$(center_rid "$F" "$RID") || return 1
  [ -n "$CTR" ] || return 1
  log "tap by testTag $RID at ($CTR)"
  adb shell input swipe $(echo "$CTR" | cut -d' ' -f1) $(echo "$CTR" | cut -d' ' -f2) \
    $(echo "$CTR" | cut -d' ' -f1) $(echo "$CTR" | cut -d' ' -f2) 150 || true
  return 0
}
tdump() {
  timeout 40 adb shell uiautomator dump /sdcard/probe_dump.xml > /dev/null 2>&1
  adb pull /sdcard/probe_dump.xml "$1" > /dev/null 2>&1
  [ -s "$1" ]
}
tshot() {
  local t0 t1 ms
  t0=$(date +%s%N)
  if timeout 45 adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; then
    t1=$(date +%s%N); ms=$(( (t1 - t0) / 1000000 ))
    log "shot $1 completed in ${ms}ms"
    [ "$ms" -gt 20000 ] && return 2
    return 0
  fi
  log "shot $1 TIMEOUT"
  return 1
}
start_app() {
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
  sleep 5
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
  log "started, state=$(state) pid=$(curpid)"
}
death_watch() {
  local L="$1" i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    sleep 1
    if ! devok; then log "$L: device lost at +${i}s"; return 1; fi
    if [ -z "$(curpid)" ]; then log "$L: app pid gone at +${i}s (device alive)"; return 2; fi
  done
  log "$L: alive through +10s"
  return 0
}
enter_and_check() {
  local L="$1"
  log "=== enter_and_check $L (state=$(state) pid=$(curpid)) ==="
  if ! devok; then ng "$L-DEVICE-LOST-before"; return 1; fi
  if ! tdump "$OUT/$L-home.xml"; then ng "$L-home-dump-blocked"; return 1; fi
  if ! tap_rid "$OUT/$L-home.xml" "dock_pet_cell"; then ng "$L-seat-not-found"; return 1; fi
  death_watch "$L"
  case $? in
    1) ng "$L-DEVICE-LOST-after-tap"; return 1 ;;
    2) ng "$L-APP-DIED(device-alive)"; return 1 ;;
  esac
  # fix3 错峰：聚焦在 +450ms、IME 弹起更晚，胶囊 dump 前多等一拍
  sleep 3
  if tdump "$OUT/$L-in.xml" && grep -q 'resource-id="intent_input"' "$OUT/$L-in.xml"; then
    ok "$L-capsule-present"
  else
    ng "$L-capsule-missing-or-dump-blocked"
    return 1
  fi
  tshot "$L-input"
  case $? in
    0) ok "$L-screencap-free" ;;
    2) ng "$L-screencap-slow(>20s)" ;;
    *) ng "$L-screencap-blocked" ;;
  esac
  return 0
}
exit_input() {
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
}
cold_start() {
  adb shell am force-stop "$PKG" || true
  sleep 1
  start_app
  sleep 10
}

# ================= D0：深色预置 + 冷启 + 冷 IME（最大加重） =================
adb shell cmd uimode night yes || true
sleep 2
start_app
sleep 10
enter_and_check "D0" && exit_input

# ================= D1：深色 + 冷启再进（v3 C9 点火配置） =================
if devok; then
  cold_start
  enter_and_check "D1" && exit_input
else
  ng "D1-skipped-device-lost"
fi

# ================= L1：浅色 + 冷启进态 =================
if devok; then
  adb shell cmd uimode night no || true
  sleep 2
  cold_start
  enter_and_check "L1" && exit_input
else
  ng "L1-skipped-device-lost"
fi

# ================= A2：同进程第二次进/退 =================
if alive; then
  enter_and_check "A2" && exit_input
else
  ng "A2-app-gone-before"
fi

adb shell cmd uimode night auto || true
kill "$LOGCAT_PID" > /dev/null 2>&1 || true
log "RESULT probe-freeze-v4 PASS=$PASS FAIL=$FAIL"
exit "$FAIL"
