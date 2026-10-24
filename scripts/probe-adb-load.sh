#!/usr/bin/env bash
# PROBE ONLY — 不装应用，以 smoke.sh 的速率做 adb 流量（每 10 秒一次 dump+screencap），持续 12 分钟。
# 判定：全程存活 => exit 0；中途失联 => PROBE-DEAD exit 1。
# 只存在于 probe-ci-* 分支，永不合入 main。
OUT="probe-out"
mkdir -p "$OUT"
echo "LEG=adb-load" > "$OUT/LEG.txt"
log() { echo "[probe-load $(date +%H:%M:%S)] $*"; }
timeout 60 adb wait-for-device > /dev/null 2>&1 || true
for i in $(seq 0 72); do
  T=$((i * 10))
  STATE=$(timeout 20 adb get-state 2>/dev/null | tr -d '\r')
  if [ "$STATE" != "device" ]; then
    log "PROBE-DEAD t=${T}s state=${STATE:-?}"
    exit 1
  fi
  timeout 30 adb shell uiautomator dump /sdcard/load_dump.xml > /dev/null 2>&1 || true
  timeout 30 adb pull /sdcard/load_dump.xml "$OUT/load_dump.xml" > /dev/null 2>&1 || true
  SZ=0
  if timeout 30 adb exec-out screencap -p > "$OUT/load_shot.png" 2>/dev/null; then
    SZ=$(stat -c%s "$OUT/load_shot.png" 2>/dev/null || echo 0)
  fi
  if [ $((i % 12)) = 0 ]; then
    log "PROBE-HEARTBEAT t=${T}s shot_bytes=$SZ dump_bytes=$(stat -c%s "$OUT/load_dump.xml" 2>/dev/null || echo 0)"
  fi
  [ "$SZ" -lt 1000 ] && log "PROBE-WARN t=${T}s small/empty shot bytes=$SZ"
  sleep 10
done
log "PROBE-ALIVE-12MIN-ADB-LOAD"
exit 0
