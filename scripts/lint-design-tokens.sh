#!/usr/bin/env bash
# 设计 token 门禁：只检查本次 diff 的「新增行」，存量不动。
# 用法：scripts/lint-design-tokens.sh [base-ref]   默认 origin/main
# 逃生口：行内带 `lint-ok` 注释即放行（须说明原因）。
#  - 裸 fontSize（13/14/15/17/20/32 等有等值档者）→ 失败（docs/TYPOGRAPHY_TOKENS.md 第 5 条）
#  - 新增 Color(0x…)（theme/glass/场景文件除外）→ 仅警告，提示评估是否进 Palette
set -euo pipefail
BASE="${1:-origin/main}"
FONT_EQ='(12|13|14|15|17|20|32)'
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

[ "$fail" -eq 0 ] && echo "design-lint: 通过"
exit "$fail"
