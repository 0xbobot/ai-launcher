package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.data.PetRepository.GazeTarget
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

    fun current(nowMs: Long): GazeTarget? =
        if (target != null && nowMs < untilMs) target else null
}

/** 游走周期：基线 ±20% 随机（打破严格正弦）。 */
fun wanderPeriodMs(baseMs: Float, random: Random): Float =
    baseMs * (0.8f + random.nextFloat() * 0.4f)

/** 游走幅度：1.0 ±20% 随机。 */
fun wanderAmp(random: Random): Float = 0.8f + random.nextFloat() * 0.4f
