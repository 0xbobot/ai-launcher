#!/usr/bin/env bash
# 310 冻结探针（probe-freeze-* 分支专用）：§9「force-stop 重启后进输入态楔死」三路对照。
# 路径 A：同进程连续两次进/退输入态；路径 B：uimode 切换（Activity 重建、进程不变）后进态；
# 路径 C：force-stop 冷启后首入（= §9 复现）；C2：再冷启一次（区分「每个冷启都楔」vs「特定会话序列」）。
# 每路检查点：进程存活（pidof）/ 层级可读且胶囊在场（uiautomator dump 带超时）/
# screencap 不阻塞（带超时计时；楔死签名为阻塞 ~28s）。楔死时落 logcat + 进程表取证。
# 与 scripts/smoke.sh 同口径：容错计数、PASS/FAIL 汇总、退出码 = FAIL 数。
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
log "night mode state: $(adb shell cmd uimode night 2>/dev/null | tr -d '\r')"

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

# --- 基础 helper（center_rid / tap_rid 与 smoke.sh 同实现） ---
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

curpid() { adb shell pidof "$PKG" 2>/dev/null | tr -d '\r'; }
alive() { [ -n "$(curpid)" ]; }

# 带超时的层级 dump：楔死时 uiautomator 会挂起，40s 掐断判负
tdump() {
  timeout 40 adb shell uiautomator dump /sdcard/probe_dump.xml > /dev/null 2>&1
  adb pull /sdcard/probe_dump.xml "$1" > /dev/null 2>&1
  [ -s "$1" ]
}
# 带超时的截图：楔死签名 = screencap 阻塞约 28s；>20s 即记阻塞
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
forensics() {
  log "forensics: $1"
  timeout 30 adb logcat -d -t 800 > "$OUT/$1-logcat.txt" 2>/dev/null || true
  timeout 30 adb shell dumpsys activity processes > "$OUT/$1-procs.txt" 2>/dev/null || true
  timeout 30 adb shell ls /data/tombstones > "$OUT/$1-tombstones.txt" 2>/dev/null || true
}

start_app() {
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
  sleep 5
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
  log "started, pid=$(curpid)"
}

# 进输入态并做三项检查点；$1=路径标签。返回 0 = 全过。
enter_and_check() {
  local L="$1"
  log "=== enter_and_check $L (pid before=$(curpid)) ==="
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
  if ! alive; then
    ng "$L-PROCESS-DIED"
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
  log "pid after $L = $(curpid)"
  return 0
}

exit_input() {
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
}

# ================= 路径 A：同进程两次进/退 =================
start_app
PID_P1="$(curpid)"
enter_and_check "A1" && exit_input
sleep 1
if alive; then enter_and_check "A2" && exit_input; else ng "A2-process-gone-before"; fi
log "path A done, pid=$(curpid) (P1=$PID_P1)"

# ================= 路径 B：Activity 重建（uimode 两次切换）后进态 =================
adb shell cmd uimode night yes || true
sleep 3
log "after night-yes pid=$(curpid) (recreate expected, process same)"
adb shell cmd uimode night auto || true
sleep 3
log "after night-auto pid=$(curpid)"
if alive; then
  enter_and_check "B1" && exit_input
else
  ng "B1-process-gone-before"
fi

# ================= 加料：开一次应用中心再回（同 §7 的进程内履历） =================
if alive; then
  adb shell input swipe 540 2000 540 800 300 || true
  sleep 3
  adb shell input keyevent KEYCODE_BACK || true
  sleep 2
  log "appcenter seasoning done, pid=$(curpid)"
fi

# ================= 路径 C：force-stop 冷启首入（§9 复现） =================
adb shell am force-stop "$PKG" || true
sleep 1
start_app
enter_and_check "C1" && exit_input

# ================= 路径 C2：再冷启一次 =================
adb shell am force-stop "$PKG" || true
sleep 1
start_app
enter_and_check "C2" && exit_input

log "RESULT probe-freeze PASS=$PASS FAIL=$FAIL"
exit "$FAIL"
