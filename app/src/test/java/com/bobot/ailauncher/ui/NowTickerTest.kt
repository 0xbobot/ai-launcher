package com.bobot.ailauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.68.95（批二-A·F-05）：分钟 ticker 与时间窗判定的纯逻辑单测。
 * ticker 的生命周期接线（RESUMED 才跑）在真机/冒烟层验证；这里只钉
 * 对齐算法与窗口边界语义（严格小于，与原 `now - time < window` 一致）。
 */
class NowTickerTest {

    // ---------- delayToNextMinute ----------

    @Test
    fun `delay at exact minute boundary waits a full minute plus tolerance`() {
        // 整分钟整点（如 12:00:00.000）：等到下一分钟 + 200ms 容差
        assertEquals(60_200L, delayToNextMinute(1_700_000_040_000L)) // %60000 == 0
    }

    @Test
    fun `delay mid minute aligns to next boundary`() {
        // 分钟内 30 秒处 → 30 秒后到整点，再 +200ms
        assertEquals(30_200L, delayToNextMinute(1_700_000_070_000L)) // %60000 == 30000
    }

    @Test
    fun `delay just before boundary is minimal`() {
        assertEquals(201L, delayToNextMinute(1_700_000_099_999L)) // %60000 == 59999
    }

    // ---------- isWithinWindow（TODAY 消息 3 小时窗同口径） ----------

    @Test
    fun `window boundary is strict`() {
        val window = 3 * 60 * 60 * 1000L
        val now = 1_700_000_000_000L
        assertTrue(isWithinWindow(now - window + 1, now, window))
        assertFalse(isWithinWindow(now - window, now, window))
        assertFalse(isWithinWindow(now - window - 60_000L, now, window))
    }

    @Test
    fun `future item stays within window`() {
        // 时钟回拨等情况下 time 略大于 now：与原语义一致（差为负 < 窗口）
        val window = 3 * 60 * 60 * 1000L
        val now = 1_700_000_000_000L
        assertTrue(isWithinWindow(now + 5_000L, now, window))
    }
}
