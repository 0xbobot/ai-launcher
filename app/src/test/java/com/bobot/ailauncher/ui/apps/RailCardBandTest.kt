package com.bobot.ailauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 卡片叠层带换算单测（451）：首页 picker 的 48dp 隐形长按触发条全高盖在
 * HomeScreen 之上，卡片右侧按钮（连接/×）落在条内时点按会被条偷走、
 * 到不了卡片（449 实拍）。WaveRail 把触摸层在卡带处断开——带内不挂触摸层、
 * 点按落到卡片，带外仍挂、长按开 picker。本文件锁死带换算的纯函数
 * cardBandInRail（根坐标上下缘 → rail 本地坐标，夹到 rail 高度内）。
 */
class RailCardBandTest {

    // rail：顶在根坐标 y=100px、高 1000px（本地 0..1000）

    @Test
    fun cardBandInRail_noCardGivesNull() {
        assertNull(cardBandInRail(null, 100f, 1000f))
    }

    @Test
    fun cardBandInRail_cardFullyInside() {
        // 卡片根坐标 300..600 → 本地 200..500
        assertEquals(200f to 500f, cardBandInRail(300f to 600f, 100f, 1000f))
    }

    @Test
    fun cardBandInRail_cardAboveRailTopClampsToZero() {
        // 卡片根坐标 0..400（顶在 rail 顶之上）→ 本地 0..300
        assertEquals(0f to 300f, cardBandInRail(0f to 400f, 100f, 1000f))
    }

    @Test
    fun cardBandInRail_cardBelowRailBottomClampsToHeight() {
        // 卡片根坐标 900..1500（底在 rail 底之下）→ 本地 800..1000
        assertEquals(800f to 1000f, cardBandInRail(900f to 1500f, 100f, 1000f))
    }

    @Test
    fun cardBandInRail_cardEntirelyAboveGivesNull() {
        assertNull(cardBandInRail(0f to 90f, 100f, 1000f))
    }

    @Test
    fun cardBandInRail_cardEntirelyBelowGivesNull() {
        assertNull(cardBandInRail(1200f to 1500f, 100f, 1000f))
    }

    @Test
    fun cardBandInRail_zeroOrNegativeRailHeightGivesNull() {
        assertNull(cardBandInRail(300f to 600f, 100f, 0f))
        assertNull(cardBandInRail(300f to 600f, 100f, -10f))
    }

    @Test
    fun cardBandInRail_cardCoversWholeRail() {
        // 卡片比 rail 还高：上下都夹，整条都是带
        assertEquals(0f to 1000f, cardBandInRail(0f to 2000f, 100f, 1000f))
    }

    @Test
    fun cardBandInRail_videoGeometryCoversBothButtons() {
        // 449 视频实测几何（1080×2392，密度 3）：rail 顶在根坐标 y=0、
        // 高 2184px（屏高减 72dp 底距）；卡片根坐标约 770..1380。
        // 连接（约 y=1220）与 ×（约 y=890）都在带内——这正是点按被偷的范围。
        val band = cardBandInRail(770f to 1380f, 0f, 2184f)
        assertEquals(770f to 1380f, band)
        val (top, bottom) = band!!
        assertEquals(true, 890f in top..bottom)
        assertEquals(true, 1220f in top..bottom)
    }
}
