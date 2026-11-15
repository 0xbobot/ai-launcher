package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CAROUSEL_INTERVAL_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CAROUSEL_MANUAL_PAUSE_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CROSSFADE_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_CROSSFADE_REDUCED_MS
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_DRAG_MAX_FRACTION
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_DRAG_RELEASE_FRACTION
import com.bobot.ailauncher.ui.smartcard.SMART_CARD_FLING_VELOCITY_DP_PER_SEC
import com.bobot.ailauncher.ui.smartcard.carouselClampIndex
import com.bobot.ailauncher.ui.smartcard.carouselDotTapDir
import com.bobot.ailauncher.ui.smartcard.carouselDragOffsetClamp
import com.bobot.ailauncher.ui.smartcard.carouselDragUnderOffset
import com.bobot.ailauncher.ui.smartcard.carouselNextIndex
import com.bobot.ailauncher.ui.smartcard.carouselReleaseDecision
import com.bobot.ailauncher.ui.smartcard.carouselShouldAutoRotate
import com.bobot.ailauncher.ui.smartcard.carouselSlideInOffset
import com.bobot.ailauncher.ui.smartcard.carouselSlideOutAlpha
import com.bobot.ailauncher.ui.smartcard.carouselSlideOutOffset
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
 * 锁死的是轮播下标／自动轮换／等待时长／切卡滑动时长四组纯函数：
 * - carouselNextIndex：自动轮换下一张，首尾相接循环；
 * - carouselShouldAutoRotate／carouselClampIndex：≥2 张才轮换、摘卡后下标钳制；
 * - carouselWaitMs：正常一档间隔，点圆点后等到恢复时刻；
 * - crossfadeDurationMs：切卡滑动时长选档（正常 300ms、减动效 120ms；
 *   第四十批起 Crossfade 已撤，现为滑动时长，函数名与常量名沿用）。
 * 划走判定（shouldSkipCard／resolveSwipeRelease）、飞出时长
 * （flyOutDurationMs）、拖动揭示（swipeRevealProgress）已随第三十三批
 * 一并删掉（SmartCardSwipeTest 整个文件已删）。
 * 自动轮换计时（LaunchedEffect＋delay）、点圆点跳卡的接线、切卡滑动
 * 本身是 Compose 运行时，JVM 测不到，靠代码走查＋Bob 真机验（自动轮换
 * 5 秒、圆点点按跳卡、「我知道了」摘卡进下一张、1 张不轮、0 张清空、
 * 切卡滑动不闪）。第四十批：Crossfade 已撤——点圆点／自动轮换也不再
 * 直接改下标触发淡入淡出，一律走 startSlideSwitch 一张接一张滑动；
 * 点圆点方向（carouselDotTapDir）是纯函数，单测锁死。
 * 第三十八批：跟手拖动的松手判定（carouselReleaseDecision）与位移夹子
 * （carouselDragOffsetClamp）是纯函数，单测锁死；手势接线
 * （pointerInput＋VelocityTracker→jumpTo）与点按／竖翻
 * 的互不抢占是 Compose 运行时，JVM 测不到，靠代码走查＋Bob 真机验
 * （拖动跟手、松手换卡／回弹、滑完暂停 8 秒、按钮照常可点、长文竖翻照常）。
 * 第三十九批：松手判定距离线由 1/3 放宽到 1/4 卡宽；滑出／滑入位移
 * （carouselSlideOutOffset／carouselSlideInOffset）与滑出透明度
 * （carouselSlideOutAlpha）是纯函数，单测锁死。
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

    // ---------- 第四十批：点圆点跳卡的滑动方向（carouselDotTapDir） ----------

    @Test
    fun dotTapDir_twoCards_alwaysForward() {
        // 2 张时点的那张一定是循环下一张（+1，左滑入）。
        assertEquals(+1, carouselDotTapDir(0, 1, 2))
        assertEquals(+1, carouselDotTapDir(1, 0, 2))
    }

    @Test
    fun dotTapDir_nextCard_forward() {
        // 3 张：点循环下一张就 +1（左滑入）。
        assertEquals(+1, carouselDotTapDir(0, 1, 3))
        assertEquals(+1, carouselDotTapDir(2, 0, 3))
    }

    @Test
    fun dotTapDir_previousCard_backward() {
        // 3 张：点循环上一张就 −1（右滑入）。
        assertEquals(-1, carouselDotTapDir(1, 0, 3))
        assertEquals(-1, carouselDotTapDir(0, 2, 3))
    }

    @Test
    fun dotTapDir_nonAdjacent_takesShortestRingDirection() {
        // 4 张：第 0 张点第 2 张——顺时针两步、逆时针两步一样远，
        // 按规则取 −1（不是循环下一张就 −1）；第 0 张点第 3 张是
        // 循环上一张，−1。
        assertEquals(-1, carouselDotTapDir(0, 2, 4))
        assertEquals(-1, carouselDotTapDir(0, 3, 4))
        assertEquals(+1, carouselDotTapDir(0, 1, 4))
    }

    @Test
    fun dotTapDir_singleCard_returnsZero() {
        // 只有 0／1 张时没有下一张可切，返回 0（调用方不调）。
        assertEquals(0, carouselDotTapDir(0, 0, 1))
        assertEquals(0, carouselDotTapDir(0, 0, 0))
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

    // ---------- 第三十三批：切卡滑动时长选档（crossfadeDurationMs） ----------
    // 单卡槽位切卡只用淡入淡出：正常 300ms 看得见切换、不拖沓；
    // 减动效 120ms 缩短但不硬切。第四十批：Crossfade 已撤，现为一张接一张
    // 的滑动时长；函数名与常量名沿用。

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
    // 位移超 1/4 卡宽或松手速度够就换卡（左滑＝下一张、右滑＝上一张），
    // 否则回弹；换卡一张接一张（第三十九批：出卡继续滑出、进卡从反方向
    // 滑入，收尾 jumpTo 与点圆点同一条链）。不做飞出／揭示。
    // 卡宽取 300f（阈值：1/4＝75f、半张＝150f）、速度线取 500f。
    // 第三十九批：滑出／滑入位移（carouselSlideOutOffset／
    // carouselSlideInOffset）与滑出透明度（carouselSlideOutAlpha）
    // 是纯函数，单测锁死；滑动切换的接线（startSlideSwitch＋
    // suppressCrossfade）是 Compose 运行时，JVM 测不到，靠代码走查＋
    // Bob 真机验（一张接一张、松手换卡／回弹、滑完暂停 8 秒）。

    @Test
    fun releaseDecision_distancePastQuarter_isNext() {
        assertEquals(+1, carouselReleaseDecision(-76f, 300f, 0f, 500f))
        assertEquals(+1, carouselReleaseDecision(-150f, 300f, 0f, 500f))
    }

    @Test
    fun releaseDecision_distancePastQuarter_isPrevious() {
        assertEquals(-1, carouselReleaseDecision(76f, 300f, 0f, 500f))
        assertEquals(-1, carouselReleaseDecision(150f, 300f, 0f, 500f))
    }

    @Test
    fun releaseDecision_belowQuarterLowVelocity_isSpringBack() {
        assertEquals(0, carouselReleaseDecision(-74f, 300f, 0f, 500f))
        assertEquals(0, carouselReleaseDecision(74f, 300f, 0f, 500f))
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
        // 距离优先：拖过 1/4 后松手往反方向带，仍按位移方向换卡。
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
        // 距离线 1/4 卡宽（第三十九批由 1/3 放宽）、夹子半张卡宽、速度线 500dp/s。
        assertEquals(1f / 4f, SMART_CARD_DRAG_RELEASE_FRACTION, 0.001f)
        assertEquals(0.5f, SMART_CARD_DRAG_MAX_FRACTION, 0.001f)
        assertEquals(500, SMART_CARD_FLING_VELOCITY_DP_PER_SEC)
    }

    // ---------- 第三十九批：一张接一张（滑出／滑入位移＋滑出透明度） ----------
    // 出卡从松手位移出发、沿滑动方向滑到 ±1 卡宽外（轻微淡出 1→0.6），
    // 进卡从反方向 ±1 卡宽外滑入到中心；两张同一条 300ms 时间线。
    // 卡宽取 300f：左滑（dir +1）出卡终点 −300f、进卡起点 ＋300f；
    // 右滑（dir −1）出卡终点 ＋300f、进卡起点 −300f。

    @Test
    fun slideOutOffset_leftSwipe_goesToMinusOneWidth() {
        // 左滑：松手位移 −100f → 进度 0 在松手处、进度 1 到 −300f。
        assertEquals(-100f, carouselSlideOutOffset(0f, -100f, +1, 300f), 0.001f)
        assertEquals(-200f, carouselSlideOutOffset(0.5f, -100f, +1, 300f), 0.001f)
        assertEquals(-300f, carouselSlideOutOffset(1f, -100f, +1, 300f), 0.001f)
    }

    @Test
    fun slideOutOffset_rightSwipe_goesToPlusOneWidth() {
        // 右滑：松手位移 ＋100f → 进度 0 在松手处、进度 1 到 ＋300f。
        assertEquals(100f, carouselSlideOutOffset(0f, 100f, -1, 300f), 0.001f)
        assertEquals(200f, carouselSlideOutOffset(0.5f, 100f, -1, 300f), 0.001f)
        assertEquals(300f, carouselSlideOutOffset(1f, 100f, -1, 300f), 0.001f)
    }

    @Test
    fun slideOutOffset_zeroWidth_staysAtReleaseOffset() {
        // 卡宽没量到就不滑（不动，等收尾 snapTo 0）。
        assertEquals(-100f, carouselSlideOutOffset(1f, -100f, +1, 0f), 0.001f)
    }

    @Test
    fun slideInOffset_leftSwipe_comesFromPlusOneWidth() {
        // 左滑：进卡从右侧 ＋300f 处进来，进度 1 到中心 0。
        assertEquals(300f, carouselSlideInOffset(0f, +1, 300f), 0.001f)
        assertEquals(150f, carouselSlideInOffset(0.5f, +1, 300f), 0.001f)
        assertEquals(0f, carouselSlideInOffset(1f, +1, 300f), 0.001f)
    }

    @Test
    fun slideInOffset_rightSwipe_comesFromMinusOneWidth() {
        // 右滑：进卡从左侧 −300f 处进来，进度 1 到中心 0。
        assertEquals(-300f, carouselSlideInOffset(0f, -1, 300f), 0.001f)
        assertEquals(-150f, carouselSlideInOffset(0.5f, -1, 300f), 0.001f)
        assertEquals(0f, carouselSlideInOffset(1f, -1, 300f), 0.001f)
    }

    @Test
    fun slideInOffset_zeroWidth_isZero() {
        assertEquals(0f, carouselSlideInOffset(1f, +1, 0f), 0.001f)
    }

    @Test
    fun slideOutAlpha_slightFadeOnly() {
        // 轻微淡出：起点 1、终点 0.6，淡出是配角、位移是主角。
        assertEquals(1f, carouselSlideOutAlpha(0f), 0.001f)
        assertEquals(0.8f, carouselSlideOutAlpha(0.5f), 0.001f)
        assertEquals(0.6f, carouselSlideOutAlpha(1f), 0.001f)
    }

    @Test
    fun slideInOffset_withReleaseOffset_startsShiftedByReleaseOffset() {
        // 第四十一批：手动滑动时进卡起点带上松手位移——左滑松手位移 −100f，
        // 进卡从 300−100＝200f 处进来；右滑松手位移 ＋100f，从 −300＋100＝−200f 处进来。
        assertEquals(200f, carouselSlideInOffset(0f, +1, 300f, -100f), 0.001f)
        assertEquals(100f, carouselSlideInOffset(0.5f, +1, 300f, -100f), 0.001f)
        assertEquals(0f, carouselSlideInOffset(1f, +1, 300f, -100f), 0.001f)
        assertEquals(-200f, carouselSlideInOffset(0f, -1, 300f, 100f), 0.001f)
        assertEquals(-100f, carouselSlideInOffset(0.5f, -1, 300f, 100f), 0.001f)
        assertEquals(0f, carouselSlideInOffset(1f, -1, 300f, 100f), 0.001f)
        // 松手位移为 0（自动轮换／点圆点）时行为与原来一致。
        assertEquals(300f, carouselSlideInOffset(0f, +1, 300f, 0f), 0.001f)
    }

    @Test
    fun slideOffsets_withReleaseOffset_stayExactlyOneWidthApart() {
        // 第四十一批（538 闪的根因锁死）：带松手位移时，出卡与进卡在滑动
        // 全程恰好差一整张卡宽——左滑时进卡左缘 − 出卡右缘恒为 0，右滑时
        // 出卡左缘 − 进卡右缘恒为 0；既无重叠、也无蓝底缝。旧版进卡起点
        // 不带松手位移，起点缝宽＝|松手位移|（Bob 录屏 250–306px），随进度收到 0。
        val width = 300f
        for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (release in listOf(-150f, -100f, -75f)) {
                val out = carouselSlideOutOffset(progress, release, +1, width)
                val inn = carouselSlideInOffset(progress, +1, width, release)
                assertEquals(0f, inn - (out + width), 0.001f)
            }
            for (release in listOf(75f, 100f, 150f)) {
                val out = carouselSlideOutOffset(progress, release, -1, width)
                val inn = carouselSlideInOffset(progress, -1, width, release)
                assertEquals(0f, out - (inn + width), 0.001f)
            }
        }
    }

    @Test
    fun slideOffsets_outAndInDoNotOverlap() {
        // 同一进度下出卡右缘始终在进卡左缘左侧（左滑）：两张不重叠，
        // 一张接一张走。进度 0.5、松手位移 −100f 时出卡占 [−200, 100]、
        // 进卡占 [100, 400]（带上松手位移后恰好贴住，缝宽 0）。
        val outAtHalf = carouselSlideOutOffset(0.5f, -100f, +1, 300f)
        val inAtHalf = carouselSlideInOffset(0.5f, +1, 300f, -100f)
        assertTrue(outAtHalf + 300f <= inAtHalf)
        assertEquals(0f, inAtHalf - (outAtHalf + 300f), 0.001f)
    }

    @Test
    fun dragUnderOffset_leftDrag_nextCardOnRight() {
        // 第四十二批：向左拖（位移 −100f），垫卡是下一张、在右侧，
        // 位移＝−100＋300＝200f（卡占 [200, 500]，当前卡占 [−100, 200]，贴住）。
        assertEquals(200f, carouselDragUnderOffset(-100f, +1, 300f), 0.001f)
    }

    @Test
    fun dragUnderOffset_rightDrag_prevCardOnLeft() {
        // 第四十二批：向右拖（位移 ＋100f），垫卡是上一张、在左侧，
        // 位移＝100−300＝−200f（卡占 [−200, 100]，当前卡占 [100, 400]，贴住）。
        assertEquals(-200f, carouselDragUnderOffset(100f, -1, 300f), 0.001f)
    }

    @Test
    fun dragUnderOffset_zeroWidth_returnsDragOffset() {
        // 卡宽没量到时返回拖动位移（不动，等卡宽量到再垫）。
        assertEquals(-100f, carouselDragUnderOffset(-100f, +1, 0f), 0.001f)
    }

    @Test
    fun dragUnderOffset_underAndCurrentExactlyOneWidthApart() {
        // 第四十二批（539 闪的根因锁死）：拖动全程垫卡与当前卡恰好差
        // 一整张卡宽——左拖时垫卡左缘 − 当前卡右缘恒为 0，右拖时当前卡
        // 左缘 − 垫卡右缘恒为 0；既无重叠、也无蓝底缝。旧版拖动时只画
        // 当前一张，拖开多少就露多少蓝底（Bob 录屏 100–420px）。
        val width = 300f
        for (drag in listOf(-150f, -100f, -75f, -10f)) {
            val under = carouselDragUnderOffset(drag, +1, width)
            assertEquals(0f, under - (drag + width), 0.001f)
        }
        for (drag in listOf(10f, 75f, 100f, 150f)) {
            val under = carouselDragUnderOffset(drag, -1, width)
            assertEquals(0f, drag - (under + width), 0.001f)
        }
    }

    @Test
    fun dragUnderOffset_atReleaseMatchesSlideInStart() {
        // 第四十二批（拖动到滑动无跳变锁死）：松手那一刻垫卡位置＝滑入
        // 起点（p＝0 的滑入位移＝dir×卡宽＋松手位移），换卡滑动从垫卡
        // 已在的位置继续走，不弹回、不跳变。左滑松手位移 −100f 时两边
        // 都是 200f；右滑松手位移 ＋100f 时两边都是 −200f。
        assertEquals(
            carouselSlideInOffset(0f, +1, 300f, -100f),
            carouselDragUnderOffset(-100f, +1, 300f),
            0.001f
        )
        assertEquals(
            carouselSlideInOffset(0f, -1, 300f, 100f),
            carouselDragUnderOffset(100f, -1, 300f),
            0.001f
        )
    }
}
