package com.bobot.ailauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobot.ailauncher.ui.theme.MotionTokens
import com.bobot.ailauncher.ui.theme.PetMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 输入态专注·进出过渡（2026-10-26 起，2026-10-27 竖屏区块切换重做）。
 *
 * 竖屏（2026-10-27，Bob 12:54 verdict：367 在真机上未过关，进出都闪）：
 * 区块离散切换——信息区、Dock 区、意图区三区块同帧整体显隐
 * （Enter/ExitTransition.None），不 stagger、不 crossfade 重叠、不走
 * funnel 行程、不做 70% 交接与落地溶解；宠物随区块显隐（主页属 Dock
 * 区、聚焦属意图区），不跨区块飞行。竖屏进出不再用本文件的行程／
 * 交接函数（已删除），只借 travel／travelAlpha 的终态值（竖屏进态
 * effect 直接 snap 到 1，供横竖屏旋转时横屏渲染读一致值）。
 *
 * 横屏（2026-10-26，Bob 22:57 最终口径，竖屏重做一字不动）：场景整体换
 * （先出后进），本文件只剩 beginExit／finishExit 作重入守卫与相位复位，
 * 以及横屏进态 effect 用的 animateEnter／freezeDelta／travel／travelAlpha。
 * [PetPerchTransitionState] 由 HomeScreen 持有（remember）。
 *
 * 责任边界（只动过渡状态与退出机制本身）：
 * - 不碰 Dock 几何（B2+A）、视频层应急开关、351 noRim 口径；
 * - 不碰卡片 Shape 与肩线几何（B 的活）、专注壳背景与隐藏清单（A 的活）；
 * - 不碰意图逻辑本身（提交、IME Done、拼音 composing、状态条）；
 * - 不另起第三套宠物渲染或响应路径：宠物本体仍是 Dock 那只 HybridPet，
 *   眼睛／眨眼／呼吸／注视全部复用 Dock 的活感引擎（见 IntentEntry /
 *   PullUpDock 的 HybridPet 调用点，本文件只驱动位移与 alpha）。
 *   影子已无：进出影子动画（550ms＋90ms 延迟进、300ms 退）按 Bob
 *   22:02 verdict 随接触影一并作废，本文件不再有影子通道。
 *
 * 竖屏旧四步退态时序（键盘落下→镜像走回→Dock 淡入→首页淡入，70%
 * 翻转点）已随竖屏区块切换删除；横屏退聚焦不走行程，见 HomeScreen
 * 横屏分支（beginExit／finishExit 仅借 isExiting 守卫与相位复位）。
 *
 * 减动效：横屏进态直接 snap（animateEnter 的 reduceMotion 分支）；
 * 竖屏离散切换本身无动画，减动效同一行为。
 *
 * 退出入口与 D 的 ✕ 接线（协调方集成时）：
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
 *   false，不经过退出入口——那是切屏不是专注退出，退态不与 D3 覆盖层打架；
 *   若横屏退态进行中途用户开了 D3，旧退态协程只是 harmless 地跑完
 *   （翻转调用幂等）；竖屏无在途退态协程。
 */
enum class PetPerch { DOCK, INTENT }

enum class PetPerchPhase {
    IDLE,
    ENTERING,
    FOCUSED,
    EXIT_KEYBOARD,
}

/**
 * 进出共用的过渡状态。Animatable 在构造时创建、由 HomeScreen 的 remember
 * 持有，整个输入态会话期间不重建；进态冻结的位移量（[travelDeltaX] /
 * [travelDeltaY]）只在横屏进态那一刻写入（竖屏离散切换不冻结、不读）。
 */
class PetPerchTransitionState {
    var phase: PetPerchPhase by mutableStateOf(PetPerchPhase.IDLE)
        private set
    var from: PetPerch by mutableStateOf(PetPerch.DOCK)
        private set
    var to: PetPerch by mutableStateOf(PetPerch.DOCK)
        private set

    /** 横屏退聚焦进行中（重入守卫与挂载门用）；竖屏离散切换不置此位。 */
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

    fun freezeDelta(deltaX: Dp, deltaY: Dp) {
        travelDeltaX = deltaX
        travelDeltaY = deltaY
    }

    /**
     * 横屏进态（竖屏进态 effect 不调本函数，直接 snap）：家族规格——
     * 位移走 PetMotion.EnterStandard（值与 355 一字不差，damping
     * 0.75），alpha 走 FadeHandoff 180ms＋EaseOutCubic（值不动、仅具名）。
     * 影子通道已随 22:02 摘除作废，不再有影子动画。
     */
    suspend fun animateEnter(reduceMotion: Boolean) {
        from = PetPerch.DOCK
        to = PetPerch.INTENT
        phase = PetPerchPhase.ENTERING
        if (reduceMotion) {
            travel.snapTo(1f)
            travelAlpha.snapTo(1f)
        } else {
            coroutineScope {
                launch { travel.animateTo(1f, PetMotion.EnterStandard) }
                launch {
                    travelAlpha.animateTo(
                        1f,
                        tween(PetMotion.FadeHandoff, easing = MotionTokens.Easing)
                    )
                }
            }
        }
        phase = PetPerchPhase.FOCUSED
    }

    fun beginExit() {
        if (isExiting) return
        isExiting = true
        from = PetPerch.INTENT
        to = PetPerch.DOCK
        phase = PetPerchPhase.EXIT_KEYBOARD
    }

    fun finishExit() {
        isExiting = false
        phase = PetPerchPhase.IDLE
    }
}


