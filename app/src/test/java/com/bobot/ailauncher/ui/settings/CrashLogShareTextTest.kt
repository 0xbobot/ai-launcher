package com.bobot.ailauncher.ui.settings

import com.bobot.ailauncher.data.CrashRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** crashLogShareText（正式版审计 ②-1）：分享用纯文本，逐字锁死。 */
class CrashLogShareTextTest {

    @Test
    fun emptyReturnsEmpty() {
        assertEquals("", crashLogShareText(emptyList()))
    }

    @Test
    fun oneRecordKeepsTimeTitleAndFullContent() {
        val record = CrashRecord(
            epochMillis = 1763174400000L,
            title = "java.lang.NullPointerException: boom",
            content = "AI桌面崩溃记录\ntime: 2026-11-15 08:00\n---\njava.lang.NullPointerException: boom"
        )
        val text = crashLogShareText(listOf(record))
        // 时间行、标题、全文都在（时间格式随设备时区，不锁死具体值）
        assertTrue(text.contains("java.lang.NullPointerException: boom"))
        assertTrue(text.contains("AI桌面崩溃记录"))
        assertTrue(text.contains("time: 2026-11-15 08:00"))
    }

    @Test
    fun multipleRecordsSeparatedInOrder() {
        val a = CrashRecord(1L, "a", "content-a")
        val b = CrashRecord(2L, "b", "content-b")
        val text = crashLogShareText(listOf(a, b))
        assertTrue(text.indexOf("content-a") < text.indexOf("content-b"))
        assertTrue(text.contains("\n\n---\n\n"))
    }
}
