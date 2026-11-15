package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CAROUSEL_INTERVAL_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CROSSFADE_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CROSSFADE_REDUCED_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_DRAG_MAX_FRACTION
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_DRAG_RELEASE_FRACTION
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_FLING_VELOCITY_DP_PER_SEC
import com.bobot.ailauncher.ui.smartcard.carouselClampIndex
import com.bobot.ailauncher.ui.smartcard.carouselDragOffsetClamp
import com.bobot.ailauncher.ui.smartcard.carouselNextIndex
import com.bobot.ailauncher.ui.smartcard.carouselReleaseDecision
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
 * 第三十八批：跟手拖动的松手判定（carouselReleaseDecision）与位移夹子
 * （carouselDragOffsetClamp）是纯函数，单测锁死；手势接线
 * （pointerInput＋VelocityTracker→jumpTo）与点按／竖翻
 * 的互不抢占是 Compose 运行时，JVM 测不到，靠代码走查＋Bob 真机验
 * （拖动跟手、松手换卡／回弹、滑完暂停 8 秒、按钮照常可点、长文竖翻照常）。
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

    // ---------- 第三十八批：跟手拖动（拖动跟随＋松手判定） ----------
    // 第三十七批的 48dp 触发作废：拖动过程中顶卡横向位移跟手指走
    // （1:1，夹到半张卡宽内）；松手判定（carouselReleaseDecision）——
    // 位移超 1/3 卡宽或松手速度够就换卡（左滑＝下一张、右滑＝上一张），
    // 否则回弹；换卡走 jumpTo（carouselNextIndex 改下标＋暂停 8 秒）＋
    // Crossfade，与点圆点同一条链。不做飞出／滑动／揭示。
    // 卡宽取 300f（阈值：1/3＝100f、半张＝150f）、速度线取 500f。

    @Test
    fun releaseDecision_distancePastThird_isNext() {
        assertEquals(+1, carouselReleaseDecision(-100f, 300f, 0f, 500f))
        assertEquals(+1, carouselReleaseDecision(-150f, 300f, 0f, 500f))
    }

    @Test
    fun releaseDecision_distancePastThird_isPrevious() {
        assertEquals(-1, carouselReleaseDecision(100f, 300f, 0f, 500f))
        assertEquals(-1, carouselReleaseDecision(150f, 300f, 0f, 500f))
    }

    @Test
    fun releaseDecision_belowThirdLowVelocity_isSpringBack() {
        assertEquals(0, carouselReleaseDecision(-99f, 300f, 0f, 500f))
        assertEquals(0, carouselReleaseDecision(99f, 300f, 0f, 500f))
        assertEquals(0, carouselReleaseDecision(0f, 300f, 0f, 500f))
    }

    @Test
    fun releaseDecision_fastFling_switchesWithoutDistance() {
        // 轻扫：位移没过线、速度过线也换卡；速度刚好在线下不换。
        assertEquals(+1, carouselReleaseDecision(-10f, 300f, -500f, 500f))
        assertEquals(-1, carouselReleaseDecision(10f, 300f, 500f, 500f))
        assertEquals(0, carouselReleaseDecision(0f, 300f, -499f, 500f))
        assertEquals(0, carouselReleaseDecision(0f, 300f, 499f, 500f))
    }

    @Test
    fun releaseDecision_distanceBeatsConflictingVelocity() {
        // 距离优先：拖过 1/3 后松手往反方向带，仍按位移方向换卡。
        assertEquals(+1, carouselReleaseDecision(-150f, 300f, 900f, 500f))
        assertEquals(-1, carouselReleaseDecision(150f, 300f, -900f, 500f))
    }

    @Test
    fun releaseDecision_zeroWidth_isSpringBack() {
        // 卡宽没量到就不换卡（不除零、不误判）。
        assertEquals(0, carouselReleaseDecision(-150f, 0f, 0f, 500f))
    }

    @Test
    fun releaseDecision_usesSameNextIndexAsDotTap() {
        // 松手换卡后走 carouselNextIndex：左滑 +1、右滑 −1，首尾循环，
        // 与点圆点跳卡同一条换卡链（jumpTo）。
        assertEquals(1, carouselNextIndex(0, 3, carouselReleaseDecision(-150f, 300f, 0f, 500f)))
        assertEquals(2, carouselNextIndex(0, 3, carouselReleaseDecision(150f, 300f, 0f, 500f)))
        assertEquals(0, carouselNextIndex(2, 3, carouselReleaseDecision(-150f, 300f, 0f, 500f)))
    }

    @Test
    fun dragOffsetClamp_withinHalfWidth_unchanged() {
        assertEquals(100f, carouselDragOffsetClamp(100f, 300f), 0.001f)
        assertEquals(-100f, carouselDragOffsetClamp(-100f, 300f), 0.001f)
    }

    @Test
    fun dragOffsetClamp_beyondHalfWidth_clamped() {
        // 拖过半张卡宽：卡停在半张处，不飞出屏。
        assertEquals(150f, carouselDragOffsetClamp(200f, 300f), 0.001f)
        assertEquals(-150f, carouselDragOffsetClamp(-200f, 300f), 0.001f)
    }

    @Test
    fun dragOffsetClamp_zeroWidth_isZero() {
        assertEquals(0f, carouselDragOffsetClamp(100f, 0f), 0.001f)
    }

    @Test
    fun dragConstants_matchDesign() {
        // 距离线 1/3 卡宽、夹子半张卡宽、速度线 500dp/s。
        assertEquals(1f / 3f, SMART_CARD_DRAG_RELEASE_FRACTION, 0.001f)
        assertEquals(0.5f, SMART_CARD_DRAG_MAX_FRACTION, 0.001f)
        assertEquals(500, SMART_CARD_FLING_VELOCITY_DP_PER_SEC)
    }
}
