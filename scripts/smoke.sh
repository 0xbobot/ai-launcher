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
# v0.68.93（布局与交互 Loop 一批）追加：第 14 段内加会议双处呈现去重断言
# （nextup-dedupe：种子事件在场时聚焦出建议面，标题全屏只出现一次）；
# 第 18 段为意图焦点点外部取消（focus-cancel-outside：聚焦输字后点七仔，
# dump 无 focused 节点且文本保留）；crash 检查顺延为第 19 段。
# 玻璃试点批合入后：玻璃取证续为第 19 段，crash 再顺延为第 20 段。
# v0.68.95（布局与交互 Loop 二批-A）追加：第 20 段为数据新鲜度（装/卸桩应用
# 在应用中心开着时实时刷新 A-Z 列表，catalog-install/uninstall-refresh），
# crash 检查再顺延为第 21 段。
# v0.68.97（布局普查批）追加：第 21 段为布局几何断言（scripts/layout_check.py
# 量 tpl_*.xml / fan_dump.xml 与本段新拉的 layout_home/ac/settings dump：
# cover_hero / 胶囊 / 发送键 / NextUp / 七仔 / Dock 面板两两不相交 + 关键元素
# 完整在屏内），crash 检查再顺延为第 22 段。
# v0.68.98（布局与交互 Loop 二批-C·F-06）追加：第 22 段为 Planning 取消
# （planning-strip / planning-cancel-dismisses / planning-cancel-drops-late：
# mock 慢响应把协调器停在 Planning 态 -> 思考条带「取消」-> 点取消后条消失 ->
# 等迟到结果落地窗口过后断言追问不上屏），crash 检查再顺延为第 23 段。形态 env 追加 SMOKE_WM_SIZE /
# SMOKE_USER_ROTATION：第 0 段几何读取前覆写显示尺寸与旋转锁定（矩阵的
# 横屏手机短高、平板竖屏腿用），不带 env 时行为不变。
# 第 19 段为玻璃试点批双背景取证：默认壁纸 / 沙丘照片壁纸（scripts/gen_wallpaper.py
# 生成）/ 纯纸面三态各截首页（Dock+TODAY 卡），照片与纸面下再长按七仔截气泡
# （glass-default / glass-photo / glass-bubble-photo / glass-paper /
# glass-bubble-paper.png，气泡图各双拍一张 -b 保险，另有 glass-wp-debug.txt
# 壁纸诊断）。玻璃质感是视觉判断、不设像素断言，硬断言只有换壁纸+重启后应用
# 存活且首页可达（glass-alive-after-wallpaper-switches）。
# 体检批D ⑥（testTag 分段改造）：关键 composable 已加 testTag（intent_input /
# intent_submit / intent_clear / result_item / pet_qizai / today_nextup /
# appcenter_settings / dock_item），MainActivity 根容器开 testTagsAsResourceId，
# dump 里以 resource-id 暴露。各点按段的定位优先序：testTag 节点 > 原文本/
# content-desc 定位 > SX/SY 兜底坐标；断言口径未动。
# 剩余硬编码坐标清单（无法或不宜语义定位，保留并在此登记）：
#  1) 第 2 段兜底：onboarding「跳过」(950,130)、低电量「不用」(585,1310)——
#     仅 prefs 预写失败时走；该路径历史上 uiautomator 解析翻过车（见段内注释），
#     且属兜底的兜底，坐标经 SX/SY 换算，保留。
#  2) 手势类 swipe（第 5/5b 段扇形展开、第 7/10/12 段上滑开应用中心）：
#     起点必须落在空白背景/避开 Dock 与卡片，节点定位无意义，保留坐标。
#  3) 各段 dump 定位失败时的 SX/SY 兜底值（胶囊 540,1247、宠物 540,950 等）：
#     三级定位的最后一级，保留。

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

# --- 0a. 形态覆写（v0.68.97 布局普查批）：在几何读取前把基线设备改造成
# 横屏手机短高 / 平板竖屏等形态；不带 env 时整段跳过、行为不变 ---
if [ -n "$SMOKE_WM_SIZE" ]; then
  adb shell wm size "$SMOKE_WM_SIZE" || true
  log "wm size override -> $SMOKE_WM_SIZE"
fi
if [ -n "$SMOKE_USER_ROTATION" ]; then
  adb shell settings put system accelerometer_rotation 0 || true
  adb shell settings put system user_rotation "$SMOKE_USER_ROTATION" || true
  log "user rotation lock -> $SMOKE_USER_ROTATION"
fi
if [ -n "$SMOKE_WM_SIZE" ] || [ -n "$SMOKE_USER_ROTATION" ]; then
  # v0.68.97 实测：覆写不重启只部分生效——wm 尺寸先行、旋转迟迟不跟随，
  # 横屏腿前半程（模板段等）仍按竖屏跑，直到玻璃段换壁纸重启后才横过来，
  # 且 SCR 几何与真实坐标系脱节导致多段坐标断言误伤。覆写后立即重启一次，
  # 让整个运行期形态一致；覆写值重启后保持（同轮实测）。
  log "form-factor override: rebooting to apply"
  adb reboot > /dev/null 2>&1 || true
  adb wait-for-device > /dev/null 2>&1 || true
  BOOTED=""
  for i in $(seq 1 40); do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then BOOTED=1; break; fi
    sleep 3
  done
  [ -n "$BOOTED" ] || log "form-factor override: boot wait timeout, continuing anyway"
  adb shell input keyevent KEYCODE_WAKEUP || true
  sleep 2
fi

# --- 0. 屏幕几何（R4）：基准 1080x2400 的写死坐标经 SX/SY 换算到实际分辨率；密度供 a11y 的 px->dp ---
# v0.68.97 教训：wm size 输出首行是 Physical size，有形态覆写时必须取 Override 行，
# 否则 SCR_W/H 与真实 UI 坐标系脱节（横屏腿首轮实测：几何读成竖屏、全程坐标错位）。
SIZE_LINE=$(adb shell wm size 2>/dev/null | grep -i "override" | grep -o '[0-9]\+x[0-9]\+' | head -1)
if [ -z "$SIZE_LINE" ]; then
  SIZE_LINE=$(adb shell wm size 2>/dev/null | grep -o '[0-9]\+x[0-9]\+' | head -1)
fi
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

# v0.68.99（主页重构 R-1）：静态主页撤胶囊——简报 + Dock 两层，Dock 左端
# 七仔格（dock_pet_cell）是意图入口。原「胶囊常驻」类段全部改走
# enter_input_mode helper（点七仔格进输入态）再沿原路径验证；第 11 段静态
# 断言改为简报（cover_hero）+ 七仔格在场；第 18 段升级为「退出 + 重进文本
# 保留」三段断言；today_nextup 已迁移到简报会行（第 14/19/21 段语义不变）。

# --- 体检批D ⑥：testTag 语义定位 helper ---
# center_rid <dumpfile> <resource-id>：在已拉取的 dump 里按 resource-id（=testTag）
# 找第一个节点，命中打印其 bounds 中心 "X Y"，未命中无输出、返回 1。
# tap_rid <dumpfile> <resource-id>：同上并直接 press 该中心，命中返回 0。
center_rid() {
  local F="$1" RID="$2" NODE NUMS X1 Y1 X2 Y2
  [ -f "$F" ] || return 1
  NODE=$(grep -o "<node[^>]*resource-id=\"$RID\"[^>]*>" "$F" | head -1)
  [ -n "$NODE" ] || return 1
  NUMS=$(echo "$NODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
  X1=$(echo "$NUMS" | sed -n 1p); Y1=$(echo "$NUMS" | sed -n 2p)
  X2=$(echo "$NUMS" | sed -n 3p); Y2=$(echo "$NUMS" | sed -n 4p)
  [ -n "$X1" ] && [ -n "$Y2" ] || return 1
  echo "$(( (X1 + X2) / 2 )) $(( (Y1 + Y2) / 2 ))"
}
tap_rid() {
  local F="$1" RID="$2" CTR
  CTR=$(center_rid "$F" "$RID") || return 1
  [ -n "$CTR" ] || return 1
  log "tap by testTag $RID at ($CTR)"
  adb shell input swipe $(echo "$CTR" | cut -d' ' -f1) $(echo "$CTR" | cut -d' ' -f2) \
    $(echo "$CTR" | cut -d' ' -f1) $(echo "$CTR" | cut -d' ' -f2) 150 || true
  return 0
}

# --- v0.68.99（主页重构 R-1）：进输入态 helper ---
# 静态主页已无常驻胶囊：需要胶囊的段先调本 helper——dump 到目标文件，若
# intent_input 已在场直接成功；否则点 Dock 七仔格（兜底点简报讲述者七仔）
# 进输入态（胶囊底部浮现 + 自动聚焦 + 键盘弹起）后重新 dump 同一文件确认。
# 返回 0 = 胶囊在场。注意返回时键盘是弹起态，需要「键盘收起坐标系」的段
# 自行先 BACK 再重 dump（第 9 段法）。
enter_input_mode() {
  local F="$1"
  adb shell uiautomator dump /sdcard/eim_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/eim_dump.xml "$F" > /dev/null 2>&1 || true
  if [ -f "$F" ] && grep -q 'resource-id="intent_input"' "$F"; then
    log "enter_input_mode: capsule already present"
    return 0
  fi
  if ! tap_rid "$F" "dock_pet_cell"; then
    tap_rid "$F" "pet_qizai" || true
  fi
  sleep 2
  adb shell uiautomator dump /sdcard/eim_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/eim_dump.xml "$F" > /dev/null 2>&1 || true
  if [ -f "$F" ] && grep -q 'resource-id="intent_input"' "$F"; then
    log "enter_input_mode: capsule present after pet-cell tap"
    return 0
  fi
  # uiautomator 偶发残缺 dump 会让第一份 dump 缺七仔格、点按落空（R-1 二轮
  # 实测 giant 模板中招，同第 10 段登记过的残缺层级现象）：第二份 dump 里
  # 七仔格在场则再点一次重试，不把偶发当产品失败。
  if [ -f "$F" ] && grep -q 'resource-id="dock_pet_cell"' "$F"; then
    log "enter_input_mode: retrying pet-cell tap (first dump may have been partial)"
    tap_rid "$F" "dock_pet_cell" || true
    sleep 2
    adb shell uiautomator dump /sdcard/eim_dump.xml > /dev/null 2>&1 || true
    adb pull /sdcard/eim_dump.xml "$F" > /dev/null 2>&1 || true
    if [ -f "$F" ] && grep -q 'resource-id="intent_input"' "$F"; then
      log "enter_input_mode: capsule present after retry"
      return 0
    fi
  fi
  log "enter_input_mode: capsule NOT present"
  return 1
}

# --- 1. 安装 ---
if [ -f "$APK" ]; then
  if adb install -r -g "$APK"; then ok "install"; else ng "install"; fi
else
  ng "apk-missing($APK)"
fi

# --- 2. 预写 SharedPreferences：跳过 onboarding（onboarded）+ 低电量保护询问（battery_asked）---
# 体检批D ⑥ 登记：本段失败兜底的两处坐标点按（onboarding「跳过」950,130 /
# 低电量「不用」585,1310）为剩余硬编码段之一——仅 prefs 预写失败时才进兜底，
# 且该路径历史上 uiautomator 解析翻过车（见下文注），保留坐标（经 SX/SY 换算）不迁语义定位。
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

# --- 6. 点七仔（宠物区中央；果冻反馈+开心表情 1.2 秒，0.7 秒后截图抓反应中途）---
# 体检批D ⑥：优先 dump 按 testTag pet_qizai 定位七仔本体中心；未命中才走旧坐标
# （按当前首页布局实测约 (540,950)；旧坐标 840,630 点空导致 pet 与 home 同帧，R1 实测）。
# press 150ms 模拟真手指（瞬时 tap 会被首页手势层取消，见第 9 段注）
adb shell uiautomator dump /sdcard/pet_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/pet_dump.xml "$OUT/pet_dump.xml" > /dev/null 2>&1 || true
if ! tap_rid "$OUT/pet_dump.xml" "pet_qizai"; then
  adb shell input swipe "$(SX 540)" "$(SY 950)" "$(SX 540)" "$(SY 950)" 150 || true
fi
sleep 0.7
shot "pet"
# v0.68.99（主页重构 R-1）：点七仔现在 = 进入输入态（胶囊浮现 + 键盘弹起），
# pet.png 即输入态开启瞬间（果冻反馈仍在七仔身上）。随后退回静态基线：
# 第 1 次 BACK 收键盘（焦点保留）、第 2 次 BACK 走输入态 BackHandler 收起
# 胶囊、Dock 回位——否则下一段上滑会被输入态/键盘干扰。
adb shell input keyevent KEYCODE_BACK || true
sleep 1
adb shell input keyevent KEYCODE_BACK || true
sleep 1

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
# 体检批D ⑥：testTag 优先（命中则覆盖 content-desc 定位结果）
CTR=$(center_rid "$OUT/set_dump.xml" "appcenter_settings")
if [ -n "$CTR" ]; then SGX=$(echo "$CTR" | cut -d' ' -f1); SGY=$(echo "$CTR" | cut -d' ' -f2); log "settings gear by tag at ($SGX,$SGY)"; fi
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
# v0.68.99（主页重构 R-1）：静态无胶囊——先点 Dock 七仔格进输入态；进态后
# 先 BACK 收键盘（焦点与输入态保留），再拉 dump 量坐标——与旧流程「dump
# 时键盘未弹起」的坐标系一致，后续点提交的坐标才准。
enter_input_mode "$OUT/intent_dump.xml" || true
adb shell input keyevent KEYCODE_BACK || true
sleep 1
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
# 体检批D ⑥：testTag 优先（命中则覆盖文本/content-desc 定位结果）
CTR=$(center_rid "$OUT/intent_dump.xml" "intent_input")
if [ -n "$CTR" ]; then CX=$(echo "$CTR" | cut -d' ' -f1); CY=$(echo "$CTR" | cut -d' ' -f2); log "intent capsule by tag at ($CX,$CY)"; fi
CTR=$(center_rid "$OUT/intent_dump.xml" "intent_submit")
if [ -n "$CTR" ]; then BX=$(echo "$CTR" | cut -d' ' -f1); BY=$(echo "$CTR" | cut -d' ' -f2); log "intent submit by tag at ($BX,$BY)"; fi
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
# 定位胶囊（v0.68.99：先点七仔格进输入态再 dump 找占位，兜底坐标同第 9 段）
LCX=$(SX 540); LCY=$(SY 1247)
enter_input_mode "$OUT/local_dump0.xml" || true
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
# 体检批D ⑥：testTag 优先（命中则覆盖文本定位结果）
CTR=$(center_rid "$OUT/local_dump0.xml" "intent_input")
if [ -n "$CTR" ]; then LCX=$(echo "$CTR" | cut -d' ' -f1); LCY=$(echo "$CTR" | cut -d' ' -f2); log "local capsule by tag at ($LCX,$LCY)"; fi
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
# 体检批D ⑥：testTag 优先（命中则覆盖文本定位结果）
CTR=$(center_rid "$OUT/ac_dump.xml" "intent_input")
if [ -n "$CTR" ]; then ACX=$(echo "$CTR" | cut -d' ' -f1); ACY=$(echo "$CTR" | cut -d' ' -f2); log "appcenter capsule by tag at ($ACX,$ACY)"; fi
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
# 体检批D ⑥：testTag 优先（命中则覆盖 content-desc 定位结果；ok/ng 口径不动）
if [ -f "$OUT/ac_dump2.xml" ]; then
  CTR=$(center_rid "$OUT/ac_dump2.xml" "intent_clear")
  if [ -n "$CTR" ]; then KX=$(echo "$CTR" | cut -d' ' -f1); KY=$(echo "$CTR" | cut -d' ' -f2); log "appcenter clear by tag at ($KX,$KY)"; fi
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
# 截 intent-tpl-<name>.png 并 dump 断言静态在场，再点七仔格进输入态输 "clo" 截 -local 图并断言结果条 Clock。
# v0.68.99（主页重构 R-1）：静态断言改为简报（cover_hero）+ Dock 七仔格在场
# （断言名 tpl-<tpl>-brief；静态已无胶囊）；聚焦态 dump 文件名不变
# （tpl_<tpl>_suggest.xml 仍供第 21 段 FOCUS_PAIRS 几何断言消费）。
# 日期改不动（如镜像无权限）时只 log 记录、不判失败——此时以代码级证据为准：
# 三模板共用同一 CoverHero 渲染路径与同一 Dock，静态构图同源。
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
  # v0.68.99：tpl_<tpl>.xml 仍是静态 dump（第 21 段消费：静态下 intent_input /
  # intent_submit 缺席走 INFO 跳过，cover_hero / dock_panel / pet_qizai 照常断言）
  if [ -f "$OUT/tpl_${TPL}.xml" ] && grep -q 'resource-id="cover_hero"' "$OUT/tpl_${TPL}.xml" && grep -q 'resource-id="dock_pet_cell"' "$OUT/tpl_${TPL}.xml"; then
    ok "tpl-$TPL-brief"
    # 点七仔格进输入态后，在输入态 dump 里定位胶囊
    enter_input_mode "$OUT/tpl_${TPL}_in.xml" || true
    TNODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/tpl_${TPL}_in.xml" | head -1)
    TNUMS=$(echo "$TNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    TX1=$(echo "$TNUMS" | sed -n 1p); TY1=$(echo "$TNUMS" | sed -n 2p)
    TX2=$(echo "$TNUMS" | sed -n 3p); TY2=$(echo "$TNUMS" | sed -n 4p)
    if [ -n "$TX1" ] && [ -n "$TY2" ]; then
      TCX=$(( (TX1 + TX2) / 2 )); TCY=$(( (TY1 + TY2) / 2 ))
      # 体检批D ⑥：testTag 优先（命中则覆盖文本定位结果）
      CTR=$(center_rid "$OUT/tpl_${TPL}_in.xml" "intent_input")
      if [ -n "$CTR" ]; then TCX=$(echo "$CTR" | cut -d' ' -f1); TCY=$(echo "$CTR" | cut -d' ' -f2); log "tpl capsule by tag at ($TCX,$TCY)"; fi
      adb shell input swipe "$TCX" "$TCY" "$TCX" "$TCY" 150 || true
      sleep 2
      # 意图改进一批：聚焦+空文本先截建议面（三模板各一张供人工亲验）并断言标题在场；
      # 之后照旧输 "clo" 验本地结果（文本非空后建议面必须让位给本地结果）
      shot "intent-tpl-$TPL-suggest"
      # v0.68.99（R-1）：几何断言用的聚焦态 dump 改在收键盘后拉——胶囊底部
      # 锚定，IME 弹起时窗口被压缩，胶囊在压缩帧里压住 hero 下沿（真实视觉
      # 即输入浮层盖住简报下部，与聊天类 App 一致）；收键盘后焦点与建议面
      # 保留、窗口恢复全高，此时验 hero/pet 与胶囊两两不相交（同第 15 段
      # suggest_dump 的既有口径）。截图仍是键盘弹起态，不受影响。
      adb shell input keyevent KEYCODE_BACK || true
      sleep 1
      adb shell uiautomator dump /sdcard/tpl_dump_s.xml > /dev/null 2>&1 || true
      adb pull /sdcard/tpl_dump_s.xml "$OUT/tpl_${TPL}_suggest.xml" > /dev/null 2>&1 || true
      if [ -f "$OUT/tpl_${TPL}_suggest.xml" ] && grep -q '七仔猜你现在想用' "$OUT/tpl_${TPL}_suggest.xml"; then
        ok "tpl-$TPL-suggest"
      else
        ng "tpl-$TPL-suggest"
      fi
      # 收键盘后重新点胶囊唤回键盘再输字（同第 15 段后半段法：BACK 只收
      # 键盘、焦点保留，但输字前重点一次最稳）；其后的 BACK 仍是收键盘
      CTR=$(center_rid "$OUT/tpl_${TPL}_suggest.xml" "intent_input")
      if [ -n "$CTR" ]; then
        adb shell input swipe $(echo "$CTR" | cut -d' ' -f1) $(echo "$CTR" | cut -d' ' -f2) $(echo "$CTR" | cut -d' ' -f1) $(echo "$CTR" | cut -d' ' -f2) 150 || true
        sleep 2
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
    ng "tpl-$TPL-brief"
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
# v0.68.99：先点七仔格进输入态（胶囊聚焦即倾听姿态），再定位
enter_input_mode "$OUT/focus_dump.xml" || true
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
# 体检批D ⑥：testTag 优先（命中则覆盖文本定位结果）
CTR=$(center_rid "$OUT/focus_dump.xml" "intent_input")
if [ -n "$CTR" ]; then FCX=$(echo "$CTR" | cut -d' ' -f1); FCY=$(echo "$CTR" | cut -d' ' -f2); log "reaction capsule by tag at ($FCX,$FCY)"; fi
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
  # 体检批D ⑥：点按点优先用会议卡 testTag 节点（today_nextup）中心——卡面即点按面；
  # 标题中心保留给上面的可点祖先断言。未命中则沿用标题中心（原行为）。
  local CTR2
  CTR2=$(center_rid "$OUT/nextup_${ANAME}.xml" "today_nextup")
  if [ -n "$CTR2" ]; then
    TX=$(echo "$CTR2" | cut -d' ' -f1); TY=$(echo "$CTR2" | cut -d' ' -f2)
    log "nextup-$ANAME tap by tag at ($TX,$TY)"
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


nextup_dedupe_flow() {
  # $1=事件标题。v0.68.93（布局与交互 Loop 一批④）：同一会议双处呈现去重断言。
  # v0.68.99（主页重构 R-1）：会议常驻主场由 NextUp 条迁移为简报会行
  # （today_nextup 在简报内）：种子事件在场、进输入态出建议面时，该标题在
  # 全屏 dump 里只能出现一次（简报会行标题），建议面内不得再有会议行。
  # 环境门同 nextup_tap_flow：种子事件进不了 Instances 视图时简报会行无从
  # 呈现，明示 SKIP 不计数（不冒充已验，报告如实标注）。
  local TITLE="$1"
  adb shell am force-stop "$PKG" || true
  sleep 1
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
  sleep 4
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
  local WIN_B WIN_E DIAG_IN
  WIN_B=$(( (EMU_S - 86400) * 1000 )); WIN_E=$(( (EMU_S + 86400) * 1000 ))
  DIAG_IN=$(adb shell content query --uri "content://com.android.calendar/instances/when/$WIN_B/$WIN_E" --projection title 2>&1 | head -4 | tr '\n' ' ')
  if ! echo "$DIAG_IN" | grep -q "$TITLE"; then
    log "nextup-dedupe: SKIP (seeded event not visible in Instances on this image; dedupe unverified here)"
    return
  fi
  adb shell uiautomator dump /sdcard/dedupe_dump0.xml > /dev/null 2>&1 || true
  adb pull /sdcard/dedupe_dump0.xml "$OUT/dedupe_dump0.xml" > /dev/null 2>&1 || true
  if [ -f "$OUT/dedupe_dump0.xml" ] && grep -q 'resource-id="today_nextup"' "$OUT/dedupe_dump0.xml"; then
    ok "nextup-dedupe-home"
  else
    ng "nextup-dedupe-home"
    return
  fi
  # v0.68.99：点 Dock 七仔格进输入态出建议面（静态无胶囊可聚焦）
  enter_input_mode "$OUT/dedupe_in.xml" || true
  # BACK 只收键盘、焦点与建议面保留、输入态不退（同第 15 段法）：之后 dump
  # 里简报会行与建议面同屏——正是去重要验的同屏状态。
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
  shot "intent-dedupe-suggest"
  adb shell uiautomator dump /sdcard/dedupe_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/dedupe_dump.xml "$OUT/dedupe_dump.xml" > /dev/null 2>&1 || true
  if [ ! -f "$OUT/dedupe_dump.xml" ]; then
    ng "nextup-dedupe"
    return
  fi
  # 前提：建议面真的在场（否则"无会议行"是空断言，直接判失败而非通过）
  if ! grep -q '七仔猜你现在想用' "$OUT/dedupe_dump.xml"; then
    log "nextup-dedupe: suggestion panel not present - assertion precondition missing"
    ng "nextup-dedupe"
    return
  fi
  # 标题出现次数：简报会行标题一处（纯标题文本节点；行自身的
  # content-desc 是另一属性不计入）；建议面若复刻会议行会有第二处同名文本。
  local CNT
  CNT=$(grep -o "text=\"[^\"]*${TITLE}[^\"]*\"" "$OUT/dedupe_dump.xml" | wc -l | tr -d ' ')
  log "nextup-dedupe: title occurrences=$CNT (expect 1, brief meeting row only)"
  if [ "$CNT" = "1" ] && grep -q 'resource-id="today_nextup"' "$OUT/dedupe_dump.xml"; then
    ok "nextup-dedupe"
  else
    ng "nextup-dedupe"
  fi
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
    nextup_dedupe_flow "SmokeNoLink"
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
# v0.68.99：先点七仔格进输入态（聚焦即出建议面），再在输入态 dump 里定位
enter_input_mode "$OUT/suggest_dump0.xml" || true
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
# 体检批D ⑥：testTag 优先（命中则覆盖文本定位结果）
CTR=$(center_rid "$OUT/suggest_dump0.xml" "intent_input")
if [ -n "$CTR" ]; then SGX=$(echo "$CTR" | cut -d' ' -f1); SGY=$(echo "$CTR" | cut -d' ' -f2); log "suggest capsule by tag at ($SGX,$SGY)"; fi
adb shell input swipe "$SGX" "$SGY" "$SGX" "$SGY" 150 || true
sleep 2
shot "intent-suggest"
# 量测用 dump 在收键盘后拉：键盘弹起时窗口 resize，坐标与层级都不可靠；
# BACK 只收键盘，焦点与建议面保留（同第 10 段法），再截一张图。
# v0.68.99 注：输入态下 Dock 按设计让位（D1/D2 卡隐藏），本图不再验 Dock
# 在位（Dock 在静态基线由 home.png / 第 21 段几何断言覆盖）。
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
# 点第一个建议应用：体检批D ⑥ 起优先按 testTag result_item 取第一个节点
# （文档序≈布局序，第一项即 Top1，其 bounds 即整项可点容器）；
# 未命中退回旧启发式：标题文本之后第一个带 content-desc 的节点（应用项图标的
# 合并语义节点）。打开证据同第 10 段双路。
T1X=""; T1Y=""
CTR=$(center_rid "$OUT/suggest_dump.xml" "result_item")
if [ -n "$CTR" ]; then
  T1X=$(echo "$CTR" | cut -d' ' -f1); T1Y=$(echo "$CTR" | cut -d' ' -f2)
  log "suggest top1 by tag at ($T1X,$T1Y)"
fi
if [ -z "$T1X" ] && [ -f "$OUT/suggest_dump.xml" ]; then
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
# v0.68.99：点 Top1 打开应用后输入态已自动退出（焦点释放触发）——重新进输入态再定位
enter_input_mode "$OUT/suggest_dump1.xml" || true
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
# 体检批D ⑥：testTag 优先（命中则覆盖文本定位结果）
CTR=$(center_rid "$OUT/suggest_dump1.xml" "intent_input")
if [ -n "$CTR" ]; then SGX=$(echo "$CTR" | cut -d' ' -f1); SGY=$(echo "$CTR" | cut -d' ' -f2); log "suggest capsule2 by tag at ($SGX,$SGY)"; fi
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
# 体检批D ⑥：testTag 优先（命中则覆盖 content-desc 定位结果）
CTR=$(center_rid "$OUT/suggest_kb.xml" "intent_submit")
if [ -n "$CTR" ]; then SUBX=$(echo "$CTR" | cut -d' ' -f1); SUBY=$(echo "$CTR" | cut -d' ' -f2); log "submit button by tag at ($SUBX,$SUBY)"; fi
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

# --- 17. 缺参追问态 + 能力动态绑定端到端（意图改进二批 + v0.68.88）---
# 手段（仅测试路径，生产逻辑零改动）：LlmConfig 读 SharedPreferences "llm"——
# run-as 预写 api_key/base_url/model，把 base_url 指向 runner 本机 mock LLM
#（scripts/mock_llm.py，对任何输入恒回 daohang 空参）。通路沿用二批定案：
# adb reverse + 127.0.0.1 + src/debug 网络配置放行回环明文。
#
# 阶段 A（出厂镜像：没装任何地图应用）：
# - 提交 -> mock 回 daohang 空参 -> 协调器执行前校验拦下进 NeedsInput 追问
#   （needsinput-question，沿用二批）；
# - 该次请求里的 hints 不得含未装应用的能力（wechat/doubao）——v0.68.88
#   「不许诺做不到的事」（hints-excludes-unavailable）；上下文包【现在】应在；
# - 补参后执行：无提供方且本镜像无 geo 处理者 -> 如实报「本机没有安装支持」
#   并离开追问态（fill-without-provider-honest）。
# 阶段 B（装上腾讯地图桩 scripts/tencent-stub，包名 com.tencent.map——复现
# Bob 真机「只装腾讯地图」场景；桩由 build-tools 在 runner 上现场拼装签名）：
# - 同一句提交，这次请求里的 hints 必须含 daohang 的参数说明——绑定随实装
#   变化（planner-pack-injected，含上下文包断言）；
# - 追问补参后执行应真正分发：前台变成 com.tencent.map
#   （needsinput-fill-executes，语义较二批升级：从「如实失败」变「真打开了」）。
# mock 起不来或 prefs 预写失败时整段跳过不计 FAIL（环境问题不冒充产品失败）；
# 段末清掉 llm prefs 并 force-stop，桩卸载，不污染后续段落与真机配置。
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

# 定位胶囊 -> CGX/CGY（体检批D ⑥：优先 testTag intent_input；未命中退回 dump 占位文案，与第 15 段同法）
ni_locate_capsule() {
  CGX=""; CGY=""
  # v0.68.99（主页重构 R-1）：静态无胶囊——先确保已进输入态（点七仔格），
  # helper 的 dump 即 ni_dump0，后续定位逻辑不变。第 22 段共用本函数。
  enter_input_mode "$OUT/ni_dump0.xml" || true
  local CTR=$(center_rid "$OUT/ni_dump0.xml" "intent_input")
  if [ -n "$CTR" ]; then
    CGX=$(echo "$CTR" | cut -d' ' -f1); CGY=$(echo "$CTR" | cut -d' ' -f2)
    log "ni capsule by tag at ($CGX,$CGY)"
    return 0
  fi
  if [ -f "$OUT/ni_dump0.xml" ]; then
    local CNODE=$(grep -o '<node[^>]*text="想做什么？"[^>]*>' "$OUT/ni_dump0.xml" | head -1)
    if [ -n "$CNODE" ]; then
      local CNUMS=$(echo "$CNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
      local CX1=$(echo "$CNUMS" | sed -n 1p); local CY1=$(echo "$CNUMS" | sed -n 2p)
      local CX2=$(echo "$CNUMS" | sed -n 3p); local CY2=$(echo "$CNUMS" | sed -n 4p)
      if [ -n "$CX1" ] && [ -n "$CY2" ]; then
        CGX=$(( (CX1 + CX2) / 2 )); CGY=$(( (CY1 + CY2) / 2 ))
      fi
    fi
  fi
  [ -n "$CGX" ]
}

# 定位并点按金色提交钮（体检批D ⑥：优先 testTag intent_submit；未命中退回 content-desc=提交）；成功返回 0
ni_tap_submit() {
  adb shell uiautomator dump /sdcard/ni_btn.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ni_btn.xml "$OUT/ni_btn.xml" > /dev/null 2>&1 || true
  if tap_rid "$OUT/ni_btn.xml" "intent_submit"; then
    return 0
  fi
  local BNODE=$(grep -o '<node[^>]*content-desc="提交"[^>]*>' "$OUT/ni_btn.xml" 2> /dev/null | head -1)
  [ -n "$BNODE" ] || return 1
  local BNUMS=$(echo "$BNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
  local BX1=$(echo "$BNUMS" | sed -n 1p); local BY1=$(echo "$BNUMS" | sed -n 2p)
  local BX2=$(echo "$BNUMS" | sed -n 3p); local BY2=$(echo "$BNUMS" | sed -n 4p)
  [ -n "$BX1" ] && [ -n "$BY2" ] || return 1
  adb shell input swipe $(( (BX1 + BX2) / 2 )) $(( (BY1 + BY2) / 2 )) $(( (BX1 + BX2) / 2 )) $(( (BY1 + BY2) / 2 )) 150 > /dev/null 2>&1 || true
  return 0
}

# 聚焦胶囊 -> 输词（补空格让 LatinIME 组合上屏，同第 15 段注）-> 点提交
ni_type_submit() {
  # v0.68.99（R-1）：胶囊位置随 IME 升降而变（底部锚定：弹起时随窗口压缩
  # 上移、收起时落回底部）——每次输字前按 testTag 重新定位再点，不复用
  # ni_locate_capsule 的缓存坐标（旧版胶囊在流内、位置恒定才可复用；
  # R-1 首轮实测：补参时点空、文字没进框，fill 两项连坐）。
  adb shell uiautomator dump /sdcard/ni_tap.xml > /dev/null 2>&1 || true
  adb pull /sdcard/ni_tap.xml "$OUT/ni_tap.xml" > /dev/null 2>&1 || true
  if ! tap_rid "$OUT/ni_tap.xml" "intent_input"; then
    adb shell input swipe "$CGX" "$CGY" "$CGX" "$CGY" 150 > /dev/null 2>&1 || true
  fi
  sleep 2
  adb shell input text "$1" > /dev/null 2>&1 || true
  sleep 1
  adb shell input text "%s" > /dev/null 2>&1 || true
  sleep 1
  ni_tap_submit
}

# 轮询等 dump 里出现某串（最多约 12 秒，慢腿余量）；最后一份 dump 留在 ni_poll.xml
ni_wait_text() {
  for i in $(seq 1 12); do
    sleep 1
    adb shell uiautomator dump /sdcard/ni_poll.xml > /dev/null 2>&1 || true
    adb pull /sdcard/ni_poll.xml "$OUT/ni_poll.xml" > /dev/null 2>&1 || true
    if grep -q "$1" "$OUT/ni_poll.xml" 2> /dev/null; then return 0; fi
  done
  return 1
}

# 腾讯地图桩：用 runner 的 build-tools 现场拼装（javac -> d8 -> aapt2 link ->
# zip 塞 dex -> zipalign -> apksigner 自签），成功置 STUB_APK 并返回 0
STUB_APK=""
ni_build_stub() {
  local BT=$(ls "$ANDROID_HOME/build-tools" 2> /dev/null | sort -V | tail -1)
  [ -n "$BT" ] || return 1
  local AJAR=$(ls "$ANDROID_HOME"/platforms/android-*/android.jar 2> /dev/null | sort -V | tail -1)
  [ -n "$AJAR" ] || return 1
  local BTOOLS="$ANDROID_HOME/build-tools/$BT"
  local D="$OUT/stubbuild"
  rm -rf "$D"; mkdir -p "$D/classes"
  javac -encoding UTF-8 -cp "$AJAR" -d "$D/classes" scripts/tencent-stub/MainActivity.java 2> "$D/javac.log" || return 1
  "$BTOOLS/d8" --min-api 24 --output "$D" $(find "$D/classes" -name '*.class') 2> "$D/d8.log" || return 1
  "$BTOOLS/aapt2" link -I "$AJAR" --manifest scripts/tencent-stub/AndroidManifest.xml -o "$D/stub.apk" 2> "$D/aapt2.log" || return 1
  (cd "$D" && zip -q stub.apk classes.dex) || return 1
  "$BTOOLS/zipalign" -f 4 "$D/stub.apk" "$D/stub-aligned.apk" || return 1
  keytool -genkeypair -keystore "$D/stub.keystore" -storepass android -keypass android -alias stub -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=smoke-stub" 2> /dev/null || return 1
  "$BTOOLS/apksigner" sign --ks "$D/stub.keystore" --ks-pass pass:android --key-pass pass:android --out "$D/tencent-stub.apk" "$D/stub-aligned.apk" || return 1
  STUB_APK="$D/tencent-stub.apk"
  return 0
}

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
    if ni_locate_capsule; then
      # ===== 阶段 A：没装任何地图（如实失败 + hints 不许诺）=====
      REQ_A0=$(wc -l < "$MOCK_LOG" 2> /dev/null || echo 0)
      if ni_type_submit "daohang"; then
        if ni_wait_text "去哪儿"; then
          ok "needsinput-question"
        else
          ng "needsinput-question"
        fi
        shot "intent-needsinput"
        # 诊断（mock 通路排障）：把 LlmPlan 的网络异常原文打进冒烟日志并存档
        adb logcat -d 2> /dev/null | grep "LlmPlan" | tail -4 | while IFS= read -r line; do log "llm: $line"; done
        adb logcat -d 2> /dev/null | grep "LlmPlan" > "$OUT/ni_logcat.txt" || true
        # hints 过滤取证：A 段请求含上下文包、但不得含未装应用的能力行
        A_LOG=$(tail -n +$((REQ_A0 + 1)) "$MOCK_LOG" 2> /dev/null)
        if ! echo "$A_LOG" | grep -q "wechat: " && ! echo "$A_LOG" | grep -q "doubao: " && echo "$A_LOG" | grep -q "【现在】"; then
          ok "hints-excludes-unavailable"
        else
          ng "hints-excludes-unavailable"
        fi
        # 补参 -> 执行：无提供方 + 无 geo 处理者 -> 如实报错且离开追问态
        if ni_type_submit "jichang"; then
          ni_wait_text "本机没有安装支持" || true
          cp "$OUT/ni_poll.xml" "$OUT/ni_a.xml" 2> /dev/null || true
          shot "intent-needsinput-answered"
          if grep -q "本机没有安装支持" "$OUT/ni_a.xml" 2> /dev/null && ! grep -q "去哪儿" "$OUT/ni_a.xml" 2> /dev/null; then
            ok "fill-without-provider-honest"
          else
            ng "fill-without-provider-honest"
          fi
        else
          ng "fill-without-provider-honest"
        fi
      else
        ng "needsinput-question"
        ng "hints-excludes-unavailable"
        ng "fill-without-provider-honest"
      fi
      # ===== 阶段 B：只装腾讯地图（桩）时，导航应绑腾讯 =====
      if ni_build_stub && adb install -r "$STUB_APK" > /dev/null 2>&1; then
        log "tencent stub installed"
        # 重启应用进程：清掉阶段 A 的错误态与旧缓存，从干净状态重走一遍
        adb shell am force-stop "$PKG" || true
        sleep 1
        adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
        sleep 5
        adb shell input keyevent KEYCODE_HOME || true
        sleep 2
        REQ_B0=$(wc -l < "$MOCK_LOG" 2> /dev/null || echo 0)
        if ni_locate_capsule && ni_type_submit "daohang"; then
          ni_wait_text "去哪儿" || true
          # 绑定取证：这次请求里 daohang 必须带着参数说明回到 hints
          B_LOG=$(tail -n +$((REQ_B0 + 1)) "$MOCK_LOG" 2> /dev/null)
          if echo "$B_LOG" | grep -q "必填 to=目的地" && echo "$B_LOG" | grep -q "【现在】"; then
            ok "planner-pack-injected"
          else
            ng "planner-pack-injected"
          fi
          if ni_type_submit "jichang"; then
            # 前台切换在慢腿上可能晚于单次 sleep：轮询最多约 15s（api29 首跑单探落空）
            FG_B=""
            for _try in 1 2 3 4 5; do
              sleep 3
              FG_B=$(adb shell dumpsys activity activities 2> /dev/null | grep -m1 -E "topResumedActivity|ResumedActivity" || true)
              if echo "$FG_B" | grep -q "com.tencent.map"; then break; fi
            done
            shot "intent-tencent-bound"
            log "phaseB foreground: $FG_B"
            if echo "$FG_B" | grep -q "com.tencent.map"; then
              ok "needsinput-fill-executes"
            else
              ng "needsinput-fill-executes"
            fi
          else
            ng "needsinput-fill-executes"
          fi
        else
          ng "planner-pack-injected"
          ng "needsinput-fill-executes"
        fi
        adb shell pm uninstall com.tencent.map > /dev/null 2>&1 || true
      else
        log "tencent stub build/install failed - phase B counted as fail"
        ng "planner-pack-injected"
        ng "needsinput-fill-executes"
      fi
    else
      ng "needsinput-question"
      ng "hints-excludes-unavailable"
      ng "fill-without-provider-honest"
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

# --- 18. 意图焦点点外部取消（v0.68.93 布局与交互 Loop 一批①：Bob 真机点名 bug）---
# 根因：Compose 无点外部自动清焦，releaseInputFocus 只从 IntentEntry 内部调，
# 点页面其他地方焦点不释放、键盘不收，用户被困编辑态。修复后：聚焦输字 ->
# press 点七仔（意图区外）-> dump 无 focused="true" 节点且已输文本仍在（不吞字）。
# v0.68.99（主页重构 R-1）：进输入态改走 Dock 七仔格；点外部（讲述者七仔）
# 现在连输入态一并退出（胶囊卸载、Dock 回位），文本改由宿主持有——断言
# 升级：post dump 无聚焦节点 + dock_panel 回位，再重进输入态验文本仍在。
# IME 可见性用 dumpsys input_method 的 mInputShown 做辅助日志（CI 读法不稳，
# 不作断言本体；断言 = 焦点状态，键盘收起由截图人工确认，报告如实标注）。
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
# v0.68.99：点七仔格进输入态（进态即聚焦），直接输字
enter_input_mode "$OUT/focus_dump0.xml" || true
sleep 1
adb shell input text "daohang" || true
sleep 1
# 补空格让 LatinIME composing 上屏（同第 15 段法，与真实输入流程对齐）
adb shell input text "%s" || true
sleep 1
adb shell uiautomator dump /sdcard/focus_pre.xml > /dev/null 2>&1 || true
adb pull /sdcard/focus_pre.xml "$OUT/focus_pre.xml" > /dev/null 2>&1 || true
if [ -f "$OUT/focus_pre.xml" ] && grep -q 'focused="true"' "$OUT/focus_pre.xml" && grep -q 'daohang' "$OUT/focus_pre.xml"; then
  # 前提成立（确实聚焦且文本在框内）才验取消；点七仔（pet_qizai，意图区外）
  PTX=$(SX 540); PTY=$(SY 950)
  CTR=$(center_rid "$OUT/focus_dump0.xml" "pet_qizai")
  if [ -n "$CTR" ]; then PTX=$(echo "$CTR" | cut -d' ' -f1); PTY=$(echo "$CTR" | cut -d' ' -f2); log "focus-cancel pet by tag at ($PTX,$PTY)"; fi
  adb shell input swipe "$PTX" "$PTY" "$PTX" "$PTY" 150 || true
  sleep 2
  shot "intent-focus-outside"
  IMESHOWN=$(adb shell dumpsys input_method 2>/dev/null | grep -m1 "mInputShown" | tr -d ' ' || true)
  log "focus-cancel ime after outside tap: ${IMESHOWN:-unavailable} (auxiliary only)"
  adb shell uiautomator dump /sdcard/focus_post.xml > /dev/null 2>&1 || true
  adb pull /sdcard/focus_post.xml "$OUT/focus_post.xml" > /dev/null 2>&1 || true
  # v0.68.99：退出证据 = 无聚焦节点 + Dock 面板回位（胶囊已卸载，文本不在
  # 本 dump 里——保留性由紧随的重进检查证明）
  if [ -f "$OUT/focus_post.xml" ] && ! grep -q 'focused="true"' "$OUT/focus_post.xml" && grep -q 'resource-id="dock_panel"' "$OUT/focus_post.xml"; then
    enter_input_mode "$OUT/focus_re.xml" || true
    if [ -f "$OUT/focus_re.xml" ] && grep -q 'daohang' "$OUT/focus_re.xml"; then
      ok "focus-cancel-outside"
    else
      log "focus-cancel: text NOT preserved across exit/re-entry"
      ng "focus-cancel-outside"
    fi
  else
    ng "focus-cancel-outside"
  fi
else
  log "focus-cancel precondition missing (capsule not focused or text absent) - counted as fail"
  ng "focus-cancel-outside"
fi

# --- 19. 玻璃试点批：三背景取证（照片壁纸全开强玻璃 / 蓝天远山 / 纯纸面轻霜）---
# v0.68.96 起三张壁纸（sky 排首位，离第 14 段种子事件最近）：每张全景 +
# NextUp 聚焦图（F-02 中玻璃补到紧凑条）+ 气泡图，走 glass_wallpaper_round。
# 应用在第 17 段末尾被 force-stop，这里重新拉起走正常冷启路径。
# v2 教训（实测）：本镜像 cmd wallpaper 没有 set 动词（help 只有 dim 相关，见
# glass-wp-debug.txt），命令式换壁纸无解；v3 改走 root 直换壁纸文件 + 重启
# （userdebug 镜像 adb root 可用时），失败自动回退默认壁纸取证并如实记录。
# 气泡可见约 2.9 秒且 adb input 启动有 1-2 秒延迟：按住 4 秒、提前抓拍两张。
# 第 8 段把 uimode 留在 auto、本镜像按时段判成夜间——本段先强制浅色保取证口径
# （深色腿 SMOKE_NIGHT=yes 时不干预）。TODAY 卡在 CI 首页不进首屏（与基线一致，
# 见各腿 home.png），本段以 Dock/气泡为玻璃取证主体，卡面材质由单测与真机补验。
log "glass: segment start"
if [ "$SMOKE_NIGHT" != "yes" ]; then adb shell cmd uimode night no > /dev/null 2>&1 || true; fi
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
shot "glass-default"
glass_home_alive() {
  adb shell input keyevent KEYCODE_BACK > /dev/null 2>&1 || true
  sleep 1
  adb shell input keyevent KEYCODE_HOME > /dev/null 2>&1 || true
  sleep 2
  adb shell uiautomator dump /sdcard/glass_alive.xml > /dev/null 2>&1 || true
  adb pull /sdcard/glass_alive.xml "$OUT/glass_alive.xml" > /dev/null 2>&1 || true
  [ -f "$OUT/glass_alive.xml" ] && { grep -q "intent_input" "$OUT/glass_alive.xml" || grep -q "pet_qizai" "$OUT/glass_alive.xml"; }
}
glass_bubble_shot() {
  # 长按七仔：首按指引说话（「长按我是抚摸哦」）触发气泡；调用前已删 guide_prefs 复位。
  # 按住 4 秒，约 1.2 秒与 2.4 秒各抓一张（气泡全显窗口约说后 0.3-2.9 秒）。
  adb shell uiautomator dump /sdcard/glass_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/glass_dump.xml "$OUT/glass_dump.xml" > /dev/null 2>&1 || true
  local ctr gx gy
  ctr=$(center_rid "$OUT/glass_dump.xml" "pet_qizai")
  if [ -n "$ctr" ]; then
    gx=$(echo "$ctr" | cut -d' ' -f1); gy=$(echo "$ctr" | cut -d' ' -f2)
  else
    gx=$(SX 880); gy=$(SY 683)
  fi
  log "glass: bubble long-press at ($gx,$gy)"
  adb shell input swipe "$gx" "$gy" "$gx" "$gy" 5000 > /dev/null 2>&1 &
  local SWPID=$!
  sleep 1.6
  shot "$1"
  adb shell uiautomator dump /sdcard/glass_bubble_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/glass_bubble_dump.xml "$OUT/${1}-dump.xml" > /dev/null 2>&1 || true
  if [ -f "$OUT/${1}-dump.xml" ]; then
    grep -o 'text="[^"]*"' "$OUT/${1}-dump.xml" | grep -E "抚摸|°|好啦" | head -3 | while IFS= read -r line; do log "glass: bubble semantics during hold: $line"; done
    grep -q "抚摸" "$OUT/${1}-dump.xml" || log "glass: bubble semantics during hold: NO speech text node"
  fi
  shot "$1-b"
  wait $SWPID || true
  sleep 1
}
glass_relaunch_home() {
  adb shell am force-stop "$PKG" || true
  adb shell "run-as $PKG rm -f shared_prefs/guide_prefs.xml" > /dev/null 2>&1 || true
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
  sleep 4
  adb shell input keyevent KEYCODE_HOME || true
  sleep 2
}
glass_root_swap_wallpaper() {
  # $1=宿主本地图片。root 直换系统壁纸文件并重启让 WallpaperManagerService 重读；
  # 任一步失败返回 1（调用方回退默认壁纸取证）。诊断追加进 glass-wp-debug.txt。
  local img="$1"
  adb root >> "$OUT/glass-wp-debug.txt" 2>&1 || return 1
  adb wait-for-device || return 1
  adb shell id 2>/dev/null | grep -q "uid=0" || { log "glass: adb root unavailable"; return 1; }
  adb push "$img" /data/local/tmp/wp.png >> "$OUT/glass-wp-debug.txt" 2>&1 || return 1
  adb shell "cp /data/local/tmp/wp.png /data/system/users/0/wallpaper && chown system:system /data/system/users/0/wallpaper && chmod 600 /data/system/users/0/wallpaper" >> "$OUT/glass-wp-debug.txt" 2>&1 || return 1
  adb shell "chcon u:object_r:system_data_file:s0 /data/system/users/0/wallpaper" >> "$OUT/glass-wp-debug.txt" 2>&1 || true
  log "glass: wallpaper file swapped ($img), rebooting"
  adb reboot > /dev/null 2>&1 || return 1
  adb wait-for-device || return 1
  local i booted=""
  for i in $(seq 1 30); do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then booted=1; break; fi
    sleep 5
  done
  [ -n "$booted" ] || { log "glass: reboot timeout after wallpaper swap"; return 1; }
  sleep 3
  adb shell dumpsys wallpaper 2>/dev/null | head -12 >> "$OUT/glass-wp-debug.txt" 2>&1 || true
  return 0
}
{
  echo "== cmd wallpaper help (note: no set verb on this image) =="
  adb shell cmd wallpaper help 2>&1 | head -20
  echo "== dumpsys wallpaper (initial) =="
  adb shell dumpsys wallpaper 2>/dev/null | head -25
} > "$OUT/glass-wp-debug.txt" 2>&1 || true
glass_nextup_shot() {
  # $1=截图名。简报会行取证（v0.68.99 起 NextUp 紧凑条退役、会行长进简报，
  # testTag today_nextup 迁移至简报会行；其玻璃语境即简报本体、无独立玻璃面）：
  # 第 14 段种子事件窗口（+20~+80 分钟）仍覆盖本段时，点七仔格进输入态→
  # BACK 收键盘，建议面与简报同屏入图（同 §14 dedupe 流）；事件不在场只记
  # 日志不判负（玻璃证据以全景图为准，无事件时简报会行不渲染）。
  local name="$1"
  enter_input_mode "$OUT/glass_nu_dump.xml" || true
  if [ -f "$OUT/glass_nu_dump.xml" ] && grep -q 'resource-id="today_nextup"' "$OUT/glass_nu_dump.xml"; then
    adb shell input keyevent KEYCODE_BACK > /dev/null 2>&1 || true
    sleep 1
    shot "$name"
    log "glass: brief-meeting shot taken ($name)"
    # 恢复静态基线（下一拍长按七仔截气泡在静态下进行）：再 BACK 退出输入态
    adb shell input keyevent KEYCODE_BACK > /dev/null 2>&1 || true
    sleep 1
  else
    log "glass: brief meeting row absent for $name (seeded event window passed or not seeded)"
    shot "$name"
  fi
}
glass_wallpaper_round() {
  # $1=模式名（sky/photo/paper） $2=本地图路径。换壁纸→回桌面→全景/NextUp/气泡三拍。
  local mode="$1" img="$2"
  if ! glass_root_swap_wallpaper "$img"; then
    log "glass: root swap failed ($mode) - shots fall back to current wallpaper"
  fi
  glass_relaunch_home
  shot "glass-$mode"
  glass_nextup_shot "glass-$mode-nextup"
  glass_bubble_shot "glass-bubble-$mode"
}
if python3 scripts/gen_wallpaper.py sky /tmp/glass-sky.png > /dev/null 2>&1 && \
   python3 scripts/gen_wallpaper.py photo /tmp/glass-photo.png > /dev/null 2>&1 && \
   python3 scripts/gen_wallpaper.py paper /tmp/glass-paper.png > /dev/null 2>&1; then
  # sky 排第一：离第 14 段种子事件窗口最近，NextUp 取证成功率最高
  glass_wallpaper_round sky /tmp/glass-sky.png
  glass_wallpaper_round photo /tmp/glass-photo.png
  glass_wallpaper_round paper /tmp/glass-paper.png
else
  log "glass: wallpaper generation failed - set-based shots skipped"
fi
if glass_home_alive && adb shell pidof "$PKG" > /dev/null 2>&1; then
  ok "glass-alive-after-wallpaper-switches"
else
  ng "glass-alive-after-wallpaper-switches"
fi

# --- 20. 数据新鲜度：装/卸应用实时刷新（v0.68.95 布局与交互 Loop 二批-A：F-03/F-04）---
# 此前 Dock 清单是无 key remember{}（进程内只算一次），应用中心靠自带广播在
# 组合期主线程整表重建。本批改 AppCatalog 仓库：InstalledApps.epoch 失效 ->
# IO 构建 -> StateFlow 快照递交。本段在应用中心（Dock D3）开着、不重启进程、
# 不离开表面的前提下装上腾讯地图桩：A-Z 列表应自己长出「腾讯地图桩」；再卸
# 掉，列表应自己消掉。与第 17 段阶段 B（装桩后重启进程验能力绑定）互补：
# 那里验「重启后对」，这里验「活着就刷新」。桩的构建复用 ni_build_stub。
log "freshness: segment start"
adb shell input keyevent KEYCODE_HOME || true
sleep 2
adb shell input swipe "$(SX 540)" "$(SY 2000)" "$(SX 540)" "$(SY 800)" 300 || true
sleep 3
fresh_dump() {
  adb shell uiautomator dump /sdcard/fresh_dump.xml > /dev/null 2>&1 || true
  adb pull /sdcard/fresh_dump.xml "$OUT/fresh_dump.xml" > /dev/null 2>&1 || true
}
# 当前屏 dump 命中标签返回 0；未命中滑一屏再找。滑动轴按方向选：竖屏列表
# 纵向滚；横屏应用中心是竖排流多列、整表横向滚（AppCenterLandscape），
# 必须横滑——首轮矩阵 tablet 腿实证：竖滑 12 次翻不到 T 列（断言手法问题，
# 非 App 刷新问题，横屏数据同样来自 AppCenterContent 的 azGroups）。
fresh_swipe_fwd() {
  if [ "$LANDSCAPE" = "1" ]; then
    adb shell input swipe "$(SX 800)" "$(SY 1200)" "$(SX 280)" "$(SY 1200)" 300 || true
  else
    adb shell input swipe "$(SX 540)" "$(SY 1600)" "$(SX 540)" "$(SY 650)" 300 || true
  fi
}
fresh_swipe_back() {
  if [ "$LANDSCAPE" = "1" ]; then
    adb shell input swipe "$(SX 280)" "$(SY 1200)" "$(SX 800)" "$(SY 1200)" 300 || true
  else
    adb shell input swipe "$(SX 540)" "$(SY 650)" "$(SX 540)" "$(SY 1600)" 300 || true
  fi
}
fresh_find_label() {
  local label="$1" tries="$2" i
  for i in $(seq 1 "$tries"); do
    fresh_dump
    if [ -f "$OUT/fresh_dump.xml" ] && grep -q "$label" "$OUT/fresh_dump.xml"; then
      return 0
    fi
    fresh_swipe_fwd
    sleep 1
  done
  fresh_dump
  [ -f "$OUT/fresh_dump.xml" ] && grep -q "$label" "$OUT/fresh_dump.xml"
}
fresh_scroll_top() {
  local i
  for i in $(seq 1 10); do
    fresh_swipe_back
    sleep 0.5
  done
}
STUB_LABEL="腾讯地图桩"
if ! fresh_find_label "$STUB_LABEL" 1; then
  ok "catalog-preinstall-absent"
else
  log "freshness: stub label unexpectedly present before install"
  ng "catalog-preinstall-absent"
fi
if ni_build_stub && adb install -r "$STUB_APK" > /dev/null 2>&1; then
  log "freshness: stub installed while AppCenter open"
  # 广播 -> epoch -> IO 重建 -> 快照换表，给慢腿留足轮询窗口
  sleep 3
  if fresh_find_label "$STUB_LABEL" 12; then
    ok "catalog-install-refresh"
  else
    ng "catalog-install-refresh"
  fi
  shot "appcenter-fresh-installed"
  adb shell pm uninstall com.tencent.map > /dev/null 2>&1 || true
  log "freshness: stub uninstalled while AppCenter open"
  sleep 3
  fresh_scroll_top
  if ! fresh_find_label "$STUB_LABEL" 12; then
    ok "catalog-uninstall-refresh"
  else
    ng "catalog-uninstall-refresh"
  fi
  shot "appcenter-fresh-uninstalled"
else
  log "freshness: stub build/install failed - segment counted as fail (same as phase B)"
  ng "catalog-install-refresh"
  ng "catalog-uninstall-refresh"
fi
adb shell input keyevent KEYCODE_HOME || true
sleep 1

# --- 21. 布局几何断言（v0.68.97 布局普查批）：关键元素两两不相交 + 完整在屏内 ---
# 量各段已产出的 tpl_*.xml / fan_dump.xml 与本段新拉的 layout_home / layout_ac /
# layout_settings（scripts/layout_check.py，testTag 定位：cover_hero /
# intent_input / intent_submit / today_nextup / pet_qizai / dock_panel，
# dock_panel 缺失退化为 dock_item 并集）。聚焦态不查 Dock（IME 升起时
# dump 中的 Dock bounds 是被键盘覆盖的不可见几何）。
log "layout: segment start"
# NextUp 几何要有「即将开始」的事件在场才查得到：第 14 段种的事件到本段
# （约 25 分钟后）多已开始/被删，home-current 首轮实测 nextup 恒缺席、这条
# 核心对（NextUp x Dock/胶囊）永远查不到。重启前复用第 14 段的 seed 函数
# 现种一条 +10 分钟开始的探针事件；函数不在场/种失败只记日志跳过。
if type nextup_seed_event > /dev/null 2>&1; then
  LAYOUT_NOW_S=$(adb shell date +%s 2>/dev/null | tr -d '\r')
  if [ -n "$LAYOUT_NOW_S" ]; then
    EV_BEGIN=$(( (LAYOUT_NOW_S + 600) * 1000 ))
    EV_END=$(( (LAYOUT_NOW_S + 4200) * 1000 ))
    if nextup_seed_event "LayoutProbe" ""; then
      log "layout: probe event seeded (starts in 10min)"
    else
      log "layout: probe event seed failed, nextup pair checks may skip"
    fi
  fi
else
  log "layout: nextup_seed_event unavailable on this leg, nextup pair checks may skip"
fi
# 上一段结束时停在 D3 应用中心（Dock 状态在 MainActivity 内，am start/HOME 回不到
# 首页视图，同第 9 段实测结论）→ force-stop 重启回首页基线再取 dump；
# 日历事件在 provider 侧，重启不丢，NextUp 检查不受影响。
adb shell am force-stop "$PKG" || true
sleep 1
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
sleep 4
adb shell input keyevent KEYCODE_HOME || true
sleep 2
# 到家自验（首轮实测：个别腿重启后仍停在应用中心/权限引导层，layout_home
# 误拍成应用中心、断言对象张冠李戴）：探针 dump 循环——见「暂不」点掉权限
# 引导层；仍在应用中心（齿轮在场）按返回键走 BackHandler（D3->记住的行数）
# 退出；cover_hero 在场且齿轮缺席才算到家。验不过也继续（checker 对缺席
# 元素 INFO 跳过、不假 FAIL），但日志留痕供定性。
HOME_OK=0
for TRY in 1 2 3 4; do
  adb shell uiautomator dump /sdcard/layout_probe.xml > /dev/null 2>&1 || true
  adb pull /sdcard/layout_probe.xml "$OUT/layout_probe.xml" > /dev/null 2>&1 || true
  [ -f "$OUT/layout_probe.xml" ] || break
  if grep -q 'text="暂不"' "$OUT/layout_probe.xml"; then
    PNODE=$(grep -o '<node[^>]*text="暂不"[^>]*>' "$OUT/layout_probe.xml" | head -1)
    PNUMS=$(echo "$PNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
    PX1=$(echo "$PNUMS" | sed -n 1p); PY1=$(echo "$PNUMS" | sed -n 2p)
    PX2=$(echo "$PNUMS" | sed -n 3p); PY2=$(echo "$PNUMS" | sed -n 4p)
    if [ -n "$PX1" ] && [ -n "$PY2" ]; then
      adb shell input tap $(( (PX1 + PX2) / 2 )) $(( (PY1 + PY2) / 2 )) || true
      log "layout: permission sheet dismissed via 暂不 (try $TRY)"
    fi
    sleep 1
    continue
  fi
  if grep -q 'resource-id="cover_hero"' "$OUT/layout_probe.xml" && ! grep -q 'resource-id="appcenter_settings"' "$OUT/layout_probe.xml"; then
    HOME_OK=1
    break
  fi
  if grep -q 'resource-id="appcenter_settings"' "$OUT/layout_probe.xml"; then
    adb shell input keyevent KEYCODE_BACK || true
    log "layout: still in app center, BACK to exit D3 (try $TRY)"
    sleep 1
    continue
  fi
  adb shell input keyevent KEYCODE_HOME || true
  sleep 1
done
log "layout: home arrival verified=$HOME_OK"
shot "layout-home"
adb shell uiautomator dump /sdcard/layout_home.xml > /dev/null 2>&1 || true
adb pull /sdcard/layout_home.xml "$OUT/layout_home.xml" > /dev/null 2>&1 || true
# 进应用中心：多泳道上滑 + 每轮 dump 自验（横屏短高时中泳道起点落在
# NextUp 条上会被卡片吞掉手势——run2 landphone 实测；左泳道起点在条下方
# 空区可用）。命中齿轮即停，最多三轮。
AC_OK=0
for LANE in "540 2000 540 800" "200 2250 200 1000" "200 1600 200 600"; do
  set -- $LANE
  adb shell input swipe "$(SX $1)" "$(SY $2)" "$(SX $3)" "$(SY $4)" 300 || true
  sleep 3
  adb shell uiautomator dump /sdcard/layout_ac.xml > /dev/null 2>&1 || true
  adb pull /sdcard/layout_ac.xml "$OUT/layout_ac.xml" > /dev/null 2>&1 || true
  if [ -f "$OUT/layout_ac.xml" ] && grep -q 'resource-id="appcenter_settings"' "$OUT/layout_ac.xml"; then
    AC_OK=1
    break
  fi
  log "layout: app center not reached via lane ($LANE), trying next"
done
log "layout: app center reached=$AC_OK"
shot "layout-appcenter"
if tap_rid "$OUT/layout_ac.xml" "appcenter_settings"; then
  sleep 3
  shot "layout-settings"
  adb shell uiautomator dump /sdcard/layout_set.xml > /dev/null 2>&1 || true
  adb pull /sdcard/layout_set.xml "$OUT/layout_settings.xml" > /dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_BACK || true
  sleep 1
else
  log "layout: settings gear not tappable on this leg, settings state skipped"
fi
adb shell input keyevent KEYCODE_HOME || true
sleep 1
LAYOUT_OUT=$(python3 scripts/layout_check.py "$OUT" "$SCR_W" "$SCR_H" 2>&1)
echo "$LAYOUT_OUT" | while IFS= read -r line; do log "$line"; done
LAYOUT_FAILS=$(echo "$LAYOUT_OUT" | grep -c "LAYOUT FAIL" || true)
if [ "$LAYOUT_FAILS" -gt 0 ]; then ng "layout-geometry($LAYOUT_FAILS)"; else ok "layout-geometry"; fi

# --- 22. Planning 取消（v0.68.98 布局与交互 Loop 二批-C：F-06 状态出口）---
# 手段同第 17 段（mock LLM + 预写 llm prefs），差别在 mock 对含标记词
# manmanxiang 的请求延迟 10 秒回包——把协调器稳定停在 Planning 态：
# ①思考条「七仔想想中…」+「取消」钮在场（planning-strip）；
# ②点「取消」后思考条消失、回 Idle（planning-cancel-dismisses）；
# ③等过迟到回包的落地窗口，dump 里不得出现追问句「去哪儿」——mock 恒回
#   daohang 空参，没被真取消的话迟到结果必进 NeedsInput 追问态，出现即
#   取消没作废（planning-cancel-drops-late）。
# 宿主 Job 被 cancel 后 HTTP 可能先断、迟到结果到不了客户端——本段验端到端
# UI 行为；「结果已算出、正落状态」那一瞬的竞态由协调器代际守卫 + 单测覆盖
# （IntentCoordinatorTest 15/16/17）。mock 起不来或 prefs 预写失败时整段跳过
# 不计 FAIL（同第 17 段口径）；段末清 llm prefs + force-stop，不污染后续段落。
PC_PORT=18742
PC_LOG="$OUT/mock_slow_requests.log"
PC_PID=""
PC_UP=0
if command -v python3 > /dev/null 2>&1; then
  rm -f "$PC_LOG"
  python3 scripts/mock_llm.py "$PC_PORT" "$PC_LOG" &
  PC_PID=$!
  for i in $(seq 1 20); do
    if curl -s "http://127.0.0.1:$PC_PORT/health" 2> /dev/null | grep -q ok; then
      PC_UP=1; break
    fi
    sleep 0.5
  done
fi
if [ "$PC_UP" = "1" ]; then
  adb reverse "tcp:$PC_PORT" "tcp:$PC_PORT" > /dev/null 2>&1 || true
  adb shell am force-stop "$PKG" || true
  printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" "<map>" \
    '    <string name="api_key">sk-mock</string>' \
    "    <string name=\"base_url\">http://127.0.0.1:$PC_PORT</string>" \
    '    <string name="model">mock-model</string>' "</map>" > "$OUT/llm_slow.xml"
  adb push "$OUT/llm_slow.xml" /data/local/tmp/llm_slow.xml > /dev/null 2>&1 || true
  adb shell "run-as $PKG cp /data/local/tmp/llm_slow.xml shared_prefs/llm.xml" || true
  adb shell "run-as $PKG chmod 660 shared_prefs/llm.xml" || true
  if adb shell "run-as $PKG cat shared_prefs/llm.xml" 2> /dev/null | grep -q '127.0.0.1'; then
    adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1 || true
    sleep 5
    adb shell input keyevent KEYCODE_HOME || true
    sleep 2
    if ni_locate_capsule; then
      # 聚焦胶囊 -> 输标记词（补空格让 LatinIME 组合上屏，同第 15/17 段法）
      adb shell input swipe "$CGX" "$CGY" "$CGX" "$CGY" 150 > /dev/null 2>&1 || true
      sleep 2
      adb shell input text "manmanxiang" > /dev/null 2>&1 || true
      sleep 1
      adb shell input text "%s" > /dev/null 2>&1 || true
      sleep 1
      # 提交 + 等思考条合并成一轮轮询：每轮 dump 先找思考条；没找到且提交钮
      # 在场就点一次（只点一次）。不用 ni_type_submit 的单次 dump 定音——
      # dark 腿首跑实测：单次 dump 落空导致 ni_tap_submit 找不到提交钮、三条
      # 断言连坐；轮询重试可自愈这类偶发空/旧帧。思考条在提交后立即出现、
      # 停留到慢响应回包（10 秒）为止，8 轮足够覆盖慢腿。
      PC_STRIP=0
      PC_TAPPED=0
      for i in $(seq 1 8); do
        adb shell uiautomator dump /sdcard/pc_poll.xml > /dev/null 2>&1 || true
        adb pull /sdcard/pc_poll.xml "$OUT/pc_poll.xml" > /dev/null 2>&1 || true
        if grep -q "七仔想想中" "$OUT/pc_poll.xml" 2> /dev/null && grep -q 'text="取消"' "$OUT/pc_poll.xml" 2> /dev/null; then PC_STRIP=1; break; fi
        if [ "$PC_TAPPED" = "0" ] && tap_rid "$OUT/pc_poll.xml" "intent_submit"; then
          PC_TAPPED=1
        fi
        sleep 1
      done
      if [ "$PC_STRIP" = "1" ]; then
          ok "planning-strip"
          shot "intent-planning"
          # 点「取消」：优先 testTag planning_cancel，未命中退回 text=取消 的节点中心
          if ! tap_rid "$OUT/pc_poll.xml" "planning_cancel"; then
            CNODE=$(grep -o '<node[^>]*text="取消"[^>]*>' "$OUT/pc_poll.xml" | head -1)
            CNUMS=$(echo "$CNODE" | grep -o 'bounds="\[[0-9,]*\]\[[0-9,]*\]"' | grep -o '[0-9]\+')
            CX1=$(echo "$CNUMS" | sed -n 1p); CY1=$(echo "$CNUMS" | sed -n 2p)
            CX2=$(echo "$CNUMS" | sed -n 3p); CY2=$(echo "$CNUMS" | sed -n 4p)
            if [ -n "$CX1" ] && [ -n "$CY2" ]; then
              adb shell input swipe $(( (CX1 + CX2) / 2 )) $(( (CY1 + CY2) / 2 )) $(( (CX1 + CX2) / 2 )) $(( (CY1 + CY2) / 2 )) 150 > /dev/null 2>&1 || true
            fi
          fi
          sleep 2
          adb shell uiautomator dump /sdcard/pc_after.xml > /dev/null 2>&1 || true
          adb pull /sdcard/pc_after.xml "$OUT/pc_after.xml" > /dev/null 2>&1 || true
          if [ -f "$OUT/pc_after.xml" ] && ! grep -q "七仔想想中" "$OUT/pc_after.xml" 2> /dev/null; then
            ok "planning-cancel-dismisses"
          else
            ng "planning-cancel-dismisses"
          fi
          # 等迟到回包的落地窗口整个过去（累计已过约 8 秒，再等 10 秒 > 10 秒延迟）
          sleep 10
          adb shell uiautomator dump /sdcard/pc_late.xml > /dev/null 2>&1 || true
          adb pull /sdcard/pc_late.xml "$OUT/pc_late.xml" > /dev/null 2>&1 || true
          shot "intent-planning-cancelled"
          if [ -f "$OUT/pc_late.xml" ] && ! grep -q "去哪儿" "$OUT/pc_late.xml" 2> /dev/null && ! grep -q "七仔想想中" "$OUT/pc_late.xml" 2> /dev/null; then
            ok "planning-cancel-drops-late"
          else
            ng "planning-cancel-drops-late"
          fi
      else
        log "planning-cancel: planning strip never appeared after submit retries - segment counted as fail"
        ng "planning-strip"
        ng "planning-cancel-dismisses"
        ng "planning-cancel-drops-late"
      fi
    else
      log "planning-cancel: capsule not located - segment skipped (not counted)"
    fi
    # 清理：删 llm prefs + force-stop 掉 SP 缓存，恢复无 Key 基线（同第 17 段）
    adb shell "run-as $PKG rm -f shared_prefs/llm.xml" || true
    adb reverse --remove "tcp:$PC_PORT" > /dev/null 2>&1 || true
    adb shell am force-stop "$PKG" || true
  else
    log "planning-cancel: llm prefs seed failed - segment skipped (not counted)"
  fi
else
  log "mock llm unavailable - planning-cancel segment skipped (not counted)"
fi
if [ -n "$PC_PID" ]; then kill "$PC_PID" 2> /dev/null || true; fi

# --- 23. crash 检查（只看本包 FATAL，避免系统噪音）---
if adb logcat -d 2>/dev/null | grep -i "FATAL EXCEPTION" | grep -q "$PKG"; then
  ng "no-fatal-crash"
  adb logcat -d 2>/dev/null | grep -i -A15 "FATAL EXCEPTION" | head -30 || true
else
  ok "no-fatal-crash"
fi

log "RESULT pass=$PASS fail=$FAIL"
exit "$FAIL"
