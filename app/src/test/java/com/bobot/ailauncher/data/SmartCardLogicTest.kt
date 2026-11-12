package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 智能卡片排序/去重/跳过/过期单测（447）：纯函数、JVM 可跑。
 * 规则（设计稿 §4.2–§4.3；第十四批改跳过口径，Bob 2026-11-10 23:43 定）：
 * 按截止点排、最早在最上面；同一件事只排一次（短信那条为准）；用户的
 * 事项提醒划过沉到摞底（排后面，看完其他卡就轮到它），只有点第一个按钮
 * 「我知道了」才丢；应用推的提醒划过就丢；不再有“新卡到了开新一轮”；
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
    fun isUserMatterCard_matterKindsSinkGuideDiscards() {
        // 划走按卡种分（第十四批）：账单/预约/日历/未接来电/快递航班是用户
        // 的事项，划过沉底；引导卡（以及还没落地的 TIP/UPDATE 种）是应用
        // 推的，划过就丢。skip() 按这个分支记集合，仓库层凭它分流。
        assertTrue(isUserMatterCard(SmartCardKind.BILL))
        assertTrue(isUserMatterCard(SmartCardKind.APPOINTMENT))
        assertTrue(isUserMatterCard(SmartCardKind.CALENDAR))
        assertTrue(isUserMatterCard(SmartCardKind.MISSED_CALL))
        assertTrue(isUserMatterCard(SmartCardKind.NOTIFICATION))
        assertTrue(!isUserMatterCard(SmartCardKind.GUIDE))
    }

    @Test
    fun visible_singleCardSkipped_staysAtBottom() {
        // 只有一张卡、划走 → 它就是摞底那一张，还在可见列表里、角标照数
        // （第十四批：划走 ≠ 丢，丢只靠点「我知道了」）。
        val now = ms(2026, 11, 5, 15, 0)
        val only = card("only", SmartCardKind.BILL, 100, ms(2026, 11, 7))
        val visible = visibleSmartCards(
            listOf(only), doneIds = emptySet(),
            skippedIds = listOf("only"), nowMs = now
        )
        assertEquals(listOf("only"), visible.map { it.id })
        assertEquals(1, visible.size)
    }

    @Test
    fun visible_skippedTop_nextComesUpSkippedSinksToBottom() {
        // 多张卡划走顶卡 → 下一张上顶、划走的那张沉到摞底（还在列表里，
        // 角标照数 3 张）；看完 b、c 就轮到 a。
        val now = ms(2026, 11, 5, 15, 0)
        val a = card("a", SmartCardKind.BILL, 100, ms(2026, 11, 7))
        val b = card("b", SmartCardKind.APPOINTMENT, 200, ms(2026, 11, 7))
        val c = card("c", SmartCardKind.MISSED_CALL, 300, ms(2026, 11, 7))
        val visible = visibleSmartCards(
            listOf(a, b, c), doneIds = emptySet(),
            skippedIds = listOf("a"), nowMs = now
        )
        assertEquals(listOf("b", "c", "a"), visible.map { it.id })
        assertEquals(3, visible.size)
    }

    @Test
    fun visible_skippedInSkipOrderBehindFreshCards() {
        // 划走两张：按 skippedIds 的顺序排在后面；新卡（没划走过）排在前面。
        // 旧口径“新卡到了开新一轮、跳过集合清空”已删——新卡到不改变
        // 划走的卡的位置，它本来就摞底、不用“放回来”。
        val now = ms(2026, 11, 5, 15, 0)
        val a = card("a", SmartCardKind.BILL, 300, ms(2026, 11, 7))
        val b = card("b", SmartCardKind.APPOINTMENT, 200, ms(2026, 11, 7))
        val newCard = card("new", SmartCardKind.CALENDAR, 50, ms(2026, 11, 7))
        val visible = visibleSmartCards(
            listOf(a, b, newCard), doneIds = emptySet(),
            skippedIds = listOf("a", "b"), nowMs = now
        )
        assertEquals(listOf("new", "a", "b"), visible.map { it.id })
    }

    @Test
    fun visible_doneStillRemovesSkippedCard() {
        // 划走的卡点了「我知道了」（done）才丢：done 过滤在沉底之前。
        val now = ms(2026, 11, 5, 15, 0)
        val a = card("a", SmartCardKind.BILL, 100, ms(2026, 11, 7))
        val b = card("b", SmartCardKind.APPOINTMENT, 200, ms(2026, 11, 7))
        val visible = visibleSmartCards(
            listOf(a, b), doneIds = setOf("a"),
            skippedIds = listOf("a"), nowMs = now
        )
        assertEquals(listOf("b"), visible.map { it.id })
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
    fun visible_skippedSinksNotRemoved() {
        // 第十四批：跳过的事项卡沉底，不移出可见列表（482“本轮收起来”作废）。
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

    // 第二十二批：空重建丢卡是否无法解释（refresh 保留＋重试的判定）。
    @Test
    fun unexplainedLoss_aliveCard_returnsTrue() {
        val now = ms(2026, 11, 12, 13, 57)
        val prev = card(
            "cal|1|${ms(2026, 11, 12, 14, 0)}", SmartCardKind.CALENDAR,
            ms(2026, 11, 12, 14, 0), ms(2026, 11, 12, 15, 0),
            appearFromMs = ms(2026, 11, 12, 13, 30)
        )
        assertTrue(hasUnexplainedCardLoss(listOf(prev), emptySet(), now))
    }

    @Test
    fun unexplainedLoss_doneCard_returnsFalse() {
        val now = ms(2026, 11, 12, 13, 57)
        val prev = card(
            "cal|1|${ms(2026, 11, 12, 14, 0)}", SmartCardKind.CALENDAR,
            ms(2026, 11, 12, 14, 0), ms(2026, 11, 12, 15, 0),
            appearFromMs = ms(2026, 11, 12, 13, 30)
        )
        // 点「我知道了」摘卡：消失能解释，不保留、不重试。
        assertFalse(hasUnexplainedCardLoss(listOf(prev), setOf(prev.id), now))
    }

    @Test
    fun unexplainedLoss_expiredCard_returnsFalse() {
        val now = ms(2026, 11, 12, 15, 30)
        val prev = card(
            "cal|1|${ms(2026, 11, 12, 14, 0)}", SmartCardKind.CALENDAR,
            ms(2026, 11, 12, 14, 0), ms(2026, 11, 12, 15, 0),
            appearFromMs = ms(2026, 11, 12, 13, 30)
        )
        // 过了截止点摘卡：消失能解释，不保留、不重试。
        assertFalse(hasUnexplainedCardLoss(listOf(prev), emptySet(), now))
    }

    @Test
    fun unexplainedLoss_notYetAppeared_returnsFalse() {
        val now = ms(2026, 11, 12, 13, 0)
        val prev = card(
            "cal|1|${ms(2026, 11, 12, 14, 0)}", SmartCardKind.CALENDAR,
            ms(2026, 11, 12, 14, 0), ms(2026, 11, 12, 15, 0),
            appearFromMs = ms(2026, 11, 12, 13, 30)
        )
        // 还没到出现窗口：不算丢。
        assertFalse(hasUnexplainedCardLoss(listOf(prev), emptySet(), now))
    }

    @Test
    fun unexplainedLoss_mixedDoneAndAlive_returnsTrue() {
        val now = ms(2026, 11, 12, 13, 57)
        val done = card(
            "bill|1", SmartCardKind.BILL,
            ms(2026, 11, 12, 23, 59), ms(2026, 11, 12, 23, 59)
        )
        val alive = card(
            "cal|1|${ms(2026, 11, 12, 14, 0)}", SmartCardKind.CALENDAR,
            ms(2026, 11, 12, 14, 0), ms(2026, 11, 12, 15, 0),
            appearFromMs = ms(2026, 11, 12, 13, 30)
        )
        assertTrue(
            hasUnexplainedCardLoss(listOf(done, alive), setOf(done.id), now)
        )
    }

    @Test
    fun unexplainedLoss_emptyPrev_returnsFalse() {
        assertFalse(
            hasUnexplainedCardLoss(emptyList(), emptySet(), ms(2026, 11, 12, 13, 57))
        )
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
