#!/usr/bin/env bash
# PROBE ONLY — 装包并启动应用后空载 12 分钟（不做任何手势/dump/断言）。
# 判定：模拟器全程存活 => exit 0（并记录应用进程是否中途消失）；
#       get-state/boot/system_server 失联 => PROBE-EMU-DEAD exit 1。
# 只存在于 probe-ci-* 分支，永不合入 main。
PKG="com.bobot.ailauncher"
APK="apk/app-debug.apk"
OUT="probe-out"
mkdir -p "$OUT"
echo "LEG=app-idle" > "$OUT/LEG.txt"
log() { echo "[probe-app $(date +%H:%M:%S)] $*"; }
timeout 60 adb wait-for-device > /dev/null 2>&1 || true
if [ -f "$APK" ]; then
  timeout 120 adb install -r "$APK" 2>&1 | tail -2 || true
else
  log "no APK at $APK, continuing without app"
fi
timeout 30 adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 10
APP_GONE=0
for i in $(seq 0 12); do
  T=$((i * 60))
  STATE=$(timeout 20 adb get-state 2>/dev/null | tr -d '\r')
  BOOT=$(timeout 20 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  SYS=$(timeout 20 adb shell pidof system_server 2>/dev/null | tr -d '\r')
  APP=$(timeout 20 adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')
  [ -z "$APP" ] && APP_GONE=1
  log "PROBE-HEARTBEAT t=${T}s state=${STATE:-?} boot=${BOOT:-?} sys_pid=${SYS:-?} app_pid=${APP:-?}"
  if [ "$STATE" != "device" ] || [ "$BOOT" != "1" ] || [ -z "$SYS" ]; then
    sleep 10
    STATE2=$(timeout 20 adb get-state 2>/dev/null | tr -d '\r')
    if [ "$STATE2" != "device" ]; then
      log "PROBE-EMU-DEAD t=${T}s app_gone=$APP_GONE"
      exit 1
    fi
  fi
  [ "$i" -lt 12 ] && sleep 60
done
log "PROBE-EMU-ALIVE-12MIN app_gone=$APP_GONE"
exit 0
