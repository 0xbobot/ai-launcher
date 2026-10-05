# 七仔小世界架构设计（Pet World Architecture）

- 状态：草案 v0.1（2026-10-04）
- 主角：七仔（形象：小流星）
- 一句话：首屏是一个活的微缩世界，七仔是 AI 助手的化身；NPC 是信息的拟人化载体，七仔是守门人。

---

## 1. 设计原则

1. **不打扰**：有事才出现，没事安静待着。七仔 + 全部 NPC 共同遵守。
2. **主次分明**：七仔永远是视觉中心；NPC 体型小一号、色彩更素。
3. **逻辑清晰**（NPC 三铁律）：
   - 一信息一 NPC：一种信息只归一个 NPC，不串台、不重叠；
   - 单向信息流：NPC（信源）→ 七仔（汇总 + 判断轻重）→ 用户，NPC 不直接打扰用户；
   - 三态就够：`HIDDEN`（藏起）/ `INFO`（出现 + 头顶图标）/ `URGENT`（出现 + 强调色）。
4. **渲染分档**：重渲染只给主角，配角保持轻量。

---

## 2. 角色体系

### 2.1 七仔（主角 / Gatekeeper）

- 身份：AI 助手的化身 + 信息守门人。
- 职责：汇总各 NPC 信息，按"不打扰"原则决定直接呈现 / 聚合稍后 / 忽略。
- 状态：`IDLE` / `BUSY`（任务中）/ `SLEEP` / `ALERT`（有重要信息）/ `LOW_POWER`。

### 2.2 NPC 名单（一信息一 NPC，可扩展）

| NPC | 信息 | 形态 | 出现时机 |
|---|---|---|---|
| 信使鸽 | 通知 | 小肥鸽，邮差帽，头顶信封数 | 有新通知 |
| 小青蛙 | 天气 | 头顶雨滴 / 太阳图标 | 天气变化 |
| 日历鸟 | 日程 | 头顶日历图标 | 下个日程前 15 分钟 |
| 电池精灵 | 电量 | 颜色随电量变（绿→黄→红） | 电量 < 20% |
| 工匠 | AI 任务 | 小机器人，头顶转齿轮 | AI 任务执行中 |
| 音符精灵 | 音乐 | 打拍子 | 音乐播放中 |
| 跑鞋仔 | 久坐 | 探头 | 1 小时无活动（待定：第一版可能先砍） |

### 2.3 视觉编码（统一语言）

- 信息类型看**头顶图标**，轻重看**颜色**（灰 → 常 → 红）；
- 互动语言：气泡符号（! ? ✓ …）+ 肢体动作，不用大段文字。

---

## 3. 渲染架构

### 3.1 分档渲染

```
七仔（主角）：Live2D Cubism —— 仿真动作
  待机：呼吸 + 眨眼 + 尾巴/绒毛物理（常驻，最省）
  动作库：挥手 / 开心跳 / 睡觉 / 撑伞 / 递东西 / 点头 …（按需触发）
NPC（配角）：Compose Canvas 三态贴图（HIDDEN / INFO / URGENT）
特效点缀：Lottie（雨雪、彩蛋）
```

选型结论：
- 七仔用 Live2D：2D 角色仿真动作的业界标准（VTuber 同款），网格变形 + 物理，官方有 Android Native SDK，可嵌 Compose；
- 不用游戏引擎（libGDX / Unity）：NPC 只有三态，引擎是"太重了"的技术版；Unity 空工程包体 14MB 起且静态菜单也耗电，否决；
- NPC 保持 Canvas 轻量：它们只是信息载体，不值得上重渲染。

### 3.2 宿主无关渲染器（核心抽象）

```kotlin
interface QizaiRenderer {
    fun setMood(mood: QizaiMood)            // IDLE / HAPPY / SLEEPY / LOW_POWER …
    fun playMotion(motion: QizaiMotion)      // WAVE / JUMP / NOD / UMBRELLA …
    fun setExpression(expr: QizaiExpression) // NORMAL / SURPRISED / SAD …
    fun syncWorld(state: WorldState)         // 时间段 / 天气同步
    fun setFpsMode(mode: FpsMode)            // FULL / IDLE / PAUSED（省电）
    fun release()
}
```

- Live2D 实现类只写一次；
- **宿主 A（首页）**：Compose 里 `AndroidView` 嵌 Live2D 视图；
- **宿主 B（浮窗）**：Service 里 `TextureView`，走 SDK 的 render-to-texture 路径（避开 SurfaceView 打洞问题，透明/拖动跟普通 View 一样）；
- 以后加新宿主（如锁屏小组件），只加宿主，不动模型和逻辑。

### 3.3 单激活宿主策略（重要）

- 同一时间**只激活一个渲染宿主**：在 Launcher 首页 → 用首页宿主；离开桌面进入其他 App → 自动切换到浮窗宿主；
- 状态机唯一，切换宿主无缝衔接，用户永远只看到一个七仔；
- 好处：避免"两个七仔"同框的诡异感 + 省一半渲染功耗。

---

## 4. 浮窗设计

### 4.1 技术

- `SYSTEM_ALERT_WINDOW` 权限 + 前台 Service + `WindowManager` 添加 `TYPE_APPLICATION_OVERLAY` 悬浮窗；
- Live2D 走 TextureView（render-to-texture），与首页**同一模型、同一动作库**，视觉完全一致；
- 首次使用需权限引导（参考 v0.36.0 无障碍引导模式：先讲清楚为什么，再跳系统设置）。

### 4.2 交互（可互动，但保持简单）

- **拖动**：长按拖动，位置记忆；抬手可选吸附边缘；
- **单击**：展开快捷任务面板（3–5 个一键任务）：AI 问答 / 查天气 / 设闹钟 / 清理内存 / 返回桌面；
- **任务处理**：简单直接——点任务 → 七仔播基础反馈动作（点头 NOD）→ 走现有 AI 任务通道执行 → 小气泡/Toast 反馈结果；
- **刻意不做**：复杂多段肢体语言、剧情式演出。浮窗七仔的动作库只用基础 motion（待机 / 点头 / 挥手 / 睡觉），丰富的动作留在首页。

### 4.3 耗电策略（首页 + 浮窗通用）

| 场景 | 策略 |
|---|---|
| 互动中 | 60fps 全开 |
| 待机 | 15fps，Live2D 仅更新呼吸/眨眼参数 |
| 息屏 | 暂停渲染循环，Service 保活 |
| 电量 < 15% | 浮窗休眠（收起为小图标或暂停），联动 BatteryGuard |
| Doze | WorkManager 加约束，不硬拉天气 |

---

## 5. 数据层

### 5.1 DataSource 接口（加 NPC 只加实现，不动七仔）

```kotlin
interface NpcDataSource {
    val npcId: NpcId
    fun observe(): Flow<NpcState>  // HIDDEN / INFO / URGENT + payload（图标/文案/数量）
    fun start()
    fun stop()
}
```

### 5.2 各 NPC 数据源与技术手段

| NPC | 数据源 | Android 技术 | 权限 / 依赖 |
|---|---|---|---|
| 信使鸽 | 通知到达 | NotificationListenerService | 通知使用权（手动授权）；只能拿标题/包名，读不到正文 |
| 小青蛙 | 真实天气 | 和风天气 API + WorkManager 每小时拉取 + 本地缓存 | API key；定位用手动选城市降级，免定位权限 |
| 日历鸟 | 系统日历 | CalendarContract + ContentObserver | READ_CALENDAR 运行时权限 |
| 电池精灵 | 电量 | ACTION_BATTERY_CHANGED 粘性广播 | 无需权限；复用现有 BatteryGuardService |
| 工匠 | AI 任务状态 | App 内部任务状态机 | 无，纯内部 |
| 音符精灵 | 播放状态 | MediaSessionManager | 复用信使鸽的通知使用权，不新增 |
| 跑鞋仔 | 久坐/步数 | UsageStatsManager 或计步传感器 | 授权 + 国产 ROM 后台限制；待定 |
| 昼夜 | 时间 | 系统时间，每分钟 tick | 无 |

### 5.3 调度

- WorkManager（天气轮询）、BroadcastReceiver（电量）、NotificationListenerService（通知 + 音乐）、ContentObserver（日历）。

---

## 6. 决策层：Gatekeeper（七仔）

- 输入：各 NPC 的 `NpcState` 流 + `WorldState`（时间段 / 天气 / 电量 / 前台状态）；
- 输出：呈现指令——直接呈现 / 聚合稍后 / 忽略；
- 规则示例：
  - 23:00–07:00 非 URGENT 一律聚合，天亮再说；
  - 5 分钟内多条通知合并为一条"3 条新通知"；
  - URGENT（电量 < 10%、重要联系人来电）直接呈现；
  - 用户主动点击七仔 → 视为"我想看"，聚合的信息一次性展开。

---

## 7. 世界模拟层

- **时间**：晨 / 昼 / 昏 / 夜四段，背景色温跟随；深夜七仔睡觉（SLEEP 状态，不响应非紧急信息）；
- **天气**：与小青蛙同源，画布同步下雨/下雪/晴；彩虹做小彩蛋；
- 原则：环境感，不做信息堆砌。

---

## 8. 游戏化（轻）

- 心情值：互动 + 任务完成上涨，长期冷落缓慢下降（不惩罚）；
- 天气图鉴：收集经历过的晴 / 雨 / 雪 / 彩虹；
- 成就：早睡早起、连续完成任务等；
- 数值藏后台，不做重度 UI。P5 再做，不阻塞前面阶段。

---

## 9. License 与风险

- Live2D Cubism SDK：私有授权，个人与小规模（年营收 < 1000 万日元）免费；**若未来进入沃尔玛商用，需与 Live2D 另谈授权**；
- 七仔 Live2D 模型需现做：分层原画 → Cubism Editor 绑定（脸部 XY / 身体 / 手脚 / 尾巴物理）。路线二选一：找绑定师，或研究 AI 辅助绑定；
- 国产 ROM（MIUI / EMUI）对悬浮窗、通知监听、后台保活有额外限制，权限引导和降级策略要逐个处理。

---

## 10. 分阶段路线图

- **P0**：七仔 Live2D 模型绑定（前置依赖，阻塞 P1）；
- **P1**：首页世界——时间/天气背景 + 七仔 Live2D 待机与基础动作 + QizaiRenderer 接口；
- **P2**：NPC 数据层 + 三态机 + Gatekeeper（先上信使鸽 / 电池精灵 / 小青蛙）；
- **P3**：任务可视化（工匠 NPC + AI 任务联动）；
- **P4**：浮窗宿主（简单互动 + 快捷任务面板）；
- **P5**：轻游戏化（心情值 / 图鉴 / 成就）。

---

## 11. 待确认事项

1. 名字：文档暂用"七仔"（形象即当前小流星）；
2. Live2D 模型绑定路线：找绑定师 vs AI 辅助绑定；
3. 天气 API key（和风天气免费版）由谁注册；
4. 跑鞋仔（久坐）第一版：砍 vs 降级；
5. 浮窗快捷任务面板的具体条目（当前 5 个：AI 问答 / 查天气 / 设闹钟 / 清理内存 / 返回桌面）。
