package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.ui.theme.PetMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** 活感第一批（2026-10-26）纯逻辑单测：头弹簧、触点注视、瞳孔 clamp、眨眼多样化、待机（接触影已按 22:02 摘除，相关用例删除）。 */
class AliveBatch1Test {

    @Test
    fun `head spring parameters match approved spec`() {
        // 弹簧参数 canonical 在 PetMotion.HeadSoft（家族化后唯一事实源）；
        // 注视范围仍在 AliveLogic.HeadSpring。
        assertEquals(25f, PetMotion.HeadSoft.stiffness, 0f)
        assertEquals(0.3f, PetMotion.HeadSoft.dampingRatio, 0f)
        assertEquals(8f, HeadSpring.RANGE_X_PX, 0f)
        assertEquals(5f, HeadSpring.RANGE_Y_PX, 0f)
    }

    @Test
    fun `head spring step response overshoots visibly two to three times then settles`() {
        val sim = SpringSim(PetMotion.HeadSoft.stiffness, PetMotion.HeadSoft.dampingRatio)
        val target = 8f
        val dt = 1f / 60f
        val errors = mutableListOf<Float>()
        repeat(240) { // 4 秒
            sim.step(dt, target)
            errors.add(sim.x - target)
        }
        // 跳过初始趋近段（误差由 -8 升向 0），数此后符号段中峰值 > 0.3px 的过冲瓣。
        var i = 0
        while (i < errors.size && errors[i] < 0f) i++
        var lobes = 0
        var firstPeak = 0f
        while (i < errors.size) {
            val sign = if (errors[i] >= 0f) 1 else -1
            var peak = 0f
            var j = i
            while (j < errors.size && (if (errors[j] >= 0f) 1 else -1) == sign) {
                peak = maxOf(peak, abs(errors[j]))
                j++
            }
            if (peak > 0.3f) {
                if (lobes == 0) firstPeak = peak
                lobes++
            }
            i = j
        }
        assertTrue("overshoot lobes=$lobes", lobes in 2..3)
        // 首个过冲约 37%（ζ=0.3 理论值 2.97px），一眼可见但不翻车。
        assertTrue("first peak=$firstPeak", firstPeak in 2.0f..4.0f)
        // 4 秒内落定到 0.1px 内。
        assertTrue(abs(errors.last()) < 0.1f)
    }

    @Test
    fun `touch gaze head strength is 0 point 35 times displacement clamped to head range`() {
        // 100px 位移 × 0.35 = 35px → 钳制到头范围 8px → 归一化 1.0。
        assertEquals(1f, TouchGaze.offsetFor(100f, 0f).x, 0f)
        assertEquals(0.4375f, TouchGaze.offsetFor(10f, 0f).x, 0.001f)
        assertEquals(-0.875f, TouchGaze.offsetFor(-20f, 0f).x, 0.001f)
        // 纵向：20px × 0.35 = 7px → 钳制到 5px → 1.0。
        assertEquals(1f, TouchGaze.offsetFor(0f, 20f).y, 0f)
        assertEquals(0f, TouchGaze.offsetFor(0f, 0f).x, 0f)
        assertEquals(0f, TouchGaze.offsetFor(0f, 0f).y, 0f)
    }

    @Test
    fun `touch gaze dwell gate blocks small fast moves only`() {
        // 小位移＋短间隔：不更新（防抽搐）。
        assertTrue(!TouchGaze.shouldUpdate(0f, 0f, 5f, 0f, 1000L, 1100L))
        // 大位移＋短间隔：更新。
        assertTrue(TouchGaze.shouldUpdate(0f, 0f, 9f, 0f, 1000L, 1100L))
        // 小位移＋长间隔：更新（慢拖仍跟得上）。
        assertTrue(TouchGaze.shouldUpdate(0f, 0f, 5f, 0f, 1000L, 1400L))
    }

    @Test
    fun `pupil clamp keeps pupil inside nominal opening range`() {
        assertEquals(GazeOffset(5f, -3f), clampPupilOffset(10f, -5f))
        assertEquals(GazeOffset(3f, 2f), clampPupilOffset(3f, 2f))
        assertEquals(GazeOffset(-5f, 3f), clampPupilOffset(-8f, 4f))
    }

    @Test
    fun `idle behavior interval and tilt stay in approved ranges`() {
        val r = Random(7)
        repeat(200) {
            val ms = IdleBehavior.intervalMs(r)
            assertTrue(ms in 20_000L..60_000L)
        }
        repeat(200) {
            val hold = IdleBehavior.tiltHoldMs(r)
            assertTrue(hold in 700L..1200L)
        }
        assertEquals(4f, IdleBehavior.TILT_ANGLE_DEG, 0f)
        assertEquals(1200L, IdleBehavior.LOOK_HOLD_MS)
        val kinds = mutableSetOf<IdleKind>()
        repeat(200) { kinds.add(IdleBehavior.pick(r)) }
        assertEquals(setOf(IdleKind.TILT, IdleKind.LOOK), kinds)
    }

    @Test
    fun `blink variety covers all kinds and half blinks are shallower and slower`() {
        val kinds = mutableSetOf<BlinkKind>()
        val r = Random(42)
        repeat(500) { kinds.add(BlinkVariety.pick(r)) }
        // 固定种子序列 500 次抽选应覆盖四种（概率 10%/12%/8%/70%）。
        assertEquals(
            setOf(BlinkKind.FULL, BlinkKind.DOUBLE, BlinkKind.HALF, BlinkKind.SLOW_HALF),
            kinds,
        )
        val full = BlinkVariety.timing(BlinkKind.FULL)
        assertEquals(BlinkTiming(85, 70, 85, 1f), full)
        assertEquals(full, BlinkVariety.timing(BlinkKind.DOUBLE))
        val half = BlinkVariety.timing(BlinkKind.HALF)
        assertEquals(0.45f, half.depth, 0f)
        val slowHalf = BlinkVariety.timing(BlinkKind.SLOW_HALF)
        assertEquals(0.45f, slowHalf.depth, 0f)
        assertTrue(slowHalf.closeMs + slowHalf.holdMs + slowHalf.openMs >
            half.closeMs + half.holdMs + half.openMs)
        assertEquals(120L, BlinkVariety.DOUBLE_GAP_MS)
    }
}
