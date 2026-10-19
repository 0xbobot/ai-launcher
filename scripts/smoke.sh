#!/usr/bin/env bash
# AI Launcher 冒烟测试 v1：安装 -> 跳过引导 -> 设为默认桌面 -> 首页/扇形/点七仔/D3
# 跑在 reactivecircus/android-emulator-runner 的 script 里（adb 已就绪，工作目录为仓库根）。
# 每步容错：失败记 FAIL 继续往下走，不设 -e；最后汇总 PASS/FAIL 并以 FAIL 数作为退出码。
# 截图（smoke-out/）：home.png / fan.png / pet.png / appcenter.png，供人工验证。

PKG="com.bobot.ailauncher"
APK="apk/app-debug.apk"
OUT="smoke-out"
mkdir -p "$OUT"
PASS=0
FAIL=0

log()  { echo "[smoke] $*"; }
ok()   { PASS=$((PASS+1)); log "PASS: $1"; }
ng()   { FAIL=$((FAIL+1)); log "FAIL: $1"; }
shot() {
  if adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; then
    log "shot: $1.png"
  else
    ng "shot-$1"
  fi
}

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP || true
sleep 2

# --- 1. 安装 ---
if [ -f "$APK" ]; then
  if adb install -r -g "$APK"; then ok "install"; else ng "install"; fi
else
  ng "apk-missing($APK)"
fi

# --- 2. 启动：跳过 onboarding（每步右上角都有"跳过"）---
adb shell am start -n "$PKG/.MainActivity" || true
sleep 5
adb shell input tap 950 130 || true   # "跳过"
sleep 2
adb shell pidof "$PKG" | grep -q . && ok "process-alive" || ng "process-alive"

# --- 3. 设为默认桌面 + 按 Home ---
if adb shell cmd package set-home-activity "$PKG/.MainActivity"; then
  ok "set-home"
else
  ng "set-home"
fi
sleep 1
adb shell input keyevent KEYCODE_HOME || true
sleep 4
shot "home"

# --- 4. 左滑展开盲操扇形（pixel_6 1080x2400；封面区纯展示无交互，手势直达首页手势层）---
adb shell input swipe 900 300 200 300 300 || true
sleep 2
shot "fan"

# --- 收起扇形 ---
adb shell input keyevent KEYCODE_BACK || true
sleep 1

# --- 5. 点七仔（宠物区中央；果冻反馈+开心表情）---
adb shell input tap 540 1030 || true
sleep 2
shot "pet"

# --- 6. 上滑开 D3 应用中心 ---
adb shell input swipe 540 2000 540 800 300 || true
sleep 3
shot "appcenter"

# --- 7. crash 检查（只看本包 FATAL，避免系统噪音）---
if adb logcat -d 2>/dev/null | grep -i "FATAL EXCEPTION" | grep -q "$PKG"; then
  ng "no-fatal-crash"
  adb logcat -d 2>/dev/null | grep -i -A15 "FATAL EXCEPTION" | head -30 || true
else
  ok "no-fatal-crash"
fi

log "RESULT pass=$PASS fail=$FAIL"
exit "$FAIL"
