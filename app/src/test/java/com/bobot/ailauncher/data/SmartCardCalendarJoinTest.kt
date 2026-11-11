package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日历卡「加入会议」join-once 单测（第十六批）：
 * 简报会行已随 486 拿掉，加入的唯一入口是日历卡动作（performAction
 * 的 CalendarEvent 分支）；同一场会 2 秒内只放行第一次（JoinGuard，
 * 原只拦会行连点）。performAction 本身要 Context、JVM 测不到，锁死的
 * 是它用的纯构件：防多发键的口径＋守卫只查不记／成功发出后才记的语义；
 * 分支接线（wasFired→吞掉返回 true、没发出过→开、成功才记）见收据
 * 的 diff 复核＋Bob 真机验收。
 */
class SmartCardCalendarJoinTest {

    // ---------- 防多发键：与 joinCalendarEvent 同一口径 ----------

    @Test
    fun calendarJoinKey_withMeetingUrl() {
        assertEquals(
            "42|https://vc.example/j/1",
            SmartCardActions.calendarJoinKey(42L, "https://vc.example/j/1")
        )
    }

    @Test
    fun calendarJoinKey_withoutMeetingUrl() {
        // 无链接（看日程）也是同一键格式，只是链接部分为空。
        assertEquals("42|", SmartCardActions.calendarJoinKey(42L, null))
    }

    // ---------- 守卫语义：只查不记、成功发出后才记 ----------

    @Test
    fun wasFired_freshGuard_returnsFalse_andDoesNotRecord() {
        val g = JoinGuard()
        assertFalse(g.wasFired("42|https://vc.example/j/1", 1_000L))
        // 只查不记：查完再查，同一键仍没放行过。
        assertFalse(g.wasFired("42|https://vc.example/j/1", 1_100L))
        // 没记过，shouldFire（一次性查＋记）仍放行。
        assertTrue(g.shouldFire("42|https://vc.example/j/1", 1_200L))
    }

    @Test
    fun record_armsGuard_sameEventWithinWindowIsSwallowed() {
        val g = JoinGuard()
        // 模拟 performAction 分支：第一次没发出过→开（假设成功）→记。
        assertFalse(g.wasFired("42|https://vc.example/j/1", 1_000L))
        g.record("42|https://vc.example/j/1", 1_000L)
        // 2 秒内同一场会重复点按直接吞掉。
        assertTrue(g.wasFired("42|https://vc.example/j/1", 1_500L))
        assertTrue(g.wasFired("42|https://vc.example/j/1", 2_999L))
    }

    @Test
    fun record_windowExpiry_differentEventDoNotBlock() {
        val g = JoinGuard()
        g.record("42|https://vc.example/j/1", 1_000L)
        // 窗口外（2 秒整）重新放行。
        assertFalse(g.wasFired("42|https://vc.example/j/1", 3_000L))
        // 不同场互不影响（不同事件、同一事件不同链接）。
        assertFalse(g.wasFired("43|https://vc.example/j/1", 1_100L))
        assertFalse(g.wasFired("42|https://vc.example/j/2", 1_100L))
    }

    @Test
    fun failedJoin_isNotRecorded_canRetry() {
        val g = JoinGuard()
        // 模拟 performAction 分支：第一次没发出过→开→失败→不记。
        assertFalse(g.wasFired("42|", 1_000L))
        // 没记过，2 秒内再点仍会重新尝试打开（失败不摘卡、可重试）。
        assertFalse(g.wasFired("42|", 1_500L))
    }
}
