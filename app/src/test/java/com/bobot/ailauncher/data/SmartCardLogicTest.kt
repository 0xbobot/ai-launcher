package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 智能卡片排序/去重/跳过/过期单测（447）：纯函数、JVM 可跑。
 * 规则（设计稿 §4.2–§4.3）：按截止点排、最早在最上面；同一件事只排一次
 * （短信那条为准）；跳过沉到摞底、不是消失；过了截止点即消失。
 */
class SmartCardLogicTest {

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun card(
        id: String,
        kind: SmartCardKind,
        sortMs: Long,
        deadlineMs: Long,
        appearFromMs: Long = 0L,
        sourceKey: String = id,
        sourceLabel: String = "短信"
    ) = SmartCard(
        id = id,
        kind = kind,
        sourceLabel = sourceLabel,
        fact = "fact $id",
        prep = null,
        actionLabel = "去还款",
        sortMs = sortMs,
        deadlineMs = deadlineMs,
        action = SmartCardAction.SmsThread("95558"),
        sourceKey = sourceKey,
        appearFromMs = appearFromMs
    )

    @Test
    fun sort_earliestDeadlineFirst() {
        val now = ms(2026, 11, 5, 15, 0)
        val bill = card("bill", SmartCardKind.BILL, ms(2026, 11, 7, 23, 59), ms(2026, 11, 7, 23, 59))
        val appt = card("appt", SmartCardKind.APPOINTMENT, ms(2026, 11, 5, 20, 0), ms(2026, 11, 6, 9, 0))
        val missed = card("missed", SmartCardKind.MISSED_CALL, ms(2026, 11, 5, 14, 0), ms(2026, 11, 6, 14, 0))
        val sorted = sortSmartCards(listOf(bill, appt, missed))
        // 未接来电截止最早（通知后 24 小时内最先到期的是它？按 sortMs：14:00 < 20:00 < 11-07）
        assertEquals(listOf("missed", "appt", "bill"), sorted.map { it.id })
        assertTrue(now < sorted[0].deadlineMs)
    }

    @Test
    fun sort_sameDeadlineKindPriority() {
        val at = ms(2026, 11, 6, 9, 0)
        val notif = card("n", SmartCardKind.NOTIFICATION, at, at)
        val bill = card("b", SmartCardKind.BILL, at, at)
        val appt = card("a", SmartCardKind.APPOINTMENT, at, at)
        assertEquals(
            listOf("b", "a", "n"),
            sortSmartCards(listOf(notif, appt, bill)).map { it.id }
        )
    }

    @Test
    fun dedupe_sameSourceKeyKeepsFirst() {
        val sms = card("sms-bill", SmartCardKind.BILL, 100, 200, sourceKey = "bill|x")
        val notif = card(
            "notif-bill", SmartCardKind.BILL, 100, 200,
            sourceKey = "bill|x", sourceLabel = "通知"
        )
        // 仓库层保证短信候选在前，先到的就是短信那条
        assertEquals(listOf("sms-bill"), dedupeSmartCards(listOf(sms, notif)).map { it.id })
        assertEquals(listOf("notif-bill"), dedupeSmartCards(listOf(notif)).map { it.id })
    }

    @Test
    fun skip_movesToBottomKeepsRelativeOrder() {
        val a = card("a", SmartCardKind.BILL, 100, 900)
        val b = card("b", SmartCardKind.APPOINTMENT, 200, 900)
        val c = card("c", SmartCardKind.MISSED_CALL, 300, 900)
        assertEquals(listOf("a", "c", "b"), applySkipOrder(listOf(a, b, c), listOf("b")).map { it.id })
        assertEquals(listOf("b", "c", "a"), applySkipOrder(listOf(a, b, c), listOf("a")).map { it.id })
        assertEquals(listOf("a", "b", "c"), applySkipOrder(listOf(a, b, c), emptyList()).map { it.id })
    }

    @Test
    fun visible_filtersDoneExpiredAndNotYetAppeared() {
        val now = ms(2026, 11, 5, 15, 0)
        val alive = card("alive", SmartCardKind.BILL, 100, ms(2026, 11, 7))
        val done = card("done", SmartCardKind.BILL, 50, ms(2026, 11, 7))
        val expired = card("expired", SmartCardKind.BILL, 10, ms(2026, 11, 5, 14, 0))
        val future = card(
            "future", SmartCardKind.BILL, 300, ms(2026, 11, 7),
            appearFromMs = ms(2026, 11, 6)
        )
        val visible = visibleSmartCards(
            listOf(expired, done, future, alive),
            doneIds = setOf("done"),
            skippedIds = emptyList(),
            nowMs = now
        )
        assertEquals(listOf("alive"), visible.map { it.id })
    }

    @Test
    fun visible_skippedGoesLast() {
        val now = ms(2026, 11, 5, 15, 0)
        val a = card("a", SmartCardKind.BILL, 100, ms(2026, 11, 7))
        val b = card("b", SmartCardKind.APPOINTMENT, 200, ms(2026, 11, 7))
        val visible = visibleSmartCards(
            listOf(a, b), doneIds = emptySet(),
            skippedIds = listOf("a"), nowMs = now
        )
        assertEquals(listOf("b", "a"), visible.map { it.id })
    }

    @Test
    fun maskPhoneNumber_elevenDigits() {
        assertEquals("138****5678", maskPhoneNumber("13812345678"))
        assertEquals("010****5678", maskPhoneNumber("01012345678"))
        assertEquals("95558", maskPhoneNumber("95558"))
        // 带国家码时按全部数字计（首尾各留 2 位），卡片上只作识别用
        assertEquals("86****78", maskPhoneNumber("+86 13812345678"))
    }

    @Test
    fun formatLastUsed_days() {
        val now = ms(2026, 11, 5, 15, 0)
        assertEquals("还没用过", formatLastUsed(0L, now))
        assertEquals("今天用过", formatLastUsed(ms(2026, 11, 5, 9, 0), now))
        assertEquals("昨天用过", formatLastUsed(ms(2026, 11, 4, 9, 0), now))
        assertEquals("3 天前用过", formatLastUsed(ms(2026, 11, 2, 9, 0), now))
    }
}
