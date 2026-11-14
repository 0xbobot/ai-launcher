package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.smartcard.SwipeRelease
import com.bobot.ailauncher.ui.smartcard.formatSkipRepoSuffix
import com.bobot.ailauncher.ui.smartcard.formatSwipeDiagnosis
import com.bobot.ailauncher.ui.smartcard.resolveSwipeRelease
import com.bobot.ailauncher.ui.smartcard.shouldSkipCard
import com.bobot.ailauncher.ui.smartcard.swipeRevealProgress
import org.junit.Assert.assertEquals
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

    // ---------- 第二十五批：松手判定（resolveSwipeRelease） ----------
    // 手势怎么结束（正常抬起 / 指针从事件里消失）不影响判定——只看
    // 认领与位移/速度。501 的 pointerGone 分支无条件弹回是本批根因：
    // Bob 2026-11-14 录屏里顶卡向右拖到近乎出屏（峰值位移约 889px
    // ≈ 300dp）却两次都弹回。手势本身 JVM 测不到，判定函数锁死在这里，
    // 指针消失后仍走判定由代码走查＋Bob 真机验。
    // 第二十六批改口径：松手判定看峰值位移（整条手势里 |totalX| 的最大
    // 值），不再看松手瞬间的净位移——慢拖、探一下再往回带的手势，松手时
    // 净位移会回到阈值内，但峰值才是用户的划走意图；另加第二信号渲染
    // 位移（offsetX.value，真正在屏上画出来的位置），手势循环累计漏了
    // 事件时卡片 visibly 已拖过阈值也不能弹回。

    @Test
    fun release_largeDisplacement_skips() {
        // 录屏实测峰值约 300dp，远超 96dp 阈值，必须切卡。
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = 300f, renderedOffsetXDp = 300f,
                velocityXDps = 0f
            )
        )
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = 136f, renderedOffsetXDp = 136f,
                velocityXDps = 0f
            )
        )
    }

    @Test
    fun release_negativeDisplacement_skips() {
        // 双向划走：左划右划都是“划走这张看下一张”，不是翻页器。
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = -300f, renderedOffsetXDp = -300f,
                velocityXDps = 0f
            )
        )
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = -97f, renderedOffsetXDp = -97f,
                velocityXDps = 0f
            )
        )
    }

    @Test
    fun release_smallDisplacement_snapsBack() {
        assertEquals(
            SwipeRelease.SnapBack,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = 50f, renderedOffsetXDp = 50f,
                velocityXDps = 0f
            )
        )
        assertEquals(
            SwipeRelease.SnapBack,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = -50f, renderedOffsetXDp = -50f,
                velocityXDps = -300f
            )
        )
    }

    @Test
    fun release_fastFling_skips() {
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = 20f, renderedOffsetXDp = 20f,
                velocityXDps = 1500f
            )
        )
    }

    @Test
    fun release_notClaimed_isIgnored() {
        // 竖向占优让路、正文竖翻时，手势没认领，什么都不做。
        assertEquals(
            SwipeRelease.Ignore,
            resolveSwipeRelease(
                claimed = false, skipping = false,
                peakOffsetXDp = 300f, renderedOffsetXDp = 300f,
                velocityXDps = 0f
            )
        )
    }

    @Test
    fun release_alreadySkipping_isIgnored() {
        assertEquals(
            SwipeRelease.Ignore,
            resolveSwipeRelease(
                claimed = true, skipping = true,
                peakOffsetXDp = 300f, renderedOffsetXDp = 300f,
                velocityXDps = 0f
            )
        )
    }

    // ---------- 第二十六批：峰值位移判松手 ----------

    @Test
    fun release_peakOverThreshold_thenReturned_skips() {
        // 本批核心：拖出去 300dp、松手前带回只剩 60dp——501／512 判净位移
        // 会弹回；峰值才是意图，必须切卡。
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = 300f, renderedOffsetXDp = 60f,
                velocityXDps = 0f
            )
        )
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = 150f, renderedOffsetXDp = 10f,
                velocityXDps = 100f
            )
        )
    }

    @Test
    fun release_peakOverThreshold_negativeReturn_skips() {
        // 反方向同理：向左拖出去、带回一点再松手，照样切卡。
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = -300f, renderedOffsetXDp = -40f,
                velocityXDps = 0f
            )
        )
    }

    @Test
    fun release_renderedOverThreshold_skips() {
        // 第二信号：峰值累计万一漏了事件，但卡片 visibly 已拖过阈值
        // （渲染位移 150dp），也不能弹回。
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = 50f, renderedOffsetXDp = 150f,
                velocityXDps = 0f
            )
        )
        assertEquals(
            SwipeRelease.Skip,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = -50f, renderedOffsetXDp = -150f,
                velocityXDps = 0f
            )
        )
    }

    @Test
    fun release_peakAtThreshold_doesNotSkip() {
        // 严格大于阈值才切：峰值与渲染位移恰好 96dp 都不切。
        assertEquals(
            SwipeRelease.SnapBack,
            resolveSwipeRelease(
                claimed = true, skipping = false,
                peakOffsetXDp = 96f, renderedOffsetXDp = 96f,
                velocityXDps = 0f
            )
        )
    }

    // ---------- 第二十六批：拖动揭示进度（swipeRevealProgress） ----------

    @Test
    fun revealProgress_zeroAtRest() {
        assertEquals(0f, swipeRevealProgress(0f, 96f), 0.001f)
    }

    @Test
    fun revealProgress_halfwayAtHalfThreshold() {
        assertEquals(0.5f, swipeRevealProgress(48f, 96f), 0.001f)
        assertEquals(0.5f, swipeRevealProgress(-48f, 96f), 0.001f)
    }

    @Test
    fun revealProgress_fullAtThreshold() {
        assertEquals(1f, swipeRevealProgress(96f, 96f), 0.001f)
        assertEquals(1f, swipeRevealProgress(300f, 96f), 0.001f)
        assertEquals(1f, swipeRevealProgress(-300f, 96f), 0.001f)
    }

    @Test
    fun revealProgress_zeroThreshold_isZero() {
        assertEquals(0f, swipeRevealProgress(100f, 0f), 0.001f)
    }

    // ---------- 第二十八批：划卡诊断行文案（formatSwipeDiagnosis） ----------
    // 与 logcat 里 "swipe release" 那行同值，屏上与日志永远对得上。
    // 判定三种：切卡／弹回／忽略；onSkip 只在切卡分支调。

    @Test
    fun diagnosis_skip_showsAllValues() {
        assertEquals(
            "划卡诊断：切卡｜认领=true 峰值=150dp 净=120dp 渲染=118dp " +
                "速度=200dp/s 退出=up 步数=18 onSkip=已调",
            formatSwipeDiagnosis(
                release = SwipeRelease.Skip, claimed = true,
                netDp = 120f, peakDp = 150f, renderedDp = 118f,
                velocityDps = 200f, exitReason = "up", moveCount = 18
            )
        )
    }

    @Test
    fun diagnosis_snapBack_showsAllValues() {
        assertEquals(
            "划卡诊断：弹回｜认领=true 峰值=40dp 净=30dp 渲染=28dp " +
                "速度=150dp/s 退出=up 步数=9 onSkip=未调",
            formatSwipeDiagnosis(
                release = SwipeRelease.SnapBack, claimed = true,
                netDp = 30f, peakDp = 40f, renderedDp = 28f,
                velocityDps = 150f, exitReason = "up", moveCount = 9
            )
        )
    }

    @Test
    fun diagnosis_ignore_vertical_showsAllValues() {
        assertEquals(
            "划卡诊断：忽略｜认领=false 峰值=10dp 净=5dp 渲染=0dp " +
                "速度=80dp/s 退出=vertical 步数=4 onSkip=未调",
            formatSwipeDiagnosis(
                release = SwipeRelease.Ignore, claimed = false,
                netDp = 5f, peakDp = 10f, renderedDp = 0f,
                velocityDps = 80f, exitReason = "vertical", moveCount = 4
            )
        )
    }

    @Test
    fun diagnosis_negativeDirection_keepsSign() {
        // 左划：峰值是绝对值恒正，净位移／渲染／速度保留负号。
        assertEquals(
            "划卡诊断：切卡｜认领=true 峰值=136dp 净=-136dp 渲染=-130dp " +
                "速度=-900dp/s 退出=up 步数=12 onSkip=已调",
            formatSwipeDiagnosis(
                release = SwipeRelease.Skip, claimed = true,
                netDp = -136f, peakDp = 136f, renderedDp = -130f,
                velocityDps = -900f, exitReason = "up", moveCount = 12
            )
        )
    }

    @Test
    fun diagnosis_roundsToWholeDp() {
        assertEquals(
            "划卡诊断：切卡｜认领=true 峰值=151dp 净=120dp 渲染=118dp " +
                "速度=201dp/s 退出=up 步数=18 onSkip=已调",
            formatSwipeDiagnosis(
                release = SwipeRelease.Skip, claimed = true,
                netDp = 120.4f, peakDp = 150.6f, renderedDp = 118f,
                velocityDps = 200.6f, exitReason = "up", moveCount = 18
            )
        )
    }

    // ---------- 第二十九批：划卡诊断行·仓库结果后缀 ----------
    // onSkip 调完立刻读仓库顶卡：顶换了「顶已换」，还是被划走那张
    // 「顶未换」，栈空了「已空」。Bob 划完直接读这半句。

    @Test
    fun repoSuffix_topChanged_showsSwapped() {
        assertEquals(
            "｜仓库：顶已换 张数=2",
            formatSkipRepoSuffix("a", "b", 2)
        )
    }

    @Test
    fun repoSuffix_topUnchanged_showsNotSwapped() {
        assertEquals(
            "｜仓库：顶未换 张数=2",
            formatSkipRepoSuffix("a", "a", 2)
        )
    }

    @Test
    fun repoSuffix_emptyStack_showsEmpty() {
        assertEquals(
            "｜仓库：已空 张数=0",
            formatSkipRepoSuffix("a", null, 0)
        )
    }
}
