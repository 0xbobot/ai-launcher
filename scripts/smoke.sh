#!/usr/bin/env bash
# D-6 临时取证脚本 v2（取证后删除）：同应用 A/B——同一应用先在底部 88dp
# 带内点（A），再滚到中屏点（B），隔离「位置」与「应用本身打不开」两个变量。
PKG="com.bobot.ailauncher"
APK="apk/app-debug.apk"
OUT="d6-out"
mkdir -p "$OUT"
PASS=0
FAIL=0
log()  { echo "[smoke] $*"; }
ok()   { PASS=$((PASS+1)); log "PASS: $1"; }
ng()   { FAIL=$((FAIL+1)); log "FAIL: $1"; }
redump_if_empty() {
  if [ ! -s "$OUT/$1.xml" ]; then
    sleep 1
    adb shell uiautomator dump "/sdcard/$1.xml" > /dev/null 2>&1 || true
    adb pull "/sdcard/$1.xml" "$OUT/$1.xml" > /dev/null 2>&1 || true
  fi
}
shot() {
  if adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; then
    log "shot: $1.png"
  else
    ng "shot-$1"
  fi
}
adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP || true
sleep 1
SIZE_LINE=$(adb shell wm size 2>/dev/null | grep -o '[0-9]\+x[0-9]\+' | head -1)
SCR_W=$(echo "$SIZE_LINE" | cut -dx -f1); SCR_H=$(echo "$SIZE_LINE" | cut -dx -f2)
if [ -z "$SCR_W" ] || [ -z "$SCR_H" ]; then SCR_W=1080; SCR_H=2400; fi
DENSITY=$(adb shell wm density 2>/dev/null | grep -o '[0-9]\+' | head -1)
if [ -z "$DENSITY" ]; then DENSITY=420; fi
SX() { echo $(( $1 * SCR_W / 1080 )); }
SY() { echo $(( $1 * SCR_H / 2400 )); }
log "geometry: ${SCR_W}x${SCR_H} density=$DENSITY (d6 evidence v2)"
if [ -f "$APK" ]; then
  if adb install -r -g "$APK"; then ok "install"; else ng "install"; fi
else
  ng "apk-missing($APK)"
fi
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" "<map>" \
  '    <boolean name="battery_asked" value="true" />' \
  '    <boolean name="onboarded" value="true" />' "</map>" > "$OUT/ai_launcher.xml"
adb push "$OUT/ai_launcher.xml" /data/local/tmp/ai_launcher.xml > /dev/null 2>&1 || true
adb shell "run-as $PKG mkdir -p shared_prefs" || true
adb shell "run-as $PKG cp /data/local/tmp/ai_launcher.xml shared_prefs/ai_launcher.xml" || true
adb shell "run-as $PKG chmod 660 shared_prefs/ai_launcher.xml" || true
adb shell "run-as $PKG cat shared_prefs/ai_launcher.xml" 2>/dev/null | grep -q 'name="onboarded" value="true"' && ok "seed-prefs" || ng "seed-prefs"
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell pidof "$PKG" | grep -q . && ok "process-alive" || ng "process-alive"
adb shell cmd package set-home-activity "$PKG/.MainActivity" > /dev/null 2>&1 && ok "set-home" || ng "set-home"
sleep 1
adb shell input keyevent KEYCODE_HOME || true
sleep 2
band_open_d3() {
  adb shell am force-stop "$PKG" > /dev/null 2>&1 || true
  sleep 1
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
  sleep 3
  adb shell input keyevent KEYCODE_HOME || true
  sleep 1
  adb shell input swipe "$(SX 540)" "$(SY 2000)" "$(SX 540)" "$(SY 800)" 300 || true
  sleep 2
}
label_center_in() {
  # $1=dump $2=label $3=lo $4=hi -> "cx cy" 或返回 1
  local F="$1" LBL="$2" LO="$3" HI="$4" NODE NUMS X1 Y1 X2 Y2 CX CY
  NODE=$(grep -o "<node[^>]*text=\"$LBL\"[^>]*>" "$F" | head -1)
  [ -n "$NODE" ] || return 1
  NUMS=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
  X1=$(echo "$NUMS" | sed -n 1p); Y1=$(echo "$NUMS" | sed -n 2p)
  X2=$(echo "$NUMS" | sed -n 3p); Y2=$(echo "$NUMS" | sed -n 4p)
  [ -n "$X1" ] && [ -n "$Y2" ] || return 1
  CX=$(( (X1 + X2) / 2 )); CY=$(( (Y1 + Y2) / 2 ))
  if [ "$CY" -ge "$LO" ] && [ "$CY" -lt "$HI" ]; then echo "$CX $CY"; return 0; fi
  return 1
}
first_label_in() {
  # $1=dump $2=lo $3=hi -> "cx cy label"
  local F="$1" LO="$2" HI="$3" LBL CTR
  for LBL in Clock Calendar Camera Contacts Phone Settings Messaging Gallery Files Search; do
    CTR=$(label_center_in "$F" "$LBL" "$LO" "$HI" || true)
    if [ -n "$CTR" ]; then echo "$CTR $LBL"; return 0; fi
  done
  return 1
}
fg_opened() {
  local I FG=""
  for I in 1 2 3 4 5 6 7 8 9 10; do
    sleep 0.4
    FG=$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" || true)
    if [ -n "$FG" ] && ! echo "$FG" | grep -q "$PKG"; then
      log "band-ab: foreground after tap: $FG"
      return 0
    fi
  done
  log "band-ab: foreground still ours after tap: $FG"
  return 1
}
BAND_PX=$(( 88 * DENSITY / 160 ))
BAND_TOP=$(( SCR_H - BAND_PX ))
MID_LO=$(( SCR_H * 38 / 100 ))
MID_HI=$(( SCR_H * 62 / 100 ))
log "band-ab: band=[${BAND_TOP},${SCR_H}] mid=[${MID_LO},${MID_HI}]"
band_open_d3
# A：在带内找到第一个应用
A_HIT=""
for TRY in 1 2 3 4 5; do
  adb shell uiautomator dump /sdcard/ac_band.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ac_band.xml "$OUT/ac_band.xml" > /dev/null 2>&1 || true
  redump_if_empty ac_band
  if [ -f "$OUT/ac_band.xml" ] && grep -q 'resource-id="appcenter_settings"' "$OUT/ac_band.xml"; then
    A_HIT=$(first_label_in "$OUT/ac_band.xml" "$BAND_TOP" "$SCR_H" || true)
    [ -n "$A_HIT" ] && break
  fi
  adb shell input swipe "$(SX 540)" "$(SY 1300)" "$(SX 540)" "$(SY 800)" 300 || true
  sleep 1
done
if [ -z "$A_HIT" ]; then
  log "band-ab: no app row reached the band"
  ng "appcenter-band-row-tap"
  log "RESULT pass=$PASS fail=$FAIL"
  exit "$FAIL"
fi
AX=$(echo "$A_HIT" | cut -d' ' -f1); AY=$(echo "$A_HIT" | cut -d' ' -f2); AL=$(echo "$A_HIT" | cut -d' ' -f3)
log "band-ab: A band tap '$AL' at ($AX,$AY)"
shot "appcenter-band"
adb shell input swipe "$AX" "$AY" "$AX" "$AY" 150 || true
if fg_opened; then ok "appcenter-band-row-tap"; A_OK=1; else ng "appcenter-band-row-tap"; A_OK=0; fi
shot "appcenter-band-after"
# B：同一应用滚到中屏再点（A 已打开时先回 D3 基线）
if [ "$A_OK" = "1" ]; then band_open_d3; fi
B_HIT=""
for TRY in 1 2 3 4 5 6; do
  adb shell uiautomator dump /sdcard/ac_band_b.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ac_band_b.xml "$OUT/ac_band_b.xml" > /dev/null 2>&1 || true
  redump_if_empty ac_band_b
  if [ -f "$OUT/ac_band_b.xml" ] && grep -q 'resource-id="appcenter_settings"' "$OUT/ac_band_b.xml"; then
    B_HIT=$(label_center_in "$OUT/ac_band_b.xml" "$AL" "$MID_LO" "$MID_HI" || true)
    [ -n "$B_HIT" ] && break
  fi
  adb shell input swipe "$(SX 540)" "$(SY 1300)" "$(SX 540)" "$(SY 800)" 300 || true
  sleep 1
done
if [ -n "$B_HIT" ]; then
  BX=$(echo "$B_HIT" | cut -d' ' -f1); BY=$(echo "$B_HIT" | cut -d' ' -f2)
  log "band-ab: B mid tap same app '$AL' at ($BX,$BY)"
  adb shell input swipe "$BX" "$BY" "$BX" "$BY" 150 || true
  if fg_opened; then ok "appcenter-band-sameapp-mid"; else ng "appcenter-band-sameapp-mid"; fi
else
  log "band-ab: same app '$AL' never reached mid zone"
  ng "appcenter-band-sameapp-mid"
fi
log "RESULT pass=$PASS fail=$FAIL"
exit "$FAIL"
