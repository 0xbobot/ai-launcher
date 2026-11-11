package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第十九批（设计稿 §4.2）：有时间段的智能提醒（日历/预约）在时间段内
 * 点动作不摘卡、开始后正文加「进行中 · 」。纯函数单测——performAction
 * 本身要 Context、JVM 测不到，按既往做法锁死纯决策函数
 * （shouldKeepCardAfterAction / withInProgressPrefix）。
 */
class SmartCardInProgressTest {

    // 2026-11-06（周五）10:00–11:00 的一场会 / 一个预约：时间段起点
    // 与截止点都按毫秒算。
    private val start = ms(2026, 11, 6, 10, 0)
    private val end = ms(2026, 11, 6, 11, 0)

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, min: Int = 0): Long =
        java.util.Calendar.getInstance().apply {
            set(year, month - 1, day, hour, min, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun card(kind: SmartCardKind, windowStartMs: Long, deadlineMs: Long) =
        SmartCard(
            id = "test|${kind.name}",
            kind = kind,
            sourceLabel = "短信",
            fact = "事实",
            prep = null,
            actionLabel = "动作",
            sortMs = 0L,
            deadlineMs = deadlineMs,
            action = SmartCardAction.LaunchApp("com.example"),
            sourceKey = "test|${kind.name}",
            windowStartMs = windowStartMs
        )

    // -- shouldKeepCardAfterAction：时间段内点动作不摘卡

    @Test
    fun keepCalendarCardInWindow() {
        val c = card(SmartCardKind.CALENDAR, start, end)
        // 开始那一刻与进行中都保留。
        assertTrue(SmartCardActions.shouldKeepCardAfterAction(c, start))
        assertTrue(SmartCardActions.shouldKeepCardAfterAction(c, start + 30 * 60_000L))
    }

    @Test
    fun dropCalendarCardPastDeadline() {
        val c = card(SmartCardKind.CALENDAR, start, end)
        // 结束那一刻起不再保留——过了截止点照常摘。
        assertFalse(SmartCardActions.shouldKeepCardAfterAction(c, end))
        assertFalse(SmartCardActions.shouldKeepCardAfterAction(c, end + 60_000L))
    }

    @Test
    fun keepAppointmentCardInWindow() {
        val c = card(SmartCardKind.APPOINTMENT, start, end)
        assertTrue(SmartCardActions.shouldKeepCardAfterAction(c, start + 60_000L))
    }

    @Test
    fun dropAppointmentCardPastDeadline() {
        val c = card(SmartCardKind.APPOINTMENT, start, end)
        assertFalse(SmartCardActions.shouldKeepCardAfterAction(c, end))
    }

    @Test
    fun dropNonWindowCardsEvenWithFutureDeadline() {
        // 账单 / 未接来电 / 快递航班不是时间段提醒（windowStartMs = 0），
        // 点动作照常算处理完、成功即摘——哪怕截止点在未来。
        val bill = card(SmartCardKind.BILL, 0L, end)
        val missed = card(SmartCardKind.MISSED_CALL, 0L, end)
        val notif = card(SmartCardKind.NOTIFICATION, 0L, end)
        assertFalse(SmartCardActions.shouldKeepCardAfterAction(bill, start))
        assertFalse(SmartCardActions.shouldKeepCardAfterAction(missed, start))
        assertFalse(SmartCardActions.shouldKeepCardAfterAction(notif, start))
    }

    // -- withInProgressPrefix：开始后正文加「进行中 · 」

    @Test
    fun noPrefixBeforeStart() {
        assertEquals("事实", withInProgressPrefix("事实", start, end, start - 60_000L))
    }

    @Test
    fun prefixAtStartAndDuring() {
        assertEquals("进行中 · 事实", withInProgressPrefix("事实", start, end, start))
        assertEquals(
            "进行中 · 事实",
            withInProgressPrefix("事实", start, end, start + 30 * 60_000L)
        )
    }

    @Test
    fun noPrefixAtOrAfterEnd() {
        assertEquals("事实", withInProgressPrefix("事实", start, end, end))
        assertEquals("事实", withInProgressPrefix("事实", start, end, end + 60_000L))
    }

    @Test
    fun prefixIsIdempotent() {
        // 构建层与 refresh 层都会调，同一条事实算两次也不能叠两层；
        // 开始前再算则把旧前缀剥掉。
        assertEquals(
            "进行中 · 事实",
            withInProgressPrefix("进行中 · 事实", start, end, start + 60_000L)
        )
        assertEquals(
            "事实",
            withInProgressPrefix("进行中 · 事实", start, end, start - 60_000L)
        )
    }

    @Test
    fun noPrefixWithoutWindow() {
        // 没有时间段起点的卡（账单等）永不加前缀。
        assertEquals("事实", withInProgressPrefix("事实", 0L, end, start))
    }
}
