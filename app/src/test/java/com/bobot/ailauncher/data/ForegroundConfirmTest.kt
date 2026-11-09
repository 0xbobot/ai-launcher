package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 前台确认纯函数单测（Bob 2026-11-09 12:20 ②：点未读行有时只清消息、不打开
 * 应用——先打开、确认应用真的到了前台再取消）。
 * 只测纯函数 decideForegroundConfirm；UsageStats 查询与 Handler 轮询是
 * 系统调用，验收走 Bob 真机（跳转确认框点取消时通知必须留着）。
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
}
