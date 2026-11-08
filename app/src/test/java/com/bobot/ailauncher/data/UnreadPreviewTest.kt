package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 未读预览行纯逻辑单测（Bob 2026-11-08 08:02 反馈①：未读组直接显示未读信息预览）。
 * 覆盖：截断约 40 字、单行化、通知正文优先 text/无 text 取 title、空处理、
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
        assertEquals("a".repeat(40), truncatePreview("a".repeat(40)))
    }

    @Test
    fun `truncatePreview - long text is cut to 40 chars plus ellipsis`() {
        val long = "这是一条很长的未读消息正文，用来验证预览行截断约四十个字之后会加上省略号，超过四十字的部分会被截掉。"
        val cut = truncatePreview(long)
        assertEquals(long.take(40).trimEnd() + "…", cut)
        assertEquals(41, cut.length)
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
    fun `notificationPreviewText - text wins over title`() {
        assertEquals("正文内容", notificationPreviewText(notif("com.example.chat", title = "标题", text = "正文内容")))
    }

    @Test
    fun `notificationPreviewText - title is the fallback when text is blank`() {
        assertEquals("只有标题", notificationPreviewText(notif("com.example.chat", title = "只有标题", text = "   ")))
    }

    @Test
    fun `notificationPreviewText - blank title and text give null`() {
        assertNull(notificationPreviewText(notif("com.example.chat", title = "  ", text = "")))
    }

    @Test
    fun `notificationPreviewText - long text is truncated`() {
        val long = "x".repeat(50)
        assertEquals("x".repeat(40) + "…", notificationPreviewText(notif("com.example.chat", text = long)))
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
        assertEquals("y".repeat(40) + "…", smsPreviewText("y".repeat(50)))
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
            latestByPackage = mapOf("com.android.mms" to notif("com.android.mms", text = "通知正文")),
            smsPackages = setOf("com.android.mms"),
            smsBody = null
        )
        assertEquals("通知正文", preview)
    }

    @Test
    fun `previewForApp - non-sms app uses the latest notification`() {
        val preview = previewForApp(
            packageName = "com.example.chat",
            latestByPackage = mapOf("com.example.chat" to notif("com.example.chat", title = "标题", text = "最新消息")),
            smsPackages = setOf("com.android.mms"),
            smsBody = "短信正文片段"
        )
        assertEquals("最新消息", preview)
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
}
