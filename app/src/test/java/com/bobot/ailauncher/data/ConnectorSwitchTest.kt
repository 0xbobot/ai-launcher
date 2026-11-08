package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能区单测（第七批，设计稿 §2；2026-11-08 Bob 定：提醒来源收进“智能”区，
 * 每行一个控件＋智能等级＋新顺序＋说明 v1）：
 * 开关状态＝真实接上状态、点按决议、状态文案、等级计数、顺序、说明 v1。
 * 纯函数、JVM 可跑。状态文字不可点、无文字按钮是结构保证：
 * SettingsConnectorRow 的签名里已没有 actionText/onOpen 这类按钮参数，
 * 行内也没有 TextButton（grep 可验），不是靠运行时断言。
 */
class ConnectorSwitchTest {

    private fun state(
        key: String,
        granted: Boolean,
        enabled: Boolean,
        lastUsedLabel: String = "今天用过"
    ) = ConnectorState(
        key = key, name = key, desc = "", granted = granted,
        enabled = enabled, lastUsedLabel = lastUsedLabel
    )

    // 开关状态 = 真实接上状态（权限已授 + 来源启用）

    @Test
    fun switchOn_onlyWhenGrantedAndEnabled() {
        val key = UiPrefs.CONNECTOR_MESSAGE
        assertTrue(isConnectorConnected(state(key, granted = true, enabled = true)))
        assertFalse(isConnectorConnected(state(key, granted = true, enabled = false)))
        assertFalse(isConnectorConnected(state(key, granted = false, enabled = false)))
    }

    @Test
    fun switchOff_whenNotGranted_evenIfEnabledFlagOn() {
        // 455 真机 bug：日历、消息开关开着却显示“未接上”——根因是开关只读
        // enabled（默认 true），不读 granted。此后开关必须读两者：权限未授
        // 时开关就是关的，哪怕启用标志还开着。
        val key = UiPrefs.CONNECTOR_CALENDAR
        assertFalse(isConnectorConnected(state(key, granted = false, enabled = true)))
    }

    // 点按决议：关→立即停用；开且已授→只启用；开且未授→走接上流程

    @Test
    fun resolveToggle_offAlwaysDisables() {
        assertEquals(
            ConnectorToggleAction.DISABLE,
            resolveConnectorToggle(granted = true, turnOn = false)
        )
        assertEquals(
            ConnectorToggleAction.DISABLE,
            resolveConnectorToggle(granted = false, turnOn = false)
        )
    }

    @Test
    fun resolveToggle_onGrantedOnlyEnables() {
        assertEquals(
            ConnectorToggleAction.ENABLE,
            resolveConnectorToggle(granted = true, turnOn = true)
        )
    }

    @Test
    fun resolveToggle_onNotGrantedConnects() {
        assertEquals(
            ConnectorToggleAction.CONNECT,
            resolveConnectorToggle(granted = false, turnOn = true)
        )
    }

    @Test
    fun cancelConnect_keepsSwitchOff() {
        // 接上流程取消或拒绝：granted 仍为 false；哪怕点开关开时先把启用
        // 意愿记下了（enabled=true），开关按真实接上状态仍为关。
        assertEquals(
            ConnectorToggleAction.CONNECT,
            resolveConnectorToggle(granted = false, turnOn = true)
        )
        assertFalse(
            isConnectorConnected(
                state(UiPrefs.CONNECTOR_MESSAGE, granted = false, enabled = true)
            )
        )
    }

    @Test
    fun disable_stopsSourceContent() {
        // 关开关 → DISABLE → UiPrefs.setConnectorEnabled(key, false)；仓库
        // refresh() 按 isConnectorEnabled 门控各来源（447 既有，不变），
        // 该来源不再出卡片。系统权限本身应用内撤不掉：关开关 = 不再用这个
        // 来源，不是去系统里撤权限（见收据口径）。
        assertEquals(
            ConnectorToggleAction.DISABLE,
            resolveConnectorToggle(granted = true, turnOn = false)
        )
        assertFalse(
            isConnectorConnected(
                state(UiPrefs.CONNECTOR_NOTIFICATION, granted = true, enabled = false)
            )
        )
    }

    // 状态行文案（只做展示）

    @Test
    fun statusText_notGranted() {
        assertEquals(
            "未接上",
            connectorStatusText(state(UiPrefs.CONNECTOR_MESSAGE, granted = false, enabled = true))
        )
        assertEquals(
            "未接上",
            connectorStatusText(state(UiPrefs.CONNECTOR_MESSAGE, granted = false, enabled = false))
        )
    }

    @Test
    fun statusText_grantedButDisabled() {
        assertEquals(
            "已关闭",
            connectorStatusText(state(UiPrefs.CONNECTOR_MESSAGE, granted = true, enabled = false))
        )
    }

    @Test
    fun statusText_connected() {
        assertEquals(
            "已接上 · 今天用过",
            connectorStatusText(state(UiPrefs.CONNECTOR_MESSAGE, granted = true, enabled = true))
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

    // 智能等级 = 已接上的源个数

    private fun allStates(
        message: Pair<Boolean, Boolean> = true to true,
        notification: Pair<Boolean, Boolean> = true to true,
        calendar: Pair<Boolean, Boolean> = true to true,
        phone: Pair<Boolean, Boolean> = true to true,
        contacts: Pair<Boolean, Boolean> = true to true
    ) = listOf(
        state(UiPrefs.CONNECTOR_MESSAGE, message.first, message.second),
        state(UiPrefs.CONNECTOR_NOTIFICATION, notification.first, notification.second),
        state(UiPrefs.CONNECTOR_CALENDAR, calendar.first, calendar.second),
        // 电话拨号器无自己的权限：granted 取通知的授权状态（与
        // buildConnectorStates 一致），计数时另看通知源是否已接上。
        state(UiPrefs.CONNECTOR_PHONE, notification.first, phone.second),
        state(UiPrefs.CONNECTOR_CONTACTS, contacts.first, contacts.second)
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
                    message = false to false, notification = false to false,
                    calendar = false to false, phone = false to false,
                    contacts = false to false
                )
            )
        )
    }

    @Test
    fun levelCount_countsOnlyConnected() {
        assertEquals(
            1,
            connectedSourceCount(
                allStates(
                    message = true to true, notification = false to true,
                    calendar = true to false, phone = true to true,
                    contacts = false to false
                )
            )
        )
    }

    // 电话拨号器开关跟着通知走（第九批，设计稿 §2，2026-11-08 Bob 定）

    @Test
    fun dialerDisplayOff_whenNotificationSwitchOff() {
        // 通知权限已授、通知开关关、电话拨号器自己的开关开 → 电话拨号器
        // 显示关（457 的分层边界就此关掉：显示与计数一致），等级里也不算它。
        val states = allStates(
            message = true to true,
            notification = true to false,
            calendar = true to true,
            phone = true to true,
            contacts = false to false
        )
        val effective = effectiveDialerState(states)
        assertTrue(effective != null)
        assertFalse(isConnectorConnected(effective!!))
        assertEquals("已关闭", connectorStatusText(effective))
        assertFalse(isDialerRowEnabled(states))
        assertEquals(2, connectedSourceCount(states))
    }

    @Test
    fun dialerDisplayFollowsOwnState_whenNotificationOn() {
        // 通知开着 → 电话拨号器显示回到它自己的状态：自己开着就显示开、
        // 自己关着就显示关；行可点。
        val onStates = allStates(notification = true to true, phone = true to true)
        val onEffective = effectiveDialerState(onStates)
        assertTrue(onEffective != null)
        assertTrue(isConnectorConnected(onEffective!!))
        assertTrue(isDialerRowEnabled(onStates))

        val offStates = allStates(notification = true to true, phone = true to false)
        val offEffective = effectiveDialerState(offStates)
        assertTrue(offEffective != null)
        assertFalse(isConnectorConnected(offEffective!!))
        assertEquals("已关闭", connectorStatusText(offEffective))
        assertTrue(isDialerRowEnabled(offStates))
    }

    @Test
    fun dialerOwnFlagPreserved_notificationOffThenOn() {
        // 通知开关关→开一次，电话拨号器自己的启用标志不变：同一组输入状态
        // 先后算有效态，关时显示关、开时显示开——证明没丢状态（JVM 层只测
        // 推导函数，不写持久化标志；设置页也不写回 setConnectorEnabled）。
        val base = allStates(notification = true to true, phone = true to true)
        val offStates = base.map {
            if (it.key == UiPrefs.CONNECTOR_NOTIFICATION) it.copy(enabled = false) else it
        }
        assertFalse(isConnectorConnected(effectiveDialerState(offStates)!!))
        // 通知再开：输入表里的电话拨号器 enabled 仍是 true，显示回到开。
        assertTrue(isConnectorConnected(effectiveDialerState(base)!!))
        assertTrue(base.first { it.key == UiPrefs.CONNECTOR_PHONE }.enabled)
    }

    @Test
    fun dialerDisplayOff_whenNotificationNotGranted() {
        // 通知权限未授 → 电话拨号器显示关（计数口径已有覆盖，显示也要一致），
        // 行变灰点不动。
        val states = allStates(
            message = true to true,
            notification = false to true,
            calendar = true to true,
            phone = true to true,
            contacts = false to false
        )
        val effective = effectiveDialerState(states)
        assertTrue(effective != null)
        assertFalse(isConnectorConnected(effective!!))
        assertEquals("未接上", connectorStatusText(effective))
        assertFalse(isDialerRowEnabled(states))
        assertEquals(2, connectedSourceCount(states))
    }

    @Test
    fun dialerRowEnabled_followsNotificationConnected() {
        // 行可点当且仅当通知源已接上（权限已授＋通知自己的开关开）。
        assertTrue(isDialerRowEnabled(allStates(notification = true to true)))
        assertFalse(isDialerRowEnabled(allStates(notification = true to false)))
        assertFalse(isDialerRowEnabled(allStates(notification = false to true)))
        assertFalse(isDialerRowEnabled(allStates(notification = false to false)))
    }

    @Test
    fun levelCount_phoneNeedsNotificationConnected() {
        // 电话拨号器随通知计：通知权限已授、但通知自己的开关关了 → 电话
        // 不算（未接来电卡片实际也不出，仓库门控 notifOn && phoneOn）。
        assertEquals(
            2,
            connectedSourceCount(
                allStates(
                    message = true to true,
                    notification = true to false,
                    calendar = true to true,
                    phone = true to true,
                    contacts = false to false
                )
            )
        )
        // 通知接上且电话拨号器自己的开关开 → 算。
        assertEquals(
            4,
            connectedSourceCount(
                allStates(
                    message = true to true,
                    notification = true to true,
                    calendar = true to true,
                    phone = true to true,
                    contacts = false to false
                )
            )
        )
        // 通知权限未授 → 电话不算，哪怕它自己的开关开着。
        assertEquals(
            2,
            connectedSourceCount(
                allStates(
                    message = true to true,
                    notification = false to true,
                    calendar = true to true,
                    phone = true to true,
                    contacts = false to false
                )
            )
        )
    }
}
