package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能区单测（第七批，设计稿 §2；2026-11-08 Bob 定：提醒来源收进“智能”区，
 * 每行一个控件＋智能等级＋新顺序＋说明 v1；第十三批重写：卡片跟着权限走，
 * 开关不再是一道关——开关只显示接没接上，接上＝权限给了；等级计数、
 * 状态文案、电话拨号器行都只认权限）。
 * 纯函数、JVM 可跑。状态文字不可点、无文字按钮是结构保证：
 * SettingsConnectorRow 的签名里已没有 actionText/onOpen 这类按钮参数，
 * 行内也没有 TextButton（grep 可验），不是靠运行时断言。
 * 仓库的门（refresh/buildSmsCards）是 Context 绑定的，JVM 单测够不着，
 * 锁死方式是删了 UiPrefs 的开关 API（isConnectorEnabled/setConnectorEnabled），
 * 残留的开关门编译不过——见收据。
 */
class ConnectorSwitchTest {

    private fun state(
        key: String,
        granted: Boolean,
        lastUsedLabel: String = "今天用过"
    ) = ConnectorState(
        key = key, name = key, desc = "", granted = granted,
        lastUsedLabel = lastUsedLabel
    )

    // 开关显示 = 权限给了没给（第十三批：开关不再是一道关）

    @Test
    fun switchShowsGranted() {
        // 开关的显示值就是 state.granted：权限给了开关开，没给开关关；
        // 没有第二个“启用”标志能把它再关掉——双重关已删。
        assertTrue(state(UiPrefs.CONNECTOR_MESSAGE, granted = true).granted)
        assertFalse(state(UiPrefs.CONNECTOR_CALENDAR, granted = false).granted)
    }

    // 状态行文案（只做展示）

    @Test
    fun statusText_notGranted() {
        assertEquals(
            "未接上",
            connectorStatusText(state(UiPrefs.CONNECTOR_MESSAGE, granted = false))
        )
    }

    @Test
    fun statusText_connected() {
        assertEquals(
            "已接上 · 今天用过",
            connectorStatusText(state(UiPrefs.CONNECTOR_MESSAGE, granted = true))
        )
    }

    @Test
    fun statusText_noClosedBranch() {
        // 旧文案“已关闭”（权限给了、开关关了）已随开关一起删掉；
        // 两个分支之外没有第三种文案。
        assertEquals(
            "未接上",
            connectorStatusText(state(UiPrefs.CONNECTOR_MESSAGE, granted = false))
        )
        assertEquals(
            "已接上 · 今天用过",
            connectorStatusText(state(UiPrefs.CONNECTOR_MESSAGE, granted = true))
        )
    }

    // 智能源顺序：消息 > 通知 > 日历 > 电话拨号器 > 联系人

    @Test
    fun sourceOrder_bySmartContribution() {
        assertEquals(
            listOf(
                UiPrefs.CONNECTOR_MESSAGE,
                UiPrefs.CONNECTOR_NOTIFICATION,
                UiPrefs.CONNECTOR_CALENDAR,
                UiPrefs.CONNECTOR_PHONE,
                UiPrefs.CONNECTOR_CONTACTS
            ),
            SMART_SOURCE_ORDER
        )
    }

    // 说明 v1（逐字锁死）

    @Test
    fun descriptions_v1() {
        assertEquals(
            "接上后，智能会提前提醒你账单到期、体检预约。",
            connectorDescription(UiPrefs.CONNECTOR_MESSAGE)
        )
        assertEquals(
            "接上后，智能会提醒你待办、未接来电、包裹和航班。",
            connectorDescription(UiPrefs.CONNECTOR_NOTIFICATION)
        )
        assertEquals(
            "接上后，智能知道你今天的会。",
            connectorDescription(UiPrefs.CONNECTOR_CALENDAR)
        )
        assertEquals(
            "接上后，未接来电点一下就能回拨。",
            connectorDescription(UiPrefs.CONNECTOR_PHONE)
        )
        assertEquals(
            "接上后，未接来电会显示成名字。",
            connectorDescription(UiPrefs.CONNECTOR_CONTACTS)
        )
        assertEquals("", connectorDescription("unknown"))
    }

    // 智能等级 = 权限已授的源个数；电话拨号器随通知计

    private fun allStates(
        message: Boolean = true,
        notification: Boolean = true,
        calendar: Boolean = true,
        contacts: Boolean = true
    ) = listOf(
        state(UiPrefs.CONNECTOR_MESSAGE, message),
        state(UiPrefs.CONNECTOR_NOTIFICATION, notification),
        state(UiPrefs.CONNECTOR_CALENDAR, calendar),
        // 电话拨号器无自己的权限：granted 取通知的授权状态（与
        // buildConnectorStates 一致），计数时另看通知源是否已接上。
        state(UiPrefs.CONNECTOR_PHONE, notification),
        state(UiPrefs.CONNECTOR_CONTACTS, contacts)
    )

    @Test
    fun levelCount_allConnectedIsFive() {
        assertEquals(5, connectedSourceCount(allStates()))
    }

    @Test
    fun levelCount_noneConnectedIsZero() {
        assertEquals(
            0,
            connectedSourceCount(
                allStates(
                    message = false, notification = false,
                    calendar = false, contacts = false
                )
            )
        )
    }

    @Test
    fun levelCount_countsOnlyGranted() {
        // 只认权限：消息给了、日历给了，通知没给（电话随通知不算）、
        // 联系人没给 → 2。旧口径里日历开关关着会再扣一个，那是双重关。
        assertEquals(
            2,
            connectedSourceCount(
                allStates(
                    message = true, notification = false,
                    calendar = true, contacts = false
                )
            )
        )
    }

    @Test
    fun levelCount_phoneNeedsNotificationGranted() {
        // 电话拨号器随通知计：通知给了 → 通知＋电话一次算 2 个；
        // 通知没给 → 电话不算，哪怕输入表里电话的 granted 写的是 true
        // （connectedSourceCount 按通知再校验一次，不多算）。
        assertEquals(
            4,
            connectedSourceCount(
                allStates(
                    message = true, notification = true,
                    calendar = true, contacts = false
                )
            )
        )
        assertEquals(
            2,
            connectedSourceCount(
                allStates(
                    message = true, notification = false,
                    calendar = true, contacts = false
                )
            )
        )
        val inconsistent = listOf(
            state(UiPrefs.CONNECTOR_MESSAGE, true),
            state(UiPrefs.CONNECTOR_NOTIFICATION, false),
            state(UiPrefs.CONNECTOR_CALENDAR, true),
            state(UiPrefs.CONNECTOR_PHONE, true),
            state(UiPrefs.CONNECTOR_CONTACTS, false)
        )
        assertEquals(2, connectedSourceCount(inconsistent))
    }

    // 电话拨号器行跟着通知的权限走（第十三批）

    @Test
    fun dialerRowEnabled_followsNotificationGranted() {
        // 行可点当且仅当通知已接上（权限给了）。
        assertTrue(isDialerRowEnabled(allStates(notification = true)))
        assertFalse(isDialerRowEnabled(allStates(notification = false)))
    }

    @Test
    fun dialerRowDisabled_whenNotificationNotGranted() {
        // 通知权限未授 → 电话拨号器行变灰点不动（不能单独开它），等级里也不算它。
        val states = allStates(
            message = true, notification = false,
            calendar = true, contacts = false
        )
        assertFalse(isDialerRowEnabled(states))
        assertEquals(2, connectedSourceCount(states))
    }

    @Test
    fun dialerCountsWithNotification_whenNotificationGranted() {
        // 通知接上 → 电话拨号器跟着算一个（接通知一次涨 2：通知＋电话），
        // 行可点；没有自己的开关能再单独关掉它。
        val states = allStates(
            message = true, notification = true,
            calendar = true, contacts = false
        )
        assertTrue(isDialerRowEnabled(states))
        assertEquals(4, connectedSourceCount(states))
    }
}
