package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * “已连接”庆祝反馈单测（第八批，设计稿 §4.7；2026-11-08 Bob verdict：
 * “已连接”那一刻要让人振奋，不只是换文案）。
 *
 * 动效本身 JVM 单测测不到，锁死的是状态机：反馈状态必须携带动效触发
 * 标志 celebrate 与等级增量 levelDelta；等级数必须复用 457 的
 * connectedSourceCount（同一处计数），不写死；停留时长仍是 3 秒。
 * 视觉（对勾弹入＋金色等级行＋整卡轻弹）以代码复核＋Bob 真机为准。
 * 反馈卡无动作按钮、可横滑提前离开、只对叠层里点了“继续”的卡给反馈，
 * 是宿主行为（SmartCardStackHost），Compose 侧测不到，见收据。
 */
class ConnectedCelebrationTest {

    private fun state(key: String, granted: Boolean) =
        ConnectorState(
            key = key, name = key, desc = "", granted = granted,
            lastUsedLabel = "今天用过"
        )

    /** 五个源的状态：消息/通知/日历/联系人各一对，电话拨号器随通知。 */
    private fun states(
        message: Boolean = false,
        notification: Boolean = false,
        calendar: Boolean = false,
        contacts: Boolean = false
    ) = listOf(
        state(UiPrefs.CONNECTOR_MESSAGE, message),
        state(UiPrefs.CONNECTOR_NOTIFICATION, notification),
        state(UiPrefs.CONNECTOR_CALENDAR, calendar),
        state(UiPrefs.CONNECTOR_PHONE, notification),
        state(UiPrefs.CONNECTOR_CONTACTS, contacts)
    )

    private fun messageCard() = guideCards().first { it.id == "guide|message" }

    @Test
    fun feedback_carriesCelebrateFlag() {
        val feedback = connectedFeedback(messageCard(), level = 1, previousLevel = 0)
        assertTrue(feedback.celebrate)
    }

    @Test
    fun feedback_levelDeltaIsOneForSingleSource() {
        val feedback = connectedFeedback(messageCard(), level = 1, previousLevel = 0)
        assertEquals(1, feedback.levelDelta)
        assertEquals(1, feedback.level)
        assertEquals(0, feedback.previousLevel)
    }

    @Test
    fun feedback_levelEqualsRealConnectedCount() {
        // 等级数复用 connectedSourceCount，不另写一套：接上消息后
        // 真实已接源是 1（消息），反馈里的等级就必须是 1。
        val after = states(message = true)
        val level = connectedSourceCount(after)
        assertEquals(1, level)
        val feedback = connectedFeedback(messageCard(), level, previousLevel = 0)
        assertEquals(level, feedback.level)
        assertEquals(1, feedback.levelDelta)
    }

    @Test
    fun feedback_levelDeltaIsTwoWhenNotificationAlsoCountsPhone() {
        // 接通知时电话拨号器跟着算一个：通知＋电话一次涨 2，
        // 增量如实算，不写死 +1（第十三批：电话拨号器没有自己的开关了，
        // 接通知一定涨 2，不再是“开关已开时才涨 2”）。
        val before = states()
        val after = states(notification = true)
        assertEquals(0, connectedSourceCount(before))
        assertEquals(2, connectedSourceCount(after))
        val card = guideCards().first { it.id == "guide|notification" }
        val feedback = connectedFeedback(card, level = 2, previousLevel = 0)
        assertEquals(2, feedback.levelDelta)
        assertEquals("智能 +2 · 已接 2/5 个源", celebrationLevelText(2, 0))
    }

    @Test
    fun feedback_cardKeepsConnectedCopy() {
        // 反馈卡本身还是 455 的那张“已连接”卡：fact 是“已连接”，
        // prep 是一句接下来会发生什么；庆祝感由形态给，不换文案。
        val feedback = connectedFeedback(messageCard(), level = 3, previousLevel = 2)
        assertEquals("已连接", feedback.card.fact)
        assertEquals(
            "有账单或预约短信时，这里会提前提醒你",
            feedback.card.prep
        )
        assertEquals("guide|message", feedback.card.id)
    }

    @Test
    fun feedback_phoneCardKeepsPhoneNextStep() {
        val card = guideCards().first { it.id == "guide|phone" }
        val feedback = connectedFeedback(card, level = 2, previousLevel = 1)
        assertEquals("回拨只进拨号盘，不会直接拨出去", feedback.card.prep)
    }

    @Test
    fun levelText_plusOneAndCount() {
        assertEquals("智能 +1 · 已接 3/5 个源", celebrationLevelText(3, 2))
        assertEquals("智能 +1 · 已接 1/5 个源", celebrationLevelText(1, 0))
        assertEquals("智能 +1 · 已接 5/5 个源", celebrationLevelText(5, 4))
    }

    @Test
    fun levelText_noDeltaShowsCountOnly() {
        assertEquals("已接 2/5 个源", celebrationLevelText(2, 2))
    }

    @Test
    fun feedbackDuration_stillThreeSeconds() {
        assertEquals(3000L, CONNECTED_FEEDBACK_MS)
    }
}
