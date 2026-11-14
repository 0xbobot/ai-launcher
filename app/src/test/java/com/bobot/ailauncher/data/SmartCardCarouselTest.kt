package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CAROUSEL_INTERVAL_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS
import com.bobot.ailauncher.ui.smartcard.carouselClampIndex
import com.bobot.ailauncher.ui.smartcard.carouselNextIndex
import com.bobot.ailauncher.ui.smartcard.carouselShouldAutoRotate
import com.bobot.ailauncher.ui.smartcard.carouselWaitMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hero 轮播纯函数单测（第三十二批，Bob 2026-11-14 20:49 定：叠层改 Hero 轮播）。
 *
 * 锁死的是轮播下标／自动轮换／等待时长三组纯函数：
 * - carouselNextIndex：下一张／上一张，首尾相接循环；
 * - carouselShouldAutoRotate／carouselClampIndex：≥2 张才轮换、摘卡后下标钳制；
 * - carouselWaitMs：正常一档间隔，手动切后等到恢复时刻。
 * 手势本身（轴向仲裁、消费、跟手）与自动轮换计时（LaunchedEffect＋delay）
 * 是 Compose 运行时，JVM 测不到，靠代码走查＋Bob 真机验（自动轮换、圆点、
 * 左右滑切卡、划不摘卡、「我知道了」才摘卡、1 张不轮）。
 */
class SmartCardCarouselTest {

    // ---------- 下一张／上一张：首尾相接循环 ----------

    @Test
    fun nextIndex_forward_advances() {
        assertEquals(1, carouselNextIndex(0, 3, +1))
        assertEquals(2, carouselNextIndex(1, 3, +1))
    }

    @Test
    fun nextIndex_forward_wrapsFromLastToFirst() {
        assertEquals(0, carouselNextIndex(2, 3, +1))
    }

    @Test
    fun nextIndex_backward_goesToPrevious() {
        assertEquals(0, carouselNextIndex(1, 3, -1))
        assertEquals(2, carouselNextIndex(0, 3, -1))
    }

    @Test
    fun nextIndex_backward_wrapsFromFirstToLast() {
        assertEquals(2, carouselNextIndex(0, 3, -1))
    }

    @Test
    fun nextIndex_twoCards_toggles() {
        assertEquals(1, carouselNextIndex(0, 2, +1))
        assertEquals(0, carouselNextIndex(1, 2, +1))
        assertEquals(1, carouselNextIndex(0, 2, -1))
        assertEquals(0, carouselNextIndex(1, 2, -1))
    }

    @Test
    fun nextIndex_singleCard_staysAtZero() {
        // 只有 1 张时没有下一张可切，下标恒为 0（划过阈值也弹回，不飞出）。
        assertEquals(0, carouselNextIndex(0, 1, +1))
        assertEquals(0, carouselNextIndex(0, 1, -1))
    }

    @Test
    fun nextIndex_empty_staysAtZero() {
        assertEquals(0, carouselNextIndex(0, 0, +1))
        assertEquals(0, carouselNextIndex(0, 0, -1))
    }

    // ---------- 自动轮换：≥2 张才轮 ----------

    @Test
    fun shouldAutoRotate_twoOrMore_true() {
        assertTrue(carouselShouldAutoRotate(2))
        assertTrue(carouselShouldAutoRotate(3))
        assertTrue(carouselShouldAutoRotate(10))
    }

    @Test
    fun shouldAutoRotate_zeroOrOne_false() {
        assertFalse(carouselShouldAutoRotate(0))
        assertFalse(carouselShouldAutoRotate(1))
    }

    // ---------- 下标钳制：摘卡后列表缩短 ----------

    @Test
    fun clampIndex_inRange_unchanged() {
        assertEquals(0, carouselClampIndex(0, 3))
        assertEquals(2, carouselClampIndex(2, 3))
    }

    @Test
    fun clampIndex_outOfRange_clampsToLast() {
        // 3 张、下标 2 的顶卡被摘掉，列表剩 2 张，下标 2 越界钳回 1（末尾）。
        assertEquals(1, carouselClampIndex(2, 2))
        assertEquals(0, carouselClampIndex(5, 1))
    }

    @Test
    fun clampIndex_empty_isZero() {
        assertEquals(0, carouselClampIndex(0, 0))
        assertEquals(0, carouselClampIndex(3, 0))
    }

    // ---------- 等待时长：正常一档，手动切后等到恢复时刻 ----------

    @Test
    fun waitMs_noManualPause_isInterval() {
        // 没手动切过（resumeAt 已过），等一档间隔 5 秒。
        assertEquals(
            SMART_CARD_CAROUSEL_INTERVAL_MS,
            carouselWaitMs(SMART_CARD_CAROUSEL_INTERVAL_MS, 0L, 10_000L)
        )
    }

    @Test
    fun waitMs_manualPausePending_waitsUntilResume() {
        // 手动切完 2 秒（恢复时刻在 6 秒后），等 8 秒（不是 5 秒一档）。
        assertEquals(
            SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS,
            carouselWaitMs(
                SMART_CARD_CAROUSEL_INTERVAL_MS,
                10_000L + SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS,
                10_000L + 2_000L
            )
        )
    }

    @Test
    fun waitMs_manualPauseAlmostOver_isInterval() {
        // 手动切完 7 秒（恢复时刻在 1 秒后），等一档间隔 5 秒（不等 1 秒连转）。
        assertEquals(
            SMART_CARD_CAROUSEL_INTERVAL_MS,
            carouselWaitMs(
                SMART_CARD_CAROUSEL_INTERVAL_MS,
                10_000L + SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS,
                10_000L + 7_000L
            )
        )
    }
}
