#!/usr/bin/env bash
# AI Launcher 冒烟测试 v1：安装 -> 预写 prefs 跳过引导/对话框 -> 设为默认桌面 -> 首页/扇形/点七仔/D3
# 跑在 reactivecircus/android-emulator-runner 的 script 里（adb 已就绪，工作目录为仓库根）。
# 每步容错：失败记 FAIL 继续往下走，不设 -e；最后汇总 PASS/FAIL 并以 FAIL 数作为退出码。
# 截图（smoke-out/）：home.png / fan.png / pet.png / appcenter.png / dark-home.png / dark-appcenter.png /
# dark-settings.png（R3-B 设置页深色）/ intent-focus.png / intent-present.png（R3-C 七仔反应）/
# font13-home.png / font13-appcenter.png / font15-home.png / font15-appcenter.png（R3-A 字体缩放）/
# intent-home.png / intent-nokey.png / intent-local.png / intent-opened.png / appcenter2.png /
# appcenter-filter.png / appcenter-cleared.png / intent-tpl-giant.png / intent-tpl-headline.png /
# intent-tpl-minimal.png（含各自 -local 结果条图），供人工验证。
# 首启对话框（onboarding 引导、低电量保护询问）用 run-as 预写 SharedPreferences 跳过，
# 不依赖 UI 坐标；若预写失败，用固定坐标点按做兜底（pixel_6 1080x2400，对话框位置稳定）。
# R4 矩阵化：写死坐标一律按 1080x2400 基准经 SX/SY 按实际分辨率换算；SMOKE_FONT_SCALE /
# SMOKE_NIGHT / SMOKE_LABEL 环境参数供 smoke-matrix.yml 多组合复用（不带 env 时行为不变）；
# 第 15 段为 a11y 触控目标尺寸断言（scripts/a11y_check.py 量 uiautomator dump）。

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

# --- 0. 屏幕几何（R4）：基准 1080x2400 的写死坐标经 SX/SY 换算到实际分辨率；密度供 a11y 的 px->dp ---
SIZE_LINE=$(adb shell wm size 2>/dev/null | grep -o '[0-9]\+x[0-9]\+' | head -1)
SCR_W=$(echo "$SIZE_LINE" | cut -dx -f1); SCR_H=$(echo "$SIZE_LINE" | cut -dx -f2)
if [ -z "$SCR_W" ] || [ -z "$SCR_H" ]; then SCR_W=1080; SCR_H=2400; fi
DENSITY=$(adb shell wm density 2>/dev/null | grep -o '[0-9]\+' | head -1)
if [ -z "$DENSITY" ]; then DENSITY=420; fi
SX() { echo $(( $1 * SCR_W / 1080 )); }
SY() { echo $(( $1 * SCR_H / 2400 )); }
log "geometry: ${SCR_W}x${SCR_H} density=$DENSITY label=${SMOKE_LABEL:-base} font=${SMOKE_FONT_SCALE:-default} night=${SMOKE_NIGHT:-no}"

# --- 1. 安装 ---
if [ -f "$APK" ]; then
  if adb install -r -g "$APK"; then ok "install"; else ng "install"; fi
else
  ng "apk-missing($APK)"
fi

# --- 2. 预写 SharedPreferences：跳过 onboarding（onboarded）+ 低电量保护询问（battery_asked）---
# MainActivity 读 ai_launcher SP 的这两个 key；debug 包可用 run-as 写入（文件归属 app uid）。
# 写法：本地生成 xml -> adb push 到 /data/local/tmp -> run-as cp 进 app 私有目录（避免 heredoc 嵌套引号坑）。
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" "<map>" \
  '    <boolean name="battery_asked" value="true" />' \
  '    <boolean name="onboarded" value="true" />' "</map>" > "$OUT/ai_launcher.xml"
adb push "$OUT/ai_launcher.xml" /data/local/tmp/ai_launcher.xml
adb shell "run-as $PKG mkdir -p shared_prefs"
adb shell "run-as $PKG cp /data/local/tmp/ai_launcher.xml shared_prefs/ai_launcher.xml"
adb shell "run-as $PKG chmod 660 shared_prefs/ai_launcher.xml"
if adb shell "run-as $PKG cat shared_prefs/ai_launcher.xml" 2>/dev/null | grep -q 'name="onboarded" value="true"'; then
  ok "seed-prefs"
else
  ng "seed-prefs"
  # 兜底：固定坐标点掉（pixel_6 1080x2400，对话框位置稳定；uiautomator 解析曾翻车，不再用）
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
  sleep 5
  adb shell input tap "$(SX 950)" "$(SY 130)" || true   # onboarding 右上角"跳过"
  sleep 3
  adb shell input tap "$(SX 585)" "$(SY 1310)" || true # 低电量对话框"不用"
  sleep 2
fi

# --- 2b. 矩阵环境（R4）：字体/夜间在启动前设好，首页第一帧即目标环境（不带 env 时跳过）---
if [ -n "$SMOKE_FONT_SCALE" ]; then
  adb shell settings put system font_scale "$SMOKE_FONT_SCALE" > /dev/null 2>&1 || true
  log "ambient font_scale=$SMOKE_FONT_SCALE"
fi
if [ "$SMOKE_NIGHT" = "yes" ]; then
  adb shell cmd uimode night yes > /dev/null 2>&1 || true
  sleep 1
  log "ambient night mode on"
fi

# --- 3. 启动 ---
adb shell am start -n "$PKG/.MainActivity" || true
sleep 5
adb shell pidof "$PKG" | grep -q . && ok "process-alive" || ng "process-alive"

# --- 4. 设为默认桌面 + 按 Home ---
if adb shell cmd package set-home-activity "$PKG/.MainActivity"; then
  ok "set-home"
else
  ng "set-home"
fi
sleep 1
adb shell input keyevent KEYCODE_HOME || true
sleep 4
shot "home"

# --- 5. 左滑展开盲操扇形（基准 1080x2400 坐标，经 SX/SY 换算）
# 注意：手势检测器在 HomeScreen 根 Box 上，TODAY 卡片/Dock/七仔会先消费触摸，
# 必须落在空白背景上（y=1400 为 TODAY 卡片与 Dock 之间的空白）；落在卡片上会被吃掉。
# 注意 2：扇形只在按住拖动期间可见——松手即触发（段外/拍照段进相机、⊕段进编辑）。
# 所以用后台长 swipe（3 秒），在拖动中途截图，才能抓到展开中的扇形。
adb shell input swipe "$(SX 900)" "$(SY 1400)" "$(SX 300)" "$(SY 1400)" 4000 &
SWIPE_PID=$!
sleep 1.5
shot "fan"
# R4 a11y：拖动中 dump 一次供第 15 段查扇形段语义节点（扇形为 Canvas 手势面，
# dump 里无段节点属预期内发现，只记录不判失败）；dump 赶不上拖动窗口时 pull 失败自动跳过。
adb shell uiautomator dump /sdcard/fan_dump.xml > /dev/null 2>&1 || true
wait $SWIPE_PID || true
adb pull /sdcard/fan_dump.xml "$OUT/fan_dump.xml" > /dev/null 2>&1 || true
# 清理松手后的状态：扇形编辑的 bottom sheet（选择应用/快捷方式）BACK 关不掉，
# 本 App 就是桌面、HOME 也重置不了 Compose 状态（R1 二、三轮实测，清理 tap 还会误触推进流程）
# → force-stop 重启进程，确定性回到首页基线
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2

# --- 6. 点七仔（宠物区中央；果冻反馈+开心表情 1.2 秒，0.5 秒后截图抓反应中途）---
# 坐标按当前首页布局实测：七仔在 home 截图约 (540,950)；旧坐标 840,630 点空导致 pet 与 home 同帧（R1 实测）。
# press 150ms 模拟真手指（瞬时 tap 会被首页手势层取消，见第 9 段注）
adb shell input swipe "$(SX 540)" "$(SY 950)" "$(SX 540)" "$(SY 950)" 150 || true
sleep 0.7
shot "pet"

# --- 7. 上滑开 D3 应用中心 ---
adb shell input swipe "$(SX 540)" "$(SY 2000)" "$(SX 540)" "$(SY 800)" 300 || true
sleep 3
shot "appcenter"

# --- 8. 深色模式截图（R1）：切系统夜间模式 -> 回首页截 dark-home -> 上滑截 dark-appcenter -> 复原 ---
adb shell cmd uimode night yes || true
sleep 3
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
shot "dark-home"
adb shell input swipe "$(SX 540)" "$(SY 2000)" "$(SX 540)" "$(SY 800)" 300 || true
sleep 3
shot "dark-appcenter"
# R3-B：设置页深色亲验——点应用中心齿轮（content-desc="设置"，dump 定位，兜底坐标按
# dark-appcenter 截图实测齿轮约 (985,505)）进设置页，断言"让七仔更聪明"卡在场后截图，BACK 返回。
SGX=$(SX 985); SGY=$(SY 505)
adb shell uiautomator dump /sdcard/set_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/set_dump.xml "$OUT/set_dump.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/set_dump.xml" ]; then
  SNODE=$(grep -o '<node[^>]*content-desc="设置"[^>]*>' "$OUT/set_dump.xml" | head -1)
  if [ -n "$SNODE" ]; then
    SNUMS=$(echo "$SNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    SX1=$(echo "$SNUMS" | sed -n 1p); SY1=$(echo "$SNUMS" | sed -n 2p)
    SX2=$(echo "$SNUMS" | sed -n 3p); SY2=$(echo "$SNUMS" | sed -n 4p)
    if [ -n "$SX1" ] && [ -n "$SY2" ]; then
      SGX=$(( (SX1 + SX2) / 2 )); SGY=$(( (SY1 + SY2) / 2 ))
      log "settings gear at ($SGX,$SGY)"
    fi
  fi
fi
adb shell input swipe "$SGX" "$SGY" "$SGX" "$SGY" 150 || true
sleep 3
shot "dark-settings"
adb shell uiautomator dump /sdcard/set_dump2.xml > /dev/null 2>&1 || true
adb pull /sdcard/set_dump2.xml "$OUT/set_dump2.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/set_dump2.xml" ] && grep -q "让七仔更聪明" "$OUT/set_dump2.xml"; then
  ok "dark-settings"
else
  ng "dark-settings"
fi
adb shell input keyevent KEYCODE_BACK || true
sleep 2
if [ "$SMOKE_NIGHT" != "yes" ]; then adb shell cmd uimode night auto || true; fi
sleep 2

# --- 9. 意图入口（R2 UI）：回首页截含胶囊图 -> 点胶囊输入一句话 -> 提交 -> 截无 Key 错误态 ---
# CI 全新环境未配大模型 Key：提交后应进 Error 态（"先在设置里配置大模型 Key"小条 + 去设置），不崩溃、不误启 App。
# 胶囊位置随封面模板（3 模板按日轮换）变化：用 uiautomator dump 找占位文案"想做什么？"的 bounds 取中心点，
# 找不到时用固定坐标兜底（pixel_6 1080x2400，按 home 截图实测胶囊中心约 y=1247）。输入用英文：adb input text
# 打不了中文，走的是同一条无 Key 路径，不影响断言目的。
# 基线：上一段结束时停在 D3 应用中心（Dock 状态在 MainActivity 内，am start/HOME 回不到首页视图，
# 首轮实测 intent-home 拍成 D3、点按误开 Contacts）→ 同第 5 段清理法：force-stop 重启回首页基线。
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
shot "intent-home"
CX=$(SX 540); CY=$(SY 1247); BX=$(SX 928); BY=$(SY 1246)
adb shell uiautomator dump /sdcard/intent_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/intent_dump.xml "$OUT/intent_dump.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/intent_dump.xml" ]; then
  NODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/intent_dump.xml" | head -1)
  if [ -n "$NODE" ]; then
    NUMS=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    X1=$(echo "$NUMS" | sed -n 1p); Y1=$(echo "$NUMS" | sed -n 2p)
    X2=$(echo "$NUMS" | sed -n 3p); Y2=$(echo "$NUMS" | sed -n 4p)
    if [ -n "$X1" ] && [ -n "$Y2" ]; then
      CX=$(( (X1 + X2) / 2 )); CY=$(( (Y1 + Y2) / 2 ))
      log "intent capsule at ($CX,$CY)"
    fi
  fi
  # 提交按钮中心同法从 dump 取（content-desc="提交" 的图标节点 ≈ 按钮中心）
  BNODE=$(grep -o '<node[^>]*content-desc="提交"[^>]*>' "$OUT/intent_dump.xml" | head -1)
  if [ -n "$BNODE" ]; then
    BNUMS=$(echo "$BNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    BX1=$(echo "$BNUMS" | sed -n 1p); BY1=$(echo "$BNUMS" | sed -n 2p)
    BX2=$(echo "$BNUMS" | sed -n 3p); BY2=$(echo "$BNUMS" | sed -n 4p)
    if [ -n "$BX1" ] && [ -n "$BY2" ]; then
      BX=$(( (BX1 + BX2) / 2 )); BY=$(( (BY1 + BY2) / 2 ))
      log "intent submit button at ($BX,$BY)"
    fi
  fi
fi
# 点按用带时长的 press（swipe 同点 150ms）模拟真手指：adb 瞬时 tap 在本应用首页
# 手势层下会被取消（实测：瞬时 tap 点提交/点宠物均不触发，IME Done 与长按压正常）。
adb shell input swipe "$CX" "$CY" "$CX" "$CY" 150 || true
sleep 2
adb shell input text "open%swechat" || true
sleep 1
# 先收键盘再点提交：键盘弹起时窗口 resize、首页内容上移，胶囊不在 dump 坐标处
# （二轮实测：文字输进去了但提交点空、无错误条）。BACK 只收键盘，输入与焦点保留。
adb shell input keyevent KEYCODE_BACK || true
sleep 1
adb shell input swipe "$BX" "$BY" "$BX" "$BY" 150 || true
sleep 2
shot "intent-nokey"
# 点后 dump 层级复核错误条确实进了 UI 树（截图+层级双证据）；没有则再走 IME Done 复验并如实记录。
adb shell uiautomator dump /sdcard/intent_after.xml > /dev/null 2>&1 || true
adb pull /sdcard/intent_after.xml smoke-out/intent_after.xml > /dev/null 2>&1 || true
if grep -q "先在设置里配置大模型" smoke-out/intent_after.xml 2>/dev/null; then
  echo "[smoke] intent error strip: FOUND in hierarchy after button press"
else
  echo "[smoke] intent error strip: NOT in hierarchy after button press; trying IME Done (keyevent 66)"
  adb shell input keyevent 66 || true
  sleep 2
  shot "intent-done"
  adb shell uiautomator dump /sdcard/intent_after2.xml > /dev/null 2>&1 || true
  adb pull /sdcard/intent_after2.xml smoke-out/intent_after2.xml > /dev/null 2>&1 || true
  if grep -q "先在设置里配置大模型" smoke-out/intent_after2.xml 2>/dev/null; then
    echo "[smoke] intent error strip: FOUND after IME Done"
  else
    echo "[smoke] intent error strip: NOT FOUND after IME Done either"
  fi
fi
adb shell pidof "$PKG" | grep -q . && ok "intent-alive" || ng "intent-alive"

# --- 10. 本地直达层 + 应用中心同组件（R2 UI 10-21 新规格）---
# 首页打字出本地结果：点胶囊输 "cam" -> 七仔结果条应出现 Camera（英文标签，名称包含匹配）；
# press 点第一个结果 -> 前台包应切换。应用中心：顶部应换成新胶囊（占位"想做什么？"），
# 输 "cam" 后网格过滤出 Camera 且出现「问七仔」行。基线用 force-stop 重启回首页（同第 9 段法）。
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
# 定位胶囊（dump 找占位，兜底坐标同第 9 段）
LCX=$(SX 540); LCY=$(SY 1247)
adb shell uiautomator dump /sdcard/local_dump0.xml > /dev/null 2>&1 || true
adb pull /sdcard/local_dump0.xml "$OUT/local_dump0.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/local_dump0.xml" ]; then
  NODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/local_dump0.xml" | head -1)
  if [ -n "$NODE" ]; then
    NUMS=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    X1=$(echo "$NUMS" | sed -n 1p); Y1=$(echo "$NUMS" | sed -n 2p)
    X2=$(echo "$NUMS" | sed -n 3p); Y2=$(echo "$NUMS" | sed -n 4p)
    if [ -n "$X1" ] && [ -n "$Y2" ]; then
      LCX=$(( (X1 + X2) / 2 )); LCY=$(( (Y1 + Y2) / 2 ))
      log "local capsule at ($LCX,$LCY)"
    fi
  fi
fi
adb shell input swipe "$LCX" "$LCY" "$LCX" "$LCY" 150 || true
sleep 2
adb shell input text "cam" || true
sleep 1
# 收键盘再截图/定位：键盘会遮挡结果条下沿且 dump 坐标会变（同第 9 段教训）
adb shell input keyevent KEYCODE_BACK || true
sleep 1
shot "intent-local"
adb shell uiautomator dump /sdcard/local_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/local_dump.xml "$OUT/local_dump.xml" > /dev/null 2>&1 || true
RCX=""; RCY=""
if [ -f "$OUT/local_dump.xml" ]; then
  # 结果条里的应用名节点（首页基线 UI 无其它 "Camera" 文本）
  RNODE=$(grep -o '<node[^>]*text="Camera"[^>]*>' "$OUT/local_dump.xml" | head -1)
  if [ -n "$RNODE" ]; then
    ok "intent-local-results"
    RNUMS=$(echo "$RNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    RX1=$(echo "$RNUMS" | sed -n 1p); RY1=$(echo "$RNUMS" | sed -n 2p)
    RX2=$(echo "$RNUMS" | sed -n 3p); RY2=$(echo "$RNUMS" | sed -n 4p)
    if [ -n "$RX1" ] && [ -n "$RY2" ]; then
      RCX=$(( (RX1 + RX2) / 2 )); RCY=$(( (RY1 + RY2) / 2 ))
      log "camera result label at ($RCX,$RCY)"
    fi
  else
    ng "intent-local-results"
  fi
fi
# press 点结果 -> 相机应被打开。先清 logcat 再点；本模拟器 -camera-back none 无相机硬件，
# AOSP Camera2 可能被拉起后立即退出（3 秒后单次前台采样会错过），故双路取证：
# 前台轮询 6 秒任一命中 camera，或 logcat 出现目标包的 START 派发记录，都算链路通。
if [ -n "$RCX" ]; then
  adb logcat -c > /dev/null 2>&1 || true
  adb shell input swipe "$RCX" "$RCY" "$RCX" "$RCY" 150 || true
  OPENED=0
  FG=""
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    sleep 0.5
    FG=$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" || true)
    if echo "$FG" | grep -qi "camera"; then OPENED=1; break; fi
  done
  shot "intent-opened"
  log "foreground after open: $FG"
  if [ "$OPENED" = "1" ]; then
    ok "intent-open-app"
  elif adb logcat -d 2>/dev/null | grep "START u0" | grep -qi "camera"; then
    log "camera start dispatched (foreground did not stick: no camera HAL in this emulator)"
    ok "intent-open-app"
  else
    ng "intent-open-app"
  fi
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
else
  ng "intent-open-app"
fi
# 应用中心同组件：上滑开 D3（同第 7 段基线），顶部应是新胶囊而非旧搜索框
adb shell input swipe "$(SX 540)" "$(SY 2000)" "$(SX 540)" "$(SY 800)" 300 || true
sleep 3
shot "appcenter2"
ACX=$(SX 540); ACY=$(SY 330)
adb shell uiautomator dump /sdcard/ac_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/ac_dump.xml "$OUT/ac_dump.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/ac_dump.xml" ]; then
  if grep -q 'text="想做什么？"' "$OUT/ac_dump.xml"; then
    ok "appcenter-capsule"
  else
    ng "appcenter-capsule"
  fi
  ANODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/ac_dump.xml" | head -1)
  if [ -n "$ANODE" ]; then
    ANUMS=$(echo "$ANODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    AX1=$(echo "$ANUMS" | sed -n 1p); AY1=$(echo "$ANUMS" | sed -n 2p)
    AX2=$(echo "$ANUMS" | sed -n 3p); AY2=$(echo "$ANUMS" | sed -n 4p)
    if [ -n "$AX1" ] && [ -n "$AY2" ]; then
      ACX=$(( (AX1 + AX2) / 2 )); ACY=$(( (AY1 + AY2) / 2 ))
      log "appcenter capsule at ($ACX,$ACY)"
    fi
  fi
fi
adb shell input swipe "$ACX" "$ACY" "$ACX" "$ACY" 150 || true
sleep 2
adb shell input text "cam" || true
sleep 1
adb shell input keyevent KEYCODE_BACK || true
sleep 2
shot "appcenter-filter"
adb shell uiautomator dump /sdcard/ac_dump2.xml > /dev/null 2>&1 || true
adb pull /sdcard/ac_dump2.xml "$OUT/ac_dump2.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/ac_dump2.xml" ]; then
  if grep -q '问七仔' "$OUT/ac_dump2.xml"; then
    ok "appcenter-ask-row"
  else
    ng "appcenter-ask-row"
  fi
  if grep -q 'text="Camera"' "$OUT/ac_dump2.xml"; then
    ok "appcenter-filter"
  else
    ng "appcenter-filter"
  fi
fi
# ✕ 一键清空（10-21 追加，应用中心变体专属）：ac_dump2 里应有 content-desc="清空" 节点；
# press 点它后文本清空——占位"想做什么？"回来、「问七仔」行消失（网格恢复由截图人工验）。
KX=""; KY=""
if [ -f "$OUT/ac_dump2.xml" ]; then
  KNODE=$(grep -o '<node[^>]*content-desc="清空"[^>]*>' "$OUT/ac_dump2.xml" | head -1)
  if [ -n "$KNODE" ]; then
    ok "appcenter-clear-btn"
    KNUMS=$(echo "$KNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    KX1=$(echo "$KNUMS" | sed -n 1p); KY1=$(echo "$KNUMS" | sed -n 2p)
    KX2=$(echo "$KNUMS" | sed -n 3p); KY2=$(echo "$KNUMS" | sed -n 4p)
    if [ -n "$KX1" ] && [ -n "$KY2" ]; then
      KX=$(( (KX1 + KX2) / 2 )); KY=$(( (KY1 + KY2) / 2 ))
      log "appcenter clear button at ($KX,$KY)"
    fi
  else
    ng "appcenter-clear-btn"
  fi
fi
if [ -n "$KX" ]; then
  adb shell input swipe "$KX" "$KY" "$KX" "$KY" 150 || true
  sleep 2
  shot "appcenter-cleared"
  adb shell uiautomator dump /sdcard/ac_dump3.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ac_dump3.xml "$OUT/ac_dump3.xml" > /dev/null 2>&1 || true
  if [ -f "$OUT/ac_dump3.xml" ] && grep -q 'text="想做什么？"' "$OUT/ac_dump3.xml" && ! grep -q '问七仔' "$OUT/ac_dump3.xml"; then
    ok "appcenter-cleared"
  else
    ng "appcenter-cleared"
  fi
else
  ng "appcenter-cleared"
fi

# --- 11. 封面 3 模板下的胶囊 + 结果条（R2 UI 10-21 追加：Bob 提醒主页有 3 模板轮换）---
# 模板选择是纯日期逻辑：CoverHero 里 coverTemplateFor() = DAY_OF_YEAR % 3 ->
# values()[0]=GIANT_TIME（时间巨像）/ [1]=HEADLINE（头条简报）/ [2]=MINIMAL（留白时刻），不走 prefs。
# 故靠改模拟器日期（今天起 +0/+1/+2 天必覆盖三余数）逐个切换；每次 force-stop 重启回首页，
# 截 intent-tpl-<name>.png 并 dump 断言胶囊在场，再输 "cam" 截 -local 图并断言结果条 Camera。
# 日期改不动（如镜像无权限）时只 log 记录、不判失败——此时以代码级证据为准：
# PetZone 封面 Column 里 CoverHero 之后隔 16dp 无条件调用 coverIntentSlot()，三模板共用同一渲染路径。
adb shell settings put global auto_time 0 > /dev/null 2>&1 || true
TPL_ALL=1
for OFF in 0 1 2; do
  TGT=$(date -d "+$OFF day" +%m%d1000%Y.00)
  DOY=$(date -d "+$OFF day" +%j)
  RES=$((10#$DOY % 3))
  case $RES in
    0) TPL=giant ;;
    1) TPL=headline ;;
    *) TPL=minimal ;;
  esac
  adb shell date "$TGT" > /dev/null 2>&1 || true
  sleep 1
  NOW_DOY=$(adb shell date +%j 2>/dev/null | tr -d '\r')
  if [ "$NOW_DOY" != "$DOY" ]; then
    adb root > /dev/null 2>&1 || true
    adb wait-for-device > /dev/null 2>&1 || true
    sleep 2
    adb shell date "$TGT" > /dev/null 2>&1 || true
    sleep 1
    NOW_DOY=$(adb shell date +%j 2>/dev/null | tr -d '\r')
  fi
  if [ "$NOW_DOY" != "$DOY" ]; then
    log "template switch unavailable (date set failed: want doy $DOY, got $NOW_DOY) - code-level evidence only"
    TPL_ALL=0
    break
  fi
  log "cover template: $TPL (doy $DOY, residue $RES)"
  adb shell am force-stop "$PKG" || true
  sleep 1
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
  sleep 4
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
  shot "intent-tpl-$TPL"
  adb shell uiautomator dump /sdcard/tpl_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/tpl_dump.xml "$OUT/tpl_${TPL}.xml" > /dev/null 2>&1 || true
  if [ -f "$OUT/tpl_${TPL}.xml" ] && grep -q 'text="想做什么？"' "$OUT/tpl_${TPL}.xml"; then
    ok "tpl-$TPL-capsule"
    TNODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/tpl_${TPL}.xml" | head -1)
    TNUMS=$(echo "$TNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    TX1=$(echo "$TNUMS" | sed -n 1p); TY1=$(echo "$TNUMS" | sed -n 2p)
    TX2=$(echo "$TNUMS" | sed -n 3p); TY2=$(echo "$TNUMS" | sed -n 4p)
    if [ -n "$TX1" ] && [ -n "$TY2" ]; then
      TCX=$(( (TX1 + TX2) / 2 )); TCY=$(( (TY1 + TY2) / 2 ))
      adb shell input swipe "$TCX" "$TCY" "$TCX" "$TCY" 150 || true
      sleep 2
      adb shell input text "cam" || true
      sleep 1
      adb shell input keyevent KEYCODE_BACK || true
      sleep 1
      shot "intent-tpl-$TPL-local"
      adb shell uiautomator dump /sdcard/tpl_dump2.xml > /dev/null 2>&1 || true
      adb pull /sdcard/tpl_dump2.xml "$OUT/tpl_${TPL}_local.xml" > /dev/null 2>&1 || true
      if [ -f "$OUT/tpl_${TPL}_local.xml" ] && grep -q 'text="Camera"' "$OUT/tpl_${TPL}_local.xml"; then
        ok "tpl-$TPL-local"
      else
        ng "tpl-$TPL-local"
      fi
    else
      ng "tpl-$TPL-local"
    fi
  else
    ng "tpl-$TPL-capsule"
  fi
done
adb shell settings put global auto_time 1 > /dev/null 2>&1 || true
if [ "$TPL_ALL" = "1" ]; then
  log "cover templates exercised: giant/headline/minimal"
fi

# --- 12. 字体缩放（R3-A）：font_scale 1.3 / 1.5 下首页与应用中心不溢出崩坏 ---
# settings 改系统 font_scale（sp 随之放大、dp 布局不变）；每档 force-stop 重启回首页基线，
# 截 home + appcenter 供人工逐张验（胶囊/结果条/应用行/刊头无重叠截断灾难）；验完恢复 1.0。
for FS in 1.3 1.5; do
  FTAG=$(echo "$FS" | tr -d .)
  adb shell settings put system font_scale "$FS" > /dev/null 2>&1 || true
  sleep 1
  adb shell am force-stop "$PKG" || true
  sleep 1
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
  sleep 4
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
  shot "font$FTAG-home"
  adb shell input swipe "$(SX 540)" "$(SY 2000)" "$(SX 540)" "$(SY 800)" 300 || true
  sleep 3
  shot "font$FTAG-appcenter"
  adb shell input keyevent KEYCODE_HOME || true
  sleep 1
done
adb shell settings put system font_scale "${SMOKE_FONT_SCALE:-1.0}" > /dev/null 2>&1 || true
sleep 1
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
adb shell pidof "$PKG" | grep -q . && ok "font-scale-alive" || ng "font-scale-alive"

# --- 13. 七仔反应（R3-C 对话感）：聚焦倾听态 / 本地结果呈现态 ---
# force-stop 回首页基线 -> press 点胶囊（键盘弹起、七仔应进倾听姿态 FETCH）截 intent-focus；
# 输 "cam" 收键盘截 intent-present（结果条在场 + 七仔应为呈现姿态 FILE 抱卡片）。
# Planning 思考态在无 Key 环境瞬时跳过（协调器无 Key 直接进 Error），本轮取不到图，如实标注未验。
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
FCX=$(SX 540); FCY=$(SY 1247)
adb shell uiautomator dump /sdcard/focus_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/focus_dump.xml "$OUT/focus_dump.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/focus_dump.xml" ]; then
  FNODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/focus_dump.xml" | head -1)
  if [ -n "$FNODE" ]; then
    FNUMS=$(echo "$FNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    FX1=$(echo "$FNUMS" | sed -n 1p); FY1=$(echo "$FNUMS" | sed -n 2p)
    FX2=$(echo "$FNUMS" | sed -n 3p); FY2=$(echo "$FNUMS" | sed -n 4p)
    if [ -n "$FX1" ] && [ -n "$FY2" ]; then
      FCX=$(( (FX1 + FX2) / 2 )); FCY=$(( (FY1 + FY2) / 2 ))
      log "reaction capsule at ($FCX,$FCY)"
    fi
  fi
fi
adb shell input swipe "$FCX" "$FCY" "$FCX" "$FCY" 150 || true
sleep 2
shot "intent-focus"
adb shell input text "cam" || true
sleep 1
adb shell input keyevent KEYCODE_BACK || true
sleep 1
shot "intent-present"
adb shell uiautomator dump /sdcard/present_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/present_dump.xml "$OUT/present_dump.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/present_dump.xml" ] && grep -q 'text="Camera"' "$OUT/present_dump.xml"; then
  ok "intent-present-results"
else
  ng "intent-present-results"
fi

# --- 15. 无障碍触控尺寸断言（R4）：量已拉取 dump 里关键目标的实际 dp 尺寸（>=48dp）---
# 覆盖：提交按钮（intent_dump）/ 清空按钮 + 问七仔行（ac_dump2）/ 本地结果项（local_dump）。
# 扇形段（fan_dump）只测量记录：扇形是 Canvas 手势面，无段语义节点即记为发现项，
# TalkBack 走查归真机验收，CI 不冒充已验；liveRegion/stateDescription 不出现在
# uiautomator dump 里，同样归真机验收项（a11y_check.py 会明示）。
if command -v python3 > /dev/null 2>&1; then
  A11Y_OUT=$(python3 scripts/a11y_check.py "$DENSITY" "$OUT" 2>&1)
  echo "$A11Y_OUT" | while IFS= read -r line; do log "$line"; done
  echo "$A11Y_OUT" > "$OUT/a11y-report.txt"
  if echo "$A11Y_OUT" | grep -q "^A11Y FAIL"; then
    ng "a11y-targets"
  else
    ok "a11y-targets"
  fi
else
  log "python3 unavailable - a11y check skipped (not counted)"
fi

# --- 16. crash 检查（只看本包 FATAL，避免系统噪音）---
if adb logcat -d 2>/dev/null | grep -i "FATAL EXCEPTION" | grep -q "$PKG"; then
  ng "no-fatal-crash"
  adb logcat -d 2>/dev/null | grep -i -A15 "FATAL EXCEPTION" | head -30 || true
else
  ok "no-fatal-crash"
fi

log "RESULT pass=$PASS fail=$FAIL"
exit "$FAIL"
