package com.bobot.ailauncher.data

import com.bobot.ailauncher.ui.smartcard.shouldShowAppSubscript
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用小标画不画单测（第四十九批，Bob 2026-11-21 定审计 A2a：未接来电卡
 * 小标“电话”去掉——按钮“回拨”必然开拨号盘，小标不增加信息）。
 *
 * 锁死的是纯函数 shouldShowAppSubscript（标题行右上角画不画小标的判定）：
 * 未接来电卡一律不画；其他真卡照常画；引导卡本来就不画；“已经连上了”
 * 反馈卡（hideActionRow）不画。布局本身 JVM 测不到（Compose 排版），
 * 见收据的 diff 复核＋Bob 真机验收。
 */
class SmartCardSubscriptTest {

    private fun card(
        kind: SmartCardKind,
        targetApp: TargetApp? = TargetApp("com.example.app", "应用")
    ) = SmartCard(
        id = "t|${kind.name}",
        kind = kind,
        sourceLabel = "短信",
        fact = "fact",
        prep = null,
        actionLabel = "动作",
        sortMs = 100L,
        deadlineMs = 200L,
        action = SmartCardAction.SmsThread("95558"),
        sourceKey = "t|${kind.name}",
        targetApp = targetApp
    )

    // ---------- 未接来电卡：不画小标（本批改动） ----------

    @Test
    fun missedCall_noSubscript() {
        assertFalse(shouldShowAppSubscript(card(SmartCardKind.MISSED_CALL), false))
    }

    @Test
    fun missedCall_noSubscriptEvenWhenHiddenActionRow() {
        assertFalse(shouldShowAppSubscript(card(SmartCardKind.MISSED_CALL), true))
    }

    // ---------- 其他真卡：照常画小标 ----------

    @Test
    fun bill_showsSubscript() {
        assertTrue(shouldShowAppSubscript(card(SmartCardKind.BILL), false))
    }

    @Test
    fun appointment_showsSubscript() {
        assertTrue(shouldShowAppSubscript(card(SmartCardKind.APPOINTMENT), false))
    }

    @Test
    fun calendar_showsSubscript() {
        // 日历卡小标是事件所在的日历账户名（如“豆包日历”，无包名、只画文字）——
        // 审计 A2b：Bob 2026-11-21 定不改，始终显示，本批不动。
        val calendarCard = card(
            SmartCardKind.CALENDAR,
            TargetApp(packageName = null, label = "豆包日历")
        )
        assertTrue(shouldShowAppSubscript(calendarCard, false))
    }

    @Test
    fun notification_showsSubscript() {
        assertTrue(shouldShowAppSubscript(card(SmartCardKind.NOTIFICATION), false))
    }

    @Test
    fun dayOverview_showsSubscript() {
        assertTrue(shouldShowAppSubscript(card(SmartCardKind.DAY_OVERVIEW), false))
    }

    @Test
    fun defaultLauncher_showsSubscript() {
        assertTrue(shouldShowAppSubscript(card(SmartCardKind.DEFAULT_LAUNCHER), false))
    }

    @Test
    fun update_showsSubscript() {
        assertTrue(shouldShowAppSubscript(card(SmartCardKind.UPDATE), false))
    }

    // ---------- 引导卡 / 反馈卡：本来就不画 ----------

    @Test
    fun guide_noSubscript() {
        assertFalse(shouldShowAppSubscript(card(SmartCardKind.GUIDE), false))
    }

    @Test
    fun hideActionRow_noSubscriptForAnyKind() {
        // “已经连上了”反馈卡：hideActionRow 时不画，与卡种无关。
        assertFalse(shouldShowAppSubscript(card(SmartCardKind.BILL), true))
        assertFalse(shouldShowAppSubscript(card(SmartCardKind.CALENDAR), true))
    }
}
