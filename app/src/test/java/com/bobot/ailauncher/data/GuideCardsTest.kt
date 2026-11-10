package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引导卡片单测（448 起，设计稿 §4.7；v3 按权限重做，455/v1.2.8；
 * 第十四批：联系人取消＋单卡提醒轮换推，Bob 2026-11-10 23:07/23:45 定）：
 * 四张卡固定清单（顺序与智能区同一份 SMART_SOURCE_ORDER）＋按授权状态
 * 过滤＋轮换推的纯函数（被拒一次放队尾、被拒两次出局、一次只推一张）
 * ＋“已连接”反馈文案＋文案不拿用户真实短信内容当例子。纯函数、JVM 可跑。
 */
class GuideCardsTest {

    @Test
    fun guideCards_fourInSourceOrder() {
        // 四张卡，顺序就是 SMART_SOURCE_ORDER（第十四批统一）：消息、通知、
        // 日历、电话拨号器；联系人已取消，没有第五张。
        val cards = guideCards()
        assertEquals(4, cards.size)
        assertEquals("guide|message", cards[0].id)
        assertEquals("guide|notification", cards[1].id)
        assertEquals("guide|calendar", cards[2].id)
        assertEquals("guide|phone", cards[3].id)
        assertEquals(SMART_SOURCE_ORDER.size, cards.size)
        assertTrue(cards.all { it.kind == SmartCardKind.GUIDE })
        // 第十二批：右按钮显示下一步的意图——引导卡是“连接＋来源名”。
        assertEquals(
            listOf("连接消息", "连接通知", "连接日历", "连接电话拨号器"),
            cards.map { it.actionLabel }
        )
        assertTrue(cards.all { it.action is SmartCardAction.Connect })
        assertEquals(
            listOf("消息", "通知", "日历", "电话拨号器"),
            cards.map { it.sourceLabel }
        )
    }

    @Test
    fun visibleGuideCards_nothingGrantedShowsAll() {
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false
        )
        assertEquals(4, cards.size)
    }

    @Test
    fun visibleGuideCards_messageGrantedHidesOnlyMessage() {
        // 452 的问题：连一个权限整叠全消失——修完的标准是只消失这一张，
        // 其他权限的引导卡留着。
        val cards = visibleGuideCards(
            messageGranted = true, notificationGranted = false,
            calendarGranted = false
        )
        assertEquals(
            listOf(
                "guide|notification", "guide|calendar",
                "guide|phone"
            ),
            cards.map { it.id }
        )
    }

    @Test
    fun visibleGuideCards_notificationGrantedHidesNotificationAndPhone() {
        // 电话拨号器随通知一起开：通知一接上，通知卡与电话拨号器卡一起消失。
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = true,
            calendarGranted = false
        )
        assertEquals(
            listOf("guide|message", "guide|calendar"),
            cards.map { it.id }
        )
    }

    @Test
    fun visibleGuideCards_calendarGrantedHidesOnlyCalendar() {
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = true
        )
        assertEquals(
            listOf(
                "guide|message", "guide|notification",
                "guide|phone"
            ),
            cards.map { it.id }
        )
    }

    @Test
    fun visibleGuideCards_allGrantedShowsNone() {
        val cards = visibleGuideCards(
            messageGranted = true, notificationGranted = true,
            calendarGranted = true
        )
        assertTrue(cards.isEmpty())
    }

    @Test
    fun visibleGuideCards_dismissedIdsFiltered() {
        // 处理完（接上后反馈已给完、或反馈卡被横滑走）永久记下的卡不再出现。
        // 点「我知道了」/横滑一张引导卡不再走这里——那是轮换推的被拒一次
        // （放队尾，见 guideQueueAfterReject），不是永久记下。
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            dismissedIds = setOf("guide|message", "guide|notification")
        )
        assertEquals(
            listOf("guide|calendar", "guide|phone"),
            cards.map { it.id }
        )
    }

    @Test
    fun visibleGuideCards_allDismissedShowsNone() {
        val cards = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            dismissedIds = setOf(
                "guide|message", "guide|notification", "guide|calendar",
                "guide|phone"
            )
        )
        assertTrue(cards.isEmpty())
    }

    @Test
    fun guideCards_copyV3Verbatim() {
        // 设计稿 §4.7 v3（455，Bob 2026-11-08 凌晨 verdict 后重写）：
        // 每张讲三件事——这是什么、为什么要连、连上之后会发生什么。
        // 逐字锁死，不改词句（第十四批只删联系人那张，其余三张一字不动）。
        val cards = guideCards()
        assertEquals(
            "连接消息后：信用卡快到期、体检预约快到了，我会提前提醒你。",
            cards[0].fact
        )
        assertEquals(
            "连接通知后：没接到的电话、包裹到了、航班要飞了，我会给你一张卡片，点一下就能处理。",
            cards[1].fact
        )
        assertEquals("连接日历后：会议会出现在简报里，到时间提醒你。", cards[2].fact)
        assertEquals("回拨只进拨号盘，不会直接拨出去。", cards[3].fact)
        assertEquals("读短信里的账单和预约，只在本地处理", cards[0].prep)
        assertEquals("只读通知内容，只在本地处理", cards[1].prep)
        assertEquals("只读当天的日程，只在本地处理", cards[2].prep)
        assertEquals("随通知一起开，不另要权限", cards[3].prep)
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
                UiPrefs.CONNECTOR_NOTIFICATION,
                UiPrefs.CONNECTOR_CALENDAR,
                UiPrefs.CONNECTOR_NOTIFICATION
            ),
            keys
        )
    }

    @Test
    fun connectedGuideCard_showsNextStep() {
        // 接上权限后先显示“已连接”＋一句话接下来会发生什么，再消失；
        // 同一张卡（id 不变），只换文案，没有动作按钮（宿主负责不画动作行）。
        val cards = guideCards()
        val connected = cards.map { connectedGuideCard(it) }
        assertEquals(cards.map { it.id }, connected.map { it.id })
        assertTrue(connected.all { it.fact == "已连接" })
        assertTrue(connected.all { it.kind == SmartCardKind.GUIDE })
        assertEquals(
            "有账单或预约短信时，这里会提前提醒你",
            connected[0].prep
        )
        assertEquals(
            "有未接来电、包裹或航班提醒时，这里会出现一张卡片，点一下就能处理",
            connected[1].prep
        )
        assertEquals("会议会出现在简报里，到时间提醒你", connected[2].prep)
        assertEquals("回拨只进拨号盘，不会直接拨出去", connected[3].prep)
    }

    @Test
    fun guideIdsForConnector_mapsCorrectly() {
        assertEquals(
            listOf("guide|message"),
            guideIdsForConnector(UiPrefs.CONNECTOR_MESSAGE)
        )
        // 电话拨号器与通知共用通知 key
        assertEquals(
            listOf("guide|notification", "guide|phone"),
            guideIdsForConnector(UiPrefs.CONNECTOR_NOTIFICATION)
        )
        assertEquals(
            listOf("guide|calendar"),
            guideIdsForConnector(UiPrefs.CONNECTOR_CALENDAR)
        )
    }

    @Test
    fun isGuideConnectorGranted_phoneFollowsNotification() {
        assertTrue(
            isGuideConnectorGranted(
                UiPrefs.CONNECTOR_PHONE,
                messageGranted = false, notificationGranted = true,
                calendarGranted = false
            )
        )
        assertFalse(
            isGuideConnectorGranted(
                UiPrefs.CONNECTOR_PHONE,
                messageGranted = false, notificationGranted = false,
                calendarGranted = false
            )
        )
        assertTrue(
            isGuideConnectorGranted(
                UiPrefs.CONNECTOR_MESSAGE,
                messageGranted = true, notificationGranted = false,
                calendarGranted = false
            )
        )
    }

    // ---------- 轮换推（第十四批，Bob 2026-11-10 23:07 定） ----------

    private fun queue() = listOf(
        "guide|message", "guide|notification",
        "guide|calendar", "guide|phone"
    )

    @Test
    fun guideQueueAfterReject_firstRejectMovesToTail() {
        // 被拒一次：放队尾，下次推其他来源的卡。
        assertEquals(
            listOf(
                "guide|notification", "guide|calendar",
                "guide|phone", "guide|message"
            ),
            guideQueueAfterReject(queue(), "guide|message", priorRejects = 0)
        )
    }

    @Test
    fun guideQueueAfterReject_secondRejectRemovesForThisVersion() {
        // 被拒两次：本版不再推（等引导换版），其他卡的顺序不动。
        assertEquals(
            listOf(
                "guide|notification", "guide|calendar",
                "guide|phone"
            ),
            guideQueueAfterReject(queue(), "guide|message", priorRejects = 1)
        )
    }

    @Test
    fun guideQueueAfterReject_unknownIdLeavesQueueAlone() {
        assertEquals(queue(), guideQueueAfterReject(queue(), "guide|nope", 0))
        assertEquals(queue(), guideQueueAfterReject(queue(), "guide|nope", 1))
    }

    @Test
    fun guideMaxRejects_isTwo() {
        assertEquals(2, GUIDE_MAX_REJECTS)
    }

    @Test
    fun currentGuideCard_pushesOneAtATimeInQueueOrder() {
        // 一次只推一张：队列第一张符合条件的卡；已被拒放队尾的那张，
        // 排在其他卡后面才轮到。
        val eligible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false
        )
        assertEquals(
            "guide|message",
            currentGuideCard(queue(), eligible)?.id
        )
        val rotated = guideQueueAfterReject(queue(), "guide|message", 0)
        assertEquals(
            "guide|notification",
            currentGuideCard(rotated, eligible)?.id
        )
    }

    @Test
    fun currentGuideCard_skipsGrantedSources() {
        // 接上的来源不推：队列头是已接上的消息，推下一张通知。
        val eligible = visibleGuideCards(
            messageGranted = true, notificationGranted = false,
            calendarGranted = false
        )
        assertEquals(
            "guide|notification",
            currentGuideCard(queue(), eligible)?.id
        )
    }

    @Test
    fun currentGuideCard_noneEligibleIsNull() {
        assertNull(
            currentGuideCard(
                queue(),
                visibleGuideCards(
                    messageGranted = true, notificationGranted = true,
                    calendarGranted = true
                )
            )
        )
    }
}
