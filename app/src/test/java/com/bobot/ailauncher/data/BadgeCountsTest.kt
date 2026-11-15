package com.bobot.ailauncher.data

import android.app.AppOpsManager
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

    // ---------- 通知 number 最大值 ----------

    @Test
    fun `aggregateNumberMax - empty gives empty map`() {
        assertTrue(aggregateNumberMax(emptyList()).isEmpty())
    }

    @Test
    fun `aggregateNumberMax - zero number ignored`() {
        // 未设 number 为 0，不参与取最大。
        val numbers = listOf("com.example.a" to 0, "com.example.b" to 5)
        assertEquals(mapOf("com.example.b" to 5), aggregateNumberMax(numbers))
    }

    @Test
    fun `aggregateNumberMax - max per package`() {
        val numbers = listOf(
            "com.example.a" to 3,
            "com.example.a" to 15,
            "com.example.a" to 7,
            "com.example.b" to 125
        )
        assertEquals(
            mapOf("com.example.a" to 15, "com.example.b" to 125),
            aggregateNumberMax(numbers)
        )
    }

    // ---------- 三路合并 ----------

    @Test
    fun `combineBadgeCounts - notification count alone is the fallback`() {
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.a" to 3),
            numberMax = emptyMap(),
            smsUnread = 0,
            defaultSmsPackage = null
        )
        assertEquals(mapOf("com.example.a" to 3), combined)
    }

    @Test
    fun `combineBadgeCounts - number wins when larger than notification count`() {
        // 应用自报未读数 125 > 在用通知 2 条，取 125（文案层收成 "99+"）。
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.chat" to 2),
            numberMax = mapOf("com.example.chat" to 125),
            smsUnread = 0,
            defaultSmsPackage = null
        )
        assertEquals(125, combined.getValue("com.example.chat"))
        assertEquals("99+", formatBadgeCount(combined.getValue("com.example.chat")))
    }

    @Test
    fun `combineBadgeCounts - notification count wins when larger than number`() {
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.chat" to 8),
            numberMax = mapOf("com.example.chat" to 3),
            smsUnread = 0,
            defaultSmsPackage = null
        )
        assertEquals(mapOf("com.example.chat" to 8), combined)
    }

    @Test
    fun `combineBadgeCounts - sms unread applies only to default sms package`() {
        // 短信库 15 条未读：默认短信应用包取 max(15, 通知 2 条) = 15；
        // 其他包不受短信数影响。
        val combined = combineBadgeCounts(
            notifCounts = mapOf(
                "com.example.sms" to 2,
                "com.example.chat" to 4
            ),
            numberMax = emptyMap(),
            smsUnread = 15,
            defaultSmsPackage = "com.example.sms"
        )
        assertEquals(
            mapOf("com.example.sms" to 15, "com.example.chat" to 4),
            combined
        )
    }

    @Test
    fun `combineBadgeCounts - sms package with no notifications still shows unread`() {
        // 通知栏已清（通知 0 条、number 0），短信库仍有 15 条未读——
        // 这正是 Bob 真机报的场景：短信没角标的根因。
        val combined = combineBadgeCounts(
            notifCounts = emptyMap(),
            numberMax = emptyMap(),
            smsUnread = 15,
            defaultSmsPackage = "com.example.sms"
        )
        assertEquals(mapOf("com.example.sms" to 15), combined)
    }

    @Test
    fun `combineBadgeCounts - sms unread ignored without default package`() {
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.chat" to 2),
            numberMax = emptyMap(),
            smsUnread = 15,
            defaultSmsPackage = null
        )
        assertEquals(mapOf("com.example.chat" to 2), combined)
    }

    @Test
    fun `combineBadgeCounts - all zero gives empty map`() {
        val combined = combineBadgeCounts(
            notifCounts = emptyMap(),
            numberMax = mapOf("com.example.a" to 0),
            smsUnread = 0,
            defaultSmsPackage = "com.example.sms"
        )
        assertTrue(combined.isEmpty())
    }

    // ---------- 短信数双包归属（Bob 2026-11-08 凌晨：归属加固作兜底） ----------

    @Test
    fun `combineBadgeCounts - same sms package recorded once`() {
        // 默认短信包与可见短信应用是同一包：短信数只记一处。
        val combined = combineBadgeCounts(
            notifCounts = emptyMap(),
            numberMax = emptyMap(),
            smsUnread = 6,
            smsPackages = listOf("com.example.sms", "com.example.sms")
        )
        assertEquals(mapOf("com.example.sms" to 6), combined)
    }

    @Test
    fun `combineBadgeCounts - different sms packages both get the count`() {
        // 默认短信包与可见短信应用不同：两处都记上，各取最大值。
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.sms" to 2),
            numberMax = emptyMap(),
            smsUnread = 6,
            smsPackages = listOf("com.example.sms", "com.example.sms2")
        )
        assertEquals(
            mapOf("com.example.sms" to 6, "com.example.sms2" to 6),
            combined
        )
    }

    @Test
    fun `combineBadgeCounts - null default only visible sms app gets the count`() {
        val combined = combineBadgeCounts(
            notifCounts = emptyMap(),
            numberMax = emptyMap(),
            smsUnread = 6,
            smsPackages = listOf("com.example.sms2")
        )
        assertEquals(mapOf("com.example.sms2" to 6), combined)
    }

    @Test
    fun `combineBadgeCounts - zero sms count enters no map`() {
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.chat" to 2),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = listOf("com.example.sms", "com.example.sms2")
        )
        assertEquals(mapOf("com.example.chat" to 2), combined)
    }

    // ---------- 探针第四路（角标广播 + ZTE provider） ----------

    @Test
    fun `combineBadgeCounts - probe counts merge with max`() {
        // 探针读到飞书 125：与通知 0 条、number 0 取最大，进第四路。
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.chat" to 2),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = emptyList(),
            extraCounts = mapOf("com.example.feishu" to 125)
        )
        assertEquals(
            mapOf("com.example.chat" to 2, "com.example.feishu" to 125),
            combined
        )
        assertEquals("99+", formatBadgeCount(combined.getValue("com.example.feishu")))
    }

    @Test
    fun `combineBadgeCounts - probe count does not override larger local count`() {
        // 探针 3 条 < 通知 8 条，取 8。
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.chat" to 8),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = emptyList(),
            extraCounts = mapOf("com.example.chat" to 3)
        )
        assertEquals(mapOf("com.example.chat" to 8), combined)
    }

    @Test
    fun `combineBadgeCounts - empty probe counts change nothing`() {
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.chat" to 2),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = emptyList(),
            extraCounts = emptyMap()
        )
        assertEquals(mapOf("com.example.chat" to 2), combined)
    }

    // ---------- 粘性数第五路（Bob 2026-11-08） ----------

    @Test
    fun `combineBadgeCounts - empty sticky counts change nothing`() {
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.chat" to 2),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = emptyList(),
            stickyCounts = emptyMap()
        )
        assertEquals(mapOf("com.example.chat" to 2), combined)
    }

    @Test
    fun `combineBadgeCounts - sticky larger than all other sources wins`() {
        // 通知栏已清（notifCounts/numberMax 为空），粘住的 125 仍让角标显示 125。
        val combined = combineBadgeCounts(
            notifCounts = emptyMap(),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = emptyList(),
            stickyCounts = mapOf("com.ss.android.lark" to 125)
        )
        assertEquals(mapOf("com.ss.android.lark" to 125), combined)
    }

    @Test
    fun `combineBadgeCounts - larger live source beats sticky`() {
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.ss.android.lark" to 130),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = emptyList(),
            stickyCounts = mapOf("com.ss.android.lark" to 125)
        )
        assertEquals(mapOf("com.ss.android.lark" to 130), combined)
    }

    @Test
    fun `combineBadgeCounts - sticky combines with probe as max of all`() {
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.chat" to 2),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = emptyList(),
            extraCounts = mapOf("com.example.chat" to 8),
            stickyCounts = mapOf("com.example.chat" to 5, "com.ss.android.lark" to 125)
        )
        assertEquals(
            mapOf("com.example.chat" to 8, "com.ss.android.lark" to 125),
            combined
        )
    }

    // ---------- 通知权限门控（Bob 2026-11-15：通知关了，角标里的通知那份就清掉） ----------

    @Test
    fun `postNotificationAllowed - allowed is on`() {
        assertTrue(postNotificationAllowed(AppOpsManager.MODE_ALLOWED, false))
        assertTrue(postNotificationAllowed(AppOpsManager.MODE_ALLOWED, true))
    }

    @Test
    fun `postNotificationAllowed - ignored and errored are off`() {
        assertTrue(!postNotificationAllowed(AppOpsManager.MODE_IGNORED, true))
        assertTrue(!postNotificationAllowed(AppOpsManager.MODE_ERRORED, true))
    }

    @Test
    fun `postNotificationAllowed - default falls back to permission`() {
        // MODE_DEFAULT 是“没记过明确决定”：持有权限算开，没持有算关。
        assertTrue(postNotificationAllowed(AppOpsManager.MODE_DEFAULT, true))
        assertTrue(!postNotificationAllowed(AppOpsManager.MODE_DEFAULT, false))
    }

    @Test
    fun `postNotificationAllowed - unknown mode fails safe to on`() {
        assertTrue(postNotificationAllowed(99, false))
    }

    @Test
    fun `combineBadgeCounts - notif off package drops sticky only`() {
        // Bob 真机：抖音通知权限关着、粘性数 2——门控后角标归零、离开未读组。
        val combined = combineBadgeCounts(
            notifCounts = emptyMap(),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = emptyList(),
            stickyCounts = mapOf("com.example.douyin" to 2),
            notifOffPackages = setOf("com.example.douyin")
        )
        assertTrue(combined.isEmpty())
    }

    @Test
    fun `combineBadgeCounts - notif off package drops live notif signals too`() {
        // 权限刚关、旧的在用通知/number 还没刷新走时，同样不计。
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.douyin" to 3),
            numberMax = mapOf("com.example.douyin" to 5),
            smsUnread = 0,
            smsPackages = emptyList(),
            stickyCounts = mapOf("com.example.douyin" to 2),
            notifOffPackages = setOf("com.example.douyin")
        )
        assertTrue(combined.isEmpty())
    }

    @Test
    fun `combineBadgeCounts - notif off package keeps probe counts`() {
        // 探针是独立通道（应用自己发的角标广播 / 系统桌面自己的角标库），
        // 不走我们的通知监听，权限关了也不动它。
        val combined = combineBadgeCounts(
            notifCounts = emptyMap(),
            numberMax = emptyMap(),
            smsUnread = 0,
            smsPackages = emptyList(),
            extraCounts = mapOf("com.example.douyin" to 4),
            stickyCounts = mapOf("com.example.douyin" to 2),
            notifOffPackages = setOf("com.example.douyin")
        )
        assertEquals(mapOf("com.example.douyin" to 4), combined)
    }

    @Test
    fun `combineBadgeCounts - notif off sms package keeps sms unread`() {
        // 短信数与通知无关：短信应用就算通知关了，短信未读照常显示。
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.sms" to 2),
            numberMax = emptyMap(),
            smsUnread = 15,
            smsPackages = listOf("com.example.sms"),
            stickyCounts = mapOf("com.example.sms" to 3),
            notifOffPackages = setOf("com.example.sms")
        )
        assertEquals(mapOf("com.example.sms" to 15), combined)
    }

    @Test
    fun `combineBadgeCounts - notif off only affects listed packages`() {
        // 一个包关、一个包开：开的包三路信号照常，关的包只剩探针。
        val combined = combineBadgeCounts(
            notifCounts = mapOf("com.example.off" to 2, "com.example.on" to 3),
            numberMax = mapOf("com.example.on" to 7),
            smsUnread = 0,
            smsPackages = emptyList(),
            extraCounts = mapOf("com.example.off" to 1),
            stickyCounts = mapOf("com.example.off" to 5),
            notifOffPackages = setOf("com.example.off")
        )
        assertEquals(
            mapOf("com.example.off" to 1, "com.example.on" to 7),
            combined
        )
    }
}
