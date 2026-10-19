#!/usr/bin/env bash
# AI Launcher v1 冒烟测试：安装 -> 设为默认桌面 -> 左滑扇形 -> 点七仔
# 跑在 reactivecircus/android-emulator-runner 的 script 里，adb 已可用。
# 硬断言：进程存活 + 本包无 FATAL crash；每步截图存 smoke-out/。
set -euo pipefail

PKG="com.bobot.ailauncher"
APK="app/build/outputs/apk/debug/app-debug.apk"
OUT="${SMOKE_OUT:-smoke-out}"
mkdir -p "$OUT"

log() { echo "[smoke] $*"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; log "screenshot: $1.png"; }

assert_alive() {
  if ! adb shell pidof "$PKG" | grep -q .; then
    log "FATAL: $PKG 进程不存在"
    adb shell pidof "$PKG" || true
    exit 1
  fi
}

check_crash() {
  # 只看我们自己包的 FATAL，避免系统噪音
  if adb logcat -d 2>/dev/null | grep -i "FATAL EXCEPTION" | grep -q "$PKG"; then
    log "FATAL: logcat 发现 $PKG crash"
    adb logcat -d 2>/dev/null | grep -i -B2 -A20 "FATAL EXCEPTION" | grep "$PKG" | head -40
    exit 1
  fi
}

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP || true
# 关动画：截图更稳定、手势更快
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
sleep 2

log "install $APK"
adb install -r -g "$APK"

# 01 启动
adb shell am start -n "$PKG/.MainActivity"
sleep 4
assert_alive
check_crash
shot "01-launch"

# 02 设为默认桌面 + 按 Home
adb shell cmd package set-home-activity "$PKG/.MainActivity"
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 4
assert_alive
check_crash
shot "02-home"

# 03 首页左滑（从右往左）触发盲操扇形
adb shell input swipe 900 1100 200 1100 300
sleep 2
assert_alive
check_crash
shot "03-fan-sector"

# 收起扇形（Back）
adb shell input keyevent KEYCODE_BACK
sleep 1

# 04 点七仔（底部中央；pixel_6 1080x2400 估算坐标，v1 近似）
adb shell input tap 540 2000
sleep 2
assert_alive
check_crash
shot "04-tap-qizai"

check_crash
log "smoke PASS"
