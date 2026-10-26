package com.bobot.ailauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 输入态专注·进出过渡 C（2026-10-26，设计文档 input-focus-design.md §4）。
 *
 * 退态反向位移缺口的闭合点：355 的退场是“HomeScreen fadeOut＋Dock 卡片重进”
 * 各自为政——意图宠物随槽位淡出消失、Dock 宠物随卡片重进出现，两段动画无
 * 交接、宠物没有“走回去”的过程。本文件把进出两路收进同一个状态：
 * [PetPerchTransitionState] 由 HomeScreen 持有（remember），进态与退态共用
 * 同一组 Animatable、同一弹簧、同一份进态冻结的位移量；退态 = 位移取负
 * （travel 1→0 时附加 offset = 冻结 delta × (1−travel)，自然回到 Dock 方向），
 * 不是第二套动画。
 *
 * 责任边界（只动过渡状态与退出 funnel 本身）：
 * - 不碰 Dock 几何（B2+A）、视频层应急开关、351 noRim 口径；
 * - 不碰卡片 Shape 与肩线几何（B 的活）、专注壳背景与隐藏清单（A 的活）；
 * - 不碰意图逻辑本身（提交、IME Done、拼音 composing、状态条）；
 * - 不另起第三套宠物渲染或响应路径：宠物本体仍是 Dock 那只 HybridPet，
 *   眼睛／眨眼／呼吸／注视全部复用 Dock 的活感引擎（见 IntentEntry /
 *   PullUpDock 的 HybridPet 调用点，本文件只驱动位移与 alpha/影子）。
 *
 * 四步退态时序（写死在 HomeScreen.requestExitFocus，不许打乱）：
 *  ① 键盘落下：清焦点＋收 IME，卡片＋宠物组跟键盘一起落下（组内相对位置
 *    不动——组锚定 IME 顶边是 A 的专注壳的活，本文件只保证退态在键盘落下
 *    后才起步，见 requestExitFocus 的 IME 归零等待，上限 350ms）；
 *  ② 宠物离开卡片肩峰，沿镜像路径走回 Dock 肩峰（本文件的 animateExitTravel）；
 *  ③ Dock 卡片在宠物落点下方淡入（150ms，宠物落地时淡入完成，宠物最后
 *    落地、不悬空——淡入由 intentInputMode 翻转驱动 PullUpDock 的 cardVisible
 *    门禁，翻转点定在行程 70%，见 exitDockFadeShouldStart；注意：PullUpDock
 *    的卡片 enter fadeIn 实测是 180ms，不是 150ms，HomeScreen 的意图宠物
 *    alpha 交接按 180ms 对齐 Dock 实测值，30ms 差值留给协调方定夺是否改
 *    Dock enter 规格，本批不擅自改 Dock 的进场规格）；
 *  ④ 首页内容淡入（同样由 mode 翻转驱动 PetZone inputActive 回落，翻转点
 *    即行程 70%，与落地交叠，不出现“宠物已走、内容未回”的空窗）。
 *
 * 减动效：进出皆直接 snap（355 已有口径，专注态沿用），见 animateEnter /
 * animateExitTravel 的 reduceMotion 分支。
 *
 * 退出 funnel 与 D 的 ✕ 接线（协调方集成时）：
 * - HomeScreen 内唯一的退出入口是它创建并向下传的 onExitFocusRequest
 *   lambda（= requestExitFocus）；Back、点卡片外、落定、滞留兜底、点本地
 *   结果打开 App 全部改走它，不再直接调 onExitIntentInput。
 * - D 的 ✕ 一键退出只调这个入口（协调方须把 HomeScreen 传下的
 *   IntentEntry.onExitFocusRequest 再透传给专注卡片的 ✕ 按钮），不自写
 *   退出动画、不直接调 onExitIntentInput、不各自保留一套 fade。
 * - ✕ 的门禁与 Back 同款：intentStateHoldsInput 为真时不启用（思考／回复／
 *   确认／追问／权限在场时退出走各自收起路径；否则 MainScreen 的
 *   “在场状态自动带起”effect 会把刚翻转的 mode 又翻回 true，造成进出打架）。
 * - 旁路（本批不动，协调方知晓）：MainScreen 上滑开 D3 时直接把 mode 置
 *   false，不经过 funnel——那是切屏不是专注退出，退态位移不与 D3 覆盖层打架；
 *   若退态进行中途用户开了 D3，旧退态协程只是 harmless 地跑完（翻转调用幂等）。
 */
enum class PetPerch { DOCK, INTENT }

enum class PetPerchPhase {
    IDLE,
    ENTERING,
    FOCUSED,
    EXIT_KEYBOARD,
    EXIT_TRAVEL,
    EXIT_DOCK_FADE,
}

/**
 * 进出共用的过渡状态。Animatable 在构造时创建、由 HomeScreen 的 remember
 * 持有，整个输入态会话期间不重建；进态冻结的位移量（[travelDeltaX] /
 * [travelDeltaY]）只在进态那一刻写入，退态只读不重算。
 */
class PetPerchTransitionState {
    var phase: PetPerchPhase by mutableStateOf(PetPerchPhase.IDLE)
        private set
    var from: PetPerch by mutableStateOf(PetPerch.DOCK)
        private set
    var to: PetPerch by mutableStateOf(PetPerch.DOCK)
        private set

    /** 退态进行中：HomeScreen 的槽位挂载条件（mode || isExiting）据此保持。 */
    var isExiting: Boolean by mutableStateOf(false)
        private set

    /** 进态冻结的位移量：Dock 脚底 → 意图卡片肩峰（dp，见 IntentEntry 的估算）。 */
    var travelDeltaX: Dp by mutableStateOf(0.dp)
        private set
    var travelDeltaY: Dp by mutableStateOf(0.dp)
        private set

    /** 0 = Dock perch，1 = 意图卡片 perch；附加 offset = delta × (1 − travel)。 */
    val travel = Animatable(0f)
    val travelAlpha = Animatable(0f)
    val shadowTravel = Animatable(0f)

    fun freezeDelta(deltaX: Dp, deltaY: Dp) {
        travelDeltaX = deltaX
        travelDeltaY = deltaY
    }

    /** 进态：与 355 一字不差——弹簧 damping 0.75、alpha 180ms、影子晚 90ms。 */
    suspend fun animateEnter(reduceMotion: Boolean) {
        from = PetPerch.DOCK
        to = PetPerch.INTENT
        phase = PetPerchPhase.ENTERING
        if (reduceMotion) {
            travel.snapTo(1f)
            travelAlpha.snapTo(1f)
            shadowTravel.snapTo(1f)
        } else {
            coroutineScope {
                launch {
                    travel.animateTo(
                        1f,
                        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
                    )
                }
                launch { travelAlpha.animateTo(1f, tween(180)) }
                launch { shadowTravel.animateTo(1f, tween(550, delayMillis = 90)) }
            }
        }
        phase = PetPerchPhase.FOCUSED
    }

    /**
     * 退态行程：同一弹簧、同一冻结位移量取负（travel 1→0）。
     * 影子先离地（300ms 淡出）——进态“身体先到、影子后跟”的镜像。
     * alpha 不在此处动：它由 HomeScreen 在 Dock 淡入步里单独走 180ms，
     * 与 Dock 卡片 fadeIn 交叠为一次交接（意图宠物淡出、Dock 宠物淡入），
     * 不出现两只实体的静止并存。
     */
    suspend fun animateExitTravel(reduceMotion: Boolean) {
        phase = PetPerchPhase.EXIT_TRAVEL
        if (reduceMotion) {
            travel.snapTo(0f)
            shadowTravel.snapTo(0f)
        } else {
            coroutineScope {
                launch { shadowTravel.animateTo(0f, tween(300)) }
                launch {
                    travel.animateTo(
                        0f,
                        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
                    )
                }
            }
        }
    }

    fun beginExit() {
        if (isExiting) return
        isExiting = true
        from = PetPerch.INTENT
        to = PetPerch.DOCK
        phase = PetPerchPhase.EXIT_KEYBOARD
    }

    fun markDockFade() {
        phase = PetPerchPhase.EXIT_DOCK_FADE
    }

    fun finishExit() {
        isExiting = false
        phase = PetPerchPhase.IDLE
    }
}

/** 退态行程进度：travel 1→0 对应进度 0→1（纯函数，单测锁死）。 */
fun exitTravelProgress(travelValue: Float): Float =
    (1f - travelValue).coerceIn(0f, 1f)

/**
 * Dock 淡入／首页淡入触发点：行程过 70%（travel ≤ 0.30）时开始。
 * 触发动作是 intentInputMode 翻转（Dock cardVisible 门禁＋PetZone
 * inputActive 回落同由它驱动），故 Dock 淡入与首页淡入同点起步、与
 * 宠物落地交叠（纯函数，单测锁死）。
 */
fun exitDockFadeShouldStart(travelValue: Float): Boolean = travelValue <= 0.30f
