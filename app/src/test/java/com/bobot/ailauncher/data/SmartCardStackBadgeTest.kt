package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能卡片第四批单测（Bob 2026-11-07 晚真机 verdict：连接/× 点按无反应、
 * 叠层加数字角标；第五批按权限重做为五张卡，455/v1.2.8）。仓库只有 JVM
 * 纯单测、无 Compose UI 测试基建，真机手势只能由 Bob 真机验；这里锁死
 * 点按行为的纯函数一级：角标计数口径（数＝当前叠层可见卡片张数，随处理/
 * 跳过/新增实时变，0 张不画）与点按路由（连接按来源分流、忽略永久
 * dismiss、横滑只本轮沉底）。
 * 点按本身的接线（onClick → onAction/onClose → 弹框/整叠消失）在
 * SmartCardStack.kt，CI 编译＋ Bob 真机验收。
 */
class SmartCardStackBadgeTest {

    // ---------- 角标计数：数＝当前叠层可见卡片张数 ----------

    @Test
    fun badgeCount_fiveVisibleShows5() {
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false, contactsGranted = false
        )
        assertEquals(5, visible.size)
        assertEquals("5", formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_dismissOneShows4() {
        // 点“忽略”一张：永久 dismiss，那张不再出现，角标 5 → 4。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false, contactsGranted = false,
            dismissedIds = setOf("guide|message")
        )
        assertEquals(4, visible.size)
        assertEquals("4", formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_dismissAllHides() {
        // 五张全忽略：叠层清空，角标不显示（formatBadgeCount(0) 为 null，
        // AppBadge 不画；SmartCardStack 本身也提前返回、不留占位）。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false, contactsGranted = false,
            dismissedIds = setOf(
                "guide|message", "guide|notification", "guide|calendar",
                "guide|contacts", "guide|phone"
            )
        )
        assertTrue(visible.isEmpty())
        assertNull(formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_swipeAllEmptiesThisRound() {
        // 五张全横滑跳过：本轮叠层清空，角标不显示；宿主随即记一次。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false, contactsGranted = false,
            swipedIds = setOf(
                "guide|message", "guide|notification", "guide|calendar",
                "guide|contacts", "guide|phone"
            )
        )
        assertTrue(visible.isEmpty())
        assertNull(formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_grantedSourceHidesOnlyItsCards() {
        // 接上消息来源：只消失消息那一张，角标 5 → 4（452 的问题修完的标准）。
        val visible = visibleGuideCards(
            messageGranted = true, notificationGranted = false,
            calendarGranted = false, contactsGranted = false
        )
        assertEquals(4, visible.size)
        assertEquals("4", formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_notificationGrantedHidesTwo() {
        // 接上通知来源：通知卡与电话拨号器卡一起消失，角标 5 → 3。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = true,
            calendarGranted = false, contactsGranted = false
        )
        assertEquals(3, visible.size)
        assertEquals("3", formatBadgeCount(visible.size))
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
    fun connectRouting_contactsCardGoesToDisclosure() {
        // 联系人：连接联系人——宿主先显著披露再弹系统框（同设置页）。
        val contactsCards = guideCards().filter {
            (it.action as? SmartCardAction.Connect)?.connectorKey ==
                UiPrefs.CONNECTOR_CONTACTS
        }
        assertEquals(
            listOf("guide|contacts"),
            contactsCards.map { it.id }
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

    // ---------- 忽略 vs 横滑：一个永久、一个只本轮 ----------

    @Test
    fun ignoreIsPermanent_dismissedStaysFiltered() {
        // 点过“忽略”的卡：dismissedIds 记下后一直过滤，不随滑走状态变。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false, contactsGranted = false,
            swipedIds = emptySet(),
            dismissedIds = setOf("guide|message")
        )
        assertEquals(
            listOf(
                "guide|notification", "guide|calendar",
                "guide|contacts", "guide|phone"
            ),
            visible.map { it.id }
        )
    }

    @Test
    fun skipIsRoundOnly_swipedCardReturnsWhenSetCleared() {
        // 横滑只沉底本轮：swipedIds 清空（下一轮）后那张重新出现。
        val swiped = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false, contactsGranted = false,
            swipedIds = setOf("guide|message")
        )
        assertEquals(4, swiped.size)
        val nextRound = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            calendarGranted = false, contactsGranted = false,
            swipedIds = emptySet()
        )
        assertEquals(5, nextRound.size)
    }
}
