package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.smartcard.shouldSkipCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 横滑切卡判定单测（第二十三批，Bob 2026-11-13 横滑切卡 bug 的根因修法）。
 *
 * 锁死的是纯函数 shouldSkipCard（位移或速度任一过线就切卡）：
 * 位移阈值 96dp（由 120dp 放宽）、速度阈值 1000dp/s（快速轻扫）。
 * 手势本身（轴向仲裁、消费、跟手）是 Compose 指针处理，JVM 测不到，
 * 靠代码走查＋Bob 真机验（轻扫与慢拖都能切到下一张）。
 */
class SmartCardSwipeTest {

    // ---------- 位移过线就切（慢拖） ----------

    @Test
    fun displacement_overThreshold_skips() {
        // Bob 录屏里的划法：136dp，远超阈值。
        assertTrue(shouldSkipCard(offsetXDp = 136f, velocityXDps = 0f))
        assertTrue(shouldSkipCard(offsetXDp = 120f, velocityXDps = 0f))
        assertTrue(shouldSkipCard(offsetXDp = 97f, velocityXDps = 0f))
    }

    @Test
    fun displacement_negativeDirection_skips() {
        assertTrue(shouldSkipCard(offsetXDp = -136f, velocityXDps = 0f))
        assertTrue(shouldSkipCard(offsetXDp = -97f, velocityXDps = 0f))
    }

    @Test
    fun displacement_atOrUnderThreshold_doesNotSkip() {
        // 严格大于阈值才切：恰好 96dp 不切。
        assertFalse(shouldSkipCard(offsetXDp = 96f, velocityXDps = 0f))
        assertFalse(shouldSkipCard(offsetXDp = 50f, velocityXDps = 0f))
        assertFalse(shouldSkipCard(offsetXDp = 0f, velocityXDps = 0f))
        assertFalse(shouldSkipCard(offsetXDp = -96f, velocityXDps = 0f))
    }

    // ---------- 速度过线就切（快速轻扫，位移不到阈值也切） ----------

    @Test
    fun fling_overThreshold_skips() {
        assertTrue(shouldSkipCard(offsetXDp = 50f, velocityXDps = 1500f))
        assertTrue(shouldSkipCard(offsetXDp = 20f, velocityXDps = 2000f))
        assertTrue(shouldSkipCard(offsetXDp = 0f, velocityXDps = 1200f))
    }

    @Test
    fun fling_negativeDirection_skips() {
        assertTrue(shouldSkipCard(offsetXDp = -50f, velocityXDps = -1500f))
        assertTrue(shouldSkipCard(offsetXDp = 0f, velocityXDps = -1200f))
    }

    @Test
    fun fling_atOrUnderThreshold_doesNotSkip() {
        // 严格大于阈值才切：恰好 1000dp/s 不切。
        assertFalse(shouldSkipCard(offsetXDp = 50f, velocityXDps = 1000f))
        assertFalse(shouldSkipCard(offsetXDp = 50f, velocityXDps = 300f))
        assertFalse(shouldSkipCard(offsetXDp = 50f, velocityXDps = -1000f))
    }

    // ---------- 两项都不够就不切 ----------

    @Test
    fun neither_overThreshold_doesNotSkip() {
        assertFalse(shouldSkipCard(offsetXDp = 50f, velocityXDps = 300f))
        assertFalse(shouldSkipCard(offsetXDp = -50f, velocityXDps = -300f))
    }

    // ---------- 自定义阈值生效（调用处可调） ----------

    @Test
    fun customThresholds_areHonored() {
        assertTrue(
            shouldSkipCard(
                offsetXDp = 50f,
                velocityXDps = 0f,
                displacementThresholdDp = 40f
            )
        )
        assertFalse(
            shouldSkipCard(
                offsetXDp = 50f,
                velocityXDps = 0f,
                displacementThresholdDp = 60f
            )
        )
        assertTrue(
            shouldSkipCard(
                offsetXDp = 0f,
                velocityXDps = 500f,
                flingThresholdDps = 400f
            )
        )
        assertFalse(
            shouldSkipCard(
                offsetXDp = 0f,
                velocityXDps = 500f,
                flingThresholdDps = 600f
            )
        )
    }
}
