package com.bobot.ailauncher.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批B ③ 回归锁：CompositionLocal 化后，Light/Dark 两套调色板的每个 token
 * 必须与原全局 var getter 的字面值逐一相等（长相零变化的单测防线）。
 */
class AILauncherColorsTest {

    @Test
    fun lightPaletteTokens() {
        val p = AILauncherColors.Light
        assertFalse(p.isDark)
        assertEquals(Color(0xFFF4F2EE), p.Background)
        assertEquals(Color(0xFFFFFFFF), p.Card)
        assertEquals(Color(0xFF1F1D1A), p.Title)
        assertEquals(Color(0xFF5C5852), p.Body)
        // 应用中心升级 P0-1（Bob 定稿：12sp Hint 加深）：浅色 Hint
        // 0xFFA8A29A → 0xFF8A8378（暖白底对比约 2.2:1 → 约 3.2:1）
        assertEquals(Color(0xFF8A8378), p.Hint)
        assertEquals(Color(0xFFC9A227), p.Accent)
        assertEquals(Color(0xFFF6ECD4), p.AccentSoft)
        assertEquals(Color(0xFF4C9A52), p.Success)
        assertEquals(Color(0xFFE8E2D9), p.Divider)
        assertEquals(Color(0x8CFFFFFF), p.GlassCard)
        assertEquals(Color(0xE0FFFFFF), p.GlassCardStrong)
        assertEquals(Color(0x80FFFFFF), p.GlassBorder)
        assertEquals(Color(0xFF1F1D1A).copy(alpha = 0.28f), p.Grabber)
    }

    @Test
    fun darkPaletteTokens() {
        val p = AILauncherColors.Dark
        assertTrue(p.isDark)
        assertEquals(Color(0xFF1A1815), p.Background)
        assertEquals(Color(0xFF2A2723), p.Card)
        assertEquals(Color(0xFFF4F2EE), p.Title)
        assertEquals(Color(0xFFB8B2A8), p.Body)
        assertEquals(Color(0xFF6B6560), p.Hint)
        assertEquals(Color(0xFFC9A227), p.Accent)
        assertEquals(Color(0xFF3A3226), p.AccentSoft)
        assertEquals(Color(0xFF4C9A52), p.Success)
        assertEquals(Color(0xFF3A362F), p.Divider)
        assertEquals(Color(0x8C1A1815), p.GlassCard)
        assertEquals(Color(0xE01A1815), p.GlassCardStrong)
        assertEquals(Color(0x801A1815), p.GlassBorder)
        assertEquals(Color(0xFFF4F2EE).copy(alpha = 0.28f), p.Grabber)
    }

    @Test
    fun forDarkPicksInstance() {
        assertSame(AILauncherColors.Dark, AILauncherColors.forDark(true))
        assertSame(AILauncherColors.Light, AILauncherColors.forDark(false))
    }

    @Test
    fun coverPaletteBranches() {
        // 浅色：午版标准暖纸 / 夜版压暗
        assertEquals(Color(0xFFF4F2EE), coverPaletteFor(13, isDark = false).background)
        assertEquals(Color(0xFFE9E0D2), coverPaletteFor(21, isDark = false).background)
        // 深色：午版暖黑 / 夜版更暗（批B 前读全局 isDark 的分支等价）
        assertEquals(Color(0xFF1A1815), coverPaletteFor(13, isDark = true).background)
        assertEquals(Color(0xFF141210), coverPaletteFor(21, isDark = true).background)
    }
}
