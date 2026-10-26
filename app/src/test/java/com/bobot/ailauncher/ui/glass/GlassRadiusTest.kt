package com.bobot.ailauncher.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 357 横屏闪退防线单测（崩溃记录 2026-10-26 19:41，nubia P0110）：
 * RadialGradient 要求 ending radius 严格 > 0；玻璃面宽为 0 时
 * fraction × width = 0，主线程炸 IllegalArgumentException。
 * glassRadialRadius 保证任何输入都不返回 ≤ 0，正常尺寸下返回值一字不动。
 */
class GlassRadiusTest {

    @Test
    fun `zero width clamps to one px`() {
        assertEquals(1f, glassRadialRadius(0.35f, 0f), 0f)
    }

    @Test
    fun `negative extent clamps to one px`() {
        assertEquals(1f, glassRadialRadius(0.35f, -10f), 0f)
    }

    @Test
    fun `normal widths unchanged`() {
        // 0.30f × 100f 二进制浮点为 30.000002，非精确 30f，容差 0.001f
        // 只排除浮点噪声，不掩盖钳制逻辑（钳制分支返回值恒为精确 1f）。
        assertEquals(35f, glassRadialRadius(0.35f, 100f), 0.001f)
        assertEquals(30f, glassRadialRadius(0.30f, 100f), 0.001f)
    }

    @Test
    fun `sub pixel product clamps to one px`() {
        // 0.35 × 2px = 0.7px，仍钳制到 1px，不许出现 (0, 1) 区间的非法半径。
        assertEquals(1f, glassRadialRadius(0.35f, 2f), 0f)
    }
}
