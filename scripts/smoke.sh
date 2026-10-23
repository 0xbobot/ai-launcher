#!/usr/bin/env bash
# D-6 临时取证脚本 v3（取证后删除）：OUT 改 smoke-out 让标准 workflow 上传
# artifact（看截图实况）；流程：D3 -> 带内行上沿条（A1，条带上界之上 30px）
# -> 带内行标签中心（A）-> 点后 dump 全量打印带区节点 -> 同应用中屏（B）。
PKG="com.bobot.ailauncher"
APK="apk/app-debug.apk"
OUT="smoke-out"
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
log "geometry: ${SCR_W}x${SCR_H} density=$DENSITY (d6 evidence v3)"
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
label_bounds() {
  # $1=dump $2=label -> "cx cy y1 y2" 或返回 1
  local F="$1" LBL="$2" NODE NUMS X1 Y1 X2 Y2
  NODE=$(grep -o "<node[^>]*text=\"$LBL\"[^>]*>" "$F" | head -1)
  [ -n "$NODE" ] || return 1
  NUMS=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
  X1=$(echo "$NUMS" | sed -n 1p); Y1=$(echo "$NUMS" | sed -n 2p)
  X2=$(echo "$NUMS" | sed -n 3p); Y2=$(echo "$NUMS" | sed -n 4p)
  [ -n "$X1" ] && [ -n "$Y2" ] || return 1
  echo "$(( (X1 + X2) / 2 )) $(( (Y1 + Y2) / 2 )) $Y1 $Y2"
}
fg_opened() {
  local I FG=""
  for I in 1 2 3 4 5 6 7 8 9 10; do
    sleep 0.4
    FG=$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" || true)
    if [ -n "$FG" ] && ! echo "$FG" | grep -q "$PKG"; then
      log "band-v3: foreground after tap: $FG"
      return 0
    fi
  done
  log "band-v3: foreground still ours after tap: $FG"
  return 1
}
dump_d3() {
  adb shell uiautomator dump "/sdcard/$1.xml" > /dev/null 2>&1 || true
  adb pull "/sdcard/$1.xml" "$OUT/$1.xml" > /dev/null 2>&1 || true
  redump_if_empty "$1"
}
BAND_PX=$(( 88 * DENSITY / 160 ))
BAND_TOP=$(( SCR_H - BAND_PX ))
MID_LO=$(( SCR_H * 38 / 100 ))
MID_HI=$(( SCR_H * 62 / 100 ))
log "band-v3: band=[${BAND_TOP},${SCR_H}] mid=[${MID_LO},${MID_HI}]"
band_open_d3
dump_d3 ac_band
# 带内第一个应用（标签中心在带内）
A_LBL=""; A_CX=0; A_CY=0
for LBL in Clock Calendar Camera Contacts Phone Settings Messaging Gallery Files Search; do
  B=$(label_bounds "$OUT/ac_band.xml" "$LBL" || true)
  [ -n "$B" ] || continue
  CY=$(echo "$B" | cut -d' ' -f2)
  if [ "$CY" -ge "$BAND_TOP" ] && [ "$CY" -lt "$SCR_H" ]; then
    A_LBL="$LBL"; A_CX=$(echo "$B" | cut -d' ' -f1); A_CY="$CY"
    A_Y1=$(echo "$B" | cut -d' ' -f3); A_Y2=$(echo "$B" | cut -d' ' -f4)
    break
  fi
done
if [ -z "$A_LBL" ]; then
  log "band-v3: no app label in band at scroll0"
  ng "appcenter-band-row-tap"
  log "RESULT pass=$PASS fail=$FAIL"
  exit "$FAIL"
fi
log "band-v3: band app '$A_LBL' label center=($A_CX,$A_CY) label y=[$A_Y1,$A_Y2] band_top=$BAND_TOP"
shot "appcenter-band"
# A1：标签上沿之上、条带上界之下若有空隙则点行体（取行上沿+12px 处，
# 用标签 y1-40 近似行内偏上点）；点位必须 < BAND_TOP 才有判别意义
A1Y=$(( A_Y1 - 40 ))
if [ "$A1Y" -gt 0 ] && [ "$A1Y" -lt "$BAND_TOP" ]; then
  log "band-v3: A1 sliver tap '$A_LBL' at ($A_CX,$A1Y) (above strip top)"
  adb shell input swipe "$A_CX" "$A1Y" "$A_CX" "$A1Y" 150 || true
  if fg_opened; then
    log "band-v3: A1 opened the app (sliver above strip works)"
    band_open_d3
    dump_d3 ac_band
    B=$(label_bounds "$OUT/ac_band.xml" "$A_LBL" || true)
    A_CX=$(echo "$B" | cut -d' ' -f1); A_CY=$(echo "$B" | cut -d' ' -f2)
  else
    log "band-v3: A1 sliver tap did NOT open (dead zone extends above strip top?)"
  fi
fi
# A：标签中心（带内）
log "band-v3: A band tap '$A_LBL' at ($A_CX,$A_CY)"
adb shell input swipe "$A_CX" "$A_CY" "$A_CX" "$A_CY" 150 || true
if fg_opened; then ok "appcenter-band-row-tap"; A_OK=1; else ng "appcenter-band-row-tap"; A_OK=0; fi
shot "appcenter-band-after"
dump_d3 ac_band_after
# 点后带区节点全量打印（找展开卡/弹窗/提示类节点）
python3 - "$OUT/ac_band_after.xml" "$BAND_TOP" "$SCR_H" <<'PYEOF' || true
import re, sys
data = open(sys.argv[1], encoding="utf-8", errors="replace").read()
top, bot = int(sys.argv[2]), int(sys.argv[3])
for i, n in enumerate(re.findall(r"<node[^>]*>", data)):
    m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not m: continue
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    if y2 > top and y1 < bot:
        def attr(k):
            mm = re.search(k + r'="([^"]*)"', n)
            return mm.group(1) if mm else None
        print("band-zone: idx=%d bounds=[%d,%d][%d,%d] text=%r rid=%r clickable=%s class=%s" % (
            i, x1, y1, x2, y2, attr("text"), attr("resource-id"), attr("clickable"), attr("class")))
PYEOF
# B：同应用中屏（A 打开过则已在上方重开 D3 并滚回带位；此处统一处理）
if [ "$A_OK" = "1" ]; then band_open_d3; fi
B_HIT=""
for TRY in 1 2 3 4 5 6; do
  dump_d3 ac_band_b
  if [ -f "$OUT/ac_band_b.xml" ] && grep -q 'resource-id="appcenter_settings"' "$OUT/ac_band_b.xml"; then
    B=$(label_bounds "$OUT/ac_band_b.xml" "$A_LBL" || true)
    if [ -n "$B" ]; then
      BCY=$(echo "$B" | cut -d' ' -f2)
      if [ "$BCY" -ge "$MID_LO" ] && [ "$BCY" -lt "$MID_HI" ]; then B_HIT="$B"; break; fi
    fi
  fi
  adb shell input swipe "$(SX 540)" "$(SY 1300)" "$(SX 540)" "$(SY 800)" 300 || true
  sleep 1
done
if [ -n "$B_HIT" ]; then
  BX=$(echo "$B_HIT" | cut -d' ' -f1); BY=$(echo "$B_HIT" | cut -d' ' -f2)
  log "band-v3: B mid tap same app '$A_LBL' at ($BX,$BY)"
  adb shell input swipe "$BX" "$BY" "$BX" "$BY" 150 || true
  if fg_opened; then ok "appcenter-band-sameapp-mid"; else ng "appcenter-band-sameapp-mid"; fi
else
  log "band-v3: same app '$A_LBL' never reached mid zone"
  ng "appcenter-band-sameapp-mid"
fi
log "RESULT pass=$PASS fail=$FAIL"
exit "$FAIL"
