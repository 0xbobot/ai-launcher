# 宠物走查矩阵（Pet Walkthrough Matrix）

> 目标：机器每轮全量验，人只看 diff 和里程碑手感（Bob 2026-10-26 批）。
> P0 只实现“已实现”列标 P0 的项；其余列入 P1 扩展，不提前做。
> 报告语义：通过／失败（相对黄金基线漂移）／新增 diff（无基线）三栏；
> P0 阶段报告不阻塞发布（发布门禁是 P1）。

## 轴（全量空间）

| 轴 | 取值 |
|---|---|
| 姿态 | 闲置、眨眼（全／半／慢半／双）、注视、倾听、思考、点按反应、低电量 |
| perch | Dock 上沿、意图卡片上沿 |
| 尺寸档 | D1、D2、紧配置（字号 ≥1.3 或可用高 <400dp 时宠物 ×0.8） |
| 横竖屏 | 竖屏、横屏 |
| 壁纸 | 亮底、暗底、繁忙（明暗混杂／地平线横切） |
| 字号档 | 1.0、1.3 |

全量笛卡尔积只作覆盖规则，不逐项展开：P0 跑下表；P1 按“每姿态 × 每 perch ×
每尺寸档（竖屏／1.0／亮底）＋横屏／繁忙壁纸／1.3 字号抽查”扩展。

## P0 已实现项

| id | 断言 | 量测口径 | 状态 |
|---|---|---|---|
| shoulder-d1/d2-chord/rise | 肩线弦长／rise | 生产公式：宽＝面板高×0.52×1.224，弦＝宽+70dp，rise＝弦×18/156；D1 面板 136dp、D2 184dp | 数值＋快照 |
| shoulder-compact-d1/d2-chord/rise | 紧配置肩线 | 同上，宠物 ×0.8 | 数值 |
| shoulder-arch-d1/d2/compact-d1/d1-peak | 肩线拱渲染 | Paparazzi 快照：生产 DockShoulderShape 轮廓＋appendShoulderArch 拱高光描边；peak 档 7dp | 快照 |
| petbox-d1/d2/compact-d1/compact-d2/intent-height | 宠物盒高 | 盒高＝宽×1318/1043；意图宽 108×0.8＝86.4dp | 数值 |
| head-visible-height/width-frac | 玻璃头可见比 | 真资产 glass_head.png 的 alpha 包围盒（alpha>16），盒子尺寸 ≠ 可见尺寸的实测 | 数值＋快照 head-asset-box-d1 |
| bubble-height | 气泡高 | 52（标题行）＋340（内容区上限，恒绑定）＋8（底垫）＋10（尾巴）＝410dp | 数值 |
| dock-clearance-700/800/900dp | Dock perch 气泡净空 | 气泡顶 y＝屏高−（面板 136＋rise 18.06−压入 5＋盒高 109.38）−8−410；净空＝气泡顶 y−状态栏 24dp（假设值）；为负＝探出屏顶，记 attention 不判 fail | 数值 |
| blink-*-timing / double-gap / settles | 眨眼时序 | BlinkVariety 纯函数值（全 85/70/85、半 70/50/70、慢半 120/100/120、双眨间隔 120ms）＋任意种类 2000ms 内复位不变量 | 数值 |
| touch-targets-checklist | 触控 ≥48dp | P0 为代码位置清单：气泡关闭钮 48dp（PetActionList）、意图 ✕ 48dp（IntentEntry）、示例行 min 48dp（InputDeck）、Dock 图标 56dp(D1)/46dp(D2)；渲染树实测是 P1 | 清单 |
| briefing-contrast（引用） | 简报字色对比度 | 复用现有 BriefingAdaptiveTextTest（含 23:14 左区取样、双峰 guard、连续加权用例）；P0 不新增 | 引用既有 suite |

## P1 扩展（未做，清单）

- 意图 perch 气泡净空：卡片容器底 y 需渲染树实测（Paparazzi 语义／布局量测
  或 onGloballyPositioned），再算气泡顶 y＝容器底−（面板高＋18＋109.18−5＋8＋410）。
- 触控渲染树实测：同上，量关闭钮／✕／示例行／图标的实测触控高。
- 眼睛本体渲染：FloatingPet 眼睛绘制目前嵌在本体内，需行为中性测试钩子
  （如 closedPhase／blinkDepth 可注入的测试参数）才能逐帧快照；P0 只量时序。
- 注视／倾听／思考／点按反应／低电量各姿态快照：需可注入的状态脚手架，
  与眼睛本体同属 P1。
- 横屏／繁忙壁纸／1.3 字号抽查：按覆盖规则扩展快照 deviceConfig 与字号。
- 交互脚本：点宠物→清单→点动作→帧序列的脚本化走查（状态机断言）。
- 发布门禁：走查工作流从报告任务升级为 required check（enforce 常开），
  黄金图漂移阻塞合入。
- 成本优化：main-push 全量触发改为路径过滤或定时全量＋每批子集。

## 运行方式

- 手动：Actions → Pet Walkthrough P0 → Run workflow，可填 subset（id 前缀，
  如 `shoulder-`，空＝全量 P0）。
- 分支：推到 `pet-walkthrough-*` 分支自动跑全量 P0。
- 主干：推到 main 自动跑全量 P0（报告任务，不阻塞）。
- 产物：报告 Markdown＋渲染图＋diff 图＋JUnit XML（Artifacts，见工作流）。
