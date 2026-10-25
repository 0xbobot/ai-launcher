package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.data.GazeTarget
import com.bobot.ailauncher.data.PetMood
import kotlin.random.Random

/**
 * 七仔活物一期（alive-p1）纯逻辑层：不依赖 Compose/Android 框架，单测锁死。
 *
 * 组合层（PetView）只消费这里的映射与策略，不在组合代码里算区间与时序。
 */

/** 注视方向的二维偏移（-1..1）：x 右正，y 下正（屏幕坐标）。 */
data class GazeOffset(val x: Float, val y: Float)

/**
 * 注视目标 → 方向偏移。语义（输入态七仔坐胶囊极左为基准）：
 * INPUT 在右（胶囊本体方向）；TODAY 卡/天气场景区在上；Dock 在下。
 */
fun gazeOffsetFor(target: GazeTarget): GazeOffset = when (target) {
    GazeTarget.CENTER -> GazeOffset(0f, 0f)
    GazeTarget.INPUT -> GazeOffset(1f, 0f)
    GazeTarget.TODAY_CARD -> GazeOffset(-0.3f, -1f)
    GazeTarget.WEATHER_SCENE -> GazeOffset(0.3f, -1f)
    GazeTarget.DOCK -> GazeOffset(0f, 1f)
}

/** 巡看游离落点池：只从桌面语义点里选，不再纯随机坐标。 */
fun patrolTargetPool(): List<GazeTarget> =
    listOf(GazeTarget.TODAY_CARD, GazeTarget.WEATHER_SCENE, GazeTarget.DOCK)

/**
 * 气泡展示时长（SpeechBubble 口径：打字机 text.length*40 + 停留 2600ms +
 * 淡出 350ms）。say() 用它安排注视释放，避免气泡没渲染时注视被钉住。
 */
fun speechDisplayMs(text: String): Long = text.length * 40L + 2600L + 350L

/** 眨眼调度策略。 */
object BlinkPolicy {
    /** 低电量固定 12 秒（沿用现有口径）；常态 2–6 秒随机。 */
    fun intervalMs(lowPower: Boolean, random: Random): Long =
        if (lowPower) 12_000L else 2_000L + (random.nextFloat() * 4_000L).toLong()

    /** 约 10% 概率连眨。 */
    fun isDoubleBlink(random: Random): Boolean = random.nextFloat() < 0.10f

    /** alive-p2：约 12% 概率半眨（只收到 [BLINK_HALF_TARGET]，放松/困的前奏）。 */
    fun isHalfBlink(random: Random): Boolean = random.nextFloat() < 0.12f

    /** alive-p2：扫视出发瞬间约 30% 概率伴眨。 */
    fun isSaccadeBlink(random: Random): Boolean =
        random.nextFloat() < SACCADE_BLINK_PROB
}

/**
 * 瞥视时序状态：一次瞥视持有至多 600ms，到期自动释放；新瞥视刷新窗口。
 * 纯逻辑，组合层只按 current(nowMs) 渲染。
 */
class GlanceState(private val holdMs: Long = 600L) {
    private var target: GazeTarget? = null
    private var untilMs: Long = Long.MIN_VALUE

    fun onGlance(newTarget: GazeTarget, nowMs: Long) {
        target = newTarget
        untilMs = nowMs + holdMs
    }

    fun current(nowMs: Long): GazeTarget? {
        val t = target
        return if (t != null && nowMs < untilMs) t else null
    }
}

/** 游走周期：基线 ±20% 随机（打破严格正弦）。 */
fun wanderPeriodMs(baseMs: Float, random: Random): Float =
    baseMs * (0.8f + random.nextFloat() * 0.4f)

/** 游走幅度：1.0 ±20% 随机。 */
fun wanderAmp(random: Random): Float = 0.8f + random.nextFloat() * 0.4f

// ------------------------------------------------------------------
// alive-p2（七仔活物 P0 程序化运动变换）纯逻辑层。
// 设计依据《七仔视觉活化设计》§5 时序规格总表：所有区间、时序、概率
// 收敛在这里，组合层（PetView）只消费常量与策略，不在组合代码里算。
// ------------------------------------------------------------------

/** L0 呼吸：吸 40% / 呼 60% 非对称（严格 50/50 正弦是机器）。 */
const val BREATHE_INHALE_FRAC = 0.4f
/** 体积守恒：纵向鼓起时横向微收。 */
const val BREATHE_SCALE_Y = 1.025f
const val BREATHE_SCALE_X = 0.993f
/** 呼吸基线周期 4s，游走 ±20% → 3.2–4.8s。 */
const val BREATHE_PERIOD_BASE_MS = 4000f
/** 呼气段起点相位（2π × 吸占比）；姿态落地时呼吸相位重置到这里。 */
const val BREATHE_EXHALE_START_RAD = 2f * Math.PI.toFloat() * BREATHE_INHALE_FRAC
/** 浮动幅度：毛绒有重量，从玻璃头的 -8dp 砍到 ±2dp。 */
const val BOB_AMP_DP = 2f

/**
 * 呼吸波形：phaseRad∈[0,2π) → 肺活量∈[0,1]。
 * 前 40% 周期吸气（0→1），后 60% 呼气（1→0），两段半余弦拼接，
 * 端点一阶导均为 0，循环处无拐点。
 */
fun breatheWave(phaseRad: Float): Float {
    val twoPi = 2f * Math.PI.toFloat()
    val p = (((phaseRad % twoPi) + twoPi) % twoPi) / twoPi
    return if (p < BREATHE_INHALE_FRAC) {
        val u = p / BREATHE_INHALE_FRAC
        0.5f - 0.5f * kotlin.math.cos(Math.PI.toFloat() * u)
    } else {
        val q = (p - BREATHE_INHALE_FRAC) / (1f - BREATHE_INHALE_FRAC)
        0.5f + 0.5f * kotlin.math.cos(Math.PI.toFloat() * q)
    }
}

/** L0 眨眼三段时序：闭 55ms（FastOutLinear）→ 停 25ms → 开 110ms（FastOutSlowIn）。 */
const val BLINK_CLOSE_MS = 55
const val BLINK_HOLD_MS = 25
const val BLINK_OPEN_MS = 110
/** 全眨压到 0.08；半眨只收到 0.45（放松/困的前奏），同一眼盒压缩通道。 */
const val BLINK_FULL_TARGET = 0.08f
const val BLINK_HALF_TARGET = 0.45f

/** L2 瞳孔 saccade 两段式：120ms 冲到 106%，90ms 回落到 100%。 */
const val SACCADE_OUT_MS = 120
const val SACCADE_SETTLE_MS = 90
const val SACCADE_OVERSHOOT = 1.06f
/** 扫视伴眨：瞳孔出发瞬间 30% 概率带一次眨眼（转移视线有成本）。 */
const val SACCADE_BLINK_PROB = 0.30f

/** L1 重心摆动：rotationZ ±1.2° 绕脚底，基线 6.75s 游走 ±20% → 5.4–8.1s。 */
const val SWAY_PERIOD_BASE_MS = 6750f
const val SWAY_AMP_DEG = 1.2f

/** 微扫视：注视停留期间瞳孔在目标点 ±1.5px 内轻跳。 */
const val MICRO_SACCADE_RANGE_PX = 1.5f
const val MICRO_SACCADE_STEP_MS = 90

/** L1 重心转移：每 25–50s 换一只脚承重。 */
object WeightShift {
    fun intervalMs(random: Random): Long =
        25_000L + (random.nextFloat() * 25_000L).toLong()
    fun holdMs(random: Random): Long =
        2_000L + (random.nextFloat() * 2_000L).toLong()
    /** ±2.5°，spring（d0.6）回。 */
    fun angleDeg(random: Random): Float =
        if (random.nextBoolean()) 2.5f else -2.5f
}

/** 微扫视调度。 */
object MicroSaccade {
    fun intervalMs(random: Random): Long =
        1_500L + (random.nextFloat() * 1_500L).toLong()
    fun offsetPx(random: Random): Float =
        (random.nextFloat() * 2f - 1f) * MICRO_SACCADE_RANGE_PX
}

/** L3 微行为池：P0 三个（歪头 / 伸懒腰 / 抖毛）。 */
enum class Fidget { HEAD_TILT, STRETCH, SHAKE }

object FidgetPool {
    /** 每 20–45s 抽 1 个。 */
    fun intervalMs(random: Random): Long =
        20_000L + (random.nextFloat() * 25_000L).toLong()

    /**
     * idle 越久池子越安静：每闲置 20 分钟间隔 ×1，最多 ×4。
     * 冷落感来自行为频率本身，不需要新表情。
     */
    fun quietedIntervalMs(baseMs: Long, minutesIdle: Long): Long {
        val factor = 1f + (minutesIdle / 20f).coerceAtMost(3f)
        return (baseMs * factor).toLong()
    }

    fun pick(random: Random): Fidget =
        Fidget.values()[random.nextInt(Fidget.values().size)]

    /** 歪头：绕头部 pivot (0.5, 0.28) rotationZ 7–10°，hold 0.7–1.2s，spring d0.5 回。 */
    const val HEAD_PIVOT_Y = 0.28f
    fun headTiltAngleDeg(random: Random): Float =
        (7f + random.nextFloat() * 3f) * if (random.nextBoolean()) 1f else -1f
    fun headTiltHoldMs(random: Random): Long =
        700L + (random.nextFloat() * 500L).toLong()

    /** 伸懒腰：scaleY 1.05 / scaleX 0.97 绕底，260ms 上，hold 300ms，spring 回。 */
    const val STRETCH_UP_MS = 260
    const val STRETCH_HOLD_MS = 300L
    const val STRETCH_SCALE_Y = 1.05f
    const val STRETCH_SCALE_X = 0.97f

    /** 抖毛：rotationZ ±1.5° ×3 次，每次 90ms（复用 shyTremble 思路）。 */
    const val SHAKE_STEP_MS = 90
    const val SHAKE_AMP_DEG = 1.5f
    const val SHAKE_REPS = 3
}

/**
 * 姿态切图 squash 深度随情绪：HAPPY 0.85 跳得高，困时 0.95 没力气，
 * 其余沿用 v0.29.1 的 0.88。切图藏在 squash 最低点那一帧（组合层负责时机）。
 */
fun squashDepth(mood: PetMood, sleepy: Boolean): Float = when {
    mood == PetMood.HAPPY -> 0.85f
    sleepy -> 0.95f
    else -> 0.88f
}
