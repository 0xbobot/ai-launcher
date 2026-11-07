package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能卡片第四批单测（Bob 2026-11-07 晚真机 verdict：连接/× 点按无反应、
 * 叠层加数字角标）。仓库只有 JVM 纯单测、无 Compose UI 测试基建，真机
 * 手势只能由 Bob 真机验；这里锁死点按行为的纯函数一级：角标计数口径
 * （数＝当前叠层可见卡片张数，随处理/跳过/新增实时变，0 张不画）与
 * 点按路由（连接按来源分流、忽略永久 dismiss、横滑只本轮沉底）。
 * 点按本身的接线（onClick → onAction/onClose → 弹框/整叠消失）在
 * SmartCardStack.kt，CI 编译＋ Bob 真机验收。
 */
class SmartCardStackBadgeTest {

    // ---------- 角标计数：数＝当前叠层可见卡片张数 ----------

    @Test
    fun badgeCount_fourVisibleShows4() {
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false
        )
        assertEquals(4, visible.size)
        assertEquals("4", formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_dismissOneShows3() {
        // 点“忽略”一张：永久 dismiss，那张不再出现，角标 4 → 3。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            dismissedIds = setOf("guide|bill")
        )
        assertEquals(3, visible.size)
        assertEquals("3", formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_dismissAllHides() {
        // 四张全忽略：叠层清空，角标不显示（formatBadgeCount(0) 为 null，
        // AppBadge 不画；SmartCardStack 本身也提前返回、不留占位）。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            dismissedIds = setOf(
                "guide|bill", "guide|appointment",
                "guide|missed-call", "guide|delivery"
            )
        )
        assertTrue(visible.isEmpty())
        assertNull(formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_swipeAllEmptiesThisRound() {
        // 四张全横滑跳过：本轮叠层清空，角标不显示；宿主随即记一次。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            swipedIds = setOf(
                "guide|bill", "guide|appointment",
                "guide|missed-call", "guide|delivery"
            )
        )
        assertTrue(visible.isEmpty())
        assertNull(formatBadgeCount(visible.size))
    }

    @Test
    fun badgeCount_grantedSourceHidesItsCards() {
        // 接上消息来源：账单、体检两张引导卡消失，角标 4 → 2。
        val visible = visibleGuideCards(
            messageGranted = true, notificationGranted = false
        )
        assertEquals(2, visible.size)
        assertEquals("2", formatBadgeCount(visible.size))
    }

    // ---------- 点“连接”的路由：按来源分流 ----------

    @Test
    fun connectRouting_messageCardsGoToDisclosure() {
        // 账单、体检：连接消息——宿主先显著披露再弹系统框。
        val messageCards = guideCards().filter {
            (it.action as? SmartCardAction.Connect)?.connectorKey ==
                UiPrefs.CONNECTOR_MESSAGE
        }
        assertEquals(
            listOf("guide|bill", "guide|appointment"),
            messageCards.map { it.id }
        )
    }

    @Test
    fun connectRouting_notificationCardsGoToSettings() {
        // 未接来电、快递航班：连接通知——宿主跳系统设置页。
        val notificationCards = guideCards().filter {
            (it.action as? SmartCardAction.Connect)?.connectorKey ==
                UiPrefs.CONNECTOR_NOTIFICATION
        }
        assertEquals(
            listOf("guide|missed-call", "guide|delivery"),
            notificationCards.map { it.id }
        )
    }

    // ---------- 忽略 vs 横滑：一个永久、一个只本轮 ----------

    @Test
    fun ignoreIsPermanent_dismissedStaysFiltered() {
        // 点过“忽略”的卡：dismissedIds 记下后一直过滤，不随滑走状态变。
        val visible = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            swipedIds = emptySet(),
            dismissedIds = setOf("guide|bill")
        )
        assertEquals(
            listOf("guide|appointment", "guide|missed-call", "guide|delivery"),
            visible.map { it.id }
        )
    }

    @Test
    fun skipIsRoundOnly_swipedCardReturnsWhenSetCleared() {
        // 横滑只沉底本轮：swipedIds 清空（下一轮）后那张重新出现。
        val swiped = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            swipedIds = setOf("guide|bill")
        )
        assertEquals(3, swiped.size)
        val nextRound = visibleGuideCards(
            messageGranted = false, notificationGranted = false,
            swipedIds = emptySet()
        )
        assertEquals(4, nextRound.size)
    }
}
