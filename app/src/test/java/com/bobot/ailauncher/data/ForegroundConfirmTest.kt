package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 前台确认纯函数单测（Bob 2026-11-09 12:20 ②：点未读行有时只清消息、不打开
 * 应用——先打开、确认应用真的到了前台再取消；2026-11-11 补：UNKNOWN 不取消、
 * 回退核验 2 秒未见前台则回退直接打开）。
 * 只测纯函数 decideForegroundConfirm / decidePollStep；UsageStats 查询与
 * Handler 轮询是系统调用，验收走 Bob 真机（跳转确认框点取消时通知必须留着；
 * Google 健康行点了 2 秒内应用必须打开）。
 */
class ForegroundConfirmTest {

    private val target = "com.example.chat"
    private val other = "com.example.other"

    @Test
    fun `poll window is 1 second`() {
        assertEquals(200L, FOREGROUND_POLL_INTERVAL_MILLIS)
        assertEquals(5, FOREGROUND_MAX_POLLS)
        assertEquals(200L * 5, 1000L)
    }

    @Test
    fun `target foreground event confirms`() {
        val events = listOf(
            ForegroundEvent("com.bobot.ailauncher", false),
            ForegroundEvent(target, true)
        )
        assertEquals(
            ForegroundConfirmResult.CONFIRMED,
            decideForegroundConfirm(events, target, true)
        )
    }

    @Test
    fun `target opened then left within window still confirms`() {
        val events = listOf(
            ForegroundEvent(target, true),
            ForegroundEvent(target, false),
            ForegroundEvent("com.bobot.ailauncher", true)
        )
        assertEquals(
            ForegroundConfirmResult.CONFIRMED,
            decideForegroundConfirm(events, target, true)
        )
    }

    @Test
    fun `no target event means not seen`() {
        val events = listOf(
            ForegroundEvent("com.bobot.ailauncher", false),
            ForegroundEvent(other, true)
        )
        assertEquals(
            ForegroundConfirmResult.NOT_SEEN,
            decideForegroundConfirm(events, target, true)
        )
    }

    @Test
    fun `empty events with permission means not seen`() {
        assertEquals(
            ForegroundConfirmResult.NOT_SEEN,
            decideForegroundConfirm(emptyList(), target, true)
        )
    }

    @Test
    fun `target background only means not seen`() {
        val events = listOf(ForegroundEvent(target, false))
        assertEquals(
            ForegroundConfirmResult.NOT_SEEN,
            decideForegroundConfirm(events, target, true)
        )
    }

    @Test
    fun `no permission means unknown regardless of events`() {
        val events = listOf(ForegroundEvent(target, true))
        assertEquals(
            ForegroundConfirmResult.UNKNOWN,
            decideForegroundConfirm(events, target, false)
        )
    }

    @Test
    fun `null events with permission means unknown`() {
        assertEquals(
            ForegroundConfirmResult.UNKNOWN,
            decideForegroundConfirm(null, target, true)
        )
    }

    @Test
    fun `fallback window is 2 seconds`() {
        assertEquals(200L, FOREGROUND_POLL_INTERVAL_MILLIS)
        assertEquals(10, FOREGROUND_FALLBACK_MAX_POLLS)
        assertEquals(200L * 10, 2000L)
    }

    @Test
    fun `confirmed step regardless of polls`() {
        assertEquals(
            ForegroundPollStep.CONFIRMED,
            decidePollStep(ForegroundConfirmResult.CONFIRMED, 1, 5)
        )
        assertEquals(
            ForegroundPollStep.CONFIRMED,
            decidePollStep(ForegroundConfirmResult.CONFIRMED, 5, 5)
        )
    }

    @Test
    fun `unknown step never confirms`() {
        assertEquals(
            ForegroundPollStep.UNDECIDABLE,
            decidePollStep(ForegroundConfirmResult.UNKNOWN, 1, 5)
        )
        assertEquals(
            ForegroundPollStep.UNDECIDABLE,
            decidePollStep(ForegroundConfirmResult.UNKNOWN, 5, 5)
        )
    }

    @Test
    fun `not seen before limit continues`() {
        assertEquals(
            ForegroundPollStep.CONTINUE,
            decidePollStep(ForegroundConfirmResult.NOT_SEEN, 1, 5)
        )
        assertEquals(
            ForegroundPollStep.CONTINUE,
            decidePollStep(ForegroundConfirmResult.NOT_SEEN, 4, 5)
        )
    }

    @Test
    fun `not seen at limit exhausts`() {
        assertEquals(
            ForegroundPollStep.EXHAUSTED,
            decidePollStep(ForegroundConfirmResult.NOT_SEEN, 5, 5)
        )
        assertEquals(
            ForegroundPollStep.EXHAUSTED,
            decidePollStep(ForegroundConfirmResult.NOT_SEEN, 6, 5)
        )
    }

    @Test
    fun `fallback window exhausts at ten polls`() {
        assertEquals(
            ForegroundPollStep.CONTINUE,
            decidePollStep(
                ForegroundConfirmResult.NOT_SEEN,
                9,
                FOREGROUND_FALLBACK_MAX_POLLS
            )
        )
        assertEquals(
            ForegroundPollStep.EXHAUSTED,
            decidePollStep(
                ForegroundConfirmResult.NOT_SEEN,
                10,
                FOREGROUND_FALLBACK_MAX_POLLS
            )
        )
    }
}
