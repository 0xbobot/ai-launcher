# 刘海与状态栏避让规范（2026-10-25，Bob：主页横屏刘海问题 + 全页检查后立）

## 前提

App 是 edge-to-edge（`decorFitsSystemWindows=false`，见 `ui/theme/Insets.kt` 的
`enableEdgeToEdge`）：内容默认延伸到状态栏 / 挖孔 / 手势区之后。**任何一屏
都不许只靠固定 dp 顶距 / 边距「恰好」躲开系统区**——固定值只表达设计间距，
避让必须显式读 inset。横屏时挖孔不在顶部中央，而在左右两侧边（打孔屏横屏
侧边 inset 常 30–40dp+，刘海屏更大）；竖屏侧边 inset 为 0。

## 统一口径（新页面 / 改动布局时照此执行）

1. **顶部——状态栏必须显式处理**：根容器加 `statusBarsTopPadding()`
  （`ui/theme/Insets.kt` 的统一 helper，等价于 `statusBarsPadding()`）。
   固定顶距（如主页 16dp）只能叠加在 inset 之上，不能替代 inset。
   例外只许一种：已有固定顶距合计已高于标准状态栏、且改动竖屏布局代价
   大于收益时，列入「待观察」并写明数值依据，不许 silently 当作已处理。
2. **两侧——横屏挖孔用 displayCutout，不要写死数值**：全屏 / 贴边内容的根
   容器统一加
   `windowInsetsPadding(WindowInsets.displayCutout.only(Start, End))`。
   竖屏侧边 inset 为 0，此条可无条件加、竖屏布局零变化；横屏时自动按机型
   实际挖孔垫开。**禁止写死挖孔数值**（30dp / 40dp 之类在别机型上必错）。
3. **右边缘 rail 类控件——横屏必须让 cutoutRight**：rail 整块
   `padding(end = cutoutRight)`（cutoutRight 取自
   `WindowInsets.displayCutout.getRight(density, LayoutDirection.Ltr)`），
   触控排除区同量右扩。且**高度不许写死**：横屏屏高不足时写死高会裁掉
   顶尾（前例：首页 picker rail 400dp 在横屏被裁到房子出屏），横屏改容器
   跟随高（`railHeight = null` + `railFraction`），竖屏口径不动。
4. **底部——手势区**：底部面板底距不得侵占系统手势区；拖拽起点避让走
   `systemGestureBottomPx()`（`Insets.kt` helper），不要另起数值。
5. **键盘弹出**：窗口 `adjustResize` + 键盘弹出专用布局（应用中心 324 批口径：
   隐藏 rail / 幽灵字母 / 刊头标题区，问七仔行保留）；键盘在场时不得有内
   容被键盘盖住且无滚动 / 收起路径。
6. **居中弹窗 / 卡片**：水平 margin ≥ 32dp 且居中，默认通过；仍须在横屏
   挖孔机上目视确认一次（左右两种旋转方向各一遍，挖孔在左和在右都会出现）。

## 每屏必查清单（合入前逐项打勾）

- [ ] 竖屏顶部：内容是否 clears 状态栏（含大状态栏机型）
- [ ] 横屏顶部：同上（横屏状态栏带 + 侧边挖孔的竖向带）
- [ ] 横屏左侧 / 右侧：贴边内容、rail、横排 hero 是否 clears displayCutout
- [ ] 底部手势区：面板 / 按钮是否被手势条遮挡或抢手势
- [ ] 键盘弹出：输入态内容是否仍可见、可达
- [ ] 挖孔机真机：左右两种旋转方向各看一遍

## 已落地页表（2026-10-25 检查结论）

| 页面 | 结论 |
|---|---|
| 主页横屏 | **本批修**：根容器加 displayCutout 两侧 + 横屏顶部 max(16dp, 状态栏高)；picker rail 横屏改容器跟随高 + 右侧让 cutoutRight + 底距 16dp。竖屏一律不动 |
| 主页竖屏 | 通过（静态 hero 顶距合计 72dp，标准状态栏下 clears）；输入态紧凑顶距合计 32dp，无显式 inset——标准状态栏下恰好 clears，大状态栏机型可能擦边，列入待观察，不动竖屏布局 |
| 应用中心竖屏 | 通过：顶部 statusBars；rail 底部锚定（底距 70dp），顶缘离顶带远 |
| 应用中心横屏 | 通过（320 批已做）：内容区 `(cutoutLeft - 16dp)` 正部右移、rail 整块让 cutoutRight、排除区同量右扩、顶部走共享容器 statusBars |
| 设置页 | **本批修**：根容器加 displayCutout 两侧（顶部早有 statusBarsTopPadding） |
| 灵动岛 Toast | **本批修**：宿主加 displayCutout 两侧（顶部早有 statusBarsTopPadding + 8dp，pill 居中） |
| 引导页 | **本批修**：StepScaffold 加 displayCutout 两侧（顶部 32dp 本就高于标准状态栏） |
| 输入态（胶囊 / 推荐行） | 通过：主页胶囊在根容器内（随本批横屏避让走）；窗口 adjustResize，键盘弹出不盖胶囊；应用中心键盘布局 324 批已做 |
| Dock 面板 | 通过：横屏居中 344dp 定宽，左右挖孔区够不到；底部 16dp + 手势区拖拽避让，与状态栏无关 |
| 弹窗 / 对话框（更新 / 权限引导 / 隐藏 Dock 确认 / 快捷添加） | 通过：居中 + 水平 margin ≥ 32dp，不贴边 |
| 盲操扇形 | 通过：全屏手势层，无贴边可读内容；右边缘豁免区已有处理 |

## 反面案例（本规范的来源）

- 主页横屏：根容器只有固定 16dp 顶距、无任何显式 inset；hero 横排固定
  28dp 边距顶不到横屏 displayCutout 侧边 inset → 顶端内容顶到挖孔区。
- 首页 picker rail：400dp 写死高 + 右边缘无 cutoutRight → 横屏顶缘被裁、
  房子顶到状态栏带（与应用中心横屏 rail 在 320 批修掉的是同一类问题，
  首页侧当时没补）。
