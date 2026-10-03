# AI Launcher 技术方案 v1.0

> 依据《AI Launcher PRD v1.0》制定。代码现状：v0.16.1，单 `:app` 模块，28 个 Kotlin 文件。
> 本文档是工程执行依据：定义目标架构、分阶段路线、每阶段的验收标准。

---

## 一、PRD 对齐与冲突裁决

以下冲突按 Bob 已确认的口头方向裁决，PRD 文字服从已交付行为：

| # | PRD 条款 | 现状/口头方向 | 裁决 |
|---|----------|---------------|------|
| 1 | §五"右滑=更多"（且§五内部自相矛盾：同时写了"左滑：更多→更少"） | 全局手势签名：**左滑=多，右滑=少**（v0.13 起全 App 统一，永久规则） | 以已交付规则为准，PRD §五按左滑=多/右滑=少执行 |
| 2 | §6.1 Home 有"顶部时间日期 + Pet 周围动态信息" | Bob 最新方向"宠物即桌面"：**首页除 Dock 只留宠物**，时间日期环境化（不直接显示数字） | 以 Bob 最新口头方向为准；PRD §6.1 的顶部信息改为环境化表达 |
| 3 | §三十四"长按 Pet：Pet 状态/个性" | 原型已定"点耳朵=意图入口"（待 Bob 确认） | 待原型确认后定；技术方案预留两种映射 |
| 4 | §八 Dock"4→10→All" | 已交付 D1/D2/D3（v0.14–v0.16） | 对齐，无需改 |

---

## 二、目标架构（PRD §三十九落地）

```
┌─────────────────────────────────────┐
│ UI Layer                            │  ui.home / ui.apps / ui.pet / ui.settings
│ Home / Dock / App Center / Pet      │  纯展示 + 手势，不直接调 LLM/引擎
└──────────────┬──────────────────────┘
               │ 手势/点击 → Event
┌──────────────▼──────────────────────┐
│ Interaction Layer                   │  core.event.EventBus（本方案新增）
│ Event Bus（唯一跨层通道）            │
└──────────────┬──────────────────────┘
               │ Event
┌──────────────▼──────────────────────┐
│ AI Brain                            │  core.brain.AiBrain（本方案新增骨架）
│ 决策环：Event → Context → Think?    │  Nothing 是合法结果（PRD §四十三）
│        → Decision → 执行/无动作      │
└──────────────┬──────────────────────┘
               │ Decision
┌──────────────▼──────────────────────┐
│ Action Engine（Phase 2）             │  按风险分级 A–E 执行（PRD §二十三）
│ Capability Registry 查询 → 执行      │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│ Android System                      │  现有 data/* + service/*
│ Launcher / Notification / Usage     │
└─────────────────────────────────────┘
```

**铁律（PRD §七十三）：** UI 层永远不直接调用 LLM/引擎；一切跨层走 EventBus；
LLM 只做 Intent/Decision，不直接操作手机；高风险 Action 必须过权限层。

---

## 三、模块拆分（务实版，PRD §四十）

PRD 列了 20 个模块。单人 + GitHub Actions 构建的前提下，一次性拆 20 个 Gradle
模块会显著拖慢构建、增加维护成本。采用两阶段：

- **Phase A（现在–Phase 3）：包级隔离。** 单 `:app` 模块内按包划分边界：
  `core.event` / `core.brain` / `core.action` / `core.context` / `core.memory` /
  `data.*` / `service.*` / `ui.*`。包之间只允许按§二箭头方向依赖，
  用 `internal` 可见性 + Code Review 约束（暂不靠 Gradle 强制）。
- **Phase B（按需）：** 当某个包稳定且被多处复用时，再抽成独立 Gradle 模块。
  优先抽无 Android 依赖的纯 Kotlin 包（`core.event`、`core.brain` 的决策纯逻辑）。

---

## 四、EventBus 事件表（PRD §四十二）

`core.event.LauncherEvent`（sealed interface），`core.event.EventBus`（SharedFlow）：

| 事件 | 来源 | 说明 |
|------|------|------|
| `NotificationReceived(n)` | NotificationListenerService | 新通知（替代原来直调 PetRepository） |
| `NotificationRemoved(key)` | NotificationListenerService | 通知被清除 |
| `UserTappedPet` | PetZone | 点按宠物 |
| `VoiceStarted` / `VoiceEnded` | 意图入口 | 语音起止 |
| `TaskCompleted(id, success)` | ActionEngine | 任务结束（含失败，PRD §六十四） |
| `ContextChanged` | ContextEngine | 时间/地点/前台 App 变化等 |
| `ScreenOn` / `ScreenOff` | SystemReceiver（Phase 3） | 屏幕事件 |
| `AppOpened(pkg)` / `AppClosed(pkg)` | UsageStats 轮询（Phase 3） | 前台 App 变化 |
| `DockStateChanged(state)` | PullUpDock | Dock 档位变化 |

**迁移原则：** 现有直调链路逐个改为"发 Event → Brain 决策 → 原逻辑"，
每次只迁一条，CI 常绿，行为不变。

---

## 五、AI Brain 决策环（PRD §四十三/四十四）

`core.brain.AiBrain.onEvent(event)`：

```
Event
 ↓ ContextEngine.update（Phase 3 完整版；Phase 1 委托现有 Repository）
 ↓ shouldThink? —— Attention Budget（PRD §十四）：每日主动预算，
 ↓                 高频事件直接返回 Nothing
Decision = PetBehavior | ActionRequest | Nothing
 ↓
 PetBehavior → PetRepository.applyBehavior（表情/动作/标签）
 ActionRequest → ActionEngine.submit（Phase 2）
 Nothing → 结束（合法结果，不打扰用户）
```

Phase 1 的 `shouldThink` 保守策略：只有 `NotificationReceived` 进入决策
（行为与现状完全一致），其余事件一律 `Nothing`。后续 Phase 逐步放开。

---

## 六、Capability Registry（PRD §六十/六十一）

现有 `data/CapabilityRegistry.kt` 已有雏形（id/label/包名/deep link）。
按 PRD §六十一补齐数据模型（Phase 1 只加模型，不改加载逻辑）：

```kotlin
enum class RiskLevel { READ, NAVIGATE, LOW_RISK, MEDIUM_RISK, HIGH_RISK } // A–E
enum class ExecutionMethod { LAUNCH, INTENT, DEEP_LINK, SHORTCUT, APP_FUNCTION, ACCESSIBILITY }

data class Capability(
    ...,
    val riskLevel: RiskLevel = RiskLevel.LOW_RISK,       // 默认低风险
    val executionMethod: ExecutionMethod = ExecutionMethod.LAUNCH,
    val permission: String? = null,                      // 需要的 Android 权限
    // inputSchema/outputSchema：Phase 2 随 Planner 加入
)
```

执行优先级（PRD §二十一）：`APP_FUNCTION > DEEP_LINK > SHORTCUT > INTENT > LAUNCH`，
`ACCESSIBILITY` 永远只是兼容层（PRD §六十评审结论 2）。

---

## 七、Action 安全门（PRD §二十三，Phase 2 实现）

```
ActionRequest(intent, capability, params)
 ↓ riskLevel == HIGH_RISK → 必须用户明确确认（弹窗），否则拒绝执行
 ↓ permission 缺失 → 解释用途并引导授权（PRD §三十），不静默失败
 ↓ 执行 → 结果回写 TaskCompleted(success|fail) → Pet 如实反馈（PRD §六十四：
   失败不许说"好啦完成了"）
```

---

## 八、Context 分级（PRD §十九，Phase 3 实现）

L0 无需权限 → L1 基础设备 → L2 UsageStats（已持有） → L3 通知（已持有） →
L4 日历/位置/联系人 → L5 无障碍 → L6 App Functions（未来）。
每条 Context 记录来源等级；AI 建议必须可追溯"用了哪些 Context"
（Privacy Center 展示，PRD §五十一）。

---

## 九、分阶段路线

| 阶段 | 内容 | 验收 |
|------|------|------|
| **Phase 1（本轮）** | EventBus + AiBrain 骨架 + 通知链路迁移到事件；Capability 模型补 risk/execution 字段 | CI 绿；行为与 v0.16.1 完全一致；v0.17.0 OTA |
| Phase 2 | ActionEngine + 安全门（A–E）；意图入口走 Intent→Planner→Action | 高风险 Action 必须确认；失败如实反馈 |
| Phase 3 | ContextEngine（L0–L6 分级）+ EventBus 全量事件（Screen/App 前后台）；Memory 三层 + MemoryCenter | 权限可逐项关闭；Nothing 为默认结果 |
| Phase 4 | Attention Budget + Pet 三层行为（Idle/Reactive/Proactive）；宠物即桌面落地 | 主动行为可解释、可撤销、有预算 |
| Phase 5 | 按需抽 Gradle 模块；OEM 兼容矩阵（Pixel/Samsung/Xiaomi） | §六十五矩阵 |

**不做的（PRD §五十五）：** 社交/养成/商店/货币/Agent 市场/支付自动化/
高风险自动发送/复杂 3D Pet——架构上不预留这些的钩子。

---

## 十、性能与电量红线（PRD §三十六–三十八）

- AI 永不阻塞 UI 线程；Brain 决策在后台协程，超时直接 Nothing。
- 事件驱动，禁止轮询式状态检查（UsageStats 按需查询）。
- LLM/网络失败时 Launcher 零感知（现有 LlmRouter 已满足，保持）。
- 后台：不常驻 AI Brain；ForegroundService 只在确有必要时用。

---

## 十一、验收映射（PRD §六十七/六十八）

- 设为默认 Home、Home 秒开、App 启动稳定、不影响返回键/最近任务——每版 OTA 前回归。
- 模型/网络失败不影响 Launcher——Phase 1 已满足（事件总线 tryEmit 永不抛）。
- 关闭 AI 权限后 Launcher 正常——事件无消费者时自然退化（Phase 1 已满足）。
