package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未读角标纯逻辑单测（Bob 2026-11-07 晚：Dock + 应用中心显示消息数量）。
 * 覆盖数字格式化（0 不显示 / 1–99 显示数字 / ≥100 显示 "99+"）与按包名
 * 汇总（截断坑：多包合计超 20 条时计数仍准，不共用展示列表的 take(20)）。
 */
class BadgeCountsTest {

    private fun notif(pkg: String, time: Long) = SimpleNotification(
        appName = pkg,
        title = "t",
        text = "x",
        time = time,
        packageName = pkg
    )

    // ---------- 数字格式化 ----------

    @Test
    fun `formatBadgeCount - zero and negative show nothing`() {
        assertNull(formatBadgeCount(0))
        assertNull(formatBadgeCount(-1))
    }

    @Test
    fun `formatBadgeCount - 1 to 99 show the number`() {
        assertEquals("1", formatBadgeCount(1))
        assertEquals("9", formatBadgeCount(9))
        assertEquals("15", formatBadgeCount(15))
        assertEquals("99", formatBadgeCount(99))
    }

    @Test
    fun `formatBadgeCount - 100 and above show 99 plus`() {
        assertEquals("99+", formatBadgeCount(100))
        assertEquals("99+", formatBadgeCount(125))
        assertEquals("99+", formatBadgeCount(999))
    }

    // ---------- 按包名汇总 ----------

    @Test
    fun `aggregateBadgeCounts - empty list gives empty map`() {
        assertTrue(aggregateBadgeCounts(emptyList()).isEmpty())
    }

    @Test
    fun `aggregateBadgeCounts - single package counted`() {
        val list = (0 until 3).map { notif("com.example.sms", it.toLong()) }
        assertEquals(mapOf("com.example.sms" to 3), aggregateBadgeCounts(list))
    }

    @Test
    fun `aggregateBadgeCounts - over 20 total across packages still exact`() {
        // 截断坑：展示列表只取最新 20 条；计数若共用它，15+15 会被截成 20 条以内。
        // 汇总输入是完整列表，两包各 15 条必须原样计出。
        val list = (0 until 15).map { notif("com.example.a", it.toLong()) } +
            (0 until 15).map { notif("com.example.b", (100 + it).toLong()) }
        assertEquals(
            mapOf("com.example.a" to 15, "com.example.b" to 15),
            aggregateBadgeCounts(list)
        )
    }

    @Test
    fun `aggregateBadgeCounts - 125 from one package stays 125`() {
        val list = (0 until 125).map { notif("com.example.chat", it.toLong()) }
        val counts = aggregateBadgeCounts(list)
        assertEquals(125, counts["com.example.chat"])
        // 文案层再收成 "99+"（Bob 亲选口径）。
        assertEquals("99+", formatBadgeCount(counts.getValue("com.example.chat")))
    }
}
