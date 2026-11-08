package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 未读预览行纯逻辑单测（Bob 2026-11-08 08:02 反馈①：未读组直接显示未读信息预览；
 * 2026-11-08 18:37 反馈：通知预览组装标题 + 正文、截断 40 → 60 字）。
 * 覆盖：截断约 60 字、单行化、通知标题 + 正文组装（去重/单方/空处理）、
 * 短信应用取短信正文、短信无正文时退回通知正文、都没正文时不显示预览行。
 */
class UnreadPreviewTest {

    private fun notif(pkg: String, title: String = "", text: String = "") = SimpleNotification(
        appName = pkg,
        title = title,
        text = text,
        time = 1000L,
        packageName = pkg
    )

    // ---------- 截断 ----------

    @Test
    fun `truncatePreview - short text is unchanged`() {
        assertEquals("你好", truncatePreview("你好"))
        assertEquals("a".repeat(60), truncatePreview("a".repeat(60)))
    }

    @Test
    fun `truncatePreview - long text is cut to 60 chars plus ellipsis`() {
        val long = "这是一条很长的未读消息正文，用来验证预览行截断约六十个字之后会加上省略号，超过六十字的部分会被截掉，这句是凑字数的尾巴，再补几个字凑满六十以上。"
        val cut = truncatePreview(long)
        assertEquals(long.take(60).trimEnd() + "…", cut)
        assertEquals(61, cut.length)
    }

    @Test
    fun `truncatePreview - newlines become spaces and edges are trimmed`() {
        assertEquals("第一行 第二行", truncatePreview("  第一行\n第二行  "))
        assertEquals("a b", truncatePreview("a\rb"))
    }

    // ---------- 通知预览 ----------

    @Test
    fun `notificationPreviewText - null notification gives null`() {
        assertNull(notificationPreviewText(null))
    }

    @Test
    fun `notificationPreviewText - title and text are joined`() {
        assertEquals("标题 · 正文内容", notificationPreviewText(notif("com.example.chat", title = "标题", text = "正文内容")))
    }

    @Test
    fun `notificationPreviewText - doubao case keeps the title`() {
        assertEquals(
            "做测试 · 今天 19:07 - 20:07",
            notificationPreviewText(notif("com.larus.nova", title = "做测试", text = "今天 19:07 - 20:07"))
        )
    }

    @Test
    fun `notificationPreviewText - title equal to text is shown once`() {
        assertEquals("同一句话", notificationPreviewText(notif("com.example.chat", title = "同一句话", text = "同一句话")))
    }

    @Test
    fun `notificationPreviewText - only title is shown as is`() {
        assertEquals("只有标题", notificationPreviewText(notif("com.example.chat", title = "只有标题", text = "   ")))
    }

    @Test
    fun `notificationPreviewText - only text is shown as is`() {
        assertEquals("只有正文", notificationPreviewText(notif("com.example.chat", title = "  ", text = "只有正文")))
    }

    @Test
    fun `notificationPreviewText - blank title and text give null`() {
        assertNull(notificationPreviewText(notif("com.example.chat", title = "  ", text = "")))
    }

    @Test
    fun `notificationPreviewText - long title plus text is truncated to 60`() {
        val long = "x".repeat(70)
        assertEquals("x".repeat(60) + "…", notificationPreviewText(notif("com.example.chat", text = long)))
        val joined = notificationPreviewText(notif("com.example.chat", title = "标题", text = "y".repeat(70)))
        assertEquals(("标题 · " + "y".repeat(70)).take(60).trimEnd() + "…", joined)
    }

    // ---------- 短信预览 ----------

    @Test
    fun `smsPreviewText - null or blank body gives null`() {
        assertNull(smsPreviewText(null))
        assertNull(smsPreviewText(""))
        assertNull(smsPreviewText("   "))
    }

    @Test
    fun `smsPreviewText - body is truncated like a notification`() {
        assertEquals("短信正文", smsPreviewText("短信正文"))
        assertEquals("y".repeat(60) + "…", smsPreviewText("y".repeat(70)))
    }

    // ---------- 按应用取预览 ----------

    @Test
    fun `previewForApp - sms app uses the sms body`() {
        val preview = previewForApp(
            packageName = "com.android.mms",
            latestByPackage = mapOf("com.android.mms" to notif("com.android.mms", text = "通知正文")),
            smsPackages = setOf("com.android.mms"),
            smsBody = "短信正文片段"
        )
        assertEquals("短信正文片段", preview)
    }

    @Test
    fun `previewForApp - sms app falls back to notification when no unread sms`() {
        val preview = previewForApp(
            packageName = "com.android.mms",
            latestByPackage = mapOf("com.android.mms" to notif("com.android.mms", title = "标题", text = "通知正文")),
            smsPackages = setOf("com.android.mms"),
            smsBody = null
        )
        assertEquals("标题 · 通知正文", preview)
    }

    @Test
    fun `previewForApp - non-sms app uses the latest notification`() {
        val preview = previewForApp(
            packageName = "com.example.chat",
            latestByPackage = mapOf("com.example.chat" to notif("com.example.chat", title = "标题", text = "最新消息")),
            smsPackages = setOf("com.android.mms"),
            smsBody = "短信正文片段"
        )
        assertEquals("标题 · 最新消息", preview)
    }

    @Test
    fun `previewForApp - no notification and not sms gives null`() {
        assertNull(
            previewForApp(
                packageName = "com.ss.android.lark",
                latestByPackage = emptyMap(),
                smsPackages = setOf("com.android.mms"),
                smsBody = null
            )
        )
    }

    // ---------- 每包最新一条在用通知（2026-11-08 应用中心崩溃 hotfix：去 `!!`） ----------

    @Test
    fun `latestNotificationByPackage - empty list gives empty map`() {
        assertEquals(emptyMap<String, SimpleNotification>(), latestNotificationByPackage(emptyList()))
    }

    @Test
    fun `latestNotificationByPackage - one per package is itself`() {
        val a = notif("com.example.a", text = "a1").copy(time = 100L)
        val b = notif("com.example.b", text = "b1").copy(time = 200L)
        val out = latestNotificationByPackage(listOf(a, b))
        assertEquals(mapOf("com.example.a" to a, "com.example.b" to b), out)
    }

    @Test
    fun `latestNotificationByPackage - same package keeps the latest by time`() {
        val old = notif("com.example.a", text = "old").copy(time = 100L)
        val newer = notif("com.example.a", text = "new").copy(time = 300L)
        val mid = notif("com.example.a", text = "mid").copy(time = 200L)
        val out = latestNotificationByPackage(listOf(old, newer, mid))
        assertEquals(1, out.size)
        assertEquals("new", out["com.example.a"]?.text)
    }

    @Test
    fun `latestNotificationByPackage - mixed packages keep latest per package`() {
        val a1 = notif("com.example.a", text = "a1").copy(time = 100L)
        val a2 = notif("com.example.a", text = "a2").copy(time = 400L)
        val b1 = notif("com.example.b", text = "b1").copy(time = 250L)
        val out = latestNotificationByPackage(listOf(a1, b1, a2))
        assertEquals(2, out.size)
        assertEquals("a2", out["com.example.a"]?.text)
        assertEquals("b1", out["com.example.b"]?.text)
    }
}
