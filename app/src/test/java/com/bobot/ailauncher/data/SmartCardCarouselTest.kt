package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CAROUSEL_INTERVAL_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CROSSFADE_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CROSSFADE_REDUCED_MS
import com.bobot.ailauncher.ui.smartcard.carouselClampIndex
import com.bobot.ailauncher.ui.smartcard.carouselNextIndex
import com.bobot.ailauncher.ui.smartcard.carouselShouldAutoRotate
import com.bobot.ailauncher.ui.smartcard.carouselWaitMs
import com.bobot.ailauncher.ui.smartcard.crossfadeDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单卡槽位自动轮播纯函数单测（第三十二批 Hero 轮播立、第三十三批
 * Bob 2026-11-14 22:44 定单卡槽位自动轮播、无划走重做）。
 *
 * 锁死的是轮播下标／自动轮换／等待时长／淡入淡出四组纯函数：
 * - carouselNextIndex：自动轮换下一张，首尾相接循环；
 * - carouselShouldAutoRotate／carouselClampIndex：≥2 张才轮换、摘卡后下标钳制；
 * - carouselWaitMs：正常一档间隔，点圆点后等到恢复时刻；
 * - crossfadeDurationMs：淡入淡出时长选档（正常 300ms、减动效 120ms）。
 * 划走判定（shouldSkipCard／resolveSwipeRelease）、飞出时长
 * （flyOutDurationMs）、拖动揭示（swipeRevealProgress）已随第三十三批
 * 一并删掉（SmartCardSwipeTest 整个文件已删）。
 * 自动轮换计时（LaunchedEffect＋delay）、点圆点跳卡的接线、Crossfade
 * 本身是 Compose 运行时，JVM 测不到，靠代码走查＋Bob 真机验（自动轮换
 * 5 秒、圆点点按跳卡、「我知道了」摘卡进下一张、1 张不轮、0 张清空、
 * 淡入淡出不闪）。
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
        // 只有 1 张时没有下一张可切，下标恒为 0（不轮换、无圆点）。
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

    // ---------- 等待时长：正常一档，点圆点后等到恢复时刻 ----------

    @Test
    fun waitMs_noManualPause_isInterval() {
        // 没点过圆点（resumeAt 已过），等一档间隔 5 秒。
        assertEquals(
            SMART_CARD_CAROUSEL_INTERVAL_MS,
            carouselWaitMs(SMART_CARD_CAROUSEL_INTERVAL_MS, 0L, 10_000L)
        )
    }

    @Test
    fun waitMs_manualPausePending_waitsUntilResume() {
        // 点圆点后 2 秒（恢复时刻在 6 秒后），等 6 秒（不是 5 秒一档、也不是满 8 秒）。
        assertEquals(
            6_000L,
            carouselWaitMs(
                SMART_CARD_CAROUSEL_INTERVAL_MS,
                10_000L + SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS,
                10_000L + 2_000L
            )
        )
    }

    @Test
    fun waitMs_manualPauseAlmostOver_isInterval() {
        // 点圆点后 7 秒（恢复时刻在 1 秒后），等一档间隔 5 秒（不等 1 秒连转）。
        assertEquals(
            SMART_CARD_CAROUSEL_INTERVAL_MS,
            carouselWaitMs(
                SMART_CARD_CAROUSEL_INTERVAL_MS,
                10_000L + SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS,
                10_000L + 7_000L
            )
        )
    }

    // ---------- 第三十三批：摘卡后下标——摘掉当前卡进下一张 ----------

    @Test
    fun clampIndex_afterDismissTop_staysOnNextCard() {
        // 3 张、当前下标 1 的卡被「我知道了」摘掉，剩 2 张，下标 1 不动——
        // 顶上自然是下一张（摘卡后自动进到下一张），不是钳回末尾。
        assertEquals(1, carouselClampIndex(1, 2))
        assertEquals(0, carouselClampIndex(0, 2))
    }

    // ---------- 第三十三批：淡入淡出时长选档（crossfadeDurationMs） ----------
    // 单卡槽位切卡只用淡入淡出：正常 300ms 看得见切换、不拖沓；
    // 减动效 120ms 缩短但不硬切。

    @Test
    fun crossfadeDuration_normal_is300ms() {
        assertEquals(SMART_CARD_CROSSFADE_MS, crossfadeDurationMs(false))
        assertEquals(300, SMART_CARD_CROSSFADE_MS)
    }

    @Test
    fun crossfadeDuration_reducedMotion_is120ms() {
        assertEquals(SMART_CARD_CROSSFADE_REDUCED_MS, crossfadeDurationMs(true))
        assertEquals(120, SMART_CARD_CROSSFADE_REDUCED_MS)
    }

    @Test
    fun crossfadeDuration_reduced_isShorterButNotZero() {
        // 减动效缩短、但不直接硬切：时长必须大于 0 且小于正常档。
        val reduced = crossfadeDurationMs(true)
        assertTrue(reduced > 0)
        assertTrue(reduced < crossfadeDurationMs(false))
    }
}
