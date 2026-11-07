package com.bobot.ailauncher.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.68.169（盲操扇形找回）：盲操手势仲裁谓词回归锁。
 * 374 批的 Dock bounds 误捕获为整屏矩形（PullUpDock 外层 modifier 根为
 * fillMaxSize），致所有主页手势都被判为 Dock 区内、扇形全屏失效——
 * 本测试把“整屏矩形排除一切”钉死为已知 bug 机制，把“面板矩形只排除
 * 面板”钉死为修复后语义。
 */
class BlindArbitrationTest {

    // 竖屏 1080×2400（px，按比例取整）：面板单元为底部 0..1080 × 1980..2400。
    private val panelRect = Rect(left = 0f, top = 1980f, right = 1080f, bottom = 2400f)
    private val fullScreenRect = Rect(left = 0f, top = 0f, right = 1080f, bottom = 2400f)
    private val cardRect = Rect(left = 60f, top = 1200f, right = 1020f, bottom = 1980f)

    @Test
    fun fullScreenDockRect_excludesEverything_documentsBugMechanism() {
        // 374 误捕获的整屏矩形：连壁纸区起点也被排除——扇形永不触发。
        assertTrue(blindStartExcluded(Offset(540f, 900f), null, fullScreenRect))
        assertTrue(blindStartExcluded(Offset(540f, 2100f), null, fullScreenRect))
    }

    @Test
    fun panelRect_excludesDockPointOnly() {
        assertTrue(blindStartExcluded(Offset(540f, 2100f), null, panelRect))
        assertFalse(blindStartExcluded(Offset(540f, 900f), null, panelRect))
    }

    @Test
    fun nullRects_neverExclude() {
        assertFalse(blindStartExcluded(Offset(540f, 900f), null, null))
        assertFalse(blindStartExcluded(Offset(540f, 2100f), null, null))
    }

    @Test
    fun intentRect_excludesCardPointOnly() {
        assertTrue(blindStartExcluded(Offset(540f, 1500f), cardRect, panelRect))
        assertFalse(blindStartExcluded(Offset(540f, 900f), cardRect, panelRect))
    }

    @Test
    fun cardStackRect_excludesStackPointOnly() {
        // 449/v1.2.2：叠层区的手势不转发盲操——横滑卡片不再弹扇形/直开相机。
        // 叠层在竖屏 1080×2400 上约占 y 1200..1700（会行下方独立一段）。
        val stackRect = Rect(left = 0f, top = 1200f, right = 1080f, bottom = 1700f)
        assertTrue(blindStartExcluded(Offset(540f, 1500f), null, null, stackRect))
        assertFalse(blindStartExcluded(Offset(540f, 900f), null, null, stackRect))
        assertFalse(blindStartExcluded(Offset(540f, 2100f), null, null, stackRect))
    }

    @Test
    fun cardStackRect_nullNeverExcludes() {
        assertFalse(blindStartExcluded(Offset(540f, 1500f), null, null, null))
    }

    @Test
    fun cardStackRect_zeroAreaNeverExcludes() {
        // 叠层为空时槽位零高度、bounds 零面积：不误拦主页背景手势。
        val zeroRect = Rect(left = 0f, top = 1500f, right = 1080f, bottom = 1500f)
        assertFalse(blindStartExcluded(Offset(540f, 1500f), null, null, zeroRect))
    }
}
