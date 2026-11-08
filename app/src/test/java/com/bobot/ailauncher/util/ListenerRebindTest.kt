package com.bobot.ailauncher.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知监听自查重绑判定单测（Bob 2026-11-08 20:42：通知栏有新通知，应用
 * 中心却没有未读组/角标——监听断了就一直断着）。
 * 只测纯函数 shouldRebindListener；ensureListenerBound 读系统状态，
 * 验收走 Bob 真机（诊断卡 + 未读组出现）。
 */
class ListenerRebindTest {

    private val now = 1_000_000L
    private val never = 0L

    @Test
    fun `min interval is 30 seconds`() {
        assertEquals(30_000L, LISTENER_REBIND_MIN_INTERVAL_MILLIS)
    }

    @Test
    fun `not granted never rebinds`() {
        assertFalse(shouldRebindListener(false, false, false, never, now))
        assertFalse(shouldRebindListener(false, true, true, never, now))
    }

    @Test
    fun `granted and connected and bound does nothing`() {
        assertFalse(shouldRebindListener(true, true, true, never, now))
    }

    @Test
    fun `granted but disconnected rebinds`() {
        assertTrue(shouldRebindListener(true, false, false, never, now))
    }

    @Test
    fun `stale connected flag still rebinds when service instance is gone`() {
        // onListenerDisconnected 漏回调时 flag 陈旧地停在 true，但实例已不在：
        // 任一路说“没连上”就重绑。
        assertTrue(shouldRebindListener(true, true, false, never, now))
    }

    @Test
    fun `flag missed connect but instance alive still rebinds`() {
        // 反向陈旧：flag 还没来得及置 true，实例已在——照样重绑一次无害，
        // 系统回绑后 flag 即置 true，下次不再触发。
        assertTrue(shouldRebindListener(true, false, true, never, now))
    }

    @Test
    fun `rebind is throttled within interval`() {
        val justRebound = now - 10_000L
        assertFalse(shouldRebindListener(true, false, false, justRebound, now))
    }

    @Test
    fun `rebind allowed once interval has passed`() {
        val longAgo = now - 31_000L
        assertTrue(shouldRebindListener(true, false, false, longAgo, now))
        val exactly = now - LISTENER_REBIND_MIN_INTERVAL_MILLIS
        assertTrue(shouldRebindListener(true, false, false, exactly, now))
    }

    @Test
    fun `custom interval is respected`() {
        val justRebound = now - 1_000L
        assertFalse(shouldRebindListener(true, false, false, justRebound, now, 60_000L))
        assertTrue(shouldRebindListener(true, false, false, justRebound, now, 500L))
    }
}
