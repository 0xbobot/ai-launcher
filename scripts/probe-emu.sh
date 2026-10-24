#!/usr/bin/env bash
# PROBE ONLY — 模拟器存活探针（不装应用、不跑 smoke.sh）。
# 目的：隔离变量，只测“模拟器进程本身在某 GPU 模式下能不能活过 12 分钟”。
# 判定：12 次心跳全过 => PROBE-ALIVE-12MIN（exit 0）；
#       中途 get-state/boot/system_server 任一失联 => PROBE-DEAD（exit 1，
#       last_ok = 最后一次全过的心跳秒数，即死亡窗口下界）。
# 本文件只存在于 probe-ci-* 分支，永不合入 main。
OUT="probe-out"
mkdir -p "$OUT"
log() { echo "[probe $(date +%H:%M:%S)] $*"; }
log "host: nproc=$(nproc 2>/dev/null || echo ?) mem=$(free -m 2>/dev/null | awk '/^Mem:/{print $2}')MB"
df -h / 2>/dev/null | tail -1 | awk '{print "host disk: "$2" total "$4" avail"}'
log "avd dir listing:"
ls -la ~/.android/avd/ 2>/dev/null || log "no ~/.android/avd"
for cfg in ~/.android/avd/*.avd/config.ini; do
  [ -f "$cfg" ] || continue
  log "avd config $cfg:"
  grep -E "hw\.ramSize|disk\.dataPartition\.size|hw\.cpu\.ncore|image\.sysdir" "$cfg" 2>/dev/null || true
done
timeout 60 adb wait-for-device > /dev/null 2>&1 || true
LAST_OK=-1
for i in $(seq 0 12); do
  T=$((i * 60))
  STATE=$(timeout 20 adb get-state 2>/dev/null | tr -d '\r')
  BOOT=$(timeout 20 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  SYS=$(timeout 20 adb shell pidof system_server 2>/dev/null | tr -d '\r')
  SHOT_BYTES=0
  if timeout 30 adb exec-out screencap -p > "$OUT/shot-$i.png" 2>/dev/null; then
    SHOT_BYTES=$(stat -c%s "$OUT/shot-$i.png" 2>/dev/null || echo 0)
  fi
  log "PROBE-HEARTBEAT t=${T}s state=${STATE:-?} boot=${BOOT:-?} sys_pid=${SYS:-?} shot_bytes=$SHOT_BYTES"
  if [ "$STATE" = "device" ] && [ "$BOOT" = "1" ] && [ -n "$SYS" ] && [ "$SHOT_BYTES" -gt 1000 ]; then
    LAST_OK=$T
  else
    sleep 10
    STATE2=$(timeout 20 adb get-state 2>/dev/null | tr -d '\r')
    if [ "$STATE2" != "device" ]; then
      log "PROBE-DEAD last_ok=${LAST_OK}s failed_at=${T}s (recheck state=${STATE2:-?})"
      exit 1
    fi
    log "PROBE-HEARTBEAT-RECOVERED t=${T}s (one-off miss, continuing)"
    LAST_OK=$T
  fi
  [ "$i" -lt 12 ] && sleep 60
done
log "PROBE-ALIVE-12MIN last_ok=${LAST_OK}s"
exit 0
