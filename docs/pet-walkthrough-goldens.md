# 走查黄金图确认清单（P0）

> 规则（Bob 2026-10-26 定）：小 diff 按规则由 parent 直接确认；大 diff 找 Bob。
> P0 阶段报告不阻塞发布；P1 才把漂移升级为合入阻塞。

## 存放

- 数值黄金：`app/src/test/resources/pet-walkthrough-goldens.json`（已合入首版手算值，
  首轮 CI 即校验）。
- 图像黄金：`app/src/test/pet-walkthrough-goldens/`（P0 阶段为空——快照采图
  因 Paparazzi 阻塞延后到 P1，见矩阵文档头；P1 渲染路线恢复后，全部图像项
  首次跑出时记为“新增 diff”，见下）。

## 确认流程（每次走查报告出现“新增 diff”时）

1. 打开走查工作流的 Artifacts：`walkthrough-report.md`＋渲染图（`recorded/`）
   ＋diff 图（`diffs/`，仅有基线且漂移时产出）。
2. 逐项看渲染图：
   - 改动是预期的（本次有意改的视觉）→ 确认：把渲染图拷入
     `app/src/test/pet-walkthrough-goldens/`（与渲染产物同名），
     数值项则把报告里的 actual 写进 `pet-walkthrough-goldens.json`，随下一批
     一起合入（不要单独升版发版）。
   - 改动非预期 → 不确认，改动方修代码重跑。
3. 小 diff 判定：像素 diff ≤ 0.5%（报告脚本阈值）且肉眼无结构变化 → parent
   可直接确认；超阈或结构变化（拱形、盒高、裁剪位置变了）→ 找 Bob 定。

## 首轮待确认项（P0 合入时状态）

| 项 | 状态 | 说明 |
|---|---|---|
| 数值 29 项（肩线 8／盒高 5／气泡 1／净空 3／眨眼 12） | 已有手算基线，待首轮 CI 校验 | 若首轮报 fail，先核手算值 transcription，再核生产公式 |
| head-visible-height/width-frac | P1（需 JVM 渲染运行时） | 真资产 alpha 包围盒，普通单测里 BitmapFactory 是桩 |
| shoulder-arch-d1/d2/compact-d1/d1-peak 渲染图 | P1（Paparazzi 阻塞） | 渲染路线恢复后按原快照口径产图确认 |
| head-asset-box-d1 渲染图 | P1（Paparazzi 阻塞） | 红框＝盒子，图＝可见内容 |

## P1 注意

- P1 恢复渲染路线后，图像黄金与渲染工具默认快照目录的关系需重新确认：
  原 P0 设计是图像黄金与 Paparazzi 默认快照目录（`src/test/snapshots/`）
  分离存放、故不用 `verifyPaparazziDebug`（其比对只认默认目录）；若 P1
  换工具或换版本，需按新工具的目录语义重定存放与比对方式。
- 另：走查工作流的测试步带 `continue-on-error: true`（P0 非阻塞设计），
  该设置曾掩盖过一次真实失败（Paparazzi 阻塞期间走查 run 显示 success
  而主 CI 在红）；P0 阶段以主 CI（无掩盖）为准、走查报告的 fail 栏仍需
  人工复核；P1 升级门禁时应去掉该掩盖。
- 黄金图是大二进制：数量上来后评估 Git LFS（P1）。
