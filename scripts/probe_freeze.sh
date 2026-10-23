#!/usr/bin/env bash
# 310 冻结探针 v3（probe-freeze-* 分支专用）：在 v2 结论上做判别实验。
# v2 实证（run 40031960155）：静置 60s 全健康；点七仔格后 <1s 整个模拟器
# 消失（logcat 无宾客侧 FATAL/ANR、宿主 TCP 5554 refused = qemu 进程级死亡）；
# adb 调用在死套接字上挂 ~28s——即历史所记「screencap 阻塞约 28 秒」签名。
# v3 判别序（单模拟器会话、按信息量排序，设备死了后续自动跳过）：
#  X3 先在应用中心点搜索胶囊聚焦（IME 首秀 + 窗口 resize，但无主页进态
#     合成内容）——若此处即死，触发源是 IME/窗口机制而非主页进态内容；
#  A1 IME 已热后点七仔格进主页输入态；
#  C1 force-stop 冷启后进态（IME 进程仍热、应用进程冷）；
#  C9 深色 + 冷启后进态（§9 的确切加料：§8 留下的夜间态）。
# 全程 logcat 直落宿主文件；每次点按后逐秒探活，记录死亡延迟。
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
# 点按后逐秒探活（最多 10 秒），打印死亡延迟；存活满 10 秒返回 0
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

# ================= 控制相（短版） =================
sleep 20
start_app
for i in 1 2 3; do sleep 10; alive && log "control idle $i: alive" || { log "control idle $i: NOT alive"; ng "control-idle"; }; done
ok "control-idle-done"

# ================= X3：应用中心搜索框聚焦（IME 首秀，无主页进态内容） =================
if devok; then
  adb shell input swipe 540 2000 540 800 300 || true
  sleep 3
  if tdump "$OUT/x3-ac.xml"; then
    if tap_rid "$OUT/x3-ac.xml" "intent_input"; then
      log "X3: tapped AppCenter search field, watching"
      death_watch "X3"
      case $? in
        0) ok "X3-ime-show-survived" ;;
        1) ng "X3-DEVICE-LOST" ;;
        2) ng "X3-APP-DIED" ;;
      esac
    else
      log "X3: intent_input not found in AppCenter dump"
      ng "X3-field-not-found"
    fi
  else
    ng "X3-ac-dump-blocked"
  fi
  if devok; then
    adb shell input keyevent KEYCODE_BACK || true
    sleep 1
    adb shell input keyevent KEYCODE_HOME || true
    sleep 2
  fi
else
  ng "X3-skipped-device-lost"
fi

# ================= A1：IME 已热后主页进态 =================
if devok; then enter_and_check "A1" && exit_input; else ng "A1-skipped-device-lost"; fi

# ================= C1：冷启进态（IME 进程仍热） =================
if devok; then
  adb shell am force-stop "$PKG" || true
  sleep 1
  start_app
  sleep 10
  enter_and_check "C1" && exit_input
else
  ng "C1-skipped-device-lost"
fi

# ================= C9：深色 + 冷启进态（§9 加料） =================
if devok; then
  adb shell cmd uimode night yes || true
  sleep 2
  adb shell am force-stop "$PKG" || true
  sleep 1
  start_app
  sleep 10
  enter_and_check "C9" && exit_input
  adb shell cmd uimode night auto || true
else
  ng "C9-skipped-device-lost"
fi

kill "$LOGCAT_PID" > /dev/null 2>&1 || true
log "RESULT probe-freeze-v3 PASS=$PASS FAIL=$FAIL"
exit "$FAIL"
