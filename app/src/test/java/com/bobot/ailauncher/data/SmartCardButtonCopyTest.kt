package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 智能卡片按钮文案单测（第十一批，Bob 2026-11-09 定）：
 * 左“我知道了”、右“下一步”，真卡与引导卡都一样；右按钮不再显示
 * 动作文案（去还款/回拨/看预约/查看）。文案逐字锁死。
 */
class SmartCardButtonCopyTest {

    @Test
    fun dismissLabel_isWoZhiDaoLe() {
        assertEquals("我知道了", SMART_CARD_DISMISS_LABEL)
    }

    @Test
    fun nextLabel_isXiaYiBu() {
        assertEquals("下一步", SMART_CARD_NEXT_LABEL)
    }
}
