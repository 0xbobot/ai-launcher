package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 门闩回退纯函数与门闩对象单测（Bob 2026-11-11 两例：Google 健康行与微信
 * 「和和美美」行点了没打开——contentIntent.send() 静默无动作时，没用量
 * 统计权限也要能回退：信号是我们自己点按后有没有切到后台）。
 * 只测纯函数 decideLatchFallback 与门闩对象 AppBackgroundLatch（时间算术，
 * 不调系统时钟）；Handler 延迟检查是系统调用，验收走 Bob 真机（微信行点了
 * 2 秒内应用必须打开；跳转框点取消后不出现第二个框）。
 */
class AppBackgroundLatchTest {

    @Before
    fun resetLatch() {
        AppBackgroundLatch.resetForTest()
    }

    @Test
    fun `latch delay is 2 seconds`() {
        assertEquals(2000L, AppBackgroundLatch.LATCH_FALLBACK_DELAY_MILLIS)
    }

    @Test
    fun `backgrounded after tap means no fallback`() {
        assertFalse(decideLatchFallback(true))
    }

    @Test
    fun `never backgrounded means fallback`() {
        assertTrue(decideLatchFallback(false))
    }

    @Test
    fun `fresh latch never backgrounded`() {
        assertFalse(AppBackgroundLatch.backgroundedSince(0L))
        assertFalse(AppBackgroundLatch.backgroundedSince(Long.MAX_VALUE))
    }

    @Test
    fun `mark after tap counts as backgrounded`() {
        AppBackgroundLatch.markBackgrounded(1000L)
        assertTrue(AppBackgroundLatch.backgroundedSince(1000L))
        assertTrue(AppBackgroundLatch.backgroundedSince(999L))
    }

    @Test
    fun `mark before tap does not count`() {
        AppBackgroundLatch.markBackgrounded(1000L)
        assertFalse(AppBackgroundLatch.backgroundedSince(1001L))
    }

    @Test
    fun `latest mark wins`() {
        AppBackgroundLatch.markBackgrounded(1000L)
        AppBackgroundLatch.markBackgrounded(2000L)
        assertTrue(AppBackgroundLatch.backgroundedSince(1500L))
        assertFalse(AppBackgroundLatch.backgroundedSince(2001L))
    }
}
