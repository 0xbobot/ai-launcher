package com.bobot.ailauncher.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下滑左下开控制中心的判定回归锁（Bob 2026-11-09 21:24：首页偶尔还是会
 * 触发下拉手势——旧规则 totalX < -24dp 太松，150dp 的下滑偏 10° 就有约
 * 26dp 左移，自然拇指弧线动不动就落进“左下”）。
 * 新规则：下滑过 48dp，且左移同时超过 48dp 绝对量与下滑量的一半（约 27°）。
 * 数值均为 px，测试用 48f/48f 与生产阈值同值。
 */
class HomeControlCenterSwipeTest {

    private val threshold = 48f
    private val xBias = 48f

    @Test
    fun pureDown_doesNothing() {
        assertFalse(isControlCenterSwipe(0f, 150f, threshold, xBias))
    }

    @Test
    fun slightLeftDown_doesNothing_bobCase() {
        // Bob 的实测场景：150dp 下滑偏 10°，左移约 26dp——旧规则会开控制中心。
        assertFalse(isControlCenterSwipe(-26f, 150f, threshold, xBias))
        assertFalse(isControlCenterSwipe(-30f, 150f, threshold, xBias))
    }

    @Test
    fun belowRatio_doesNothing() {
        // 150dp 下滑、左移 70dp（比值 0.47 < 0.5）：仍是自然弧线。
        assertFalse(isControlCenterSwipe(-70f, 150f, threshold, xBias))
        // 边界：恰好一半不算（须严格大于）。
        assertFalse(isControlCenterSwipe(-75f, 150f, threshold, xBias))
    }

    @Test
    fun clearDiagonalDownLeft_opensControlCenter() {
        assertTrue(isControlCenterSwipe(-76f, 150f, threshold, xBias))
        assertTrue(isControlCenterSwipe(-100f, 150f, threshold, xBias))
        assertTrue(isControlCenterSwipe(-120f, 200f, threshold, xBias))
    }

    @Test
    fun downRight_doesNothing() {
        assertFalse(isControlCenterSwipe(50f, 150f, threshold, xBias))
        assertFalse(isControlCenterSwipe(120f, 200f, threshold, xBias))
    }

    @Test
    fun shortSwipe_belowThreshold_doesNothing() {
        // 纵向不过 48dp：再斜也不开。
        assertFalse(isControlCenterSwipe(-100f, 40f, threshold, xBias))
        assertFalse(isControlCenterSwipe(0f, 40f, threshold, xBias))
    }

    @Test
    fun shortDiagonal_belowAbsoluteBias_doesNothing() {
        // 60dp 下滑、左移 30dp（27°，比值刚过一半）——绝对量不过 48dp，不开。
        assertFalse(isControlCenterSwipe(-30f, 60f, threshold, xBias))
    }

    @Test
    fun shortSteepDiagonal_opensControlCenter() {
        // 60dp 下滑、左移 60dp（45°）：绝对量与比值都过，开。
        assertTrue(isControlCenterSwipe(-60f, 60f, threshold, xBias))
    }

    @Test
    fun longNaturalArc_doesNothing() {
        // 200dp 长下滑、左移 80dp（22°）：长滑的自然弧线，不开。
        assertFalse(isControlCenterSwipe(-80f, 200f, threshold, xBias))
    }

    @Test
    fun upSwipe_doesNothing() {
        assertFalse(isControlCenterSwipe(0f, -150f, threshold, xBias))
        assertFalse(isControlCenterSwipe(-100f, -150f, threshold, xBias))
    }
}
