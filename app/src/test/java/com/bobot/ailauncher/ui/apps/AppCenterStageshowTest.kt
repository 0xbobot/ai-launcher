package com.bobot.ailauncher.ui.apps

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stageshow 应用中心优化单测（Bob 2026-11-21 拍板「就按这个设计做」，设计文档
 * appcenter-stageshow-optimization-design.md §3）：
 * 3.1 一键隐藏按钮收金＋缩文案；3.2 组标记透明度统一 0.35；
 * 3.3 rail 字母用宋体；3.4 折叠组头用刊头体；3.5 计数只在搜索态出现。
 * 3.6 刊头降档默认不动（无改动可锁）；行与动效不动（另有既有单测覆盖）。
 */
class AppCenterStageshowTest {

    // ---------- 3.1 一键隐藏按钮：缩文案 ----------

    @Test
    fun hideButtonText_twelve() {
        assertEquals("藏起 12 个不常用的应用", hideInactiveButtonText(12))
    }

    @Test
    fun hideButtonText_one() {
        assertEquals("藏起 1 个不常用的应用", hideInactiveButtonText(1))
    }

    @Test
    fun hideButtonText_zero() {
        // 按钮只在 N>0 时出现；函数本身对 0 也全量定义，不抛。
        assertEquals("藏起 0 个不常用的应用", hideInactiveButtonText(0))
    }

    @Test
    fun hideButtonText_noNinetyDays() {
        // 同一个 N 只说一次：文案里不再出现「90 天」与「藏起来」。
        val text = hideInactiveButtonText(12)
        assertFalse(text.contains("90 天"))
        assertFalse(text.contains("藏起来"))
    }

    // ---------- 3.1 一键隐藏按钮：收金（白卡面，不再是金色满填） ----------

    @Test
    fun hideButtonContainer_isCard_notGold_light() {
        assertEquals(Color(0xFFFFFFFF), hideInactiveButtonContainerColor(AILauncherColors.Light))
        assertNotEquals(Color(0xFFC9A227), hideInactiveButtonContainerColor(AILauncherColors.Light))
    }

    @Test
    fun hideButtonContainer_isCard_notGold_dark() {
        assertEquals(Color(0xFF2A2723), hideInactiveButtonContainerColor(AILauncherColors.Dark))
        assertNotEquals(Color(0xFFC9A227), hideInactiveButtonContainerColor(AILauncherColors.Dark))
    }

    // ---------- 3.2 组标记说一种语言 ----------

    @Test
    fun ghostMarkerAlpha_is035() {
        assertEquals(0.35f, AppCenterGhostMarkerAlpha)
    }

    // ---------- 3.3 rail 字母用宋体 ----------

    @Test
    fun railLetterFont_isSerif() {
        assertEquals(FontFamily.Serif, railLetterFontFamily())
    }

    // ---------- 3.4 折叠组头用刊头体 ----------

    @Test
    fun foldHeaderText_twelve() {
        assertEquals("不常用 · 12 个", foldHeaderText(12))
    }

    @Test
    fun foldHeaderStyle_isCaptionWith4spTracking() {
        // 与首页卡片标签同口径：Caption 小字、4.sp 字距。
        val style = foldHeaderStyle()
        assertEquals(AILauncherType.Caption.fontSize, style.fontSize)
        assertEquals(4.sp, style.letterSpacing)
    }

    @Test
    fun foldHeaderStyle_notBodyLarge() {
        // 不再是 BodyLarge 素字／Body 粗体。
        val style = foldHeaderStyle()
        assertNotEquals(AILauncherType.BodyLarge.fontSize, style.fontSize)
        assertNotEquals(AILauncherType.Body.fontSize, style.fontSize)
    }

    // ---------- 3.5 计数只在搜索态出现 ----------

    @Test
    fun showCount_emptyQuery_isFalse() {
        assertFalse(shouldShowAppCenterCount(""))
    }

    @Test
    fun showCount_typedQuery_isTrue() {
        assertTrue(shouldShowAppCenterCount("微"))
    }

    @Test
    fun countText_portrait() {
        assertEquals("共 87 个", appCenterCountText(87))
    }

    @Test
    fun countText_landscape() {
        assertEquals("87 款", appCenterCountTextLandscape(87))
    }

    @Test
    fun countText_portraitAndLandscape_sameNumber() {
        // 竖屏横屏是同一个 N 的两种写法，不是两个数。
        assertEquals("共 12 个", appCenterCountText(12))
        assertEquals("12 款", appCenterCountTextLandscape(12))
    }
}
