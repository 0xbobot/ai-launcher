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
