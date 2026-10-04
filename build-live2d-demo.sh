#!/bin/bash
# Live2D MVP 一键构建 + 安装 + 启动（在有 Android SDK 的机器上跑）
set -e
cd "$(dirname "$0")"

echo "=== 1. 检查 SDK ==="
AAR="third_party/live2d/sdk/Core/android/Live2DCubismCore.aar"
if [ ! -f "$AAR" ]; then
  echo "❌ 缺少 $AAR"
  echo "   去 https://www.live2d.com/en/download/cubism-sdk/download-java/ 下载 Cubism SDK for Java 5-r.5"
  echo "   解压后把 CubismSdkForJava-5-r.5/ 整个放到 third_party/live2d/sdk/"
  exit 1
fi
echo "✅ Core AAR 就位"

echo "=== 2. 构建 debug 包 ==="
./gradlew :app:assembleDebug

APK=$(ls app/build/outputs/apk/debug/app-debug.apk)
echo "✅ $APK"

echo "=== 3. 安装到设备 ==="
adb install -r "$APK"

echo "=== 4. 启动 Live2D demo ==="
adb shell am start -n com.bobot.ailauncher/com.bobot.ailauncher.debug.Live2DDemoActivity

echo ""
echo "=== 验收 ==="
echo "- Hiyori 渲染出来，无破面/黑块"
echo "- 待机：呼吸、眨眼可见"
echo "- 点按身体 → 切换随机动作；拖拽 → 脸部跟随"
echo "- 查 Core 报错：adb logcat | grep -i cubism"
