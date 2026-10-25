package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.data.GazeTarget
import com.bobot.ailauncher.data.PetMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 七仔活物一期（alive-p1）纯逻辑单测：注视映射、眨眼调度、瞥视时序、游走区间。 */
class AliveLogicTest {

    @Test
    fun `gaze mapping covers all targets`() {
        assertEquals(GazeOffset(0f, 0f), gazeOffsetFor(GazeTarget.CENTER))
        assertEquals(GazeOffset(1f, 0f), gazeOffsetFor(GazeTarget.INPUT))
        assertEquals(GazeOffset(0f, 1f), gazeOffsetFor(GazeTarget.DOCK))
        // 肩线弹性：Dock 图标点按注视左右方位（纯水平，无上下分量）
        assertEquals(GazeOffset(-1f, 0f), gazeOffsetFor(GazeTarget.DOCK_LEFT))
        assertEquals(GazeOffset(1f, 0f), gazeOffsetFor(GazeTarget.DOCK_RIGHT))
        // 上方目标 y 为负（屏幕坐标下正），且带水平分量以区分两者
        assertTrue(gazeOffsetFor(GazeTarget.TODAY_CARD).y < 0f)
        assertTrue(gazeOffsetFor(GazeTarget.WEATHER_SCENE).y < 0f)
        assertTrue(
            gazeOffsetFor(GazeTarget.TODAY_CARD).x !=
                gazeOffsetFor(GazeTarget.WEATHER_SCENE).x
        )
    }

    @Test
    fun `patrol pool only semantic targets`() {
        val pool = patrolTargetPool()
        assertTrue(pool.isNotEmpty())
        assertTrue(pool.all { it != GazeTarget.CENTER && it != GazeTarget.INPUT })
    }

    @Test
    fun `speech display duration follows bubble formula`() {
        // 打字机 len*40 + 停留 2600 + 淡出 350
        assertEquals(2600L + 350L, speechDisplayMs(""))
        assertEquals(5 * 40L + 2600L + 350L, speechDisplayMs("12345"))
    }

    @Test
    fun `blink interval low power fixed at 12s`() {
        val r = Random(42)
        repeat(20) { assertEquals(12_000L, BlinkPolicy.intervalMs(true, r)) }
    }

    @Test
    fun `blink interval normal range 2 to 6 seconds`() {
        val r = Random(7)
        repeat(500) {
            val ms = BlinkPolicy.intervalMs(false, r)
            assertTrue(ms in 2_000L..6_000L)
        }
    }

    @Test
    fun `double blink rate near ten percent`() {
        val r = Random(1234)
        var doubles = 0
        repeat(10_000) { if (BlinkPolicy.isDoubleBlink(r)) doubles++ }
        assertTrue(doubles in 700..1300)
    }

    @Test
    fun `glance holds under 600ms then releases`() {
        val g = GlanceState()
        g.onGlance(GazeTarget.TODAY_CARD, 10_000L)
        assertEquals(GazeTarget.TODAY_CARD, g.current(10_599L))
        assertNull(g.current(10_600L))
        assertNull(g.current(20_000L))
    }

    @Test
    fun `glance refresh extends window`() {
        val g = GlanceState()
        g.onGlance(GazeTarget.DOCK, 0L)
        g.onGlance(GazeTarget.INPUT, 500L)
        assertEquals(GazeTarget.INPUT, g.current(1_000L))
        assertNull(g.current(1_100L))
    }

    @Test
    fun `wander period and amplitude within twenty percent`() {
        val r = Random(99)
        repeat(500) {
            val p = wanderPeriodMs(2400f, r)
            assertTrue(p in 1920f..2880f)
            val a = wanderAmp(r)
            assertTrue(a in 0.8f..1.2f)
        }
    }

    // ---------------- alive-p2（P0 程序化运动变换） ----------------

    @Test
    fun `breathe wave is bounded and hits exhale peak at 40 percent`() {
        val twoPi = (2 * Math.PI).toFloat()
        org.junit.Assert.assertEquals(0f, breatheWave(0f), 1e-4f)
        org.junit.Assert.assertEquals(1f, breatheWave(twoPi * BREATHE_INHALE_FRAC), 1e-4f)
        assertEquals(0.4f, BREATHE_INHALE_FRAC, 1e-6f)
        var i = 0
        while (i <= 100) {
            val w = breatheWave(twoPi * i / 100f)
            assertTrue(w in -1e-4f..1.0001f)
            i++
        }
    }

    @Test
    fun `breathe wave rises on inhale and falls on exhale`() {
        val twoPi = (2 * Math.PI).toFloat()
        var prev = breatheWave(0f)
        var i = 1
        while (i <= 40) { // 吸气段单调上升
            val w = breatheWave(twoPi * i / 100f)
            assertTrue("inhale monotone at $i", w >= prev - 1e-4f)
            prev = w
            i++
        }
        while (i < 100) { // 呼气段单调下降
            val w = breatheWave(twoPi * i / 100f)
            assertTrue("exhale monotone at $i", w <= prev + 1e-4f)
            prev = w
            i++
        }
    }

    @Test
    fun `breathe volume conservation constants`() {
        assertEquals(1.025f, BREATHE_SCALE_Y, 1e-6f)
        assertEquals(0.993f, BREATHE_SCALE_X, 1e-6f)
        assertEquals(2f, BOB_AMP_DP, 1e-6f) // 浮动从 -8dp 砍到 ±2dp
    }

    @Test
    fun `blink three phase timing totals 190ms`() {
        assertEquals(55, BLINK_CLOSE_MS)
        assertEquals(25, BLINK_HOLD_MS)
        assertEquals(110, BLINK_OPEN_MS)
        assertEquals(190, BLINK_CLOSE_MS + BLINK_HOLD_MS + BLINK_OPEN_MS)
        assertTrue(BLINK_HALF_TARGET > BLINK_FULL_TARGET)
    }

    @Test
    fun `half blink rate near twelve percent`() {
        val r = Random(4321)
        var halves = 0
        repeat(10_000) { if (BlinkPolicy.isHalfBlink(r)) halves++ }
        assertTrue("half blinks=$halves", halves in 900..1500)
    }

    @Test
    fun `saccade blink rate near thirty percent`() {
        val r = Random(999)
        var paired = 0
        repeat(10_000) { if (BlinkPolicy.isSaccadeBlink(r)) paired++ }
        assertTrue("saccade blinks=$paired", paired in 2700..3300)
    }

    @Test
    fun `saccade two phase spec`() {
        assertEquals(120, SACCADE_OUT_MS)
        assertEquals(90, SACCADE_SETTLE_MS)
        assertTrue(SACCADE_OVERSHOOT > 1f)
        assertTrue(SACCADE_OVERSHOOT <= 1.1f)
    }

    @Test
    fun `micro saccade interval 1_5 to 3s and offset within 1_5px`() {
        val r = Random(11)
        repeat(500) {
            val ms = MicroSaccade.intervalMs(r)
            assertTrue(ms in 1_500L..3_000L)
            val px = MicroSaccade.offsetPx(r)
            assertTrue(kotlin.math.abs(px) <= MICRO_SACCADE_RANGE_PX)
        }
    }

    @Test
    fun `sway spec matches design`() {
        assertEquals(1.2f, SWAY_AMP_DEG, 1e-6f)
        // 基线 6.75s 游走 ±20% → 5.4–8.1s（覆盖设计 5.5–8s）
        val r = Random(5)
        repeat(500) {
            val p = wanderPeriodMs(SWAY_PERIOD_BASE_MS, r)
            assertTrue(p in 5400f..8100f)
        }
    }

    @Test
    fun `weight shift ranges`() {
        val r = Random(31)
        repeat(500) {
            assertTrue(WeightShift.intervalMs(r) in 25_000L..50_000L)
            assertTrue(WeightShift.holdMs(r) in 2_000L..4_000L)
            assertTrue(kotlin.math.abs(WeightShift.angleDeg(r)) == 2.5f)
        }
    }

    @Test
    fun `fidget interval 20 to 45s`() {
        val r = Random(17)
        repeat(500) {
            val ms = FidgetPool.intervalMs(r)
            assertTrue(ms in 20_000L..45_000L)
        }
    }

    @Test
    fun `fidget pool quiets down the longer idle`() {
        val base = 30_000L
        assertEquals(base, FidgetPool.quietedIntervalMs(base, 0))
        assertTrue(FidgetPool.quietedIntervalMs(base, 20) > base)
        assertTrue(
            FidgetPool.quietedIntervalMs(base, 40) >
                FidgetPool.quietedIntervalMs(base, 20)
        )
        // 封顶 ×4
        assertEquals(base * 4, FidgetPool.quietedIntervalMs(base, 60))
        assertEquals(base * 4, FidgetPool.quietedIntervalMs(base, 600))
    }

    @Test
    fun `fidget pick covers all three kinds`() {
        val r = Random(23)
        val seen = mutableSetOf<Fidget>()
        repeat(300) { seen += FidgetPool.pick(r) }
        assertEquals(Fidget.values().toSet(), seen)
    }

    @Test
    fun `head tilt within 7 to 10 degrees and hold 0_7 to 1_2s`() {
        val r = Random(3)
        repeat(500) {
            val a = FidgetPool.headTiltAngleDeg(r)
            assertTrue(kotlin.math.abs(a) in 7f..10f)
            assertTrue(FidgetPool.headTiltHoldMs(r) in 700L..1_200L)
        }
    }

    @Test
    fun `squash depth follows mood`() {
        assertEquals(0.85f, squashDepth(PetMood.HAPPY, false), 1e-6f)
        assertEquals(0.95f, squashDepth(PetMood.IDLE, true), 1e-6f)
        assertEquals(0.88f, squashDepth(PetMood.IDLE, false), 1e-6f)
        assertEquals(0.88f, squashDepth(PetMood.FETCH, false), 1e-6f)
    }

    // eyes-alive：扫视节律、瞳孔大小、眼睑状态、眨眼节律随状态变。

    @Test
    fun `saccade interval 300 to 1200ms`() {
        val r = Random(11)
        repeat(500) {
            assertTrue(SaccadePolicy.intervalMs(r) in 300L..1_200L)
        }
    }

    @Test
    fun `saccade offsets within socket range`() {
        val r = Random(13)
        repeat(500) {
            val x = SaccadePolicy.offsetX(r)
            val y = SaccadePolicy.offsetY(r)
            assertTrue(kotlin.math.abs(x) in 2f..5f)
            assertTrue(kotlin.math.abs(y) in 2f..3f)
        }
    }

    @Test
    fun `pupil scale follows state`() {
        assertEquals(1.15f, eyePupilScale(PetMood.IDLE, false, true), 1e-6f)
        assertEquals(1.15f, eyePupilScale(PetMood.SURPRISED, false, false), 1e-6f)
        assertEquals(1.15f, eyePupilScale(PetMood.HAPPY, false, false), 1e-6f)
        assertEquals(0.92f, eyePupilScale(PetMood.FETCH, false, false), 1e-6f)
        assertEquals(0.92f, eyePupilScale(PetMood.IDLE, true, false), 1e-6f)
        assertEquals(1.0f, eyePupilScale(PetMood.IDLE, false, false), 1e-6f)
    }

    @Test
    fun `lid fraction follows state`() {
        assertEquals(0f, eyeLidFraction(PetMood.IDLE, false, true), 1e-6f)
        assertEquals(0f, eyeLidFraction(PetMood.SURPRISED, false, false), 1e-6f)
        assertEquals(0f, eyeLidFraction(PetMood.FETCH, false, false), 1e-6f)
        assertEquals(0f, eyeLidFraction(PetMood.IDLE, true, false), 1e-6f)
        assertEquals(0.30f, eyeLidFraction(PetMood.SHY, false, false), 1e-6f)
        // 闲时半阖 0.15：盖住珠体上缘 15%，又不吞反光点（上缘左 0.176/右 0.193）
        assertEquals(0.15f, eyeLidFraction(PetMood.IDLE, false, false), 1e-6f)
    }

    @Test
    fun `blink interval focused 4 to 8s idle 2 to 6s`() {
        val r = Random(17)
        repeat(500) {
            assertTrue(BlinkPolicy.intervalMsForState(true, r) in 4_000L..8_000L)
            assertTrue(BlinkPolicy.intervalMsForState(false, r) in 2_000L..6_000L)
        }
    }
}
