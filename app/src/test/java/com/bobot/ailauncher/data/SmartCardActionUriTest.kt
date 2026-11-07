package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 智能卡片动作 URI 与银行应用匹配单测（447）：纯函数、JVM 可跑。
 * 动作落点（设计稿 §3）：去还款落到用户真正用的那家银行应用（标签匹配＋
 * 最近使用排），不用写死一家银行；匹配本身是纯字符串逻辑，在此锁死。
 */
class SmartCardActionUriTest {

    @Test
    fun smsUri_isSmsto() {
        assertEquals("smsto:95558", SmartCardActions.smsUri("95558"))
        assertEquals("smsto:13812345678", SmartCardActions.smsUri("13812345678"))
    }

    @Test
    fun telUri_isTel() {
        assertEquals("tel:13812345678", SmartCardActions.telUri("13812345678"))
    }

    @Test
    fun matchBankApp_labelContainsShortName() {
        val installed = listOf(
            "com.china.citicbank" to "中信银行",
            "com.tencent.mm" to "微信",
            "com.citic.credit" to "中信银行信用卡",
            "com.cmbchina.ccd" to "招商银行"
        )
        val matched = SmartCardActions.matchBankAppCandidates(installed, "中信银行")
        assertEquals(
            listOf("com.china.citicbank", "com.citic.credit"),
            matched
        )
    }

    @Test
    fun matchBankApp_noMatchReturnsEmpty() {
        val installed = listOf(
            "com.tencent.mm" to "微信",
            "com.cmbchina.ccd" to "招商银行"
        )
        assertTrue(
            SmartCardActions.matchBankAppCandidates(installed, "中信银行").isEmpty()
        )
    }

    @Test
    fun matchBankApp_bankNameWithoutSuffix() {
        val installed = listOf("com.bank.abc" to "农业银行掌上银行")
        assertEquals(
            listOf("com.bank.abc"),
            SmartCardActions.matchBankAppCandidates(installed, "农业银行")
        )
    }
}
