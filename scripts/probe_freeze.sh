#!/usr/bin/env bash
# probe-freeze v6: 浅色+冷 IME+点座位聚焦进态（复刻核心冒烟 §6 首入路径）
# fix3 时序（焦点门 450ms / 伴随 1500ms）在该路径的存活验证。
PKG=com.bobot.ailauncher
APK=apk/app-debug.apk
OUT=probe-out
mkdir -p "$OUT"
PASS=0; FAIL=0
log(){ echo "[probe] $*"; }
ok(){ PASS=$((PASS+1)); log "PASS: $*"; }
ng(){ FAIL=$((FAIL+1)); log "FAIL: $*"; }

adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb logcat -c || true
adb logcat -v time > "$OUT/logcat-live.txt" &
LOGCAT_PID=$!

state(){ adb get-state 2>/dev/null | tr -d '\r'; }
curpid(){ adb shell pidof "$PKG" 2>/dev/null | tr -d '\r'; }
devok(){ [ "$(state)" = "device" ]; }
alive(){ devok && [ -n "$(curpid)" ]; }

log "apk check: cwd=$(pwd)"
if [ ! -f "$APK" ]; then
  FOUND=$(find . -name 'app-debug.apk' 2>/dev/null | head -1)
  if [ -n "$FOUND" ]; then APK="$FOUND"; log "apk found at $APK"; fi
fi
if [ -f "$APK" ]; then
  if adb install -r -g "$APK"; then ok "install"; else ng "install"; fi
else
  ng "apk-missing($APK)"
fi
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes'?>" "<map>" \
' <boolean name="battery_asked" value="true" />' \
' <boolean name="onboarded" value="true" />' "</map>" > "$OUT/ai_launcher.xml"
adb push "$OUT/ai_launcher.xml" /data/local/tmp/ai_launcher.xml > /dev/null 2>&1
adb shell "run-as $PKG mkdir -p shared_prefs" > /dev/null 2>&1
adb shell "run-as $PKG cp /data/local/tmp/ai_launcher.xml shared_prefs/ai_launcher.xml" > /dev/null 2>&1
adb shell "run-as $PKG chmod 660 shared_prefs/ai_launcher.xml" > /dev/null 2>&1
if adb shell "run-as $PKG cat shared_prefs/ai_launcher.xml" 2>/dev/null | grep -q 'name="onboarded" value="true"'; then ok "seed-prefs"; else ng "seed-prefs"; fi
adb shell cmd package set-home-activity $PKG/.MainActivity >/dev/null 2>&1 || true

tdump(){
  local F="$OUT/$1.xml"
  adb shell uiautomator dump "/sdcard/$1.xml" >/dev/null 2>&1 || return 1
  adb pull "/sdcard/$1.xml" "$F" >/dev/null 2>&1 || return 1
  [ -s "$F" ]
}
center_rid(){
  local F="$1" RID="$2" NODE X1 Y1 X2 Y2
  [ -f "$F" ] || return 1
  NODE=$(grep -o "<node[^>]*resource-id=\"[^\"]*$RID\"[^>]*>" "$F" | head -1)
  [ -n "$NODE" ] || return 1
  X1=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]' | head -1 | grep -o '[0-9]*' | sed -n 1p)
  Y1=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]' | head -1 | grep -o '[0-9]*' | sed -n 2p)
  X2=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]' | grep -o '[0-9]*' | sed -n 3p)
  Y2=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]' | grep -o '[0-9]*' | sed -n 4p)
  [ -n "$X1" ] && [ -n "$Y2" ] || return 1
  echo "$(( (X1+X2)/2 )) $(( (Y1+Y2)/2 ))"
}
shot(){
  local N="$1" T0 T1
  T0=$(date +%s%N)
  if timeout 40 adb exec-out screencap -p > "$OUT/$N.png" 2>/dev/null && [ -s "$OUT/$N.png" ]; then
    T1=$(date +%s%N); log "shot $N completed in $(( (T1-T0)/1000000 ))ms"; return 0
  fi
  log "shot $N FAILED/timeout"; return 1
}
death_watch(){
  local L="$1" i
  for i in $(seq 1 20); do
    sleep 1
    if ! devok; then log "$L: device lost at +${i}s"; return 1; fi
    if [ -z "$(curpid)" ]; then log "$L: app pid gone at +${i}s (device alive)"; return 2; fi
  done
  log "$L: alive through +20s"
  return 0
}
start_app(){
  adb shell am force-stop $PKG || true
  sleep 1
  adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $PKG/.MainActivity >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_HOME || true
  sleep 3
  log "started, state=$(state) pid=$(curpid)"
}

# ================= 复刻冒烟 §5/§5b/§6 =================
start_app
if alive; then ok "setup-alive"; else ng "setup-alive"; fi
sleep 4
if shot home; then ok "shot-home"; else ng "shot-home"; fi

# §5: 扇形拖动 3s（中途截图）后 force-stop 重启
adb shell input swipe 900 1400 300 1400 3000 &
SWP=$!
sleep 1.5
shot fan || true
wait $SWP || true
adb shell am force-stop $PKG || true
sleep 1
adb shell am start -n $PKG/.MainActivity >/dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
log "after fan1: state=$(state) pid=$(curpid)"

# §5b: 扇形拖动 6s（2.5s 时 dump）后 force-stop 重启
adb shell input swipe 900 1400 300 1400 6000 &
SWP=$!
sleep 2.5
adb shell uiautomator dump /sdcard/fan_dump.xml >/dev/null 2>&1 || true
wait $SWP || true
adb shell am force-stop $PKG || true
sleep 1
adb shell am start -n $PKG/.MainActivity >/dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
log "after fan2: state=$(state) pid=$(curpid)"
if alive; then ok "baseline3-alive"; else ng "baseline3-alive"; fi

# §6: dump 定位座位 -> 150ms 按压点击 -> 0.7s 后截图（冒烟原序）
E1RC=99
if alive && tdump pet_dump; then
  CTR=$(center_rid "$OUT/pet_dump.xml" dock_pet_cell)
  if [ -n "$CTR" ]; then
    set -- $CTR
    log "S6: press seat at ($CTR) 150ms"
    adb shell input swipe "$1" "$2" "$1" "$2" 150 || true
    sleep 0.7
    if shot pet; then ok "S6-shot-pet"; else ng "S6-shot-pet"; fi
    death_watch "S6"; E1RC=$?
    if [ $E1RC -eq 0 ]; then ok "S6-entry-survived"; else ng "S6-DIED rc=$E1RC"; fi
  else
    ng "S6-seat-not-found"
  fi
else
  ng "S6-skipped-not-alive"
fi
if [ $E1RC -eq 0 ]; then
  if tdump s6-in && grep -q 'intent_input' "$OUT/s6-in.xml"; then ok "S6-capsule-present"; else ng "S6-capsule-missing"; fi
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
  if alive; then ok "S6-exit-alive"; else ng "S6-exit-alive"; fi
fi

log "RESULT probe-freeze-v7 PASS=$PASS FAIL=$FAIL"
sleep 1
kill $LOGCAT_PID 2>/dev/null || true
