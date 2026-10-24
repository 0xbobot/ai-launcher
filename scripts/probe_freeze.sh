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
adb shell mkdir -p /sdcard/Android/data/$PKG/files || true
adb shell run-as $PKG mkdir -p shared_prefs 2>/dev/null || true
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes'?>" "<map>" \
  "<boolean name=\"onboarding_done\" value=\"true\" />" \
  "<boolean name=\"pet_has_interacted\" value=\"true\" />" "</map>" > /tmp/seed.xml
adb push /tmp/seed.xml /sdcard/seed.xml >/dev/null 2>&1 || true
adb shell "cat /sdcard/seed.xml > /data/data/$PKG/shared_prefs/ai_launcher_prefs.xml" 2>/dev/null || adb shell run-as $PKG sh -c "cat /sdcard/seed.xml > shared_prefs/ai_launcher_prefs.xml" 2>/dev/null || true
if adb shell run-as $PKG ls shared_prefs 2>/dev/null | grep -q ai_launcher_prefs; then ok "seed-prefs"; else ng "seed-prefs"; fi
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

# ================= 浅色 + 冷启 + 座位首入（聚焦+冷 IME） =================
adb shell cmd uimode night no || true
sleep 1
start_app
if alive; then ok "setup-alive"; else ng "setup-alive"; fi

E1RC=99
if alive && tdump e1-home; then
  CTR=$(center_rid "$OUT/e1-home.xml" dock_pet_cell)
  if [ -n "$CTR" ]; then
    log "E1: tap seat at ($CTR)"
    adb shell input tap $CTR || true
    death_watch "E1"; E1RC=$?
    if [ $E1RC -eq 0 ]; then ok "E1-light-coldime-focus-entry-survived"; else ng "E1-DIED rc=$E1RC"; fi
  else
    ng "E1-seat-not-found"
  fi
else
  ng "E1-skipped-not-alive"
fi
if [ $E1RC -eq 0 ]; then
  if tdump e1-in && grep -q 'intent_input' "$OUT/e1-in.xml"; then ok "E1-capsule-present"; else ng "E1-capsule-missing"; fi
  if shot e1-input; then ok "E1-screencap"; else ng "E1-screencap"; fi
  # ---- E2: 同进程退/再入（IME 已热） ----
  adb shell input keyevent KEYCODE_BACK || true
  sleep 2
  if alive && tdump e2-home; then
    CTR=$(center_rid "$OUT/e2-home.xml" dock_pet_cell)
    if [ -n "$CTR" ]; then
      log "E2: tap seat at ($CTR)"
      adb shell input tap $CTR || true
      if death_watch "E2"; then ok "E2-second-entry-survived"; else ng "E2-DIED"; fi
      if shot e2-input; then ok "E2-screencap"; else ng "E2-screencap"; fi
    else
      ng "E2-seat-not-found(already-in-input?)"
    fi
  else
    ng "E2-skipped"
  fi
fi

log "RESULT probe-freeze-v6 PASS=$PASS FAIL=$FAIL"
sleep 1
kill $LOGCAT_PID 2>/dev/null || true
