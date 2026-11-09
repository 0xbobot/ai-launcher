package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 智能卡片排序/去重/跳过/过期单测（447）：纯函数、JVM 可跑。
 * 规则（设计稿 §4.2–§4.3；第十二批改跳过口径）：按截止点排、最早在最上面；
 * 同一件事只排一次（短信那条为准）；跳过＝本轮收起来（移出可见列表，
 * 角标跟着掉），不是沉底；新卡到了开新一轮，划走的卡回来（摞底）；
 * 过了截止点即消失。
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
        val cal = card("c", SmartCardKind.CALENDAR, at, at)
        val missed = card("m", SmartCardKind.MISSED_CALL, at, at)
        val guide = card("g", SmartCardKind.GUIDE, at, at)
        assertEquals(
            listOf("b", "a", "c", "m", "n", "g"),
            sortSmartCards(listOf(guide, notif, missed, cal, appt, bill)).map { it.id }
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
    fun visible_singleCardSkipped_emptiesList() {
        // 只有一张卡、划走 → 可见列表空、角标 0（第十二批 Bob 实拍 bug：
        // 旧口径只沉底，角标还数着它；新口径移出可见列表）。
        val now = ms(2026, 11, 5, 15, 0)
        val only = card("only", SmartCardKind.BILL, 100, ms(2026, 11, 7))
        val visible = visibleSmartCards(
            listOf(only), doneIds = emptySet(),
            skippedIds = listOf("only"), nowMs = now
        )
        assertEquals(emptyList<String>(), visible.map { it.id })
        assertEquals(0, visible.size)
    }

    @Test
    fun visible_skippedTop_nextComesUpAndBadgeDropsByOne() {
        // 多张卡划走顶卡 → 下一张上顶、角标减一；划走的那张不在列表里。
        val now = ms(2026, 11, 5, 15, 0)
        val a = card("a", SmartCardKind.BILL, 100, ms(2026, 11, 7))
        val b = card("b", SmartCardKind.APPOINTMENT, 200, ms(2026, 11, 7))
        val c = card("c", SmartCardKind.MISSED_CALL, 300, ms(2026, 11, 7))
        val visible = visibleSmartCards(
            listOf(a, b, c), doneIds = emptySet(),
            skippedIds = listOf("a"), nowMs = now
        )
        assertEquals(listOf("b", "c"), visible.map { it.id })
    }

    @Test
    fun visible_skippedReturnsWhenSkipSetCleared_newRound() {
        // 新卡到了开新一轮：仓库清空跳过集合，上一轮划走的卡回来（摞底）。
        val now = ms(2026, 11, 5, 15, 0)
        val a = card("a", SmartCardKind.BILL, 300, ms(2026, 11, 7))
        val b = card("b", SmartCardKind.APPOINTMENT, 200, ms(2026, 11, 7))
        val newCard = card("new", SmartCardKind.CALENDAR, 50, ms(2026, 11, 7))
        val thisRound = visibleSmartCards(
            listOf(a, b), doneIds = emptySet(),
            skippedIds = listOf("a"), nowMs = now
        )
        assertEquals(listOf("b"), thisRound.map { it.id })
        // 新一轮：跳过集合已清空（仓库 clearSkipped），a 回到摞底、
        // 新卡排在前面（sortMs 更早）。
        val nextRound = visibleSmartCards(
            listOf(a, b, newCard), doneIds = emptySet(),
            skippedIds = emptyList(), nowMs = now
        )
        assertEquals(listOf("new", "b", "a"), nextRound.map { it.id })
    }

    @Test
    fun shouldStartNewRound_onlyUnseenIdStartsNewRound() {
        assertTrue(shouldStartNewRound(setOf("a", "b"), setOf("a", "b", "c")))
        assertTrue(shouldStartNewRound(emptySet(), setOf("a")))
        assertTrue(!shouldStartNewRound(setOf("a", "b"), setOf("a", "b")))
        assertTrue(!shouldStartNewRound(setOf("a", "b"), setOf("a")))
        assertTrue(!shouldStartNewRound(setOf("a"), emptySet()))
    }

    @Test
    fun aliveSmartCardIds_excludesDoneExpiredAndNotYetAppeared() {
        val now = ms(2026, 11, 5, 15, 0)
        val alive = card("alive", SmartCardKind.BILL, 100, ms(2026, 11, 7))
        val done = card("done", SmartCardKind.BILL, 50, ms(2026, 11, 7))
        val expired = card("expired", SmartCardKind.BILL, 10, ms(2026, 11, 5, 14, 0))
        val future = card(
            "future", SmartCardKind.BILL, 300, ms(2026, 11, 7),
            appearFromMs = ms(2026, 11, 6)
        )
        // 跳过不影响存活：划走的卡还活着，只是不显示——不然它一划走
        // 就从 seen 里掉出去，下次刷新会误判新一轮。
        val skipped = card("skipped", SmartCardKind.BILL, 150, ms(2026, 11, 7))
        assertEquals(
            setOf("alive", "skipped"),
            aliveSmartCardIds(
                listOf(expired, done, future, alive, skipped),
                doneIds = setOf("done"), nowMs = now
            )
        )
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
    fun visible_skippedRemovedNotLast() {
        // 旧口径“跳过沉底”已作废（第十二批）：跳过的卡不在可见列表里。
        val now = ms(2026, 11, 5, 15, 0)
        val a = card("a", SmartCardKind.BILL, 100, ms(2026, 11, 7))
        val b = card("b", SmartCardKind.APPOINTMENT, 200, ms(2026, 11, 7))
        val visible = visibleSmartCards(
            listOf(a, b), doneIds = emptySet(),
            skippedIds = listOf("a"), nowMs = now
        )
        assertEquals(listOf("b"), visible.map { it.id })
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
