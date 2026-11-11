package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未读行跳转日志纯函数单测（Bob 2026-11-11 21:59：删质检、加日志）。
 * 只测拼装 / 解析 / 环形上限 / 合并 / 摘要；落盘与 logcat 镜像是
 * Android 文件 IO，验收走分支 CI + Bob 真机（点一行未读行，再去
 * 设置页“关于”区那行里看那条事件对不对得上）。
 */
class UnreadJumpLogTest {

    private val tap = UnreadJumpLogLogic.Record(
        type = "tap",
        ts = 1_000L,
        tapTs = 1_000L,
        pkg = "com.example.chat",
        hasContentIntent = true,
        path = "content_intent",
        sendResult = "ok",
        launchOk = null,
        fallbackPath = "latch",
    )

    private val outcome = UnreadJumpLogLogic.Record(
        type = "outcome",
        ts = 3_000L,
        tapTs = 1_000L,
        pkg = "com.example.chat",
        cancel = "kept",
        cancelFg = "NOT_SEEN",
        fallbackFired = false,
        latchBg = false,
    )

    @Test
    fun `tap round trips through JSON`() {
        val parsed = UnreadJumpLogLogic.fromJson(UnreadJumpLogLogic.toJson(tap))
        assertEquals(tap, parsed)
    }

    @Test
    fun `outcome round trips through JSON`() {
        val parsed = UnreadJumpLogLogic.fromJson(UnreadJumpLogLogic.toJson(outcome))
        assertEquals(outcome, parsed)
    }

    @Test
    fun `null fields are omitted from JSON`() {
        val json = UnreadJumpLogLogic.toJson(tap)
        assertFalse(json.contains("launchOk"))
        assertFalse(json.contains("cancel"))
    }

    @Test
    fun `privacy guard rejects contentish keys`() {
        val withTitle = UnreadJumpLogLogic.toJson(tap)
            .replace("\"pkg\"", "\"title\":\"你好\", \"pkg\"")
        assertNull(UnreadJumpLogLogic.fromJson(withTitle))
        val withText = UnreadJumpLogLogic.toJson(tap)
            .replace("\"pkg\"", "\"text\":\"转账 500 元\", \"pkg\"")
        assertNull(UnreadJumpLogLogic.fromJson(withText))
    }

    @Test
    fun `malformed lines parse to null`() {
        assertNull(UnreadJumpLogLogic.fromJson("not json"))
        assertNull(UnreadJumpLogLogic.fromJson("{}"))
        assertNull(UnreadJumpLogLogic.fromJson("{\"type\":\"tap\",\"tapTs\":1}"))
        assertNull(UnreadJumpLogLogic.fromJson("{\"type\":\"weird\",\"ts\":1,\"tapTs\":1,\"pkg\":\"x\"}"))
    }

    @Test
    fun `ring cap keeps only the newest 200`() {
        val lines = (1..250).map { UnreadJumpLogLogic.toJson(tap.copy(ts = it.toLong(), tapTs = it.toLong())) }
        val kept = UnreadJumpLogLogic.trimToLast(lines)
        assertEquals(200, kept.size)
        assertEquals(51L, UnreadJumpLogLogic.fromJson(kept.first())!!.ts)
        assertEquals(250L, UnreadJumpLogLogic.fromJson(kept.last())!!.ts)
    }

    @Test
    fun `ring cap keeps short lists untouched`() {
        val lines = (1..50).map { it.toString() }
        assertEquals(lines, UnreadJumpLogLogic.trimToLast(lines))
    }

    @Test
    fun `formatTime has MM-dd HH-mm-ss shape`() {
        assertTrue(UnreadJumpLogLogic.formatTime(1_792_000_000_000L).matches(Regex("\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun `groupByTap pairs tap and outcome and sorts newest first`() {
        val older = UnreadJumpLogLogic.Record(
            type = "tap", ts = 500L, tapTs = 500L, pkg = "com.example.other",
            path = "launch_app", sendResult = "no_intent", launchOk = true,
        )
        val groups = UnreadJumpLogLogic.groupByTap(listOf(tap, older, outcome))
        assertEquals(2, groups.size)
        assertEquals(1_000L, groups[0].tapTs)
        assertEquals(tap, groups[0].tap)
        assertEquals(outcome, groups[0].outcome)
        assertEquals(500L, groups[1].tapTs)
        assertNull(groups[1].outcome)
    }

    @Test
    fun `merge completes only when every armed part arrived`() {
        val start = UnreadJumpLogLogic.OutcomeState(tapTs = 1_000L, pkg = "com.example.chat")
        assertTrue(UnreadJumpLogLogic.isComplete(start))
        val armed = start.copy(expectedCancel = true, expectedFallback = true)
        assertFalse(UnreadJumpLogLogic.isComplete(armed))
        val afterCancel = UnreadJumpLogLogic.withCancel(armed, "kept", "NOT_SEEN", null)
        assertFalse(UnreadJumpLogLogic.isComplete(afterCancel))
        val afterFallback = UnreadJumpLogLogic.withFallback(
            afterCancel, fired = false, opened = null, ms = null,
            fg = "CONFIRMED", fgMs = 800L, bg = null, bgMs = null,
        )
        assertTrue(UnreadJumpLogLogic.isComplete(afterFallback))
    }

    @Test
    fun `toOutcomeRecord carries merged parts and partial flag`() {
        val state = UnreadJumpLogLogic.OutcomeState(
            tapTs = 1_000L, pkg = "com.example.chat",
            expectedCancel = true, expectedFallback = true,
            cancel = "cancelled", cancelFg = "CONFIRMED", cancelFgMs = 600L,
            fallbackFired = false, fallbackFg = "CONFIRMED", fallbackFgMs = 400L,
        )
        val record = UnreadJumpLogLogic.toOutcomeRecord(state, 3_000L, partial = false)
        assertEquals("outcome", record.type)
        assertEquals("cancelled", record.cancel)
        assertEquals(600L, record.cancelFgMs)
        assertEquals(false, record.fallbackFired)
        assertNull(record.partial)
        val partial = UnreadJumpLogLogic.toOutcomeRecord(state, 3_000L, partial = true)
        assertEquals(true, partial.partial)
    }

    @Test
    fun `summary says opened and cancelled when both confirm`() {
        val entry = UnreadJumpLogLogic.GroupedEntry(
            tapTs = 1_000L, pkg = "com.example.chat", tap = tap,
            outcome = outcome.copy(cancel = "cancelled", cancelFg = "CONFIRMED", cancelFgMs = 600L),
        )
        val text = UnreadJumpLogLogic.summarize(entry)
        assertTrue(text.contains("已打开"))
        assertTrue(text.contains("通知已清"))
    }

    @Test
    fun `summary says fallback opened when fallback fired and opened`() {
        val entry = UnreadJumpLogLogic.GroupedEntry(
            tapTs = 1_000L, pkg = "com.example.chat", tap = tap,
            outcome = outcome.copy(
                cancel = "kept", fallbackFired = true, fallbackOpened = true,
                fallbackMs = 2_000L, latchBg = false,
            ),
        )
        assertTrue(UnreadJumpLogLogic.summarize(entry).contains("回退打开了应用"))
    }

    @Test
    fun `summary says send failed and direct open failed`() {
        val failedTap = tap.copy(
            path = "launch_app", sendResult = "error:SecurityException",
            launchOk = false, fallbackPath = "none",
        )
        val entry = UnreadJumpLogLogic.GroupedEntry(
            tapTs = 1_000L, pkg = "com.example.chat", tap = failedTap, outcome = null,
        )
        val text = UnreadJumpLogLogic.summarize(entry)
        assertTrue(text.contains("通知跳转发送失败"))
        assertTrue(text.contains("直接打开也失败"))
    }

    @Test
    fun `summary waits for confirmation when outcome is missing`() {
        val entry = UnreadJumpLogLogic.GroupedEntry(
            tapTs = 1_000L, pkg = "com.example.chat", tap = tap, outcome = null,
        )
        assertTrue(UnreadJumpLogLogic.summarize(entry).contains("等确认"))
    }
}
