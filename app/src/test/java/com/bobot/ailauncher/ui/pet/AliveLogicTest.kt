package com.bobot.ailauncher.ui.pet

import com.bobot.ailauncher.data.PetRepository.GazeTarget
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
}
