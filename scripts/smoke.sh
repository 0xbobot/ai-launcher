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
# 等迟到结果落地窗口过后断言追问不上屏），crash 检查再顺延为第 23 段。
# v0.68.100（卫生批）追加：第 24 段为崩溃本地日志（crash-log-written /
# v0.68.102（310 合并批）段号统一：R-2 槽位甲板 = 第 25 段；热修 D-2 输入态
# 不误退 = 第 26 段；热修 D-5 点加入只发一次 = 第 27 段（三段同插在第 22 段
# 之后、第 23/24 段之前——第 24 段故意崩溃必须保持最后两段的既有约束）。
# crash-log-content / crash-relaunch-alive：debug 专用 intent extra 触发一次
# 真崩溃 -> files/crashes 落盘且含异常首行 -> 正常重启存活不进崩溃循环）。
# 本段故意产生 FATAL，必须排在第 23 段 no-fatal-crash 之后。
# 并行线 B（密钥存储迁移）追加：第 28 段为真 fixture 迁移验证（310 合入时统一编号：
# 原线 B 第 25 段与 310 的第 25 段槽位甲板撞号，本段执行序仍在第 24 段之后）
# （keystore-fixture-old-encrypted / keystore-fixture-upgrade-install /
# keystore-migration-value-match / keystore-migration-new-format /
# keystore-migration-legacy-removed）：下载已发布 v0.68.100 包（同固定签名），
# 在旧版里走真实设置 UI 把假 Key 存进 alpha06 加密层，再覆盖装新版，
# 断言发往 mock 的 Authorization 与旧版写入逐字一致、新格式密文落盘、
# 删旧格式文件后仍可读。旧包下载不到 = 环境问题整段跳过不计 FAIL；
# 形态覆写腿（横屏/平板/大字体）设置 UI 布局不同，整段跳过。形态 env 追加 SMOKE_WM_SIZE /
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
# §9 改造（2026-10-24，310-freeze3 回执专节方案 A+B）：整机死亡三态记账
ENV_DEATHS=0
ENV_INVALID=0

log()  { echo "[smoke] $*"; }
ok()   { PASS=$((PASS+1)); log "PASS: $1"; }
ok_rec() { PASS=$((PASS+1)); log "PASS(recovered): $1"; }
ng()   { FAIL=$((FAIL+1)); log "FAIL: $1"; }
# v0.68.99（R-1 多轮实测）：uiautomator 偶发 0 字节空 dump（ni_btn/ac_dump3/
# suggest_after/focus_pre 先后中招）——关键单发 dump 拉回后为空时重拉一次。
redump_if_empty() {
  # $1 = 文件名（不带 .xml，远端 /sdcard/<name>.xml 与本地 $OUT/<name>.xml 同名）
  if [ ! -s "$OUT/$1.xml" ]; then
    log "redump_if_empty: $1"
    sleep 1
    adb shell uiautomator dump "/sdcard/$1.xml" > /dev/null 2>&1 || true
    adb pull "/sdcard/$1.xml" "$OUT/$1.xml" > /dev/null 2>&1 || true
  fi
}
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
  # 退化 bounds（[0,0][0,0]：残缺 dump 里节点在场但无几何）不算定位成功——
  # 否则点位落到屏幕角上误触他处（v0.68.101 矩阵 landphone 腿 focus-cancel 实测）
  [ "$X2" -gt "$X1" ] && [ "$Y2" -gt "$Y1" ] || return 1
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
  # eim_dump：dump+pull 一次；拉回为空（uiautomator 0 字节偶发）时重拉一次。
  # 空文件是「未知」不是「不在场」——dark 腿两轮实测：校验 dump 为空被误判
  # 未进入，调用段随后用兜底坐标误点建议面应用（Contacts 被打开）级联失败。
  eim_dump() {
    adb shell uiautomator dump /sdcard/eim_dump.xml > /dev/null 2>&1 || true
    adb pull /sdcard/eim_dump.xml "$F" > /dev/null 2>&1 || true
    if [ ! -s "$F" ]; then
      sleep 1
      adb shell uiautomator dump /sdcard/eim_dump.xml > /dev/null 2>&1 || true
      adb pull /sdcard/eim_dump.xml "$F" > /dev/null 2>&1 || true
    fi
  }
  eim_dump
  if [ -f "$F" ] && grep -q 'resource-id="intent_input"' "$F"; then
    log "enter_input_mode: capsule already present"
    return 0
  fi
  if ! tap_rid "$F" "dock_pet_cell"; then
    tap_rid "$F" "pet_qizai" || true
  fi
  sleep 2
  eim_dump
  if [ -f "$F" ] && grep -q 'resource-id="intent_input"' "$F"; then
    log "enter_input_mode: capsule present after pet-cell tap"
    return 0
  fi
  # uiautomator 偶发残缺 dump 会让第一份 dump 缺七仔格、点按落空（R-1 二轮
  # 实测 giant 模板中招，同第 10 段登记过的残缺层级现象）：第二份 dump 里
  # 七仔格在场则再点一次重试，不把偶发当产品失败。校验 dump 既无胶囊又无
  # 七仔格（层级残缺/未知）时同样重试一次。
  if [ -f "$F" ] && { grep -q 'resource-id="dock_pet_cell"' "$F" || \
      ! grep -q 'resource-id="pet_qizai"' "$F"; }; then
    log "enter_input_mode: retrying pet-cell tap (first dump may have been partial)"
    if ! tap_rid "$F" "dock_pet_cell"; then
      tap_rid "$F" "pet_qizai" || true
    fi
    sleep 2
    eim_dump
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

# --- 6. 点七仔进输入态（v0.68.101 单宠物：静态唯一七仔 = Dock 上沿坐姿，
# testTag 沿用 dock_pet_cell；旧讲述者 pet_qizai 静态已撤）---
# press 150ms 模拟真手指（瞬时 tap 会被首页手势层取消，见第 9 段注）
adb shell uiautomator dump /sdcard/pet_dump.xml > /dev/null 2>&1 || true
adb pull /sdcard/pet_dump.xml "$OUT/pet_dump.xml" > /dev/null 2>&1 || true
if ! tap_rid "$OUT/pet_dump.xml" "dock_pet_cell"; then
  log "seg6: dock_pet_cell not found in dump - seat tap skipped (shot still taken)"
fi
sleep 0.7
shot "pet"
# 点坐姿七仔 = 进入输入态（胶囊浮现 + 键盘弹起 + 伴随七仔立在胶囊旁），
# pet.png 即输入态开启瞬间。随后退回静态基线：第 1 次 BACK 收键盘
# （焦点保留）、第 2 次 BACK 走输入态 BackHandler 收起胶囊、Dock 回位
# ——否则下一段上滑会被输入态/键盘干扰。
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
log "RESULT-PARTIAL pass=$PASS fail=$FAIL"
# death_watch（§9 改造方案 A）：每秒探活 device+pid，最长 ~20s。
# 设备无响应（整机死亡签名）或始终等不到应用进程 → 返回 1（环境死亡），
# 由 §9 驱动记 ENV-DEATH 并重启重试，不在此判产品 FAIL。
death_watch() {
  local i
  for i in $(seq 1 20); do
    adb get-state 2> /dev/null | grep -qx device || return 1
    adb shell pidof "$PKG" 2> /dev/null | grep -q . && return 0
    sleep 1
  done
  adb get-state 2> /dev/null | grep -qx device || return 1
  return 1
}
s9_body() {
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
  # 进态后先 death_watch 探活（方案 A）：死亡签名提前返回 9，不带死机跑断言
  death_watch || return 9
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
  # 结尾探活（方案 A）：设备无响应或进程不在 → 死亡签名，返回 9 交驱动
  adb get-state 2> /dev/null | grep -qx device || return 9
  adb shell pidof "$PKG" 2> /dev/null | grep -q . || return 9
  return 0
}

# §9 驱动（方案 A）：首跑死亡 → adb reboot 后重试一次；恢复则
# PASS(recovered)，再死记 FAIL(env-double-death) 且整轮 ENV-INVALID。
s9_body
S9_RC=$?
if [ "$S9_RC" = "9" ]; then
  ENV_DEATHS=$((ENV_DEATHS+1))
  log "ENV-DEATH: section 9 attempt 1 (device/pid lost); adb reboot + retry once"
  adb reboot > /dev/null 2>&1 || true
  for i in $(seq 1 40); do
    if adb get-state 2> /dev/null | grep -qx device && [ "$(adb shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" = "1" ]; then break; fi
    sleep 3
  done
  sleep 5
  s9_body
  S9_RC=$?
  if [ "$S9_RC" = "9" ]; then
    ENV_DEATHS=$((ENV_DEATHS+1))
    ENV_INVALID=1
    ng "env-double-death"
    log "ENV-INVALID: section 9 died twice in one run; gate readers must void this run (not a product failure)"
  else
    ok_rec "intent-alive"
  fi
else
  ok "intent-alive"
fi


# --- 探针（§9 改造检查点）：头部段 + 第 9 段 + RESULT ---

if [ "$ENV_INVALID" = "1" ]; then
  log "RESULT pass=$PASS fail=$FAIL env_deaths=$ENV_DEATHS ENV-INVALID"
else
  log "RESULT pass=$PASS fail=$FAIL env_deaths=$ENV_DEATHS"
fi
