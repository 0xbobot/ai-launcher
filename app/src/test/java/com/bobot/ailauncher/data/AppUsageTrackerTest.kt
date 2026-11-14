package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dock 智能改造纯函数单测（Bob 2026-11-13 08:14：用了这么久 Dock 只有
 * 固定/经常用两种理由——时段桶 168 格太细、转移门槛太高，全掉到“经常用”；
 * Bob 2026-11-13 21:33：转移全是 Muse 之后打开、失去意义，且“近期”没写
 * 清楚是多久——转移改算占比、窗口计数定死 7 天（Bob 2026-11-13 22:24：
 * 30 天太长，Dock 按周走，改成 7 天）。
 * 只测纯函数 dayTypeHwSum / transferDominant / shiftYmd / recentCount /
 * dockScore / dockReason / recencyFactor / windowMature；SharedPreferences
 * 读数与重组走分支 CI + Bob 真机验收。
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

    // ---- dockScore：时段聚合 ≥3 主导，转移占主导（≥5 且占比 ≥0.6）才 +500 ----

    @Test
    fun `day-type bucket at 3 dominates`() {
        assertEquals(30000.0 + 10, AppUsageTracker.dockScore(3, 10, 0, false, 0L, 1000L), 0.0)
        assertEquals(10.0, AppUsageTracker.dockScore(2, 10, 0, false, 0L, 1000L), 0.0)
    }

    @Test
    fun `transfer adds 500 only when dominant and prev recent`() {
        // 转移 5 / 总量 8 → 占比 0.625，占主导，+500
        assertEquals(505.0, AppUsageTracker.dockScore(0, 5, 5, true, 0L, 1000L), 0.0)
        // 转移 5 / 总量 9 → 占比 0.556，占比不够，不加分
        assertEquals(9.0, AppUsageTracker.dockScore(0, 9, 5, true, 0L, 1000L), 0.0)
        // 转移 2 / 总量 4 → 次数不够（要 ≥5 次才敢说是习惯），不加分
        assertEquals(4.0, AppUsageTracker.dockScore(0, 4, 2, true, 0L, 1000L), 0.0)
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

    // ---- transferDominant：转移 ≥5 且占比 ≥0.6 ----

    @Test
    fun `transfer dominant needs count 5 and ratio six tenths`() {
        // 枢纽带出来的：转移 5 / 总量 17 → 占比 0.29，不占主导
        assertEquals(false, AppUsageTracker.transferDominant(5, 17))
        // 转移 3 / 总量 5 → 占比 0.6，但次数不够（要 ≥5 次才敢说是习惯）
        assertEquals(false, AppUsageTracker.transferDominant(3, 5))
        // 转移 5 / 总量 8 → 占比 0.625，次数与占比都够，占主导
        assertEquals(true, AppUsageTracker.transferDominant(5, 8))
        // 边界 0.6 算占主导
        assertEquals(true, AppUsageTracker.transferDominant(6, 10))
        // 边界下一点不算
        assertEquals(false, AppUsageTracker.transferDominant(5, 9))
        // 2 次的轶事不算，哪怕占比够
        assertEquals(false, AppUsageTracker.transferDominant(2, 4))
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
    fun `recent count sums only the last 7 days`() {
        val dayCounts = mapOf(
            "20261113" to 3, // 今天
            "20261107" to 2, // 今天往前 6 天，窗口内（第 7 天，计入）
            "20261106" to 9, // 今天往前 7 天，窗口外（第 8 天，不计）
            "20261101" to 7, // 更早，窗口外
            "garbage" to 100, // 脏键不计
            "2026111" to 100 // 位数不对的脏键不计
        )
        assertEquals(5, AppUsageTracker.recentCount(dayCounts, "20261113", 7))
        assertEquals(0, AppUsageTracker.recentCount(emptyMap(), "20261113", 7))
        assertEquals(0, AppUsageTracker.recentCount(dayCounts, "20261113", 0))
        // 窗口 1 天只算今天
        assertEquals(3, AppUsageTracker.recentCount(dayCounts, "20261113", 1))
        // 默认窗口就是 7 天（RECENT_WINDOW_DAYS）
        assertEquals(5, AppUsageTracker.recentCount(dayCounts, "20261113"))
    }

    // ---- dockReason：与排序同源，经常用带 7 天次数 ----

    @Test
    fun `pinned reason wins`() {
        assertEquals(
            "你固定在 Dock 上",
            AppUsageTracker.dockReason(true, 5, 5, 8, "微信", true, 5, 100, 60, true)
        )
    }

    @Test
    fun `day-type reason names weekday and period`() {
        assertEquals(
            "工作日早上 8 点常开",
            AppUsageTracker.dockReason(false, 3, 5, 8, null, false, 0, 10, 10, true)
        )
        assertEquals(
            "周六下午 15 点常开",
            AppUsageTracker.dockReason(false, 4, 6, 15, null, false, 0, 10, 10, true)
        )
        assertEquals(
            "周日夜里 2 点常开",
            AppUsageTracker.dockReason(false, 3, 7, 2, null, false, 0, 10, 10, true)
        )
    }

    @Test
    fun `transfer reason needs dominant ratio`() {
        // 转移 6 / 总量 10 → 占比 0.6、次数够，占主导，仍是“接着开”
        assertEquals(
            "打开 微信 后常接着开",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", true, 6, 10, 10, true)
        )
        // 转移 2 / 总量 4 → 次数不够（要 ≥5 次），小样本不硬说，落到“常开”
        assertEquals(
            "常开 · 近 7 天 4 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", true, 2, 4, 4, true)
        )
        // 转移 5 / 总量 17 → 占比 0.29，枢纽噪音，被占比拦下后落到“常开”
        assertEquals(
            "常开 · 近 7 天 17 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", true, 5, 17, 17, true)
        )
        assertEquals(
            "常开 · 近 7 天 10 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", false, 5, 10, 10, true)
        )
        assertEquals(
            "常开 · 近 7 天 10 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, "微信", true, 1, 10, 10, true)
        )
        assertEquals(
            "常开 · 近 7 天 10 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, true, 5, 10, 10, true)
        )
    }

    @Test
    fun `frequent reason carries the 7 day count`() {
        assertEquals(
            "常开 · 近 7 天 42 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 42, 42, true)
        )
    }

    @Test
    fun `stale app says not opened recently never zero times`() {
        // 窗口记满、总量 >0 但 7 天内一次没开：不说“近 7 天 0 次”，说“最近没怎么开”
        val reason = AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 42, 0, true)
        assertEquals("最近没怎么开", reason)
        assertEquals(false, reason.contains("0 次"))
    }

    @Test
    fun `immature window never claims not opened recently`() {
        // 窗口没满（刚开始记，前面几天根本没观测）：recent == 0 证明不了
        // “7 天没开”，不说“最近没怎么开”，只说以前用得多
        assertEquals(
            "以前常开",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 42, 0, false)
        )
        // 窗口没满但 recent > 0：正数是下限，照实说“近 7 天 X 次”
        assertEquals(
            "常开 · 近 7 天 3 次",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 42, 3, false)
        )
        // 窗口没满且总量为 0：还是“暂无你的使用记录”
        assertEquals(
            "热门应用（暂无你的使用记录）",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 0, 0, false)
        )
    }

    @Test
    fun `no record falls back to hot apps`() {
        assertEquals(
            "热门应用（暂无你的使用记录）",
            AppUsageTracker.dockReason(false, 0, 5, 8, null, false, 0, 0, 0, true)
        )
    }

    // ---- recencyFactor：7 天内不扣分，之后每 7 天减半，不断崖 ----

    @Test
    fun `recency factor is one within 7 days`() {
        val now = 1000L * 60 * 60 * 24 * 100
        val day = 86_400_000L
        assertEquals(1.0, AppUsageTracker.recencyFactor(0L, now), 0.0)
        assertEquals(1.0, AppUsageTracker.recencyFactor(now, now), 0.0)
        assertEquals(1.0, AppUsageTracker.recencyFactor(now - 7 * day, now), 0.0)
    }

    @Test
    fun `recency factor halves every week past day 7`() {
        val now = 1000L * 60 * 60 * 24 * 100
        val day = 86_400_000L
        // 14 天没开剩一半，21 天剩四分之一
        assertEquals(0.5, AppUsageTracker.recencyFactor(now - 14 * day, now), 1e-9)
        assertEquals(0.25, AppUsageTracker.recencyFactor(now - 21 * day, now), 1e-9)
        // 8 天只比 7 天少一点（不断崖），30 天剩约一成
        val f7 = AppUsageTracker.recencyFactor(now - 7 * day, now)
        val f8 = AppUsageTracker.recencyFactor(now - 8 * day, now)
        assertEquals(true, f8 < f7 && f8 > 0.9 * f7)
        val f30 = AppUsageTracker.recencyFactor(now - 30 * day, now)
        assertEquals(true, f30 > 0.09 && f30 < 0.11)
    }

    @Test
    fun `dockScore fades idle apps below recent medium ones`() {
        val now = 1000L * 60 * 60 * 24 * 100
        val day = 86_400_000L
        // 历史总量 50、30 天没开，要低于近期总量 15、今天开过
        val idleHigh = AppUsageTracker.dockScore(0, 50, 0, false, now - 30 * day, now)
        val recentMedium = AppUsageTracker.dockScore(0, 15, 0, false, now, now)
        assertEquals(true, idleHigh < recentMedium)
        // 7 天内没开不扣分：总量 50、7 天没开，仍高于近期总量 15
        val weekIdle = AppUsageTracker.dockScore(0, 50, 0, false, now - 7 * day, now)
        assertEquals(true, weekIdle > recentMedium)
    }

    @Test
    fun `dockScore keeps time bucket dominant despite recency`() {
        val now = 1000L * 60 * 60 * 24 * 100
        val day = 86_400_000L
        // 时段桶 ≥3 照旧 ×10000 主导，近期因子只作用于累计用量部分
        val score = AppUsageTracker.dockScore(3, 50, 0, false, now - 30 * day, now)
        assertEquals(true, score > 30000.0 && score < 30010.0)
    }

    // ---- windowMature：最早 day 记录 ≥6 天前，窗口才算记满 ----

    @Test
    fun `window mature needs 7 days of recording`() {
        // 今天 20261113：最早记录 20261107（6 天前）→ 覆盖 7 天，记满
        assertEquals(true, AppUsageTracker.windowMature("20261107", "20261113"))
        // 最早记录 20261106（7 天前）也记满
        assertEquals(true, AppUsageTracker.windowMature("20261106", "20261113"))
        // 最早记录 20261108（5 天前）→ 只覆盖 6 天，没满
        assertEquals(false, AppUsageTracker.windowMature("20261108", "20261113"))
        // 今天才开始记，没满
        assertEquals(false, AppUsageTracker.windowMature("20261113", "20261113"))
        // 一条记录都没有，没满
        assertEquals(false, AppUsageTracker.windowMature(null, "20261113"))
        // 脏日期不算记满
        assertEquals(false, AppUsageTracker.windowMature("garbage", "20261113"))
        assertEquals(false, AppUsageTracker.windowMature("2026111", "20261113"))
    }
}
