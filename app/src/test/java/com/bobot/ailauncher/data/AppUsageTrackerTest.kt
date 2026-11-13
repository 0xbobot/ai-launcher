package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dock 智能改造纯函数单测（Bob 2026-11-13 08:14：用了这么久 Dock 只有
 * 固定/经常用两种理由——时段桶 168 格太细、转移门槛太高，全掉到“经常用”）。
 * 只测纯函数 dayTypeHwSum / dockScore / dockReason；SharedPreferences
 * 读数（hwDayTypeCount）与重组走分支 CI + Bob 真机验收（早上 8 点开说明页，
 * 每天早上都开的应用应显示“工作日早上 8 点常开”）。
 */
class AppUsageTrackerTest {

    // ---- dayTypeHwSum：工作日 5 天求和，周六/周日单算 ----

    @Test
    fun `weekday sums Mon to Fri same hour`() {
        val hwByDow = mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 9, 7 to 9)
        assertEquals(5, AppUsageTracker.dayTypeHwSum(hwByDow, 1))
        assertEquals(5, AppUsageTracker.dayTypeHwSum(hwByDow, 3))
        assertEquals(5, AppUsageTracker.dayTypeHwSum(hwByDow, 5))
    }

    @Test
    fun `Saturday and Sunday stay single day`() {
        val hwByDow = mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 4, 7 to 2)
        assertEquals(4, AppUsageTracker.dayTypeHwSum(hwByDow, 6))
        assertEquals(2, AppUsageTracker.dayTypeHwSum(hwByDow, 7))
    }

    @Test
    fun `missing days count as zero`() {
        assertEquals(2, AppUsageTracker.dayTypeHwSum(mapOf(2 to 2), 4))
        assertEquals(0, AppUsageTracker.dayTypeHwSum(emptyMap(), 6))
        assertEquals(0, AppUsageTracker.dayTypeHwSum(mapOf(6 to 3), 1))
    }

    // ---- dockScore：时段聚合 ≥3 主导，转移 ≥2 才 +500 ----

    @Test
    fun `day-type bucket at 3 dominates`() {
        assertEquals(30000.0 + 10, AppUsageTracker.dockScore(3, 10, 0, false, 0L, 1000L), 0.0)
        assertEquals(10.0, AppUsageTracker.dockScore(2, 10, 0, false, 0L, 1000L), 0.0)
    }

    @Test
    fun `transfer at 2 adds 500 only when prev recent`() {
        assertEquals(510.0, AppUsageTracker.dockScore(0, 10, 2, true, 0L, 1000L), 0.0)
        assertEquals(10.0, AppUsageTracker.dockScore(0, 10, 2, false, 0L, 1000L), 0.0)
        assertEquals(10.0, AppUsageTracker.dockScore(0, 10, 1, true, 0L, 1000L), 0.0)
    }

    @Test
    fun `used within 30 minutes loses 2`() {
        val now = 1000L * 60 * 60
        assertEquals(8.0, AppUsageTracker.dockScore(0, 10, 0, false, now - 10 * 60 * 1000, now), 0.0)
        assertEquals(10.0, AppUsageTracker.dockScore(0, 10, 0, false, now - 31 * 60 * 1000, now), 0.0)
        assertEquals(10.0, AppUsageTracker.dockScore(0, 10, 0, false, 0L, now), 0.0)
    }

    // ---- dockReason：与排序同源，经常用带次数 ----

    @Test
    fun `pinned reason wins`() {
        assertEquals(
            "你固定在 Dock 上",
            AppUsageTracker.dockReason(true, 5, 5, 8, "微信", true, 5, 100)
        )
    }

    @Test
    fun `day-type reason names weekday and period`() {
        assertEquals(
            "工作日早上 8 点常开",
            AppUsageTracker.dockReason(false, 3, 5, 8, null, false, 0, 10)
        )
        assertEquals(
            "周六下午 15 点常开",
            AppUsageTracker.dockReason(false, 4, 6, 15, null, false, 0, 10)
        )
        assertEquals(
            "周日夜里 2 点常开",
            AppUsageTracker.dockReason(false, 3, 7, 2, null, false, 0, 10)
        )
    }

    @Test
    fun `transfer reason needs recent prev and count 2`() {
        assertEquals(
            "打开 微信 后常接着开",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", true, 2, 10)
        )
        assertEquals(
            "常开 · 近期 10 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", false, 5, 10)
        )
        assertEquals(
            "常开 · 近期 10 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", true, 1, 10)
        )
        assertEquals(
            "常开 · 近期 10 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, true, 5, 10)
        )
    }

    @Test
    fun `frequent reason carries the count`() {
        assertEquals(
            "常开 · 近期 42 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 42)
        )
    }

    @Test
    fun `no record falls back to hot apps`() {
        assertEquals(
            "热门应用（暂无你的使用记录）",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 0)
        )
    }
}
