package com.bobot.ailauncher.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

/**
 * 宠物／输入动效统一家族（2026-10-26，Bob 21:56：动画节奏惯性都不一样，要修）。
 *
 * 统一不是同参数——不同质量的身体就该有不同的弹簧（头轻故弹、卡片重故稳、
 * 拖拽松手介于其间；全改成同一套参数反而全都不像）。统一的是语言：
 * - 位置类宏观动效一律走弹簧，不用 tween 位移（垫高跟踪的 snap 除外：
 *   跟键盘顶边是精确定位，不是自由运动，snap 才贴得住 0dp）；
 * - 可见性类一律 EaseOutCubic 淡入／淡出，时长只取家族三档；
 * - 再无行内自造规格：新动效必须从家族里取，改参数只改此处。
 * 不在家族里的，一律不动：
 * - 生理微动（眨眼、扫视、呼吸、瞳孔缩放）：生理时间尺度，改了就不像活物；
 * - 手动动作引擎的编排弹簧（摇摆、点头、下压等）：动作编排，不是宏观节奏；
 * - 头弹簧的值：canonical 在此处（[HeadSoft]），AliveLogic.HeadSpring
 *   只剩注视范围（RANGE_X/Y），弹簧参数不再两处各存一份。
 * 影子已无：接触影（Dock 活感层与意图卡片旧层）按 Bob 22:02 verdict
 * 全部摘除，家族里不再有影子条目（原 550ms＋90ms 延迟作废）。
 */
object PetMotion {
    // ---- 弹簧家族（位置类宏观动效） ----

    /** 头弹簧（活感）：整颗头作颈上弹簧质量，可见 2–3 次过冲。值不动。 */
    val HeadSoft = spring<Float>(dampingRatio = 0.3f, stiffness = 25f)

    /** 进出过渡：宠物 Dock↔卡片位移，轻微过冲回落。值不动。 */
    val EnterStandard = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    /** 拖拽松手：约 20% 过冲、约 0.9s 落定。值不动，并入家族。 */
    val DragRelease = spring<Float>(dampingRatio = 0.45f, stiffness = 90f)

    /**
     * 拖拽肩水平跟随：临界阻尼，约 90ms 达目标 63%。值不动。
     * 22:10 后模型：垂直方向肩升锁定等于头升（不用弹簧），本弹簧只用于
     * 肩横移滞后跟随（头略先行、肩跟上落在此处）。
     */
    val ShoulderLag = spring<Float>(dampingRatio = 1f, stiffness = 550f)

    /**
     * 垫高滑动：临界阻尼，无过冲，约 250ms 落定。
     * 接替原 tween(250)：跟键盘／导航垫目标的滑动不再用 tween 位移。
     */
    val PadStandard = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    /**
     * 垫高快滑：临界阻尼，无过冲，约 150ms 落定。
     * 接替原 tween(150)：冷启动停靠键盘晚到、与已开 50% 差值 > 48dp 时用。
     */
    val PadFast = spring<Float>(dampingRatio = 1f, stiffness = 3000f)

    /** Dock 卡片进场滑入：右下角／底部滑入。值不动。 */
    val DockEnter = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

    /** Dock 卡片退场滑出。值不动。 */
    val DockExit = spring<IntOffset>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)

    // ---- 淡入家族（可见性类， easing 一律 MotionTokens.Easing = EaseOutCubic） ----

    /** 面板／节点／卡片槽：250ms（= MotionTokens.Medium）。 */
    const val FadeStandard = 250

    /** 交接：宠物 alpha／Dock 卡片进场，180ms（两者对齐，交接不重影）。 */
    const val FadeHandoff = 180

    /** 行级：推荐行等，150ms（= MotionTokens.Fast）。 */
    const val FadeQuick = 150
}
