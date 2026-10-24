#!/bin/bash
# 宠物高光片压制：源片 10 秒 → 按状态截段 → 480px H.264 静音。
# 用法：./scripts/transcode-pet-clips.sh [源目录] [输出目录]
# 默认源目录：~/workspace/your_files/pet-redesign/clips
# 默认输出：app/src/main/assets/clips（随包进 APK）
# 段落选择（2026-10-25 按 1fps 联系表验片定）：
#   greet     1.0-4.0  抬头+微笑（一次）
#   happy     1.0-4.0  峰值大笑（一次）
#   thinking  1.0-4.0  歪头+斜视（循环）
#   speaking  1.0-4.0  嘴部活动+睁眼（循环）
#   surprised 2.0-5.0  峰值惊讶睁眼+o 嘴（一次）
set -e
SRC="${1:-$HOME/workspace/your_files/pet-redesign/clips}"
OUT="${2:-app/src/main/assets/clips}"
mkdir -p "$OUT"
# 源片里头约占画面 70%，播放层是圆形徽章：先中心裁 80%
# （768×768，留头部位移余量），再缩到 480，圆内头的尺寸与
# 玻璃头代码层基本对齐，换片时不明显缩胀。
transcode() { # name src ss dur
  local name="$1" src="$2" ss="$3" dur="$4"
  ffmpeg -y -v error -ss "$ss" -i "$SRC/$src" -t "$dur" \
    -vf "crop=768:768:96:96,scale=480:480" -c:v libx264 -preset fast -crf 28 -pix_fmt yuv420p \
    -an -movflags +faststart "$OUT/$name.mp4"
  echo "$name.mp4  $(du -h "$OUT/$name.mp4" | cut -f1)"
}
transcode greet     media-generation-clip-greet-0-cc6257cb-4d57-42b9-b28a-45a49317331f.mp4     1.0 3.0
transcode happy     media-generation-clip-happy-0-69e5e68a-2e79-4a9c-b436-a5c183a4eff6.mp4     1.0 3.0
transcode thinking  media-generation-clip-thinking-0-b243ea45-2a6b-4f61-8f6e-649d16d0cdab.mp4 1.0 3.0
transcode speaking  media-generation-clip-speaking-0-762486d9-5bd3-4741-b19c-bbb316f52d22.mp4 1.0 3.0
transcode surprised media-generation-clip-surprised-0-fbaaa718-fd26-4a0d-ae5e-4734029911f0.mp4 2.0 3.0
