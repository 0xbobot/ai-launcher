package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能卡片第四批单测（Bob 2026-11-07 晚真机 verdict：连接/× 点按无反应、
 * 叠层加数字角标；第五批按权限重做为四张卡，455/v1.2.8，第十四批删联系人）。
 * 仓库只有 JVM 纯单测、无 Compose UI 测试基建，真机手势只能由 Bob 真机验；
 * 这里锁死点按行为的纯函数一级：角标计数口径（数＝当前叠层可见卡片张数，
 * 随处理/跳过/新增实时变，0 张不画）与点按路由（连接按来源分流、点
 * 「我知道了」/横滑是轮换推的被拒一次——放队尾，不是永久记下）。
 * 点按本身的接线（onClick → onAction/onClose → 弹框/整叠消失）在
 * SmartCardStack.kt，CI 编译＋ Bob 真机验收。
 */
class SmartCardStackBadgeTest {

    // ---------- 角标计数：数＝当前叠层可见卡片张数 ----------

    @Test
    fun badgeCount_fourEligibleShows4() {
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false
        )
        assertEquals(4, visible.size)
        assertEquals("4", formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_onlyOnePushedAtATime() {
        // 单卡提醒、轮换推（第十四批）：宿主一次只展示队列里第一张符合
        // 条件的引导卡，叠层里只有这一张，角标就是 1——不是整叠四张的数。
        val eligible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false
        )
        val queue = guideCards().map { it.id }
        val current = currentGuideCard(queue, eligible)
        assertEquals("guide|message", current?.id)
        assertEquals("1", formatBadgeCount(listOfNotNull(current).size))
    }

    @Test
    fun badgeCount_dismissOneShows3() {
        // 处理完一张（接上后反馈已给完）：那张不再出现，角标 4 → 3。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false,
            dismissedIds = setOf("guide|message")
        )
        assertEquals(3, visible.size)
        assertEquals("3", formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_dismissAllHides() {
        // 四张全处理完：叠层清空，角标不显示（formatBadgeCount(0) 为 null，
        // AppBadge 不画；SmartCardStack 本身也提前返回、不留占位）。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false,
            dismissedIds = setOf(
                "guide|message", "guide|notification", "guide|calendar",
                "guide|phone"
            )
        )
        assertTrue(visible.isEmpty())
        assertNull(formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_grantedSourceHidesOnlyItsCards() {
        // 接上消息来源：只消失消息那一张，角标 4 → 3（452 的问题修完的标准）。
        val visible = visibleGuideCards(
            messageGranted = true, notificationGranted = false,
            calendarGranted = false
        )
        assertEquals(3, visible.size)
        assertEquals("3", formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_notificationGrantedHidesTwo() {
        // 接上通知来源：通知卡与电话拨号器卡一起消失，角标 4 → 2。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = true,
            calendarGranted = false
        )
        assertEquals(2, visible.size)
        assertEquals("2", formatBadgeCount(visible.size))
    }

    // ---------- 点“连接”的路由：按来源分流 ----------

    @Test
    fun connectRouting_messageCardGoesToDisclosure() {
        // 消息：连接消息——宿主先显著披露再弹系统框。
        val messageCards = guideCards().filter {
            (it.action as? SmartCardAction.Connect)?.connectorKey ==
                UiPrefs.CONNECTOR_MESSAGE
        }
        assertEquals(
            listOf("guide|message"),
            messageCards.map { it.id }
        )
    }

    @Test
    fun connectRouting_notificationCardsGoToSettings() {
        // 通知、电话拨号器：连接通知——宿主跳系统设置页。
        val notificationCards = guideCards().filter {
            (it.action as? SmartCardAction.Connect)?.connectorKey ==
                UiPrefs.CONNECTOR_NOTIFICATION
        }
        assertEquals(
            listOf("guide|notification", "guide|phone"),
            notificationCards.map { it.id }
        )
    }

    @Test
    fun connectRouting_calendarCardGoesToOpener() {
        // 日历：连接日历——宿主直接弹系统权限框（同设置页，无显著披露）。
        val calendarCards = guideCards().filter {
            (it.action as? SmartCardAction.Connect)?.connectorKey ==
                UiPrefs.CONNECTOR_CALENDAR
        }
        assertEquals(
            listOf("guide|calendar"),
            calendarCards.map { it.id }
        )
    }

    // ---------- 处理完的记下 vs 被拒一次：一个永久、一个放队尾 ----------

    @Test
    fun dismissedStaysFiltered() {
        // 处理完记下的卡：dismissedIds 记下后一直过滤（接上后反馈已给完、
        // 或反馈卡被横滑走，才进这个集合）。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false,
            dismissedIds = setOf("guide|message")
        )
        assertEquals(
            listOf(
                "guide|notification", "guide|calendar",
                "guide|phone"
            ),
            visible.map { it.id }
        )
    }

    @Test
    fun rejectMovesCardToTail_nextCardComesUp() {
        // 被拒一次不是永久记下：点「我知道了」/横滑一张引导卡，它放队尾，
        // 下一张立刻推上来（单卡提醒，一次一张）；角标还是 1。
        val queue = guideCards().map { it.id }
        val eligible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false
        )
        assertEquals("guide|message", currentGuideCard(queue, eligible)?.id)
        val rotated = guideQueueAfterReject(queue, "guide|message", 0)
        val next = currentGuideCard(rotated, eligible)
        assertEquals("guide|notification", next?.id)
        assertEquals("1", formatBadgeCount(listOfNotNull(next).size))
    }
}
