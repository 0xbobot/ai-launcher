package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogLogicTest {

    @Test
    fun `file name round trips epoch millis`() {
        assertEquals("crash-1729000000000.log", CrashLogLogic.fileNameFor(1729000000000L))
        assertEquals(1729000000000L, CrashLogLogic.parseEpochMillis("crash-1729000000000.log"))
    }

    @Test
    fun `parse rejects non crash files`() {
        assertNull(CrashLogLogic.parseEpochMillis("diagnostics.log"))
        assertNull(CrashLogLogic.parseEpochMillis("crash-abc.log"))
        assertNull(CrashLogLogic.parseEpochMillis("crash-123.txt"))
        assertNull(CrashLogLogic.parseEpochMillis("other-123.log"))
    }

    @Test
    fun `trim plan keeps newest ten and deletes oldest first`() {
        val names = (1L..13L).map { CrashLogLogic.fileNameFor(it * 1000) }
        val plan = CrashLogLogic.trimPlan(names)
        assertEquals(
            listOf(
                CrashLogLogic.fileNameFor(1000),
                CrashLogLogic.fileNameFor(2000),
                CrashLogLogic.fileNameFor(3000)
            ),
            plan
        )
    }

    @Test
    fun `trim plan is empty at or under the cap`() {
        val names = (1L..10L).map { CrashLogLogic.fileNameFor(it * 1000) }
        assertTrue(CrashLogLogic.trimPlan(names).isEmpty())
        assertTrue(CrashLogLogic.trimPlan(emptyList()).isEmpty())
    }

    @Test
    fun `trim plan ignores non crash files`() {
        val names = (1L..10L).map { CrashLogLogic.fileNameFor(it * 1000) } + "diagnostics.log"
        // 诊断日志不占崩溃名额：10 份崩溃 + 1 个诊断文件 → 无需裁剪
        assertTrue(CrashLogLogic.trimPlan(names).isEmpty())
    }

    @Test
    fun `title is the first stack line after the separator`() {
        val content = buildString {
            appendLine("AI桌面崩溃记录")
            appendLine("time: 2026-10-22 23:41:02")
            appendLine("---")
            appendLine("java.lang.IllegalStateException: boom")
            appendLine("\tat com.example.Foo.bar(Foo.kt:1)")
        }
        assertEquals(
            "java.lang.IllegalStateException: boom",
            CrashLogLogic.titleOf(content)
        )
    }

    @Test
    fun `title falls back to first non blank line without separator`() {
        assertEquals("第一行", CrashLogLogic.titleOf("\n  \n第一行\n第二行"))
        assertEquals("未知异常", CrashLogLogic.titleOf(""))
        assertEquals("未知异常", CrashLogLogic.titleOf("---\n"))
    }

    @Test
    fun `unread epochs are strictly after the read watermark`() {
        assertEquals(
            listOf(200L, 300L),
            CrashLogLogic.unreadEpochs(listOf(100L, 200L, 300L), readUpTo = 100L)
        )
        assertTrue(CrashLogLogic.unreadEpochs(listOf(100L), readUpTo = 100L).isEmpty())
    }

    @Test
    fun `should prompt only for an unread crash not yet prompted`() {
        val epochs = listOf(100L, 200L)
        // 有未读且没提示过 → 提示
        assertTrue(CrashLogLogic.shouldPrompt(epochs, readUpTo = 0L, promptedUpTo = 0L))
        // 已为最新一份提示过 → 不再提示
        assertFalse(CrashLogLogic.shouldPrompt(epochs, readUpTo = 0L, promptedUpTo = 200L))
        // 全部已读 → 不提示
        assertFalse(CrashLogLogic.shouldPrompt(epochs, readUpTo = 200L, promptedUpTo = 0L))
        // 没有记录 → 不提示
        assertFalse(CrashLogLogic.shouldPrompt(emptyList(), readUpTo = 0L, promptedUpTo = 0L))
        // 读过旧的、又来了新的 → 再提示一次
        assertTrue(CrashLogLogic.shouldPrompt(epochs, readUpTo = 100L, promptedUpTo = 100L))
    }
}
