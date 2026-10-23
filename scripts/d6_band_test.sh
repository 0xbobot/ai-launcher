#!/usr/bin/env bash
# D-6 临时取证脚本（缺陷线 fix-appcenter-bottom-band 专用，取证后删除）：
# 2026-10-23 当日 hosted runner 模拟器在 boot 后约 2.5 分钟 adb 掉线，全量
# smoke.sh 两轮均未跑到第 28 段。本脚本只做：装包 -> 种子 prefs -> 启动 ->
# 设默认桌面 -> 开 D3 -> 第 28 段同款带内/带外对照 + 覆盖节点打印，抢在
# 模拟器掉线前完成。断言名与第 28 段一致（appcenter-band-control /
# appcenter-band-row-tap）。
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
    log "redump_if_empty: $1"
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
SIZE_LINE=$(adb shell wm size 2>/dev/null | grep -i "override" | grep -o '[0-9]\+x[0-9]\+' | head -1)
if [ -z "$SIZE_LINE" ]; then
  SIZE_LINE=$(adb shell wm size 2>/dev/null | grep -o '[0-9]\+x[0-9]\+' | head -1)
fi
SCR_W=$(echo "$SIZE_LINE" | cut -dx -f1); SCR_H=$(echo "$SIZE_LINE" | cut -dx -f2)
if [ -z "$SCR_W" ] || [ -z "$SCR_H" ]; then SCR_W=1080; SCR_H=2400; fi
DENSITY=$(adb shell wm density 2>/dev/null | grep -o '[0-9]\+' | head -1)
if [ -z "$DENSITY" ]; then DENSITY=420; fi
SX() { echo $(( $1 * SCR_W / 1080 )); }
SY() { echo $(( $1 * SCR_H / 2400 )); }
log "geometry: ${SCR_W}x${SCR_H} density=$DENSITY (d6 minimal evidence)"
# --- 安装 + 种子 prefs（同 smoke.sh 第 1/2 段）---
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
if adb shell "run-as $PKG cat shared_prefs/ai_launcher.xml" 2>/dev/null | grep -q 'name="onboarded" value="true"'; then
  ok "seed-prefs"
else
  ng "seed-prefs"
fi
# --- 启动 + 设默认桌面（同 smoke.sh 第 3/4 段）---
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell pidof "$PKG" | grep -q . && ok "process-alive" || ng "process-alive"
adb shell cmd package set-home-activity "$PKG/.MainActivity" > /dev/null 2>&1 && ok "set-home" || ng "set-home"
sleep 1
adb shell input keyevent KEYCODE_HOME || true
sleep 2
# --- D-6 对照（与 smoke.sh 第 28 段同逻辑，节拍压缩）---
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
band_label_center() {
  local F="$1" LO="$2" HI="$3" LBL NODE NUMS X1 Y1 X2 Y2 CX CY
  for LBL in Clock Calendar Camera Contacts Phone Settings Messaging Gallery Files Search; do
    NODE=$(grep -o "<node[^>]*text=\"$LBL\"[^>]*>" "$F" | head -1)
    [ -n "$NODE" ] || continue
    NUMS=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    X1=$(echo "$NUMS" | sed -n 1p); Y1=$(echo "$NUMS" | sed -n 2p)
    X2=$(echo "$NUMS" | sed -n 3p); Y2=$(echo "$NUMS" | sed -n 4p)
    [ -n "$X1" ] && [ -n "$Y2" ] || continue
    CX=$(( (X1 + X2) / 2 )); CY=$(( (Y1 + Y2) / 2 ))
    if [ "$CY" -ge "$LO" ] && [ "$CY" -lt "$HI" ]; then
      echo "$CX $CY $LBL"
      return 0
    fi
  done
  return 1
}
band_fg_opened() {
  local I FG=""
  for I in 1 2 3 4 5 6 7 8 9 10; do
    sleep 0.4
    FG=$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" || true)
    if [ -n "$FG" ] && ! echo "$FG" | grep -q "$PKG"; then
      log "band-tap: foreground after tap: $FG"
      return 0
    fi
  done
  log "band-tap: foreground still ours after tap: $FG"
  return 1
}
BAND_PX=$(( 88 * DENSITY / 160 ))
BAND_TOP=$(( SCR_H - BAND_PX ))
MID_LO=$(( SCR_H * 38 / 100 ))
MID_HI=$(( SCR_H * 62 / 100 ))
log "band-tap: screen ${SCR_W}x${SCR_H} density=$DENSITY band=[${BAND_TOP},${SCR_H}] mid=[${MID_LO},${MID_HI}]"
band_open_d3
MID_HIT=""
for TRY in 1 2; do
  adb shell uiautomator dump /sdcard/ac_band_mid.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ac_band_mid.xml "$OUT/ac_band_mid.xml" > /dev/null 2>&1 || true
  redump_if_empty ac_band_mid
  if [ -f "$OUT/ac_band_mid.xml" ] && grep -q 'resource-id="appcenter_settings"' "$OUT/ac_band_mid.xml"; then
    MID_HIT=$(band_label_center "$OUT/ac_band_mid.xml" "$MID_LO" "$MID_HI" || true)
    [ -n "$MID_HIT" ] && break
  fi
  adb shell input swipe "$(SX 540)" "$(SY 1300)" "$(SX 540)" "$(SY 900)" 300 || true
  sleep 1
done
if [ -n "$MID_HIT" ]; then
  MX=$(echo "$MID_HIT" | cut -d' ' -f1); MY=$(echo "$MID_HIT" | cut -d' ' -f2); ML=$(echo "$MID_HIT" | cut -d' ' -f3)
  log "band-tap: control row '$ML' at ($MX,$MY)"
  adb shell input swipe "$MX" "$MY" "$MX" "$MY" 150 || true
  if band_fg_opened; then ok "appcenter-band-control"; else ng "appcenter-band-control"; fi
else
  log "band-tap: no mid-screen app row found (control unavailable)"
  ng "appcenter-band-control"
fi
band_open_d3
BAND_HIT=""
for TRY in 1 2 3 4 5; do
  adb shell uiautomator dump /sdcard/ac_band.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ac_band.xml "$OUT/ac_band.xml" > /dev/null 2>&1 || true
  redump_if_empty ac_band
  if [ -f "$OUT/ac_band.xml" ] && grep -q 'resource-id="appcenter_settings"' "$OUT/ac_band.xml"; then
    BAND_HIT=$(band_label_center "$OUT/ac_band.xml" "$BAND_TOP" "$SCR_H" || true)
    [ -n "$BAND_HIT" ] && break
  fi
  adb shell input swipe "$(SX 540)" "$(SY 1300)" "$(SX 540)" "$(SY 800)" 300 || true
  sleep 1
done
if [ -n "$BAND_HIT" ]; then
  BX=$(echo "$BAND_HIT" | cut -d' ' -f1); BY=$(echo "$BAND_HIT" | cut -d' ' -f2); BL=$(echo "$BAND_HIT" | cut -d' ' -f3)
  log "band-tap: band row '$BL' at ($BX,$BY) (band top=$BAND_TOP)"
  python3 - "$OUT/ac_band.xml" "$BX" "$BY" <<'PYEOF' || true
import re, sys
data = open(sys.argv[1], encoding="utf-8", errors="replace").read()
tx, ty = int(sys.argv[2]), int(sys.argv[3])
for i, n in enumerate(re.findall(r"<node[^>]*>", data)):
    m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not m: continue
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    if x1 <= tx <= x2 and y1 <= ty <= y2:
        def attr(k):
            mm = re.search(k + r'="([^"]*)"', n)
            return mm.group(1) if mm else None
        print("band-cover: idx=%d class=%s bounds=[%d,%d][%d,%d] text=%r rid=%r clickable=%s" % (
            i, attr("class"), x1, y1, x2, y2, attr("text"), attr("resource-id"), attr("clickable")))
PYEOF
  shot "appcenter-band"
  adb shell input swipe "$BX" "$BY" "$BX" "$BY" 150 || true
  if band_fg_opened; then
    ok "appcenter-band-row-tap"
  else
    ng "appcenter-band-row-tap"
  fi
  shot "appcenter-band-after"
else
  log "band-tap: no app row reached the bottom band after scrolls"
  ng "appcenter-band-row-tap"
fi
log "RESULT pass=$PASS fail=$FAIL"
exit "$FAIL"
