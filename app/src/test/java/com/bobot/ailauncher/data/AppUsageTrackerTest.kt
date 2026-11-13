package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dock 智能改造纯函数单测（Bob 2026-11-13 08:14：用了这么久 Dock 只有
 * 固定/经常用两种理由——时段桶 168 格太细、转移门槛太高，全掉到“经常用”；
 * Bob 2026-11-13 21:33：转移全是 Muse 之后打开、失去意义，且“近期”没写
 * 清楚是多久——转移改算占比、窗口计数定死 30 天）。
 * 只测纯函数 dayTypeHwSum / transferDominant / shiftYmd / recentCount /
 * dockScore / dockReason；SharedPreferences 读数与重组走分支 CI + Bob 真机验收。
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

    // ---- dockScore：时段聚合 ≥3 主导，转移占主导（≥2 且占比 ≥0.5）才 +500 ----

    @Test
    fun `day-type bucket at 3 dominates`() {
        assertEquals(30000.0 + 10, AppUsageTracker.dockScore(3, 10, 0, false, 0L, 1000L), 0.0)
        assertEquals(10.0, AppUsageTracker.dockScore(2, 10, 0, false, 0L, 1000L), 0.0)
    }

    @Test
    fun `transfer adds 500 only when dominant and prev recent`() {
        // 转移 2 / 总量 4 → 占比 0.5，占主导，+500
        assertEquals(504.0, AppUsageTracker.dockScore(0, 4, 2, true, 0L, 1000L), 0.0)
        // 转移 2 / 总量 10 → 占比 0.2，枢纽带出来的噪音，不加分
        assertEquals(10.0, AppUsageTracker.dockScore(0, 10, 2, true, 0L, 1000L), 0.0)
        assertEquals(4.0, AppUsageTracker.dockScore(0, 4, 2, false, 0L, 1000L), 0.0)
        assertEquals(4.0, AppUsageTracker.dockScore(0, 4, 1, true, 0L, 1000L), 0.0)
    }

    @Test
    fun `used within 30 minutes loses 2`() {
        val now = 1000L * 60 * 60
        assertEquals(8.0, AppUsageTracker.dockScore(0, 10, 0, false, now - 10 * 60 * 1000, now), 0.0)
        assertEquals(10.0, AppUsageTracker.dockScore(0, 10, 0, false, now - 31 * 60 * 1000, now), 0.0)
        assertEquals(10.0, AppUsageTracker.dockScore(0, 10, 0, false, 0L, now), 0.0)
    }

    // ---- transferDominant：转移 ≥2 且占比 ≥0.5 ----

    @Test
    fun `transfer dominant needs count 2 and ratio half`() {
        // 枢纽带出来的：转移 5 / 总量 17 → 占比 0.29，不占主导
        assertEquals(false, AppUsageTracker.transferDominant(5, 17))
        // 真习惯：转移 3 / 总量 5 → 占比 0.6，占主导
        assertEquals(true, AppUsageTracker.transferDominant(3, 5))
        // 边界 0.5 算占主导
        assertEquals(true, AppUsageTracker.transferDominant(2, 4))
        // 边界下一点不算
        assertEquals(false, AppUsageTracker.transferDominant(2, 5))
        // 计数不够 / 总量为 0 都不算
        assertEquals(false, AppUsageTracker.transferDominant(1, 1))
        assertEquals(false, AppUsageTracker.transferDominant(2, 0))
        assertEquals(false, AppUsageTracker.transferDominant(0, 0))
    }

    // ---- shiftYmd：跨月、跨年 ----

    @Test
    fun `shiftYmd crosses month and year`() {
        assertEquals("20261101", AppUsageTracker.shiftYmd("20261102", -1))
        assertEquals("20261031", AppUsageTracker.shiftYmd("20261101", -1))
        assertEquals("20251231", AppUsageTracker.shiftYmd("20260101", -1))
        assertEquals("20261015", AppUsageTracker.shiftYmd("20261113", -29))
        assertEquals("20261113", AppUsageTracker.shiftYmd("20261015", 29))
        assertEquals("20261113", AppUsageTracker.shiftYmd("20261113", 0))
    }

    // ---- recentCount（纯函数）：窗口内才加 ----

    @Test
    fun `recent count sums only the last 30 days`() {
        val dayCounts = mapOf(
            "20261113" to 3, // 今天
            "20261015" to 2, // 今天往前 29 天，窗口内
            "20261014" to 9, // 今天往前 30 天，窗口外
            "20261001" to 7, // 更早，窗口外
            "garbage" to 100, // 脏键不计
            "2026111" to 100 // 位数不对的脏键不计
        )
        assertEquals(5, AppUsageTracker.recentCount(dayCounts, "20261113", 30))
        assertEquals(0, AppUsageTracker.recentCount(emptyMap(), "20261113", 30))
        assertEquals(0, AppUsageTracker.recentCount(dayCounts, "20261113", 0))
        // 窗口 1 天只算今天
        assertEquals(3, AppUsageTracker.recentCount(dayCounts, "20261113", 1))
    }

    // ---- dockReason：与排序同源，经常用带 30 天次数 ----

    @Test
    fun `pinned reason wins`() {
        assertEquals(
            "你固定在 Dock 上",
            AppUsageTracker.dockReason(true, 5, 5, 8, "微信", true, 5, 100, 60)
        )
    }

    @Test
    fun `day-type reason names weekday and period`() {
        assertEquals(
            "工作日早上 8 点常开",
            AppUsageTracker.dockReason(false, 3, 5, 8, null, false, 0, 10, 10)
        )
        assertEquals(
            "周六下午 15 点常开",
            AppUsageTracker.dockReason(false, 4, 6, 15, null, false, 0, 10, 10)
        )
        assertEquals(
            "周日夜里 2 点常开",
            AppUsageTracker.dockReason(false, 3, 7, 2, null, false, 0, 10, 10)
        )
    }

    @Test
    fun `transfer reason needs dominant ratio`() {
        // 转移 2 / 总量 4 → 占比 0.5，占主导，仍是“接着开”
        assertEquals(
            "打开 微信 后常接着开",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", true, 2, 4, 4)
        )
        // 转移 5 / 总量 17 → 占比 0.29，枢纽噪音，被占比拦下后落到“常开”
        assertEquals(
            "常开 · 近 30 天 17 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", true, 5, 17, 17)
        )
        assertEquals(
            "常开 · 近 30 天 10 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", false, 5, 10, 10)
        )
        assertEquals(
            "常开 · 近 30 天 10 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", true, 1, 10, 10)
        )
        assertEquals(
            "常开 · 近 30 天 10 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, true, 5, 10, 10)
        )
    }

    @Test
    fun `frequent reason carries the 30 day count`() {
        assertEquals(
            "常开 · 近 30 天 42 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 42, 42)
        )
    }

    @Test
    fun `stale app says not opened recently never zero times`() {
        // 总量 >0 但 30 天内一次没开：不说“近 30 天 0 次”，说“最近没怎么开”
        val reason = AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 42, 0)
        assertEquals("最近没怎么开", reason)
        assertEquals(false, reason.contains("0 次"))
    }

    @Test
    fun `no record falls back to hot apps`() {
        assertEquals(
            "热门应用（暂无你的使用记录）",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 0, 0)
        )
    }
}
