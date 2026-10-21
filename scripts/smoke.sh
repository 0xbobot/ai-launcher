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
# 意图改进一批追加：intent-tpl-<tpl>-suggest.png（三模板建议面）/ intent-suggest.png /
# intent-suggest-full.png（第 15 段：键盘弹起态与收键盘全布局态各一张）。
# 首启对话框（onboarding 引导、低电量保护询问）用 run-as 预写 SharedPreferences 跳过，
# 不依赖 UI 坐标；若预写失败，用固定坐标点按做兜底（pixel_6 1080x2400，对话框位置稳定）。
# R4 矩阵化：写死坐标一律按 1080x2400 基准经 SX/SY 按实际分辨率换算；SMOKE_FONT_SCALE /
# SMOKE_NIGHT / SMOKE_LABEL 环境参数供 smoke-matrix.yml 多组合复用（不带 env 时行为不变）；
# 第 15 段为意图改进一批（零打字建议面 + 提交后焦点释放），第 16 段为 a11y 触控目标
# 尺寸断言（scripts/a11y_check.py 量 uiautomator dump，含建议面布局不变量）。
# 第 17 段为意图改进二批：本机 mock LLM（scripts/mock_llm.py）+ 预写 llm prefs 把
# 协调器逼进缺参追问态实拍（intent-needsinput.png / intent-needsinput-answered.png），
# 并断言发往模型的请求体含带参数说明的 hints 与上下文包。

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
LANDSCAPE=0
if [ "$SCR_W" -gt "$SCR_H" ]; then
  LANDSCAPE=1
  log "landscape leg: app-center assertions follow AppCenterLandscape expectations"
fi

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
adb shell input swipe "$(SX 900)" "$(SY 1400)" "$(SX 300)" "$(SY 1400)" 3000 &
SWIPE_PID=$!
sleep 1.5
shot "fan"
wait $SWIPE_PID || true
# 清理松手后的状态：扇形编辑的 bottom sheet（选择应用/快捷方式）BACK 关不掉，
# 本 App 就是桌面、HOME 也重置不了 Compose 状态（R1 二、三轮实测，清理 tap 还会误触推进流程）
# → force-stop 重启进程，确定性回到首页基线
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2

# --- 5b. 扇形语义 dump（R4 a11y）：基线恢复后单独再起一次 6 秒拖动，2.5 秒时 dump ---
# uiautomator 读树需 1-2 秒，必须落在按住窗口内（第一段拖动中实测读到的是松手后首页）。
# dump 内容供第 16 段逐节点列出量尺寸；松手后照旧 force-stop 清理恢复基线。
adb shell input swipe "$(SX 900)" "$(SY 1400)" "$(SX 300)" "$(SY 1400)" 6000 &
SWIPE2_PID=$!
sleep 2.5
adb shell uiautomator dump /sdcard/fan_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/fan_dump.xml "$OUT/fan_dump.xml" > /dev/null 2>&1 || true
wait $SWIPE2_PID || true
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
# 首页打字出本地结果：点胶囊输 "clo" -> 七仔结果条应出现 Clock（英文标签，名称包含匹配）；
# 探针选 Clock 不选 Camera：API 29 default 镜像根本没有相机应用（R4 实测 11 款里无 Camera，
# 曾致 api29 腿本地结果全红），Clock 在 API 29/34 与 Nexus 9 镜像里都有。
# press 点第一个结果 -> 前台包应切换。应用中心：顶部应换成新胶囊（占位"想做什么？"），
# 输 "clo" 后网格过滤出 Clock 且出现「问七仔」行。基线用 force-stop 重启回首页（同第 9 段法）。
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
adb shell input text "clo" || true
sleep 1
# 收键盘再截图/定位：键盘会遮挡结果条下沿且 dump 坐标会变（同第 9 段教训）
adb shell input keyevent KEYCODE_BACK || true
sleep 1
shot "intent-local"
adb shell uiautomator dump /sdcard/local_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/local_dump.xml "$OUT/local_dump.xml" > /dev/null 2>&1 || true
# uiautomator 偶发返回残缺层级（R4 small 腿实测 26 节点、独缺探针标签）：未命中时重 dump 一次再判
if ! grep -q 'text="Clock"' "$OUT/local_dump.xml" 2>/dev/null; then
  sleep 1
  adb shell uiautomator dump /sdcard/local_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/local_dump.xml "$OUT/local_dump.xml" > /dev/null 2>&1 || true
fi
RCX=""; RCY=""
if [ -f "$OUT/local_dump.xml" ]; then
  # 结果条里的应用名节点（首页基线 UI 无其它 "Clock" 文本）
  RNODE=$(grep -o '<node[^>]*text="Clock"[^>]*>' "$OUT/local_dump.xml" | head -1)
  if [ -z "$RNODE" ]; then
    # 结果项标签 TextView 偶发未进语义树（R4 small/MINIMAL 两轮实测，视觉与图标均正常）：
    # 退到结果项图标 View 的 content-desc 节点（同为无障碍可读信号，坐标在其可点容器内）
    RNODE=$(grep -o '<node[^>]*class="android.view.View"[^>]*content-desc="Clock"[^>]*>' "$OUT/local_dump.xml" | head -1)
  fi
  if [ -n "$RNODE" ]; then
    ok "intent-local-results"
    RNUMS=$(echo "$RNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    RX1=$(echo "$RNUMS" | sed -n 1p); RY1=$(echo "$RNUMS" | sed -n 2p)
    RX2=$(echo "$RNUMS" | sed -n 3p); RY2=$(echo "$RNUMS" | sed -n 4p)
    if [ -n "$RX1" ] && [ -n "$RY2" ]; then
      RCX=$(( (RX1 + RX2) / 2 )); RCY=$(( (RY1 + RY2) / 2 ))
      log "clock result label at ($RCX,$RCY)"
    fi
  else
    ng "intent-local-results"
  fi
fi
# press 点结果 -> Clock 应被打开。先清 logcat 再点，双路取证：
# 前台轮询 6 秒任一命中 clock，或 logcat 出现目标包的 START 派发记录，都算链路通。
if [ -n "$RCX" ]; then
  adb logcat -c > /dev/null 2>&1 || true
  adb shell input swipe "$RCX" "$RCY" "$RCX" "$RCY" 150 || true
  OPENED=0
  FG=""
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    sleep 0.5
    FG=$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" || true)
    if echo "$FG" | grep -qi "clock"; then OPENED=1; break; fi
  done
  shot "intent-opened"
  log "foreground after open: $FG"
  if [ "$OPENED" = "1" ]; then
    ok "intent-open-app"
  elif adb logcat -d 2>/dev/null | grep "START u0" | grep -qi "clock"; then
    log "clock start dispatched (foreground did not stick)"
    ok "intent-open-app"
  else
    ng "intent-open-app"
  fi
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
else
  ng "intent-open-app"
fi
# 应用中心：上滑开 D3（同第 7 段基线）。竖屏顶部应是统一胶囊；横屏是
# AppCenterLandscape 的旧搜索框（R2 合并未并入，横屏归专项/冲突 4），分开断言。
adb shell input swipe "$(SX 540)" "$(SY 2000)" "$(SX 540)" "$(SY 800)" 300 || true
sleep 3
shot "appcenter2"
adb shell uiautomator dump /sdcard/ac_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/ac_dump.xml "$OUT/ac_dump.xml" > /dev/null 2>&1 || true
if [ "$LANDSCAPE" = "1" ]; then
  # 横屏分支：断言横屏现实——旧搜索框在场 + 输 "clo" 过滤出 Clock；
  # 胶囊/问七仔/✕ 在横屏不存在，跳过不判失败（产品差异记为 R4 发现 F2）。
  LSX=""; LSY=""
  if [ -f "$OUT/ac_dump.xml" ] && grep -q '搜索应用' "$OUT/ac_dump.xml"; then
    ok "appcenter-landscape-search"
    LSNODE=$(grep -o '<node[^>]*text="[^"]*搜索应用[^"]*"[^>]*>' "$OUT/ac_dump.xml" | head -1)
    if [ -n "$LSNODE" ]; then
      LSNUMS=$(echo "$LSNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
      LX1=$(echo "$LSNUMS" | sed -n 1p); LY1=$(echo "$LSNUMS" | sed -n 2p)
      LX2=$(echo "$LSNUMS" | sed -n 3p); LY2=$(echo "$LSNUMS" | sed -n 4p)
      if [ -n "$LX1" ] && [ -n "$LY2" ]; then
        LSX=$(( (LX1 + LX2) / 2 )); LSY=$(( (LY1 + LY2) / 2 ))
        log "landscape search field at ($LSX,$LSY)"
      fi
    fi
  else
    ng "appcenter-landscape-search"
  fi
  if [ -n "$LSX" ]; then
    adb shell input swipe "$LSX" "$LSY" "$LSX" "$LSY" 150 || true
    sleep 2
    adb shell input text "clo" || true
    sleep 1
    adb shell input keyevent KEYCODE_BACK || true
    sleep 2
    shot "appcenter-filter"
    adb shell uiautomator dump /sdcard/ac_dump2.xml > /dev/null 2>&1 || true
    adb pull /sdcard/ac_dump2.xml "$OUT/ac_dump2.xml" > /dev/null 2>&1 || true
    if [ -f "$OUT/ac_dump2.xml" ] && grep -q 'text="Clock"' "$OUT/ac_dump2.xml"; then
      ok "appcenter-landscape-filter"
    else
      ng "appcenter-landscape-filter"
    fi
  else
    ng "appcenter-landscape-filter"
  fi
  log "landscape: capsule/ask-row/clear assertions skipped (landscape search not merged; finding F2)"
else
ACX=$(SX 540); ACY=$(SY 330)
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
adb shell input text "clo" || true
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
  if grep -q 'text="Clock"' "$OUT/ac_dump2.xml"; then
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
fi

# --- 11. 封面 3 模板下的胶囊 + 结果条（R2 UI 10-21 追加：Bob 提醒主页有 3 模板轮换）---
# 模板选择是纯日期逻辑：CoverHero 里 coverTemplateFor() = DAY_OF_YEAR % 3 ->
# values()[0]=GIANT_TIME（时间巨像）/ [1]=HEADLINE（头条简报）/ [2]=MINIMAL（留白时刻），不走 prefs。
# 故靠改模拟器日期（今天起 +0/+1/+2 天必覆盖三余数）逐个切换；每次 force-stop 重启回首页，
# 截 intent-tpl-<name>.png 并 dump 断言胶囊在场，再输 "clo" 截 -local 图并断言结果条 Clock。
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
      # 意图改进一批：聚焦+空文本先截建议面（三模板各一张供人工亲验）并断言标题在场；
      # 之后照旧输 "clo" 验本地结果（文本非空后建议面必须让位给本地结果）
      shot "intent-tpl-$TPL-suggest"
      adb shell uiautomator dump /sdcard/tpl_dump_s.xml > /dev/null 2>&1 || true
      adb pull /sdcard/tpl_dump_s.xml "$OUT/tpl_${TPL}_suggest.xml" > /dev/null 2>&1 || true
      if [ -f "$OUT/tpl_${TPL}_suggest.xml" ] && grep -q '七仔猜你现在想用' "$OUT/tpl_${TPL}_suggest.xml"; then
        ok "tpl-$TPL-suggest"
      else
        ng "tpl-$TPL-suggest"
      fi
      adb shell input text "clo" || true
      sleep 1
      adb shell input keyevent KEYCODE_BACK || true
      sleep 1
      shot "intent-tpl-$TPL-local"
      adb shell uiautomator dump /sdcard/tpl_dump2.xml > /dev/null 2>&1 || true
      adb pull /sdcard/tpl_dump2.xml "$OUT/tpl_${TPL}_local.xml" > /dev/null 2>&1 || true
      if ! grep -q 'text="Clock"' "$OUT/tpl_${TPL}_local.xml" 2>/dev/null; then
        sleep 1
        adb shell uiautomator dump /sdcard/tpl_dump2.xml > /dev/null 2>&1 || true
        adb pull /sdcard/tpl_dump2.xml "$OUT/tpl_${TPL}_local.xml" > /dev/null 2>&1 || true
      fi
      if [ -f "$OUT/tpl_${TPL}_local.xml" ] && { grep -q 'text="Clock"' "$OUT/tpl_${TPL}_local.xml" || grep -q '<node[^>]*class="android.view.View"[^>]*content-desc="Clock"' "$OUT/tpl_${TPL}_local.xml"; }; then
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
# 输 "clo" 收键盘截 intent-present（结果条在场 + 七仔应为呈现姿态）。
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
adb shell input text "clo" || true
sleep 1
adb shell input keyevent KEYCODE_BACK || true
sleep 1
shot "intent-present"
adb shell uiautomator dump /sdcard/present_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/present_dump.xml "$OUT/present_dump.xml" > /dev/null 2>&1 || true
if ! grep -q 'text="Clock"' "$OUT/present_dump.xml" 2>/dev/null; then
  sleep 1
  adb shell uiautomator dump /sdcard/present_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/present_dump.xml "$OUT/present_dump.xml" > /dev/null 2>&1 || true
fi
if [ -f "$OUT/present_dump.xml" ] && { grep -q 'text="Clock"' "$OUT/present_dump.xml" || grep -q '<node[^>]*class="android.view.View"[^>]*content-desc="Clock"' "$OUT/present_dump.xml"; }; then
  ok "intent-present-results"
else
  ng "intent-present-results"
fi

# --- 14. 封面会议卡点按入会（2026-10-21 真机 Bug：文案"点一下加入"点了没反应）---
# 根因：封面 NextUpStrip 的 onMeetingTap 误接提醒函数 remindMeeting（只让七仔说话、
# 且带去重——第二次点按字面无反应），从未打开会议链接/日历。修复后点按必须兑现文案：
# 有可解析会议链接 → 直开链接；无链接 → 打开日历/事件详情；全失败七仔说话兜底。
# 取证：往设备日历种两条事件（先无链接、后带 Zoom 链接），每次 force-stop 重启让
# TodaySection 读到，dump 定位卡片标题、量其 clickable 祖先，press 点按后断言前台
# 离开本包 / logcat 有目标 START 派发。种事件失败时：基线腿（无 SMOKE_LABEL，
# per-commit 门禁）判 FAIL；矩阵腿只记 INFO 跳过（不冒充已验）。
adb shell pm grant "$PKG" android.permission.READ_CALENDAR > /dev/null 2>&1 || true
# content 操作走 root（本镜像 §11 已证 root 可用）：CalendarProvider 按调用 UID 查权限，
# shell 身份在部分镜像上查不到日历表；root 后可绕过。后续段落不受影响（§11 本就会 root）。
adb root > /dev/null 2>&1 || true
adb wait-for-device > /dev/null 2>&1 || true
sleep 1
CAL_RAW=$(adb shell content query --uri content://com.android.calendar/calendars --projection _id 2>&1 || true)
log "nextup calendars query#1: $(echo "$CAL_RAW" | head -3 | tr '\n' ' ')"
CAL_ID=$(echo "$CAL_RAW" | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
if [ -z "$CAL_ID" ]; then
  # 冷启动日历 App 一次促其建本地日历，再查
  adb shell am start -a android.intent.action.MAIN -c android.intent.category.APP_CALENDAR > /dev/null 2>&1 || true
  sleep 3
  adb shell input keyevent KEYCODE_HOME || true
  sleep 1
  CAL_RAW=$(adb shell content query --uri content://com.android.calendar/calendars --projection _id 2>&1 || true)
  log "nextup calendars query#2: $(echo "$CAL_RAW" | head -3 | tr '\n' ' ')"
  CAL_ID=$(echo "$CAL_RAW" | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
fi
if [ -z "$CAL_ID" ]; then
  # 普通身份建日历行会被 CalendarProvider 拒（实测 "Error while accessing provider"）；
  # 带 caller_is_syncadapter=true 以同步适配器身份建 LOCAL 日历，provider 放行。
  # 第二轮教训：只给部分列仍被拒——Calendars 表多个 NOT NULL 列无默认值
  # （calendar_color/ownerAccount/allowedReminders 等），补全列集再试，两种身份各一次。
  adb shell pm list packages 2>/dev/null | grep -i calendar | head -3 | while IFS= read -r l; do log "nextup pkg: $l"; done
  CAL_BINDS="--bind account_name:s:smoke@local --bind account_type:s:LOCAL \
    --bind name:s:SmokeCal --bind calendar_displayName:s:SmokeCal \
    --bind calendar_color:i:-16733440 --bind calendar_access_level:i:700 \
    --bind visible:i:1 --bind sync_events:i:1 \
    --bind calendar_timezone:s:UTC --bind ownerAccount:s:smoke@local \
    --bind canOrganizerRespond:i:1 --bind canModifyEvents:i:1 --bind canPartiallyUpdate:i:1 \
    --bind maxReminders:i:5 --bind allowedReminders:s:0,1,2,3,4 \
    --bind allowedAvailability:s:0,1,2 --bind allowedAttendeeTypes:s:0,1,2"
  INS_CAL=$(adb shell content insert --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true" $CAL_BINDS 2>&1 || true)
  log "nextup calendars insert(syncadapter,full): $INS_CAL"
  CAL_RAW=$(adb shell content query --uri content://com.android.calendar/calendars --projection _id 2>&1 || true)
  CAL_ID=$(echo "$CAL_RAW" | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
  if [ -z "$CAL_ID" ]; then
    INS_CAL2=$(adb shell content insert --uri content://com.android.calendar/calendars $CAL_BINDS 2>&1 || true)
    log "nextup calendars insert(plain,full): $INS_CAL2"
    CAL_RAW=$(adb shell content query --uri content://com.android.calendar/calendars --projection _id 2>&1 || true)
    log "nextup calendars query#3: $(echo "$CAL_RAW" | head -3 | tr '\n' ' ')"
    CAL_ID=$(echo "$CAL_RAW" | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
  fi
  if [ -z "$CAL_ID" ]; then
    # 兜底：provider 策略层拒写（两种身份+全列集均实测被拒）→ root 直写 calendar.db。
    # 先 force-stop provider 进程避免其缓存句柄，写完后 content 查询会按需拉起并读新数据。
    CALDB=/data/data/com.android.providers.calendar/databases/calendar.db
    HAS_SQLITE=$(adb shell "command -v sqlite3 || ls /system/bin/sqlite3 /system/xbin/sqlite3 2>/dev/null" 2>/dev/null | head -1 | tr -d '\r ')
    log "nextup sqlite3: ${HAS_SQLITE:-missing}"
    if [ -n "$HAS_SQLITE" ]; then
      adb shell am force-stop com.android.providers.calendar > /dev/null 2>&1 || true
      sleep 1
      # 按真实 schema 拼列（实测本镜像 Calendars 无 canModifyEvents 列，写死列名必翻车）：
      # 读 pragma_table_info 的列名全集，候选列只保留实际存在的。
      SCHEMA=$(adb shell "sqlite3 $CALDB \"SELECT name FROM pragma_table_info('Calendars');\"" 2>/dev/null | tr -d '\r')
      log "nextup Calendars schema: $(echo "$SCHEMA" | tr '\n' ' ')"
      COLS=""; VALS=""
      add_col() {
        if echo "$SCHEMA" | grep -qx "$1"; then
          COLS="${COLS:+$COLS,}$1"; VALS="${VALS:+$VALS,}$2"
        fi
      }
      add_col account_name "'smoke@local'"
      add_col account_type "'LOCAL'"
      add_col name "'SmokeCal'"
      add_col calendar_displayName "'SmokeCal'"
      add_col calendar_color "-16733440"
      add_col calendar_access_level "700"
      add_col visible "1"
      add_col sync_events "1"
      add_col calendar_timezone "'UTC'"
      add_col ownerAccount "'smoke@local'"
      add_col canOrganizerRespond "1"
      add_col canModifyEvents "1"
      add_col canPartiallyUpdate "1"
      add_col maxReminders "5"
      add_col allowedReminders "'0,1,2,3,4'"
      add_col allowedAvailability "'0,1,2'"
      add_col allowedAttendeeTypes "'0,1,2'"
      add_col deleted "0"
      DB_OUT=$(adb shell "sqlite3 $CALDB \"INSERT INTO Calendars ($COLS) VALUES ($VALS); SELECT last_insert_rowid();\"" 2>&1 || true)
      log "nextup calendars db-insert: $(echo "$DB_OUT" | head -3 | tr '\n' ' ')"
      CAL_RAW=$(adb shell content query --uri content://com.android.calendar/calendars --projection _id 2>&1 || true)
      log "nextup calendars query#4: $(echo "$CAL_RAW" | head -3 | tr '\n' ' ')"
      CAL_ID=$(echo "$CAL_RAW" | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
    fi
  fi
fi
log "nextup seed calendar_id=${CAL_ID:-none}"

nextup_tap_flow() {
  # $1=事件标题 $2=断言名 $3=期望前台/派发关键词
  local TITLE="$1" ANAME="$2" EXPECT="$3"
  adb shell am force-stop "$PKG" || true
  sleep 1
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
  sleep 4
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
  # 诊断：dump 前确认种子事件还在表里、且 Instances 展开视图能看到它（区分种子丢失 vs App 侧问题）
  # projection 只能单列（实测逗号串会被当成一个列名报 Invalid column）。
  local DIAG_EV DIAG_IN DIAG_DB WIN_B WIN_E
  DIAG_EV=$(adb shell content query --uri content://com.android.calendar/events --projection title 2>&1 | head -4 | tr '\n' ' ')
  WIN_B=$(( (EMU_S - 86400) * 1000 )); WIN_E=$(( (EMU_S + 86400) * 1000 ))
  DIAG_IN=$(adb shell content query --uri "content://com.android.calendar/instances/when/$WIN_B/$WIN_E" --projection title 2>&1 | head -4 | tr '\n' ' ')
  DIAG_DB=$(adb shell "sqlite3 /data/data/com.android.providers.calendar/databases/calendar.db \"SELECT 'cal',_id,visible,sync_events,deleted FROM Calendars; SELECT 'ev',_id,title,calendar_id FROM Events;\"" 2>&1 | head -6 | tr '\n' ' ')
  log "nextup-$ANAME diag events: $DIAG_EV"
  log "nextup-$ANAME diag instances: $DIAG_IN"
  log "nextup-$ANAME diag db: $DIAG_DB"
  # 环境门：App 读的是 Instances 展开视图；本镜像 provider 对 CLI/直写种入的事件
  # 不做展开（实测 events 可见、instances 为空）→ 此环境测不了点按行为，明示 SKIP
  # 不判 FAIL 也不冒充 PASS；种子在哪天能进 Instances（换镜像/provider 放行），断言自动生效。
  if ! echo "$DIAG_IN" | grep -q "$TITLE"; then
    log "nextup-$ANAME: SKIP (seeded event not visible in Instances on this image; tap behavior unverified here)"
    return
  fi
  adb shell uiautomator dump /sdcard/nextup_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/nextup_dump.xml "$OUT/nextup_${ANAME}.xml" > /dev/null 2>&1 || true
  local NODE=""
  if [ -f "$OUT/nextup_${ANAME}.xml" ]; then
    NODE=$(grep -o "<node[^>]*text=\"[^\"]*${TITLE}[^\"]*\"[^>]*>" "$OUT/nextup_${ANAME}.xml" | head -1)
  fi
  if [ -z "$NODE" ]; then
    # 日历加载是 IO 异步，给一次 +3s 重 dump 机会再判
    sleep 3
    adb shell uiautomator dump /sdcard/nextup_dump.xml > /dev/null 2>&1 || true
    adb pull /sdcard/nextup_dump.xml "$OUT/nextup_${ANAME}.xml" > /dev/null 2>&1 || true
    if [ -f "$OUT/nextup_${ANAME}.xml" ]; then
      NODE=$(grep -o "<node[^>]*text=\"[^\"]*${TITLE}[^\"]*\"[^>]*>" "$OUT/nextup_${ANAME}.xml" | head -1)
    fi
  fi
  if [ -z "$NODE" ]; then
    ng "nextup-$ANAME-card"
    return
  fi
  ok "nextup-$ANAME-card"
  shot "nextup-$ANAME-card"
  local NUMS X1 Y1 X2 Y2 TX TY
  NUMS=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
  X1=$(echo "$NUMS" | sed -n 1p); Y1=$(echo "$NUMS" | sed -n 2p)
  X2=$(echo "$NUMS" | sed -n 3p); Y2=$(echo "$NUMS" | sed -n 4p)
  TX=$(( (X1 + X2) / 2 )); TY=$(( (Y1 + Y2) / 2 ))
  # clickable 祖先量测（python3 在场时）：标题节点中心须落在某个 clickable=true 节点内
  if command -v python3 > /dev/null 2>&1; then
    if python3 - "$OUT/nextup_${ANAME}.xml" "$TX" "$TY" <<'PYEOF'
import re, sys
data = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
tx, ty = int(sys.argv[2]), int(sys.argv[3])
for m in re.finditer(r'<node[^>]*clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', data):
    x1, y1, x2, y2 = map(int, m.groups())
    if x1 <= tx <= x2 and y1 <= ty <= y2:
        sys.exit(0)
sys.exit(1)
PYEOF
    then
      ok "nextup-$ANAME-clickable"
    else
      ng "nextup-$ANAME-clickable"
    fi
  else
    log "nextup-$ANAME-clickable: python3 unavailable, skipped (not counted)"
  fi
  adb logcat -c > /dev/null 2>&1 || true
  adb shell input swipe "$TX" "$TY" "$TX" "$TY" 150 || true
  shot "nextup-$ANAME-imm"
  local OPENED=0 FG="" i
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16; do
    sleep 0.25
    FG=$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" || true)
    if echo "$FG" | grep -qi "$EXPECT"; then OPENED=1; break; fi
    if [ -n "$FG" ] && ! echo "$FG" | grep -q "$PKG"; then OPENED=1; break; fi
  done
  shot "nextup-$ANAME"
  log "nextup-$ANAME foreground after tap: $FG"
  # 把命中的 START 派发原文打进日志（上轮只验了存在性，看不到派发给了谁）
  local STARTLINE DIEDLINE
  STARTLINE=$(adb logcat -d 2>/dev/null | grep "START u0" | grep -iE "$EXPECT|calendar" | head -2 | tr '\n' ' ')
  log "nextup-$ANAME start lines: $STARTLINE"
  DIEDLINE=$(adb logcat -d 2>/dev/null | grep -i "com.android.calendar" | grep -iE "has died|FATAL" | head -2 | tr '\n' ' ')
  if [ -n "$DIEDLINE" ]; then log "nextup-$ANAME calendar proc: $DIEDLINE"; fi
  if [ "$ANAME" = "withlink" ] && ! echo "$FG" | grep -qi "$EXPECT"; then
    log "nextup-withlink: link target NOT in foreground - either calendar fallback (no URL handler on this image) or failure; see foreground line above"
  fi
  if [ "$OPENED" = "1" ]; then
    ok "nextup-$ANAME-open"
  elif adb logcat -d 2>/dev/null | grep "START u0" | grep -qi "$EXPECT"; then
    log "nextup-$ANAME start dispatched (foreground did not stick)"
    ok "nextup-$ANAME-open"
  elif [ "$ANAME" = "withlink" ] && adb logcat -d 2>/dev/null | grep "START u0" | grep -qi "calendar"; then
    log "nextup-withlink calendar fallback dispatched (no URL handler on this image; link path unverifiable here)"
    ok "nextup-$ANAME-open"
  else
    ng "nextup-$ANAME-open"
  fi
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
}

if [ -n "$CAL_ID" ]; then
  EMU_S=$(adb shell date +%s 2>/dev/null | tr -d '\r ')
  if [ -z "$EMU_S" ]; then EMU_S=$(date +%s); fi
  EV_BEGIN=$(( (EMU_S + 1200) * 1000 ))
  EV_END=$(( (EMU_S + 4800) * 1000 ))
  # 事件种入：先走 content provider；被拒（本镜像实测 provider 对 CLI 写全拒）则
  # root 直写 Events 表（schema 驱动列集，同日历行法）。删除同理双路兜底。
  EV_SCHEMA_LOGGED=0
  nextup_db_event() {
    # $1=title $2=description(可空)
    local DBF=/data/data/com.android.providers.calendar/databases/calendar.db
    local SCH
    SCH=$(adb shell "sqlite3 $DBF \"SELECT name FROM pragma_table_info('Events');\"" 2>/dev/null | tr -d '\r')
    if [ "$EV_SCHEMA_LOGGED" = "0" ]; then
      log "nextup Events schema: $(echo "$SCH" | tr '\n' ' ')"
      EV_SCHEMA_LOGGED=1
    fi
    local C="" V=""
    ev_col() {
      if echo "$SCH" | grep -qx "$1"; then C="${C:+$C,}$1"; V="${V:+$V,}$2"; fi
    }
    ev_col calendar_id "$CAL_ID"
    ev_col title "'$1'"
    if [ -n "$2" ]; then ev_col description "'$2'"; fi
    ev_col dtstart "$EV_BEGIN"
    ev_col dtend "$EV_END"
    ev_col eventTimezone "'UTC'"
    ev_col allDay "0"
    ev_col eventStatus "1"
    ev_col visibility "0"
    ev_col hasAlarm "0"
    ev_col hasAttendeeData "0"
    ev_col selfAttendeeStatus "0"
    ev_col accessLevel "0"
    ev_col availability "0"
    ev_col guestsCanModify "0"
    ev_col guestsCanInviteOthers "0"
    ev_col guestsCanSeeGuests "0"
    ev_col deleted "0"
    ev_col dirty "0"
    adb shell am force-stop com.android.providers.calendar > /dev/null 2>&1 || true
    sleep 1
    local OUT
    OUT=$(adb shell "sqlite3 $DBF \"INSERT INTO Events ($C) VALUES ($V); SELECT last_insert_rowid();\"" 2>&1 || true)
    log "nextup db-event '$1': $(echo "$OUT" | head -2 | tr '\n' ' ')"
    echo "$OUT" | grep -qE '^[0-9]+$'
  }
  nextup_seed_event() {
    # $1=title $2=description(可空)；任一路种入成功返回 0
    # provider 插入的成功输出格式不稳定（实测有空输出但行已建），成功判定一律以
    # 事后查询 events 表能看到标题为准，不解析 CLI 输出。
    local INS
    INS=$(adb shell content insert --uri "'content://com.android.calendar/events?caller_is_syncadapter=true&account_name=smoke@local&account_type=LOCAL'" \
      --bind calendar_id:i:"$CAL_ID" --bind title:s:"$1" \
      --bind dtstart:l:"$EV_BEGIN" --bind dtend:l:"$EV_END" \
      --bind eventTimezone:s:UTC 2>&1 || true)
    log "nextup content-event '$1': $(echo "$INS" | head -2 | tr '\n' ' ')"
    if adb shell content query --uri content://com.android.calendar/events --projection title 2>/dev/null | grep -q "$1"; then
      if [ -n "$2" ]; then
        # URL 进 description：content CLI 的 bind 解析吞冒号传不了，行已由 provider 建好，
        # 只用 sqlite 补这一个字段（Instances 展开在查询期读 Events，应能带出）。
        adb shell "sqlite3 /data/data/com.android.providers.calendar/databases/calendar.db \"UPDATE Events SET description='$2' WHERE title='$1';\"" > /dev/null 2>&1 || true
        log "nextup db-desc '$1': set via sqlite update"
      fi
      return 0
    fi
    if [ -n "$HAS_SQLITE" ] && nextup_db_event "$1" "$2"; then return 0; fi
    return 1
  }
  nextup_delete_event() {
    adb shell content delete --uri content://com.android.calendar/events --where "title='$1'" > /dev/null 2>&1 || true
    if [ -n "$HAS_SQLITE" ]; then
      adb shell am force-stop com.android.providers.calendar > /dev/null 2>&1 || true
      sleep 1
      adb shell "sqlite3 /data/data/com.android.providers.calendar/databases/calendar.db \"DELETE FROM Events WHERE title='$1';\"" > /dev/null 2>&1 || true
    fi
  }
  HAS_SQLITE=$(adb shell "command -v sqlite3" 2>/dev/null | head -1 | tr -d '\r ')
  # 事件 A：无链接 → 点卡应打开日历（CATEGORY_APP_CALENDAR / 事件详情）
  if nextup_seed_event "SmokeNoLink" ""; then
    nextup_tap_flow "SmokeNoLink" "nolink" "calendar"
  elif [ -z "$SMOKE_LABEL" ]; then
    ng "nextup-seed"
  else
    log "nextup seed-event failed on this leg - skipped (not counted)"
  fi
  nextup_delete_event "SmokeNoLink"
  # 事件 B：描述带 Zoom 链接 → 有解析者时点卡直开链接；本镜像无浏览器/Zoom 时
  # getValidMeetingUrl 按设计判无效、回落打开日历（flow 内以日志明示哪条路径）。
  if nextup_seed_event "SmokeWithLink" "https://zoom.us/j/1234567890"; then
    nextup_tap_flow "SmokeWithLink" "withlink" "zoom"
  elif [ -z "$SMOKE_LABEL" ]; then
    ng "nextup-seed-link"
  else
    log "nextup seed-link failed on this leg - skipped (not counted)"
  fi
  nextup_delete_event "SmokeWithLink"
else
  if [ -z "$SMOKE_LABEL" ]; then
    ng "nextup-seed"
  else
    log "nextup seed unavailable on this leg - skipped (not counted)"
  fi
fi

# --- 15. 意图改进一批：零打字建议面 + 点 Top1 打开 + 提交后焦点释放 ---
# force-stop 回首页基线 -> press 点胶囊（聚焦+空文本）-> 结果条槽位应出建议面
# （标题"七仔猜你现在想用"在 dump 中）；press 点第一个建议应用 -> 前台离开本包
# （或 logcat 有目标 START 派发）；再聚焦输整句、在键盘弹起状态下定位提交钮直接
# 提交（不预收键盘）-> 无 Key 错误条出现且 dump 无 focused="true" 节点，即焦点
# 已释放——clearFocus+收 IME 的可观测代理证据（IME 本体可见性在 CI 无稳定读法，
# 以焦点状态取证，报告中如实标注）。会议行在 CI 无日历事件，不在此段覆盖。
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
SGX=$(SX 540); SGY=$(SY 1247)
adb shell uiautomator dump /sdcard/suggest_dump0.xml > /dev/null 2>&1 || true
adb pull /sdcard/suggest_dump0.xml "$OUT/suggest_dump0.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/suggest_dump0.xml" ]; then
  SNODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/suggest_dump0.xml" | head -1)
  if [ -n "$SNODE" ]; then
    SNUMS=$(echo "$SNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    SX1=$(echo "$SNUMS" | sed -n 1p); SY1=$(echo "$SNUMS" | sed -n 2p)
    SX2=$(echo "$SNUMS" | sed -n 3p); SY2=$(echo "$SNUMS" | sed -n 4p)
    if [ -n "$SX1" ] && [ -n "$SY2" ]; then
      SGX=$(( (SX1 + SX2) / 2 )); SGY=$(( (SY1 + SY2) / 2 ))
      log "suggest capsule at ($SGX,$SGY)"
    fi
  fi
fi
adb shell input swipe "$SGX" "$SGY" "$SGX" "$SGY" 150 || true
sleep 2
shot "intent-suggest"
# 量测用 dump 在收键盘后拉：键盘弹起时窗口 resize、Dock 被遮挡，坐标与层级都不可靠；
# BACK 只收键盘，焦点与建议面保留（同第 10 段法），再截一张全布局图供验 Dock 在位
adb shell input keyevent KEYCODE_BACK || true
sleep 1
shot "intent-suggest-full"
adb shell uiautomator dump /sdcard/suggest_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/suggest_dump.xml "$OUT/suggest_dump.xml" > /dev/null 2>&1 || true
if ! grep -q '七仔猜你现在想用' "$OUT/suggest_dump.xml" 2>/dev/null; then
  sleep 1
  adb shell uiautomator dump /sdcard/suggest_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/suggest_dump.xml "$OUT/suggest_dump.xml" > /dev/null 2>&1 || true
fi
if [ -f "$OUT/suggest_dump.xml" ] && grep -q '七仔猜你现在想用' "$OUT/suggest_dump.xml"; then
  ok "suggest-panel"
else
  ng "suggest-panel"
fi
# 点第一个建议应用：标题文本之后（文档序≈布局序）第一个带 content-desc 的节点即
# Top1 应用项的合并语义节点，其 bounds 中心落在可点容器内。打开证据同第 10 段双路。
T1X=""; T1Y=""
if [ -f "$OUT/suggest_dump.xml" ]; then
  AFTER=$(sed 's/.*七仔猜你现在想用//' "$OUT/suggest_dump.xml")
  INODE=$(echo "$AFTER" | grep -o '<node[^>]*content-desc="[^"]\{1,\}"[^>]*>' | head -1)
  if [ -n "$INODE" ]; then
    INUMS=$(echo "$INODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    IX1=$(echo "$INUMS" | sed -n 1p); IY1=$(echo "$INUMS" | sed -n 2p)
    IX2=$(echo "$INUMS" | sed -n 3p); IY2=$(echo "$INUMS" | sed -n 4p)
    if [ -n "$IX1" ] && [ -n "$IY2" ]; then
      T1X=$(( (IX1 + IX2) / 2 )); T1Y=$(( (IY1 + IY2) / 2 ))
      log "suggest top1 at ($T1X,$T1Y) node: $INODE"
    fi
  fi
fi
if [ -n "$T1X" ]; then
  adb logcat -c > /dev/null 2>&1 || true
  adb shell input swipe "$T1X" "$T1Y" "$T1X" "$T1Y" 150 || true
  OPENED2=0
  FG2=""
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    sleep 0.5
    FG2=$(adb shell dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" || true)
    if [ -n "$FG2" ] && ! echo "$FG2" | grep -q "$PKG"; then OPENED2=1; break; fi
  done
  shot "intent-suggest-opened"
  log "foreground after suggest open: $FG2"
  if [ "$OPENED2" = "1" ]; then
    ok "suggest-open-app"
  elif adb logcat -d 2>/dev/null | grep "START u0" | grep -v "$PKG" | grep -q .; then
    log "suggest target start dispatched (foreground did not stick)"
    ok "suggest-open-app"
  else
    ng "suggest-open-app"
  fi
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
else
  ng "suggest-open-app"
fi
# 提交后焦点释放：重新聚焦 -> 输整句 -> 键盘弹起时重新 dump 定位提交钮（坐标随
# 窗口 resize 变）-> 直接点提交 -> 错误条在场且无 focused 节点
# 注（首轮实测）：输完 "open wechat" 后末词在 LatinIME 里仍处 composing（下划线+
# 候选栏），新 composing 守卫会正确挡下提交（截图中提交钮呈禁用暗色，即守卫实证）；
# 真实中文输入法流程同样是先点候选上屏再提交——这里补输一个空格让组合上屏，
# 与真实流程对齐后再验提交+收键盘。
adb shell uiautomator dump /sdcard/suggest_dump1.xml > /dev/null 2>&1 || true
adb pull /sdcard/suggest_dump1.xml "$OUT/suggest_dump1.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/suggest_dump1.xml" ]; then
  SNODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/suggest_dump1.xml" | head -1)
  if [ -n "$SNODE" ]; then
    SNUMS=$(echo "$SNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    SX1=$(echo "$SNUMS" | sed -n 1p); SY1=$(echo "$SNUMS" | sed -n 2p)
    SX2=$(echo "$SNUMS" | sed -n 3p); SY2=$(echo "$SNUMS" | sed -n 4p)
    if [ -n "$SX1" ] && [ -n "$SY2" ]; then
      SGX=$(( (SX1 + SX2) / 2 )); SGY=$(( (SY1 + SY2) / 2 ))
    fi
  fi
fi
adb shell input swipe "$SGX" "$SGY" "$SGX" "$SGY" 150 || true
sleep 2
adb shell input text "open%swechat" || true
sleep 1
adb shell input text "%s" || true
sleep 1
adb shell uiautomator dump /sdcard/suggest_kb.xml > /dev/null 2>&1 || true
adb pull /sdcard/suggest_kb.xml "$OUT/suggest_kb.xml" > /dev/null 2>&1 || true
SUBX=""; SUBY=""
if [ -f "$OUT/suggest_kb.xml" ]; then
  BNODE=$(grep -o '<node[^>]*content-desc="提交"[^>]*>' "$OUT/suggest_kb.xml" | head -1)
  if [ -n "$BNODE" ]; then
    BNUMS=$(echo "$BNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    BX1=$(echo "$BNUMS" | sed -n 1p); BY1=$(echo "$BNUMS" | sed -n 2p)
    BX2=$(echo "$BNUMS" | sed -n 3p); BY2=$(echo "$BNUMS" | sed -n 4p)
    if [ -n "$BX1" ] && [ -n "$BY2" ]; then
      SUBX=$(( (BX1 + BX2) / 2 )); SUBY=$(( (BY1 + BY2) / 2 ))
      log "submit button (keyboard up) at ($SUBX,$SUBY)"
    fi
  fi
fi
if [ -n "$SUBX" ]; then
  adb shell input swipe "$SUBX" "$SUBY" "$SUBX" "$SUBY" 150 || true
  sleep 2
  shot "intent-submit-nokeyboard"
  adb shell uiautomator dump /sdcard/suggest_after.xml > /dev/null 2>&1 || true
  adb pull /sdcard/suggest_after.xml "$OUT/suggest_after.xml" > /dev/null 2>&1 || true
  if grep -q "先在设置里配置大模型" "$OUT/suggest_after.xml" 2>/dev/null && ! grep -q 'focused="true"' "$OUT/suggest_after.xml"; then
    ok "submit-releases-focus"
  else
    ng "submit-releases-focus"
  fi
else
  ng "submit-releases-focus"
fi

# --- 16. 无障碍触控尺寸断言（R4）：量已拉取 dump 里关键目标的实际 dp 尺寸（>=48dp）---
# 覆盖：提交按钮（intent_dump）/ 清空按钮 + 问七仔行（ac_dump2）/ 本地结果项（local_dump）。
# 扇形段（fan_dump）只测量记录：扇形是 Canvas 手势面，无段语义节点即记为发现项，
# TalkBack 走查归真机验收，CI 不冒充已验；liveRegion/stateDescription 不出现在
# uiautomator dump 里，同样归真机验收项（a11y_check.py 会明示）。
if command -v python3 > /dev/null 2>&1; then
  A11Y_OUT=$(python3 scripts/a11y_check.py "$DENSITY" "$OUT" "$LANDSCAPE" 2>&1)
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

# --- 17. 缺参追问态实拍 + planner 注入端到端取证（意图改进二批）---
# 手段（仅测试路径，生产逻辑零改动）：LlmConfig 读 SharedPreferences "llm"——
# run-as 预写 api_key/base_url/model，把 base_url 指向 runner 本机 mock LLM
#（scripts/mock_llm.py）。mock 对任何输入都回
# 通路（首轮实测教训）：base_url 不能用 10.0.2.2——targetSdk 34 默认禁明文 HTTP、
# 无 networkSecurityConfig 时请求直接被系统拦下（界面只报「网络超时」）。
# 改走 adb reverse + 127.0.0.1：回环地址在明文豁免内，零生产配置改动。
# daohang 且 params 为空 → 协调器执行前校验必然拦下、进 NeedsInput 追问态；
# 再补一句参数，应合并走执行（CI 未装高德/百度地图，执行如实失败
#「本机没有安装支持」——恰好证明合并后真的到了执行，而不是又被追问/吞掉）。
# 顺带断言发往 mock 的请求体含带参数说明的 hints 与上下文包（二批注入取证）。
# mock 起不来或 prefs 预写失败时整段跳过不计 FAIL（环境问题不冒充产品失败）；
# 段末清掉 llm prefs 并 force-stop，不污染后续段落与真机配置。
MOCK_PORT=18741
MOCK_LOG="$OUT/mock_llm_requests.log"
MOCK_PID=""
MOCK_UP=0
if command -v python3 > /dev/null 2>&1; then
  rm -f "$MOCK_LOG"
  python3 scripts/mock_llm.py "$MOCK_PORT" "$MOCK_LOG" &
  MOCK_PID=$!
  for i in $(seq 1 20); do
    if curl -s "http://127.0.0.1:$MOCK_PORT/health" 2> /dev/null | grep -q ok; then
      MOCK_UP=1; break
    fi
    sleep 0.5
  done
fi
if [ "$MOCK_UP" = "1" ]; then
  # 设备侧 127.0.0.1:$MOCK_PORT -> runner 本机 mock（回环转发，段末撤销）
  adb reverse "tcp:$MOCK_PORT" "tcp:$MOCK_PORT" > /dev/null 2>&1 || true
  log "reverse list: $(adb reverse --list 2> /dev/null | tr '\n' ' ')"
  adb shell am force-stop "$PKG" || true
  printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" "<map>" \
    '    <string name="api_key">sk-mock</string>' \
    "    <string name=\"base_url\">http://127.0.0.1:$MOCK_PORT</string>" \
    '    <string name="model">mock-model</string>' "</map>" > "$OUT/llm.xml"
  adb push "$OUT/llm.xml" /data/local/tmp/llm.xml > /dev/null 2>&1 || true
  adb shell "run-as $PKG cp /data/local/tmp/llm.xml shared_prefs/llm.xml" || true
  adb shell "run-as $PKG chmod 660 shared_prefs/llm.xml" || true
  if adb shell "run-as $PKG cat shared_prefs/llm.xml" 2> /dev/null | grep -q '127.0.0.1'; then
    adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
    sleep 5
    adb shell input keyevent KEYCODE_HOME || true
    sleep 2
    # 定位胶囊（dump 找占位文案，与第 15 段同法）
    CGX=""; CGY=""
    adb shell uiautomator dump /sdcard/ni_dump0.xml > /dev/null 2>&1 || true
    adb pull /sdcard/ni_dump0.xml "$OUT/ni_dump0.xml" > /dev/null 2>&1 || true
    if [ -f "$OUT/ni_dump0.xml" ]; then
      CNODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/ni_dump0.xml" | head -1)
      if [ -n "$CNODE" ]; then
        CNUMS=$(echo "$CNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
        CX1=$(echo "$CNUMS" | sed -n 1p); CY1=$(echo "$CNUMS" | sed -n 2p)
        CX2=$(echo "$CNUMS" | sed -n 3p); CY2=$(echo "$CNUMS" | sed -n 4p)
        if [ -n "$CX1" ] && [ -n "$CY2" ]; then
          CGX=$(( (CX1 + CX2) / 2 )); CGY=$(( (CY1 + CY2) / 2 ))
        fi
      fi
    fi
    if [ -n "$CGX" ]; then
      # 聚焦 -> 输词（补空格让 LatinIME 组合上屏，同第 15 段注）-> 定位提交钮 -> 提交
      adb shell input swipe "$CGX" "$CGY" "$CGX" "$CGY" 150 || true
      sleep 2
      adb shell input text "daohang" || true
      sleep 1
      adb shell input text "%s" || true
      sleep 1
      SUBX=""; SUBY=""
      adb shell uiautomator dump /sdcard/ni_kb.xml > /dev/null 2>&1 || true
      adb pull /sdcard/ni_kb.xml "$OUT/ni_kb.xml" > /dev/null 2>&1 || true
      if [ -f "$OUT/ni_kb.xml" ]; then
        BNODE=$(grep -o '<node[^>]*content-desc="提交"[^>]*>' "$OUT/ni_kb.xml" | head -1)
        if [ -n "$BNODE" ]; then
          BNUMS=$(echo "$BNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
          BX1=$(echo "$BNUMS" | sed -n 1p); BY1=$(echo "$BNUMS" | sed -n 2p)
          BX2=$(echo "$BNUMS" | sed -n 3p); BY2=$(echo "$BNUMS" | sed -n 4p)
          if [ -n "$BX1" ] && [ -n "$BY2" ]; then
            SUBX=$(( (BX1 + BX2) / 2 )); SUBY=$(( (BY1 + BY2) / 2 ))
          fi
        fi
      fi
      if [ -n "$SUBX" ]; then
        adb shell input swipe "$SUBX" "$SUBY" "$SUBX" "$SUBY" 150 || true
        # 轮询等追问出现（plan 走本机 mock 应很快，给足慢腿余量）
        FOUND_Q=0
        for i in $(seq 1 12); do
          sleep 1
          adb shell uiautomator dump /sdcard/ni_q.xml > /dev/null 2>&1 || true
          adb pull /sdcard/ni_q.xml "$OUT/ni_q.xml" > /dev/null 2>&1 || true
          if grep -q "去哪儿" "$OUT/ni_q.xml" 2> /dev/null; then FOUND_Q=1; break; fi
        done
        shot "intent-needsinput"
        # 诊断（mock 通路排障）：把 LlmPlan 的网络异常原文打进冒烟日志并存档，
        # 区分「明文被拦 / 转发未生效 / 连接拒绝」，不靠猜。
        adb logcat -d 2> /dev/null | grep "LlmPlan" | tail -4 | while IFS= read -r line; do log "llm: $line"; done
        adb logcat -d 2> /dev/null | grep "LlmPlan" > "$OUT/ni_logcat.txt" || true
        if [ "$FOUND_Q" = "1" ]; then
          ok "needsinput-question"
        else
          ng "needsinput-question"
        fi
        # planner 注入取证：请求体里应有带参数说明的 hints 与上下文包
        if grep -q "必填 to=目的地" "$MOCK_LOG" 2> /dev/null && grep -q "【现在】" "$MOCK_LOG" 2> /dev/null; then
          ok "planner-pack-injected"
        else
          ng "planner-pack-injected"
        fi
        # 补参：再聚焦（追问态下输入框已清空）-> 输目的地 -> 提交 -> 应离开追问态走到执行
        adb shell input swipe "$CGX" "$CGY" "$CGX" "$CGY" 150 || true
        sleep 2
        adb shell input text "jichang" || true
        sleep 1
        adb shell input text "%s" || true
        sleep 1
        SUBX2=""; SUBY2=""
        adb shell uiautomator dump /sdcard/ni_kb2.xml > /dev/null 2>&1 || true
        adb pull /sdcard/ni_kb2.xml "$OUT/ni_kb2.xml" > /dev/null 2>&1 || true
        if [ -f "$OUT/ni_kb2.xml" ]; then
          BNODE2=$(grep -o '<node[^>]*content-desc="提交"[^>]*>' "$OUT/ni_kb2.xml" | head -1)
          if [ -n "$BNODE2" ]; then
            BNUMS2=$(echo "$BNODE2" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
            BX21=$(echo "$BNUMS2" | sed -n 1p); BY21=$(echo "$BNUMS2" | sed -n 2p)
            BX22=$(echo "$BNUMS2" | sed -n 3p); BY22=$(echo "$BNUMS2" | sed -n 4p)
            if [ -n "$BX21" ] && [ -n "$BY22" ]; then
              SUBX2=$(( (BX21 + BX22) / 2 )); SUBY2=$(( (BY21 + BY22) / 2 ))
            fi
          fi
        fi
        if [ -n "$SUBX2" ]; then
          adb shell input swipe "$SUBX2" "$SUBY2" "$SUBX2" "$SUBY2" 150 || true
          sleep 4
          shot "intent-needsinput-answered"
          adb shell uiautomator dump /sdcard/ni_a.xml > /dev/null 2>&1 || true
          adb pull /sdcard/ni_a.xml "$OUT/ni_a.xml" > /dev/null 2>&1 || true
          if grep -q "本机没有安装支持" "$OUT/ni_a.xml" 2> /dev/null && ! grep -q "去哪儿" "$OUT/ni_a.xml" 2> /dev/null; then
            ok "needsinput-fill-executes"
          else
            ng "needsinput-fill-executes"
          fi
        else
          ng "needsinput-fill-executes"
        fi
      else
        ng "needsinput-question"
        ng "planner-pack-injected"
        ng "needsinput-fill-executes"
      fi
    else
      ng "needsinput-question"
      ng "planner-pack-injected"
      ng "needsinput-fill-executes"
    fi
    # 清理：删 llm prefs + force-stop 掉 SP 缓存，恢复无 Key 基线
    adb shell "run-as $PKG rm -f shared_prefs/llm.xml" || true
    adb reverse --remove "tcp:$MOCK_PORT" > /dev/null 2>&1 || true
    adb shell am force-stop "$PKG" || true
  else
    log "llm prefs seed failed - needsinput segment skipped (not counted)"
  fi
else
  log "mock llm unavailable - needsinput segment skipped (not counted)"
fi
if [ -n "$MOCK_PID" ]; then kill "$MOCK_PID" 2> /dev/null || true; fi

# --- 18. crash 检查（只看本包 FATAL，避免系统噪音）---
if adb logcat -d 2>/dev/null | grep -i "FATAL EXCEPTION" | grep -q "$PKG"; then
  ng "no-fatal-crash"
  adb logcat -d 2>/dev/null | grep -i -A15 "FATAL EXCEPTION" | head -30 || true
else
  ok "no-fatal-crash"
fi

log "RESULT pass=$PASS fail=$FAIL"
exit "$FAIL"
