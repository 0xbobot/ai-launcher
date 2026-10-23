#!/usr/bin/env bash
# 310 冻结探针 v5（probe-freeze-* 分支专用）：IME 必要性二分。
# 前情实证：进态点后 ≤1s 模拟器进程级死亡（logcat 无宾客侧 FATAL）；
# 冷 IME、深色各为加重因子；fix3 的 450ms 聚焦错峰未能救 D0（深+冷）。
# 未决问题：点火到底需不需要「进态同窗的 IME/聚焦」？
# 本探针在深色+冷条件下走一条的进态路径：应用中心
# 「问七仔」提交 → 协调器（无 Key）落 Error 在场态 → HomeHost 自动带起
# 输入态呈现（intentRequestFocus=false，胶囊/伴随七仔/hero 紧凑/Dock
# 让位全套合成，但不请求焦点、不弹 IME）。
# 若此路径死 → 进态合成内容本身点火（IME 无罪），转内容二分；
# 若此路径活 → 聚焦/IME 是必要共犯，修法走「聚焦与进态彻底脱钩」。
# 顺序：X3D 深色下应用中心聚焦（IME 冷秀基线）→ QZ 问七仔无聚焦进态
# → T1 同会话再点七仔格（带聚焦进态，对照）。
PKG="com.bobot.ailauncher"
APK="apk/app-debug.apk"
OUT="probe-out"
mkdir -p "$OUT"
PASS=0
FAIL=0

log() { echo "[probe] $*";}
ok() { PASS=$((PASS+1)); log "PASS: $1";}
ng() { FAIL=$((FAIL+1)); log "FAIL: $1";}

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP || true
sleep 2
adb logcat -c > /dev/null 2>&1 || true
adb logcat -v time > "$OUT/logcat-live.txt" 2>/dev/null &
LOGCAT_PID=$!

state() { adb get-state 2>/dev/null | tr -d '\r';}
curpid() { adb shell pidof "$PKG" 2>/dev/null | tr -d '\r';}
devok() { [ "$(state)" = "device" ];}
alive() { devok && [ -n "$(curpid)" ];}

log "apk check: cwd=$(pwd)"; ls apk/ 2>/dev/null || log "no apk dir at cwd"
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
echo "$(( (X1 + X2) / 2)) $(( (Y1 + Y2) / 2))"
}
center_text() {
local F="$1" TXT="$2" NODE NUMS X1 Y1 X2 Y2
[ -f "$F" ] || return 1
NODE=$(grep -o "<node[^>]*text=\"[^\"]*$TXT[^\"]*\"[^>]*>" "$F" | head -1)
[ -n "$NODE" ] || return 1
NUMS=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
X1=$(echo "$NUMS" | sed -n 1p); Y1=$(echo "$NUMS" | sed -n 2p)
X2=$(echo "$NUMS" | sed -n 3p); Y2=$(echo "$NUMS" | sed -n 4p)
[ -n "$X1" ] && [ -n "$Y2" ] || return 1
[ "$X2" -gt "$X1" ] && [ "$Y2" -gt "$Y1" ] || return 1
echo "$(( (X1 + X2) / 2)) $(( (Y1 + Y2) / 2))"
}
tap_xy() {
log "tap at ($1 $2)"
adb shell input swipe "$1" "$2" "$1" "$2" 150 || true
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
t1=$(date +%s%N); ms=$(( (t1 - t0) / 1000000))
log "shot $1 completed in ${ms}ms"
[ "$ms" -gt 20000] && return 2
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
for i in $(seq 1 15); do
sleep 1
if ! devok; then log "$L: device lost at +${i}s"; return 1; fi
if [ -z "$(curpid)" ]; then log "$L: app pid gone at +${i}s (device alive)"; return 2; fi
done
log "$L: alive through +15s"
return 0
}

# ================= 深色预置 + 冷启 =================
adb shell cmd uimode night yes || true
sleep 2
start_app
sleep 10
alive && ok "setup-alive" || ng "setup-alive"

# ================= X3D：深色下应用中心聚焦（IME 冷秀） =================
if devok; then
adb shell input swipe 540 2000 540 800 300 || true
sleep 3
if tdump "$OUT/x3d-ac.xml"; then
CTR=$(center_rid "$OUT/x3d-ac.xml" "intent_input")
if [ -n "$CTR" ]; then
tap_xy $CTR
death_watch "X3D"
case $? in
0) ok "X3D-dark-ime-show-survived";;
1) ng "X3D-DEVICE-LOST";;
2) ng "X3D-APP-DIED";;
esac
else
ng "X3D-field-not-found"
fi
else
ng "X3D-ac-dump-blocked"
fi
else
ng "X3D-skipped-device-lost"
fi

# ================= QZ：打字 → 问七仔 → 无聚焦自动进态 =================
if devok; then
adb shell input text "test" || true
sleep 2
if tdump "$OUT/qz-typed.xml"; then
CTR=$(center_text "$OUT/qz-typed.xml" "问七仔")
if [ -n "$CTR" ]; then
tap_xy $CTR
log "QZ: tapped ask-qizai row, watching auto entry (no focus)"
death_watch "QZ"
case $? in
0) ok "QZ-nofocus-entry-survived";;
1) ng "QZ-DEVICE-LOST";;
2) ng "QZ-APP-DIED";;
esac
if devok && tdump "$OUT/qz-in.xml" && grep -q 'resource-id="intent_input"' "$OUT/qz-in.xml"; then
ok "QZ-capsule-present(auto-entry confirmed)"
tshot "qz-input" && ok "QZ-screencap" || ng "QZ-screencap"
elif devok; then
ng "QZ-capsule-not-confirmed"
fi
else
ng "QZ-ask-row-not-found"
fi
else
ng "QZ-typed-dump-blocked"
fi
else
ng "QZ-skipped-device-lost"
fi

# ================= T1：同会话点七仔格（带聚焦进态对照） =================
if alive; then
# 先退出当前（自动带起的）输入态：点胶囊外空白（静态区）或 BACK 两次
adb shell input keyevent KEYCODE_BACK || true
sleep 1
adb shell input keyevent KEYCODE_HOME || true
sleep 2
if tdump "$OUT/t1-home.xml"; then
CTR=$(center_rid "$OUT/t1-home.xml" "dock_pet_cell")
if [ -n "$CTR" ]; then
tap_xy $CTR
death_watch "T1"
case $? in
0) ok "T1-tap-entry-survived";;
1) ng "T1-DEVICE-LOST";;
2) ng "T1-APP-DIED";;
esac
else
ng "T1-seat-not-found"
fi
else
ng "T1-home-dump-blocked"
fi
else
ng "T1-skipped-not-alive"
fi

adb shell cmd uimode night auto || true
kill "$LOGCAT_PID" > /dev/null 2>&1 || true
log "RESULT probe-freeze-v5 PASS=$PASS FAIL=$FAIL"
exit "$FAIL"
