#!/usr/bin/env bash
# 设计 token 门禁：只检查本次 diff 的「新增行」，存量不动。
# 用法：scripts/lint-design-tokens.sh [base-ref]   默认 origin/main
# 逃生口：行内带 `lint-ok` 注释即放行（须说明原因）。
#  - 裸 fontSize（13/14/15/17/20/32 等有等值档者）→ 失败（docs/TYPOGRAPHY_TOKENS.md 第 5 条）
#  - 新增 Color(0x…)（theme/glass/场景文件除外）→ 仅警告，提示评估是否进 Palette
set -euo pipefail
BASE="${1:-origin/main}"
FONT_EQ='(12|13|14|15|16|17|20|32)'
SCENE_RE='ui/(theme|glass)/|ui/home/(WeatherEnv|BlindCapture)\.kt|ui/battery/'

fail=0
added=$(git diff -U0 "$BASE"...HEAD -- 'app/src/main/java/**/ui/**/*.kt' | awk '
  /^\+\+\+ b\// { file=substr($0,7); next }
  /^\+\+\+ /    { file=""; next }
  /^\+[^+]/ && file!="" { print file "\t" substr($0,2) }
')
[ -z "$added" ] && { echo "design-lint: 无新增 UI 代码，跳过"; exit 0; }

while IFS=$'\t' read -r file line; do
  [[ "$line" == *lint-ok* ]] && continue
  if [[ "$line" =~ fontSize\ =\ ${FONT_EQ}(\.[0-9]+)?\.sp ]]; then
    echo "::error file=${file}::裸 fontSize（${BASH_REMATCH[1]}sp 有等值档）→ 请用 AILauncherType.*.fontSize：${line}"
    fail=1
  fi
  if [[ "$line" =~ Color\(0x ]] && ! [[ "$file" =~ $SCENE_RE ]]; then
    echo "::warning file=${file}::新增硬编码 Color(0x…)，请评估是否应进 AILauncherColors.Palette：${line}"
  fi
done <<< "$added"

# 结构门禁（2026-11-15 未读长按弹窗头重影）：ClearableUnreadRow 是 Box，
# 它的 content lambda 里只能放 AppRow 本身；AnimatedVisibility（长按弹窗）
# 一旦放进去就会叠在行头上——弹窗首行（飞书的「扫一扫」）盖住图标+标题，
# 看着像重影。弹窗必须挂在外层卡片 Column 下（与字母/搜索/Dock/折叠/
# 隐藏段同款）。全文件检查（不只新增行——466 的注释写着「只包 AppRow
# 本身」，代码却把弹窗留在了 lambda 里，注释拦不住，检查才拦得住）。
python3 - <<'PYEOF' || fail=1
import re, sys
files = ["app/src/main/java/com/bobot/ailauncher/ui/apps/AppCenterSections.kt",
         "app/src/main/java/com/bobot/ailauncher/ui/apps/AppCenterLandscape.kt"]
bad = 0
for path in files:
    try:
        src = open(path, encoding="utf-8").read()
    except FileNotFoundError:
        continue
    # 去注释与字符串/字符字面量（只留结构符号；块注释按行数补回换行，
    # 报错行号才准；本文件无 """ 原始串）
    src = re.sub(r'//[^\n]*', '', src)
    src = re.sub(r'/\*.*?\*/', lambda m: '\n' * m.group(0).count('\n'), src, flags=re.S)
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    src = re.sub(r"'(?:\\.|[^'\\])'", "''", src)
    for m in re.finditer(r'ClearableUnreadRow\s*\(', src):
        i, paren, n = m.end(), 1, len(src)
        lam_start = None
        while i < n:
            c = src[i]
            if c == '(':
                paren += 1
            elif c == ')':
                paren -= 1
                if paren == 0:
                    lam_start = src.find('{', i)
                    break
            i += 1
        if lam_start is None or lam_start == -1:
            continue
        depth, k = 0, lam_start
        while k < n:
            c = src[k]
            if c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    break
            elif depth >= 1 and src.startswith('AnimatedVisibility', k):
                line = src[:k].count('\n') + 1
                print(f"::error file={path},line={line}::AnimatedVisibility 在 ClearableUnreadRow 的 content 里——Box 会把它叠在行头上（2026-11-15 未读长按弹窗头重影），请挂到外层卡片 Column 下")
                bad = 1
                break
            k += 1
sys.exit(bad)
PYEOF

[ "$fail" -eq 0 ] && echo "design-lint: 通过"
exit "$fail"
