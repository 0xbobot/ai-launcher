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
    // 肩线弹性（2026-10-25）：Dock 图标点按时注视转向被点图标方位。
    GazeTarget.DOCK_LEFT -> GazeOffset(-1f, 0f)
    GazeTarget.DOCK_RIGHT -> GazeOffset(1f, 0f)
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

    /** 约 10% 概率连眨（与 BlinkVariety 的 DOUBLE 档同概率，单测沿用）。 */
    fun isDoubleBlink(random: Random): Boolean = random.nextFloat() < 0.10f

    /** alive-p2：约 12% 概率半眨（与 BlinkVariety 的 HALF 档同概率，单测沿用）。 */
    fun isHalfBlink(random: Random): Boolean = random.nextFloat() < 0.12f

    /** alive-p2：扫视出发瞬间约 30% 概率伴眨。 */
    fun isSaccadeBlink(random: Random): Boolean =
        random.nextFloat() < SACCADE_BLINK_PROB

    /**
     * eyes-alive：眨眼节律随状态变——专注态（FETCH/思考）少眨 4–8s，
     * 闲时沿用 2–6s；思考时可双眨沿用 requestBlink 的 10% 口径，不另开通道。
     */
    fun intervalMsForState(focused: Boolean, random: Random): Long =
        if (focused) 4_000L + (random.nextFloat() * 4_000L).toLong()
        else intervalMs(false, random)
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

// ------------------------------------------------------------------
// eyes-alive（2026-10-25）：瞳孔独立层 + 扫视节律 + 瞳孔大小 + 眼睑状态。
// 几何依据 glass-cut.png 离线测量（1043×1318）：眼内框 = 去 6px 棕圈后
// 的珠体开孔，左 l=0.2119 t=0.6548 r=0.3356 b=0.7792，
// 右 l=0.6654 t=0.6548 r=0.7862 b=0.7640；反光点上缘相对开孔高：
// 左 0.176、右主点 0.193——故闲时半阖取 0.15，既盖住珠体上缘 15%，
// 又不吞反光点（组合层 GLASS_EYE_OPENINGS 即此口径）。
// ------------------------------------------------------------------

/** 扫视节律：每 0.3–1.2s 一次快速小跳（120ms 到位）+ 随机停顿。 */
object SaccadePolicy {
    fun intervalMs(random: Random): Long =
        300L + (random.nextFloat() * 900f).toLong()

    /** 横向小跳：幅度 2–5px，方向随机。 */
    fun offsetX(random: Random): Float =
        (2f + random.nextFloat() * 3f) * if (random.nextBoolean()) 1f else -1f

    /** 纵向小跳：幅度 2–3px，方向随机（眼眶纵向更窄）。 */
    fun offsetY(random: Random): Float =
        (2f + random.nextFloat() * 1f) * if (random.nextBoolean()) 1f else -1f
}

/**
 * 瞳孔缩放：反应警觉 / 惊讶 / 开心放大 15%，专注 / 思考缩小 8%，闲时 1.0。
 * 组合层以 200ms tween 平滑过渡，不许跳变。
 */
fun eyePupilScale(mood: PetMood, thinking: Boolean, alert: Boolean): Float = when {
    alert -> 1.15f
    mood == PetMood.SURPRISED || mood == PetMood.HAPPY -> 1.15f
    mood == PetMood.FETCH || thinking -> 0.92f
    else -> 1.0f
}

/**
 * 眼睑开合（上睑盖住开孔高度的比例）：闲时半阖 0.15，
 * 专注 / 思考 / 惊讶 / 反应警觉全开，害羞深阖 0.30。
 * 眨眼时组合层另以 blinkLid 压到 1.0（全闭），取两者最大值。
 */
fun eyeLidFraction(mood: PetMood, thinking: Boolean, alert: Boolean): Float = when {
    alert -> 0f
    mood == PetMood.SURPRISED -> 0f
    mood == PetMood.FETCH || thinking -> 0f
    mood == PetMood.SHY -> 0.30f
    else -> 0.15f
}

// ------------------------------------------------------------------
// 活感第一批（2026-10-26，Bob 全部接受）：头弹簧＋接触影随动＋
// 注视与注意力＋待机小行为＋重心转移（不做纯缩放呼吸）。
// 纯逻辑层：区间、时序、概率收敛在这里，组合层只消费，不在组合代码里算。
// ------------------------------------------------------------------

/**
 * ① 头弹簧：整颗头作颈上弹簧质量。k ≈ 25/s²、阻尼比 0.3 →
 * 可见 2–3 次过冲（SpringSim 单测锁死）。复用现有头 offset／倾角通道
 * （头跟随 x ±8px、y ±5px，Compose spring(dampingRatio, stiffness)
 * 的刚度单位与此 k 同量纲：对 px 值动画，刚度 25 即 ω＝5rad/s）。
 */
object HeadSpring {
    const val STIFFNESS = 25f
    const val DAMPING_RATIO = 0.3f
    const val RANGE_X_PX = 8f
    const val RANGE_Y_PX = 5f
}

/** 瞳孔跟随范围（复用眼睛引擎口径）：开孔内 x ±5px、y ±3px。 */
const val PUPIL_RANGE_X_PX = 5f
const val PUPIL_RANGE_Y_PX = 3f

/**
 * 弹簧阶跃响应模拟（半隐式 Euler，调用方按帧 dt 推进）：
 * 只供单测验证 HeadSpring 参数的过冲次数与落定，不进生产动画
 * （生产动画走 Compose spring，参数同一套）。
 */
class SpringSim(private val stiffness: Float, dampingRatio: Float) {
    var x = 0f
        private set
    var v = 0f
        private set
    private val c = 2f * dampingRatio * kotlin.math.sqrt(stiffness)

    fun step(dt: Float, target: Float) {
        val a = stiffness * (target - x) - c * v
        v += a * dt
        x += v * dt
    }
}

/**
 * ③ 注视跟随触点：头转强度＝触点位移 × 0.35，钳制到头跟随范围；
 * 归一化后走现有注视通道（头＝偏移 × 8px／5px，瞳孔＝偏移 × 5px／3px）。
 */
object TouchGaze {
    const val FOLLOW_FACTOR = 0.35f
    /** 注视停顿门：位移 < 8px 且距上次更新 < 300ms 时不更新（防抽搐）。 */
    const val MIN_MOVE_PX = 8f
    const val MIN_INTERVAL_MS = 300L
    /** 松手后注视再持有 600ms（与瞥视同口径）再释放回底层目标。 */
    const val RELEASE_HOLD_MS = 600L

    /** 触点位移（相对头心，px）→ 归一化注视偏移（-1..1）。 */
    fun offsetFor(dxPx: Float, dyPx: Float): GazeOffset = GazeOffset(
        x = (dxPx * FOLLOW_FACTOR).coerceIn(
            -HeadSpring.RANGE_X_PX, HeadSpring.RANGE_X_PX
        ) / HeadSpring.RANGE_X_PX,
        y = (dyPx * FOLLOW_FACTOR).coerceIn(
            -HeadSpring.RANGE_Y_PX, HeadSpring.RANGE_Y_PX
        ) / HeadSpring.RANGE_Y_PX,
    )

    /** 停顿门：位移够大或间隔够久才更新。 */
    fun shouldUpdate(
        lastDxPx: Float, lastDyPx: Float,
        newDxPx: Float, newDyPx: Float,
        lastMs: Long, nowMs: Long,
    ): Boolean {
        val moved = kotlin.math.sqrt(
            (newDxPx - lastDxPx) * (newDxPx - lastDxPx) +
                (newDyPx - lastDyPx) * (newDyPx - lastDyPx)
        )
        return moved >= MIN_MOVE_PX || nowMs - lastMs >= MIN_INTERVAL_MS
    }
}

/** 瞳孔 clamp：基点扫视＋高频抖动叠加后仍钳制在开孔名义范围内，不许整颗滑出。 */
fun clampPupilOffset(dxPx: Float, dyPx: Float): GazeOffset = GazeOffset(
    x = dxPx.coerceIn(-PUPIL_RANGE_X_PX, PUPIL_RANGE_X_PX),
    y = dyPx.coerceIn(-PUPIL_RANGE_Y_PX, PUPIL_RANGE_Y_PX),
)

/** 眨眼多样化：正常眨、双眨（沿用 10%）、半眨（沿用 12%）、慢半眨。 */
enum class BlinkKind { FULL, DOUBLE, HALF, SLOW_HALF }

/** 眨眼时序与深度：深度 1.0＝全闭，0.45＝半闭（不触发闭合点头）。 */
data class BlinkTiming(
    val closeMs: Long,
    val holdMs: Long,
    val openMs: Long,
    val depth: Float,
)

object BlinkVariety {
    fun pick(random: Random): BlinkKind {
        val r = random.nextFloat()
        return when {
            r < 0.10f -> BlinkKind.DOUBLE
            r < 0.22f -> BlinkKind.HALF
            r < 0.30f -> BlinkKind.SLOW_HALF
            else -> BlinkKind.FULL
        }
    }

    fun timing(kind: BlinkKind): BlinkTiming = when (kind) {
        BlinkKind.FULL, BlinkKind.DOUBLE -> BlinkTiming(85, 70, 85, 1f)
        BlinkKind.HALF -> BlinkTiming(70, 50, 70, 0.45f)
        BlinkKind.SLOW_HALF -> BlinkTiming(120, 100, 120, 0.45f)
    }

    /** 双眨两眨之间的间隔（沿用 120ms）。 */
    const val DOUBLE_GAP_MS = 120L
}

/** ④ 待机小行为：偶尔歪头／环顾，交互开始立即取消（组合层状态复位）。 */
enum class IdleKind { TILT, LOOK }

object IdleBehavior {
    /** 触发间隔随机 20–60s（Bob 定）；行为后重新掷间隔，即冷却。 */
    fun intervalMs(random: Random): Long =
        20_000L + (random.nextFloat() * 40_000L).toLong()

    /** 歪头幅度 4°（好奇，弱于手动清单的 7–10°）。 */
    const val TILT_ANGLE_DEG = 4f
    fun tiltHoldMs(random: Random): Long =
        700L + (random.nextFloat() * 500L).toLong()

    /** 环顾持有 1.2s（与手动巡看同口径）。 */
    const val LOOK_HOLD_MS = 1200L

    fun pick(random: Random): IdleKind =
        if (random.nextBoolean()) IdleKind.TILT else IdleKind.LOOK
}

/** ② 接触影随动参数：影子由头状态驱动（x 跟头走，随高低变大小深浅）。 */
data class ShadowParams(val widthScale: Float, val alpha: Float)

object ContactShadow {
    /** 基线（评估口径）：宽 62.2dp、高 10dp、黑 14%，按宠物宽相对参考宽缩放。 */
    const val BASE_WIDTH_DP = 62.2f
    const val BASE_HEIGHT_DP = 10f
    const val BASE_ALPHA = 0.14f
    const val REF_PET_WIDTH_DP = 86.4f

    /**
     * liftPx：头抬高量（＝－头总位移 Y，上为正）。头抬高 1px → 影宽 ×0.98、
     * alpha −0.004；头压低反向；下压过冲（squash01，dip 动画 0..1）再加宽
     * 加深一档。全部钳制，影子永远比头动得小（不许影比头大）。
     */
    fun params(liftPx: Float, squash01: Float): ShadowParams {
        val widthScale = ((1f - 0.02f * liftPx).coerceIn(0.8f, 1.15f) *
            (1f + 0.10f * squash01.coerceIn(0f, 1f))).coerceAtMost(1.25f)
        val alpha = (BASE_ALPHA - 0.004f * liftPx +
            0.02f * squash01.coerceIn(0f, 1f)).coerceIn(0.08f, 0.20f)
        return ShadowParams(widthScale, alpha)
    }
}
