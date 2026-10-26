package com.bobot.ailauncher.ui.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 拖拽一体拉伸（2026-10-26 定稿，22:10 后肩升锁定模型）纯 dp 映射单测：阻力曲线、肩锁定、横向、倾角、形变。 */
class DragBodyTest {

    private val eps = 0.05f

    private fun approx(expected: Float, actual: Float) {
        assertTrue("expected ≈ $expected but was $actual", kotlin.math.abs(expected - actual) <= eps)
    }

    @Test
    fun `head rise is zero at and below zero pull`() {
        assertEquals(0f, dragHeadRiseDp(0f), 0f)
        assertEquals(0f, dragHeadRiseDp(-10f), 0f)
    }

    @Test
    fun `head rise starts about one to one and grows with resistance`() {
        // 起步约 1:1（37·tanh(1/37) ≈ 1.0）
        approx(1.0f, dragHeadRiseDp(1f))
        // 20dp 手指处约 18.2dp（37·tanh(20/37)）
        approx(18.27f, dragHeadRiseDp(20f))
        // 40dp 手指处约 29.4dp（37·tanh(40/37)），即定稿“约 30dp”
        approx(29.37f, dragHeadRiseDp(40f))
    }

    @Test
    fun `head rise rubber bands beyond 40dp and hard caps at 32dp`() {
        // 40dp 后只加 0.2 倍：40→29.37，53→29.37+0.2×13≈31.97
        approx(31.97f, dragHeadRiseDp(53f))
        // 60dp 时 29.37+0.2×20=33.37 → 封顶 32
        assertEquals(32f, dragHeadRiseDp(60f), 0f)
        assertEquals(32f, dragHeadRiseDp(200f), 0f)
    }

    @Test
    fun `head rise is monotone`() {
        var prev = -1f
        for (up in 0..200 step 5) {
            val rise = dragHeadRiseDp(up.toFloat())
            assertTrue("rise($up)=$rise should exceed prev=$prev", rise >= prev)
            prev = rise
        }
    }

    @Test
    fun `shoulder locks to head rise one to one and never opens a gap`() {
        assertEquals(0f, dragShoulderRiseDp(0f), 0f)
        approx(29.37f, dragShoulderRiseDp(29.37f))
        assertEquals(32f, dragShoulderRiseDp(32f), 0f)
        // 锁定不变量（22:10 后模型，脖子带已摘）：肩升恒等于头升，头底与
        // 肩峰的 5dp 压入全程恒定，任何帧、任何速度都不出现 wallpaper
        // 夹缝——由构造保证，不靠画带子 bridging。
        for (up in 0..200 step 5) {
            val rise = dragHeadRiseDp(up.toFloat())
            val shoulder = dragShoulderRiseDp(rise)
            assertEquals(rise, shoulder, 0f)
        }
    }

    @Test
    fun `lateral moves clamp at 40dp radius with head 0 8 and shoulder 0 45`() {
        approx(16f, dragHeadXDps(20f))
        assertEquals(32f, dragHeadXDps(40f), 0f)
        assertEquals(32f, dragHeadXDps(100f), 0f)
        assertEquals(-32f, dragHeadXDps(-100f), 0f)
        approx(9f, dragShoulderXDps(20f))
        assertEquals(18f, dragShoulderXDps(40f), 0f)
        assertEquals(-18f, dragShoulderXDps(-100f), 0f)
    }

    @Test
    fun `tilt reaches 5 degrees at full shoulder travel and clamps`() {
        assertEquals(0f, dragTiltDeg(0f), 0f)
        approx(2.5f, dragTiltDeg(9f))
        assertEquals(5f, dragTiltDeg(18f), 0f)
        assertEquals(5f, dragTiltDeg(36f), 0f)
        assertEquals(-5f, dragTiltDeg(-18f), 0f)
    }

    @Test
    fun `stretch scale lengthens head up and narrows it`() {
        val (sx0, sy0) = dragStretchScale(0f, 0f)
        assertEquals(1f, sx0, 0f)
        assertEquals(1f, sy0, 0f)
        // 满拉伸：scaleY 1.06、scaleX 0.97
        val (sxFull, syFull) = dragStretchScale(32f, 32f)
        approx(0.97f, sxFull)
        approx(1.06f, syFull)
        // 半拉伸：scaleY 1.03、scaleX 0.985
        val (sxHalf, syHalf) = dragStretchScale(16f, 32f)
        approx(0.985f, sxHalf)
        approx(1.03f, syHalf)
    }

    @Test
    fun `release overshoot squashes head and never divides by zero`() {
        // 松手时头升 30dp → 过冲峰值约 20% = 6dp 下压 → 压扁满幅
        val (sx, sy) = dragStretchScale(-6f, 30f)
        approx(1.03f, sx)
        approx(0.94f, sy)
        // 半过冲 → 半压扁
        val (sxHalf, syHalf) = dragStretchScale(-3f, 30f)
        approx(1.015f, sxHalf)
        approx(0.97f, syHalf)
        // 松手时头升为 0（极端）：分母保底 1dp，不炸、钳制满幅
        val (sxZero, syZero) = dragStretchScale(-6f, 0f)
        approx(1.03f, sxZero)
        approx(0.94f, syZero)
    }
}
