package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引导卡片单测（448，设计稿 §4.7）：四张卡固定清单＋按授权状态过滤＋
 * 文案不拿用户真实短信内容当例子。纯函数、JVM 可跑。
 */
class GuideCardsTest {

    @Test
    fun guideCards_fourInOrder() {
        val cards = guideCards()
        assertEquals(4, cards.size)
        assertEquals("guide|bill", cards[0].id)
        assertEquals("guide|appointment", cards[1].id)
        assertEquals("guide|missed-call", cards[2].id)
        assertEquals("guide|delivery", cards[3].id)
        assertTrue(cards.all { it.kind == SmartCardKind.GUIDE })
        assertTrue(cards.all { it.actionLabel == "连接" })
        assertTrue(cards.all { it.action is SmartCardAction.Connect })
    }

    @Test
    fun visibleGuideCards_nothingGrantedShowsAll() {
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = false
        )
        assertEquals(4, cards.size)
    }

    @Test
    fun visibleGuideCards_messageGrantedHidesItsTwo() {
        val cards = visibleGuideCards(
            messageGranted = true, notificationGranted = false
        )
        assertEquals(listOf("guide|missed-call", "guide|delivery"),
            cards.map { it.id })
    }

    @Test
    fun visibleGuideCards_notificationGrantedHidesItsTwo() {
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = true
        )
        assertEquals(listOf("guide|bill", "guide|appointment"),
            cards.map { it.id })
    }

    @Test
    fun visibleGuideCards_bothGrantedShowsNone() {
        val cards = visibleGuideCards(
            messageGranted = true, notificationGranted = true
        )
        assertTrue(cards.isEmpty())
    }

    @Test
    fun visibleGuideCards_swipedIdsFiltered() {
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            swipedIds = setOf("guide|bill", "guide|delivery")
        )
        assertEquals(listOf("guide|appointment", "guide|missed-call"),
            cards.map { it.id })
    }

    @Test
    fun visibleGuideCards_dismissedIdsFiltered() {
        // 449：点过“忽略”的卡永久不再出现（不是滑走，滑走只是本轮沉底）
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            dismissedIds = setOf("guide|bill", "guide|missed-call")
        )
        assertEquals(listOf("guide|appointment", "guide|delivery"),
            cards.map { it.id })
    }

    @Test
    fun visibleGuideCards_allDismissedShowsNone() {
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            dismissedIds = setOf(
                "guide|bill", "guide|appointment",
                "guide|missed-call", "guide|delivery"
            )
        )
        assertTrue(cards.isEmpty())
    }

    @Test
    fun guideCards_copyV2Verbatim() {
        // 设计稿 §4.7 v2（449，Bob 真机 verdict 后重写）：先讲为什么，
        // 再讲要连接哪个来源。逐字锁死，不改词句。
        val cards = guideCards()
        assertEquals("不想错过还款日？到期前两天我提醒你。", cards[0].fact)
        assertEquals("体检和预约别忘，提前一天告诉你，要准备的都列好。", cards[1].fact)
        assertEquals("没接到的电话别错过，点一下就能回拨。", cards[2].fact)
        assertEquals("包裹到了、航班要飞了，我提前告诉你。", cards[3].fact)
        assertEquals("需要连接消息", cards[0].prep)
        assertEquals("需要连接消息", cards[1].prep)
        assertEquals("需要连接通知", cards[2].prep)
        assertEquals("需要连接通知", cards[3].prep)
    }

    @Test
    fun guideCards_copyHasNoRealSmsContent() {
        // 文案不拿用户的真实短信内容当例子：没有尾号、金额、银行名、真实地点
        val text = guideCards().joinToString(" ") {
            "${it.sourceLabel} ${it.fact} ${it.prep}"
        }
        assertFalse(text.contains("7072"))
        assertFalse(text.contains("¥"))
        assertFalse(text.contains("中信"))
        assertFalse(text.contains("2057"))
        assertFalse(text.contains("鼎和"))
    }

    @Test
    fun guideCards_connectTargetsAreKnownConnectors() {
        val keys = guideCards().map {
            (it.action as SmartCardAction.Connect).connectorKey
        }
        assertEquals(
            listOf(
                UiPrefs.CONNECTOR_MESSAGE,
                UiPrefs.CONNECTOR_MESSAGE,
                UiPrefs.CONNECTOR_NOTIFICATION,
                UiPrefs.CONNECTOR_NOTIFICATION
            ),
            keys
        )
    }
}
