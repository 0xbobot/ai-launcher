package com.bobot.ailauncher.ui.pet

import androidx.compose.animation.core.animate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bobot.ailauncher.ui.theme.PetMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.tanh

/**
 * 拖拽一体拉伸（2026-10-26 Bob 定稿，mock 参数落地；22:10 脖子带摘除后模型）。
 *
 * 模型：面板底部钉在 Dock 上不动；往上拉时整个身体纵向拉伸——头与肩
 * 同频：垂直方向肩升锁定等于头升（1:1，无滞后），压入 5dp 全程恒定，
 * 任何帧、任何速度都不出现 wallpaper 夹缝（由构造保证，非调参保证）；
 * “头略先行、肩跟上”落在水平与倾角通道（肩横移经家族弹簧滞后跟随头
 * 横移，倾角由肩横移导出故自然滞后）与松手共用释放弹簧＋压扁挤压上。
 * 垂直方向若也做滞后，快拖时滞后量会超出 5dp 压入量而开缝，故锁定是
 * 唯一无跳变保证；若 Bob 想要垂直方向可见的先行（压入量内 2–3dp 的
 * 伸缩感），那是后续调参项，本批不做。
 * 脖子连接带已按 Bob 22:10 纠正整个摘除（绘制、塞缝、间隙门限全删）：
 * 连接感由运动本身给，不由另加视觉元素给；359 的“脖子带永不断开”
 * 设计就此作废（要求保留、手段作废）。
 *
 * 映射（纯 dp 函数，可单测）：
 * - 上拉：h(d) = 37·tanh(d/37)（起步约 1:1 跟手、越拉阻力越大；40dp 手指
 *   处约 29.4dp）；超 40dp 后只加 0.2 倍橡皮筋；硬封顶 32dp，不脱离、
 *   不飞走。横向与下压：40dp 拖拽半径内硬钳制（头 0.8×、肩 0.45×、
 *   下压 ≤ 40dp）；上拉超 40dp 不走硬钳制、走橡皮筋（mock 定稿行为）。
 * - 松手：家族弹簧 PetMotion.DragRelease（dampingRatio 0.45、
 *   stiffness 90，可见约 20% 过冲、约 0.9s 落定）；过冲时头压扁
 *   （scaleY → 0.94）承载挤压感，肩经锁定与头同量下压——接触区挤压
 *   由头肩同步下压＋头压扁共同给。不新加接触影：Bob 有“全程无脚下
 *   阴影”铁律（22:02 恢复完整效力，面板面接触影亦禁）。
 *
 * 状态机：头量在拖拽中直接写 State（跟手精确、无排序风险）；松手由
 * releaseJob 用 animate() 逐帧写回 0（弹簧过冲自然经过负值 → 压扁）；
 * 肩升由 FloatingPet 的 LaunchedEffect 经 snapshotFlow 直接锁定头升
 * （同帧赋值、无弹簧）；肩横移仍经家族弹簧滞后追踪头横移的 0.5625 倍
 * （critically damped、stiffness 550，约 90ms 达 63%），拖拽与松手
 * 过冲全程跟随——连接处不焊死（水平头先行、肩延迟、落回带过冲）。
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

/**
 * 头升 → 肩升（锁定 1:1，2026-10-26 22:10 后模型）。
 * 肩升恒等于头升，头底与肩峰的 5dp 压入全程恒定，故任何拖拽帧都不
 * 出现头身分开的视觉——不靠画带子 bridging，靠锁定本身保证。
 */
fun dragShoulderRiseDp(headRiseDp: Float): Float = headRiseDp

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

    /** 肩升（px）：由 FloatingPet 的 LaunchedEffect 直接锁定头升（1:1）。 */
    var shoulderRisePx by mutableFloatStateOf(0f)

    /** 肩横移（px）：由 FloatingPet 的 LaunchedEffect 滞后追踪头横移的 0.5625 倍（= 0.45 × 手指）。 */
    var shoulderXPx by mutableFloatStateOf(0f)

    /** 是否正在拖拽中。 */
    var dragging by mutableStateOf(false)
        private set

    /**
     * 提拉换档观察（2026-10-27）：仅 Dock 侧 PullUpDock 设置，
     * FloatingPet 在 Dock 拉伸路径里顺带回调（起拖／竖向行程／松手）。
     * 意图侧实例恒为 null = 纯拉伸，不受换档影响。
     * 普通 var（非 State）：接线方在组合里赋值幂等、不触发重组。
     */
    var onLiftStart: (() -> Unit)? = null
    var onLiftMove: ((upDp: Float, downDp: Float) -> Unit)? = null
    var onLiftEnd: (() -> Unit)? = null

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

    /** 松手：三量并行弹簧回 0（家族弹簧 PetMotion.DragRelease，约 20% 过冲）。 */
    fun onDragEnd(headRiseDp: Float) {
        dragging = false
        releaseStartRiseDp = headRiseDp
        releaseJob?.cancel()
        val spec = PetMotion.DragRelease
        releaseJob = scope.launch {
            coroutineScope {
                launch { animate(headRisePx, 0f, animationSpec = spec) { v, _ -> headRisePx = v } }
                launch { animate(headXPx, 0f, animationSpec = spec) { v, _ -> headXPx = v } }
                launch { animate(headSinkPx, 0f, animationSpec = spec) { v, _ -> headSinkPx = v } }
            }
        }
    }
}

/**
 * 肩横移追踪系数（无单位，px 量直接相乘）：肩横移 = 0.5625 × 头横移
 * （= 0.45 / 0.8）。肩升不再用系数（锁定 1:1，见上）。
 * 滞后弹簧本身走家族 PetMotion.ShoulderLag，此处不再自造规格。
 */
object DragBodyLag {
    const val SHOULDER_X_RATIO = 0.5625f // 0.45 / 0.8：肩横移 = 0.5625 × 头横移
}
