#!/usr/bin/env bash
# 310 冻结探针 v2（probe-freeze-* 分支专用）：§9 进输入态楔死定因。
# v1 教训（run 40031168600）：A1 点后 adb 命令先挂 ~30s、随后整个模拟器消失
# （device not found / TCP 5554 refused），「应用进程死亡」与「模拟器死亡」
# 在 v1 的检查点口径下不可分。v2 改法：
#  ① 全程 adb logcat 后台直落宿主文件——设备死了，最后日志也留得住；
#  ② 每个检查点先记 adb get-state，应用死亡与设备死亡分开判；
#  ③ 先跑控制相：开机静置 + 应用静置 60s（周期探活），再进路径 A/C；
#  ④ 进态前先静置（开机风暴过后再点，复刻全量冒烟 §6 的自然时序）；
#  ⑤ 楔死后试 adb reconnect，区分 adbd 死亡与 qemu 死亡。
# 路径：A 同进程两次进/退；C force-stop 冷启首入（§9 复现）；C2 再冷启一次。
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

# ① 连续 logcat（宿主文件，设备死亡不丢）
adb logcat -c > /dev/null 2>&1 || true
adb logcat -v time > "$OUT/logcat-live.txt" 2>/dev/null &
LOGCAT_PID=$!

state() { adb get-state 2>/dev/null | tr -d '\r'; }
curpid() { adb shell pidof "$PKG" 2>/dev/null | tr -d '\r'; }
alive() { [ "$(state)" = "device" ] && [ -n "$(curpid)" ]; }
devok() { [ "$(state)" = "device" ]; }

# --- 安装 + 预写 prefs（与 smoke.sh 第 1/2 段同法同键） ---
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

# --- helper（center_rid / tap_rid 与 smoke.sh 同实现） ---
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
# 楔死取证：先看设备还在不在，再试 reconnect
forensics() {
  log "forensics $1: state=$(state)"
  if ! devok; then
    log "forensics $1: device LOST, trying reconnect"
    timeout 20 adb reconnect > /dev/null 2>&1 || true
    sleep 5
    log "forensics $1: after reconnect state=$(state)"
  fi
  if devok; then
    timeout 30 adb shell dumpsys activity processes > "$OUT/$1-procs.txt" 2>/dev/null || true
    timeout 30 adb shell ls /data/tombstones > "$OUT/$1-tombstones.txt" 2>/dev/null || true
    timeout 30 adb shell dumpsys activity lastanr > "$OUT/$1-lastanr.txt" 2>/dev/null || true
  fi
}

enter_and_check() {
  local L="$1"
  log "=== enter_and_check $L (state=$(state) pid=$(curpid)) ==="
  if ! devok; then ng "$L-DEVICE-LOST-before"; forensics "$L"; return 1; fi
  if ! tdump "$OUT/$L-home.xml"; then
    ng "$L-home-dump-blocked"
    forensics "$L"
    return 1
  fi
  if ! tap_rid "$OUT/$L-home.xml" "dock_pet_cell"; then
    ng "$L-seat-not-found"
    return 1
  fi
  sleep 3
  if ! devok; then
    ng "$L-DEVICE-LOST-after-tap"
    forensics "$L"
    return 1
  fi
  if ! alive; then
    ng "$L-APP-DIED(device-alive)"
    forensics "$L"
    return 1
  fi
  if tdump "$OUT/$L-in.xml" && grep -q 'resource-id="intent_input"' "$OUT/$L-in.xml"; then
    ok "$L-capsule-present"
  else
    ng "$L-capsule-missing-or-dump-blocked"
    forensics "$L"
    return 1
  fi
  tshot "$L-input"
  case $? in
    0) ok "$L-screencap-free" ;;
    2) ng "$L-screencap-slow(>20s)" ;;
    *) ng "$L-screencap-blocked" ;;
  esac
  log "after $L: state=$(state) pid=$(curpid)"
  return 0
}
exit_input() {
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
}

# ================= 控制相：静置探活 =================
log "control phase: settle 30s, then start app and idle-probe 6x10s"
sleep 30
start_app
CTRL_OK=1
for i in 1 2 3 4 5 6; do
  sleep 10
  if alive; then
    log "control idle $i: alive pid=$(curpid)"
  else
    log "control idle $i: NOT alive (state=$(state) pid=$(curpid))"
    CTRL_OK=0
    forensics "ctrl$i"
    break
  fi
done
[ "$CTRL_OK" = "1" ] && ok "control-idle-60s" || ng "control-idle-60s"
tshot "control-home" && ok "control-screencap" || ng "control-screencap"

# ================= 路径 A：同进程两次进/退 =================
enter_and_check "A1" && exit_input
sleep 1
if alive; then enter_and_check "A2" && exit_input; else ng "A2-app-gone-before"; fi

# ================= 路径 C：force-stop 冷启首入（§9 复现，进态前静置 15s） =================
if devok; then
  adb shell am force-stop "$PKG" || true
  sleep 1
  start_app
  sleep 15
  enter_and_check "C1" && exit_input
else
  ng "C1-skipped-device-lost"
fi

# ================= 路径 C2：再冷启一次 =================
if devok; then
  adb shell am force-stop "$PKG" || true
  sleep 1
  start_app
  sleep 15
  enter_and_check "C2" && exit_input
else
  ng "C2-skipped-device-lost"
fi

kill "$LOGCAT_PID" > /dev/null 2>&1 || true
log "RESULT probe-freeze-v2 PASS=$PASS FAIL=$FAIL"
exit "$FAIL"
