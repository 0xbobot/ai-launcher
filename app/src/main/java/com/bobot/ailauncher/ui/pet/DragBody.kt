package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.tanh

/**
 * 拖拽一体拉伸（2026-10-26 Bob 定稿，mock 参数落地）。
 *
 * 模型：面板底部钉在 Dock 上不动；往上拉时整个身体纵向拉伸——头升得最多，
 * 肩线以 0.55 倍量、约 90ms 滞后跟上，面板纵向拉长；头底与肩峰之间用一根
 * 上窄下宽、随拉伸收窄的“脖子”连接带填充，全程永不断开（消灭旧版
 * “头被拉离面板”分离 bug）。头升恒 ≥ 肩升（0.55 < 1），故连接不变量
 * 在映射层面即成立；可见间隙 = neckGap − press（press = 5dp，脚底压入
 * 肩峰量），间隙 ≤ 0 时头压进肩峰、同样不断开。
 *
 * 映射（纯 dp 函数，可单测）：
 * - 上拉：h(d) = 37·tanh(d/37)（起步约 1:1 跟手、越拉阻力越大；40dp 手指
 *   处约 29.4dp）；超 40dp 后只加 0.2 倍橡皮筋；硬封顶 32dp，不脱离、
 *   不飞走。横向与下压：40dp 拖拽半径内硬钳制（头 0.8×、肩 0.45×、
 *   下压 ≤ 40dp）；上拉超 40dp 不走硬钳制、走橡皮筋（mock 定稿行为）。
 * - 松手：弹簧 stiffness = 90、dampingRatio = 0.45（可见约 20% 过冲、
 *   约 0.9s 落定）；过冲时头压扁（scaleY → 0.94）承载挤压感——不新加
 *   接触影：Dock 宠物路径本无接触影 drawable，且 Bob 有“全程无脚下阴影”
 *   铁律（效果太假）。
 *
 * 状态机：头量在拖拽中直接写 State（跟手精确、无排序风险）；松手由
 * releaseJob 用 animate() 逐帧写回 0（弹簧过冲自然经过负值 → 压扁）；
 * 肩量由 FloatingPet 的 LaunchedEffect 经 snapshotFlow 滞后追踪头量
 * （critically damped、stiffness 550，约 90ms 达 63%），拖拽与松手
 * 过冲全程跟随——连接处不焊死（头先行、肩延迟、落回带过冲）。
 * 减动效不门禁拖拽：直接操纵＋弹簧回位是操纵反馈，不是环境动画。
 */

/** 上拉行程（dp）→ 头升（dp）。 */
fun dragHeadRiseDp(upDp: Float): Float {
    if (upDp <= 0f) return 0f
    val riseAt40 = 37f * tanh(40f / 37f) // ≈ 29.4dp
    val rise = if (upDp <= 40f) 37f * tanh(upDp / 37f)
        else riseAt40 + (upDp - 40f) * 0.2f
    return rise.coerceAtMost(32f)
}

/** 头升 → 肩升（0.55 倍量）。 */
fun dragShoulderRiseDp(headRiseDp: Float): Float = 0.55f * headRiseDp

/** 头升 → 头底与肩峰间隙（dp）；恒 ≥ 0，故永不断开。 */
fun dragNeckGapDp(headRiseDp: Float): Float =
    (headRiseDp - dragShoulderRiseDp(headRiseDp)).coerceAtLeast(0f)

/** 横向行程（dp）→ 头横移（0.8×，±40dp 半径内钳制）。 */
fun dragHeadXDps(latDp: Float): Float = 0.8f * latDp.coerceIn(-40f, 40f)

/** 横向行程（dp）→ 肩横移（0.45×，±40dp 半径内钳制）。 */
fun dragShoulderXDps(latDp: Float): Float = 0.45f * latDp.coerceIn(-40f, 40f)

/** 肩横移（dp）→ 整体倾角（度）；0.45×40 = 18dp 为满幅 5°。 */
fun dragTiltDeg(shoulderXDps: Float): Float =
    5f * (shoulderXDps / 18f).coerceIn(-1f, 1f)

/**
 * 头升 → 头形变（scaleX, scaleY）。
 * 拉伸：scaleY 1 → 1.06、scaleX 1 → 0.97（按头升 / 32dp 线性）；
 * 松手过冲下压（rise < 0）：scaleY → 0.94、scaleX → 1.03，分母取
 * 0.2 × 松手时头升（过冲峰值约 20%），下压量 0 时分母保底 1dp 避免除零。
 */
fun dragStretchScale(headRiseDp: Float, releaseStartRiseDp: Float): Pair<Float, Float> {
    return if (headRiseDp >= 0f) {
        val f = (headRiseDp / 32f).coerceIn(0f, 1f)
        Pair(1f - 0.03f * f, 1f + 0.06f * f)
    } else {
        val denom = maxOf(1f, 0.2f * releaseStartRiseDp)
        val f = (-headRiseDp / denom).coerceIn(0f, 1f)
        Pair(1f + 0.03f * f, 1f - 0.06f * f)
    }
}

/** 拖拽一体拉伸状态机：由 PullUpDock remember、透传进 FloatingPet。 */
class DragBodyState(private val scope: CoroutineScope) {
    /** 头升（px）：拖拽中直接写，松手由 releaseJob 弹簧写回 0。 */
    var headRisePx by mutableFloatStateOf(0f)
        private set

    /** 头横移（px）：拖拽中直接写，松手弹簧回 0。 */
    var headXPx by mutableFloatStateOf(0f)
        private set

    /** 头下沉（px）：下压行程直接写，松手弹簧回 0（头沉入面板，无分离问题）。 */
    var headSinkPx by mutableFloatStateOf(0f)
        private set

    /** 肩升（px）：由 FloatingPet 的 LaunchedEffect 滞后追踪头升。 */
    var shoulderRisePx by mutableFloatStateOf(0f)

    /** 肩横移（px）：同上，滞后追踪头横移的 0.5625 倍（= 0.45 × 手指）。 */
    var shoulderXPx by mutableFloatStateOf(0f)

    /** 是否正在拖拽中。 */
    var dragging by mutableStateOf(false)
        private set

    /** 松手时头升（dp）：供过冲压扁分母用。 */
    var releaseStartRiseDp by mutableFloatStateOf(0f)
        private set

    private var releaseJob: Job? = null

    fun onDragStart() {
        releaseJob?.cancel()
        releaseJob = null
        dragging = true
    }

    /** 拖拽跟手：头量直接写 State（映射本身即阻力曲线，不再二次平滑）。 */
    fun onDragMove(headRiseTargetPx: Float, headXTargetPx: Float, headSinkTargetPx: Float) {
        headRisePx = headRiseTargetPx
        headXPx = headXTargetPx
        headSinkPx = headSinkTargetPx
    }

    /** 松手：三量并行弹簧回 0（dampingRatio 0.45、stiffness 90，约 20% 过冲）。 */
    fun onDragEnd(headRiseDp: Float) {
        dragging = false
        releaseStartRiseDp = headRiseDp
        releaseJob?.cancel()
        val spec = spring<Float>(dampingRatio = 0.45f, stiffness = 90f)
        releaseJob = scope.launch {
            coroutineScope {
                launch { animate(headRisePx, 0f, animationSpec = spec) { v, _ -> headRisePx = v } }
                launch { animate(headXPx, 0f, animationSpec = spec) { v, _ -> headXPx = v } }
                launch { animate(headSinkPx, 0f, animationSpec = spec) { v, _ -> headSinkPx = v } }
            }
        }
    }
}

/** 肩滞后弹簧：critically damped、stiffness 550，约 90ms 达目标的 63%。 */
fun dragShoulderLagSpring() = spring<Float>(
    dampingRatio = 1f,
    stiffness = 550f,
)

/** 肩追踪系数（无单位，px 量直接相乘）：肩升 = 0.55 × 头升、肩横移 = 0.5625 × 头横移。 */
object DragBodyLag {
    const val SHOULDER_RISE_RATIO = 0.55f
    const val SHOULDER_X_RATIO = 0.5625f // 0.45 / 0.8：肩横移 = 0.5625 × 头横移
}
