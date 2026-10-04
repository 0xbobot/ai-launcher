# 七仔 Live2D MVP 运行手册（RUNBOOK）

> 目标：在 GPU 机器上把 `master.png` 跑成 Live2D 模型（`.moc3` + `.model3.json`），
> 并在 Android 真机上验证渲染。本手册精确到命令，按顺序执行即可。

---

## 0. GPU 缺口（先读）

- **本机（CI/VM）确认无 GPU**：`nvidia-smi` 不存在、`lspci` 无 VGA 设备、`/dev/dri` 不存在。
  inkinesis 的分解步骤**必须 GPU**（CPU-only 官方不支持），所以本手册所有步骤都在**租用的 GPU 机器**上执行。
- 推荐配置：**AutoDL RTX 4090（24GB）**，约 ¥2–5/小时。
- 预估用量：inkinesis 单次分解约 4 分钟（含官方数据 230–237 秒/次）；加上环境搭建、
  依赖下载、试错，**租 2 小时足够**（费用约 ¥5–10）。
- 备选：Apple Silicon（MPS）本机，或 inkinesis 的 remote H100 桥接（`--h100-queue` 参数）。

---

## 1. master 图说明

`docs/live2d-mvp/master.png`（源自 `~/workspace/ai-launcher/pet-ip/meteor3-main-cut.png`，1280×1920 PNG，透明底）

**为什么选这张**（正面标准态）：
- 正面面对镜头，无透视/侧身；
- 身体完整：头、躯干、双手、双脚、金色尾巴全部可见；
- 手臂张开、与躯干分离——**无肢体遮挡**，这是 AI 分层最省心的姿态；
- 无手持道具（对比 `meteor3-hold-full-cut.png` 手里有发光卡片挡住躯干，不适合做 master）；
- 淘汰：`wave`（抬手非标准态）、`jump`（动态姿态，分层易错）。

这张图同时是路线 A（人工绑定师拆件）和路线 B（AI 分解）的共同输入。

---

## 2. GPU 机器环境准备

```bash
# 基础工具（Ubuntu 示例）
sudo apt update && sudo apt install -y git nodejs npm python3 python3-venv

# JDK 21（inkinesis 构建链需要）
sudo apt install -y openjdk-21-jdk

# 确认 GPU 可见
nvidia-smi
```

API Key 准备（按量付费，写入环境变量或 `.env`）：
- Anthropic API key、OpenAI API key——inkinesis 的分析/图像步骤会调用，见仓库 `.env.example`。

---

## 3. 跑 inkinesis：图片 → Live2D 包

```bash
# 3.1 拉代码
git clone https://github.com/codewayco/inkinesis.git
cd inkinesis
npm install

# 3.2 环境自检（会检查依赖路径、模型、GPU）
npm run image-to-rig -- --check
# 或直接调 pipeline：
node --import tsx tools/imageToRig/pipeline.ts --check

# 3.3 上传 master.png 到 GPU 机器（示例路径 ~/qizai/master.png），然后开跑
npm run image-to-rig -- --image ~/qizai/master.png --out outputs/qizai-mvp --live2d
```

说明：
- `--live2d` 表示在默认 INP 产物之外，额外导出 Live2D 包（`.moc3` / `.model3.json` / `.cmo3` / 纹理），
  放在 `outputs/qizai-mvp/live2d-<run-id>/` 下；
- 单次约 4 分钟；中途失败可用 `--resume` 从同一输出目录继续：
  `npm run image-to-rig -- --image ~/qizai/master.png --out outputs/qizai-mvp --resume`；
- 跑完后**人工审查**：看 `assets/character.psd` 分层是否合理、遮挡补画（尾巴根部、脚底）是否穿帮。
  官方校验只保证技术兼容，**不保证视觉质量**；
- 想用浏览器 UI 微调：`npm run dev`，打开打印的本地 URL，生成后点 **Save** 持久化到 `outputs/<job-id>/`。

### 3.4 官方 Core 校验（可选但推荐）

```bash
node --import tsx reference/cubism/runCombinedCapture.ts \
  outputs/qizai-mvp/<run-id>.inp \
  outputs/qizai-mvp/live2d-<run-id> \
  --near-keys
```

- 需要先按 inkinesis 的 Live2D runbook 装好官方 SDK（`reference/cubism/dependencies.json` 有说明，
  SDK 本体需从自己合法的 Cubism Editor/SDK 安装取，**不随仓库分发**）；
- 通过标准：27/27 级别即"零 Core-aware drawable 不匹配"（官方示例曾达到）。

---

## 4. 备选/增强：live2d-agent-kit

如果 inkinesis 的自动绑定在七仔身上翻车（非人形、绒毛边缘），用这套 agent 工作流做人工辅助修复：

```bash
git clone https://github.com/ariakage/live2d-agent-kit.git
cd live2d-agent-kit

# 按 SKILL.md 先跑最小几何示例，验证本地导出链
# 1) 安装 pinned 的 psd2live 引擎
bash scripts/setup-psd2live.sh

# 2) 导出 + 校验（示例路径，按实际工作目录替换）
export CUBISM_CORE_DIR=/path/to/your/local/core
bash scripts/validate_core.sh work/qizai/low/qizai.moc3 work/qizai/core-report.json
bash scripts/validate.sh --model work/qizai/runtime/qizai.model3.json \
  --core-report work/qizai/core-report.json

# 3) 打包运行时产物
python3 scripts/package-model.py \
  --model work/qizai/runtime/qizai.model3.json \
  --core-report work/qizai/core-report.json \
  --output work/qizai/runtime
```

- 详细流程见该仓库的 `SKILL.md` / `docs/workflow.md`；它自带 WebGL 预览（跑官方 Cubism Core），
  可做捏脸式检查：中性表情、头转极限、眨眼、各毛发摆动。

---

## 5. Android 真机验证

目标：确认 `.moc3` 在 Android 上真实渲染（不是"JSON 能解析"就行）。

```bash
# 5.1 拉官方示例（含 Framework submodule）
git clone --recurse-submodules https://github.com/Live2D/CubismNativeSamples.git
cd CubismNativeSamples

# 5.2 下载 Cubism SDK for Native（需自己从官网下，不随仓库分发）
# https://www.live2d.com/download/cubism-sdk/download-native/
# 把 ZIP 内容解压后拷到本仓库的 Core/ 目录
cp -r /path/to/CubismSdkForNative-*/Core/* ./Core/

# 5.3 把我们的模型换进示例资源
# 示例模型在 Samples/Resources/ 下，把 outputs/qizai-mvp/live2d-<run-id>/ 整个目录拷进去，
# 并按示例 README 的方式把 model3.json 注册进 LAppLive2DManager 的模型列表

# 5.4 用 Android Studio 打开 Samples/Android，按其 README 构建安装到真机
```

验收标准：
- 七仔在真机上渲染出来，无破面/黑块；
- 播放待机动作：呼吸、眨眼可见；
- `adb logcat` 无 Core 报错。

### 5.5 集成到我们仓库的方向（验证通过后才做）

我们仓库是 Kotlin + Compose + Gradle 项目：
- 首页宿主：`AndroidView` 包 GLSurfaceView（官方示例即此模式）；
- 浮窗宿主：Service + TextureView，走 SDK 的 render-to-texture（`USE_MODEL_RENDER_TARGET`，见 `LAppLive2DManager.cpp`）；
- 模型资源放 `app/src/main/assets/live2d/qizai/`；
- 按 `docs/PET_WORLD_ARCHITECTURE.md` 的 `QizaiRenderer` 接口封装，宿主无关。

---

## 6. 回传清单

GPU 机器上跑完后，把以下文件传回本仓库 `docs/live2d-mvp/`：
- `qizai.moc3`、`qizai.model3.json`、纹理目录（整个 Live2D 包）；
- 校验报告（`core-report.json`）；
- 真机截图/录屏（验证证据）；
- `NOTES.md`：记录遇到的问题和 workaround（工具链新，坑多，写下来）。

## 7. License 提醒

- inkinesis：Apache-2.0；live2d-agent-kit：MIT + GPL-3.0（psd2live 部分）；
- Live2D Cubism SDK / Core：**Live2D 私有许可**，个人与小规模（年营收 < 1000 万日元）免费，
  不随仓库分发；模型文件勿单独再分发；
- 若未来进入沃尔玛商用，需与 Live2D 另谈授权（见 `docs/PET_WORLD_ARCHITECTURE.md` §9）。
