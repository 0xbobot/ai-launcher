# 走查黄金图确认清单（P0）

> 规则（Bob 2026-10-26 定）：小 diff 按规则由 parent 直接确认；大 diff 找 Bob。
> P0 阶段报告不阻塞发布；P1 才把漂移升级为合入阻塞。

## 存放

- 数值黄金：`app/src/test/resources/pet-walkthrough-goldens.json`（已合入首版手算值，
  首轮 CI 即校验）。
- 图像黄金：`app/src/test/pet-walkthrough-goldens/`（P0 首轮为空——全部图像项
  首次跑出时记为“新增 diff”，见下）。

## 确认流程（每次走查报告出现“新增 diff”时）

1. 打开走查工作流的 Artifacts：`walkthrough-report.md`＋渲染图（`recorded/`）
   ＋diff 图（`diffs/`，仅有基线且漂移时产出）。
2. 逐项看渲染图：
   - 改动是预期的（本次有意改的视觉）→ 确认：把渲染图拷入
     `app/src/test/pet-walkthrough-goldens/`（与 Paparazzi 产物同名），
     数值项则把报告里的 actual 写进 `pet-walkthrough-goldens.json`，随下一批
     一起合入（不要单独升版发版）。
   - 改动非预期 → 不确认，改动方修代码重跑。
3. 小 diff 判定：像素 diff ≤ 0.5%（报告脚本阈值）且肉眼无结构变化 → parent
   可直接确认；超阈或结构变化（拱形、盒高、裁剪位置变了）→ 找 Bob 定。

## 首轮待确认项（P0 合入时状态）

| 项 | 状态 | 说明 |
|---|---|---|
| 数值 29 项（肩线 8／盒高 5／气泡 1／净空 3／眨眼 12） | 已有手算基线，待首轮 CI 校验 | 若首轮报 fail，先核手算值 transcription，再核生产公式 |
| head-visible-height/width-frac | 待首轮量测 | 真资产 alpha 包围盒，首轮即出 actual |
| shoulder-arch-d1/d2/compact-d1/d1-peak 渲染图 | 待首轮产图确认 | Paparazzi record 产物 |
| head-asset-box-d1 渲染图 | 待首轮产图确认 | 红框＝盒子，图＝可见内容 |

## P1 注意

- 图像黄金目前与 Paparazzi 默认快照目录（`src/test/snapshots/`）分离存放，
  故 P0 不用 `verifyPaparazziDebug`（其比对只认默认目录）；P1 若要启用
  verify 模式，需把黄金图迁入默认目录或确认 Paparazzi 的 snapshotsDir 配置。
- 黄金图是大二进制：数量上来后评估 Git LFS（P1）。
