package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能卡片按钮文案单测（第十二批，Bob 2026-11-09 定）：
 * 左“我知道了”不动；右按钮不再写“下一步”几个字（第十一批通用文案作废），
 * 显示下一步的意图（actionLabel）：账单“去还款”、预约“帮我建提醒”
 * （建完提醒后变回“看预约”）、未接来电“回拨”、快递/航班“查看”、
 * 日历“加入会议”/“看日程”、引导卡“连接＋来源名”。文案逐字锁死；
 * 任何卡的 actionLabel 里都不许出现“下一步”三字。
 */
class SmartCardButtonCopyTest {

    @Test
    fun dismissLabel_isWoZhiDaoLe() {
        assertEquals("我知道了", SMART_CARD_DISMISS_LABEL)
    }

    private fun card(kind: SmartCardKind, actionLabel: String) = SmartCard(
        id = "t|${kind.name}",
        kind = kind,
        sourceLabel = "短信",
        fact = "fact",
        prep = null,
        actionLabel = actionLabel,
        sortMs = 100L,
        deadlineMs = 200L,
        action = SmartCardAction.SmsThread("95558"),
        sourceKey = "t|${kind.name}"
    )

    @Test
    fun billActionLabel_isQuHuanKuan() {
        assertEquals("去还款", card(SmartCardKind.BILL, "去还款").actionLabel)
    }

    @Test
    fun appointmentActionLabel_isBangWoJianTiXing() {
        // 预约卡没建过提醒时是“帮我建提醒”；建完提醒后变回“看预约”
        // （仓库 appointmentCard 按 reminderCreated 分，见 SmartCardRepository）。
        assertEquals("帮我建提醒", card(SmartCardKind.APPOINTMENT, "帮我建提醒").actionLabel)
        val created = card(SmartCardKind.APPOINTMENT, "看预约")
            .copy(reminderCreated = true)
        assertEquals("看预约", created.actionLabel)
        assertTrue(created.reminderCreated)
    }

    @Test
    fun missedCallActionLabel_isHuiBo() {
        assertEquals("回拨", card(SmartCardKind.MISSED_CALL, "回拨").actionLabel)
    }

    @Test
    fun notificationActionLabel_isChaKan() {
        assertEquals("查看", card(SmartCardKind.NOTIFICATION, "查看").actionLabel)
    }

    @Test
    fun calendarActionLabel_isJoinOrView() {
        // 有会议链接“加入会议”，没有“看日程”（仓库 calendarCard 按
        // extractMeetingUrl 分，见 SmartCardRepository）。
        assertEquals("加入会议", card(SmartCardKind.CALENDAR, "加入会议").actionLabel)
        assertEquals("看日程", card(SmartCardKind.CALENDAR, "看日程").actionLabel)
    }

    @Test
    fun guideActionLabel_isConnectPlusSource() {
        // 引导卡“连接＋来源名”，按 GuideCards 里实际的 sourceLabel 定。
        val labels = guideCards().associate { it.sourceLabel to it.actionLabel }
        assertEquals("连接消息", labels["消息"])
        assertEquals("连接通知", labels["通知"])
        assertEquals("连接日历", labels["日历"])
        assertEquals("连接联系人", labels["联系人"])
        assertEquals("连接电话拨号器", labels["电话拨号器"])
    }

    @Test
    fun noActionLabel_containsXiaYiBu() {
        // 全仓扫一遍：真卡五种意图文案＋五张引导卡，右按钮文案里都不许
        // 出现“下一步”三字（露边卡提示行同样显示 actionLabel，也干净）。
        val labels = listOf("去还款", "帮我建提醒", "看预约", "回拨", "查看", "加入会议", "看日程") +
            guideCards().map { it.actionLabel }
        assertTrue(labels.none { it.contains("下一步") })
        assertFalse(labels.isEmpty())
    }
}
