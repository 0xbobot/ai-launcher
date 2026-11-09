package com.bobot.ailauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 还款目标解析单测（第十一批，设计稿 §3）：纯函数、JVM 可跑。
 * 顺序：银行简称匹配的银行应用（最常用那家）→ 云闪付 / 支付宝 / 微信
 * （按最近使用排）→ null（调用方退回短信会话）。银行与支付两路都走
 * 同一条标签匹配＋最近使用排序，不另写一套匹配器。
 */
class RepaymentTargetTest {

    private val installed = listOf(
        "com.china.citicbank" to "中信银行",
        "com.unionpay" to "云闪付",
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.tencent.mm" to "微信",
        "com.cmbchina.ccd.pluto.cmbActivity" to "掌上生活",
        "com.android.mms" to "信息"
    )

    @Test
    fun matchPaymentAppCandidates_findsThreePaymentApps() {
        val matched = SmartCardActions.matchPaymentAppCandidates(installed)
        assertEquals(
            listOf("com.unionpay", "com.eg.android.AlipayGphone", "com.tencent.mm"),
            matched
        )
    }

    @Test
    fun matchPaymentAppCandidates_noPaymentApps() {
        val matched = SmartCardActions.matchPaymentAppCandidates(
            listOf("com.china.citicbank" to "中信银行", "com.android.mms" to "信息")
        )
        assertEquals(emptyList<String>(), matched)
    }

    @Test
    fun selectByRecency_picksMostRecentlyUsed() {
        val lastUsed = mapOf(
            "com.unionpay" to 100L,
            "com.eg.android.AlipayGphone" to 300L,
            "com.tencent.mm" to 200L
        )
        val picked = SmartCardActions.selectByRecency(
            listOf("com.unionpay", "com.eg.android.AlipayGphone", "com.tencent.mm")
        ) { lastUsed[it] ?: 0L }
        assertEquals("com.eg.android.AlipayGphone", picked)
    }

    @Test
    fun selectByRecency_neverUsedFallsBackToFirst() {
        val picked = SmartCardActions.selectByRecency(
            listOf("com.unionpay", "com.eg.android.AlipayGphone")
        ) { 0L }
        assertEquals("com.unionpay", picked)
    }

    @Test
    fun selectByRecency_emptyReturnsNull() {
        assertNull(SmartCardActions.selectByRecency(emptyList()) { 0L })
    }

    @Test
    fun selectRepaymentTarget_bankFirstEvenIfPaymentUsedMoreRecently() {
        // 银行应用最懂这张卡：银行候选优先，不与支付候选按使用时间混排。
        val picked = SmartCardActions.selectRepaymentTarget(
            bankCandidates = listOf("com.china.citicbank"),
            paymentCandidates = listOf("com.eg.android.AlipayGphone"),
            lastUsed = {
                when (it) {
                    "com.china.citicbank" -> 100L
                    "com.eg.android.AlipayGphone" -> 999L
                    else -> 0L
                }
            }
        )
        assertEquals("com.china.citicbank", picked)
    }

    @Test
    fun selectRepaymentTarget_noBankFallsBackToPayment() {
        val picked = SmartCardActions.selectRepaymentTarget(
            bankCandidates = emptyList(),
            paymentCandidates = listOf("com.unionpay", "com.tencent.mm"),
            lastUsed = {
                when (it) {
                    "com.unionpay" -> 50L
                    "com.tencent.mm" -> 400L
                    else -> 0L
                }
            }
        )
        assertEquals("com.tencent.mm", picked)
    }

    @Test
    fun selectRepaymentTarget_noneReturnsNull() {
        assertNull(
            SmartCardActions.selectRepaymentTarget(emptyList(), emptyList()) { 0L }
        )
    }

    @Test
    fun repaymentDeepLink_verifiedThree() {
        assertEquals(
            "alipays://platformapi/startapp?appId=09999999",
            SmartCardActions.repaymentDeepLink("com.eg.android.AlipayGphone")
        )
        assertEquals(
            "upwallet://rn/rncredit",
            SmartCardActions.repaymentDeepLink("com.unionpay")
        )
        assertEquals(
            "cmblife://cfp/BillRepayment?needLogin=1&bindCard=1000",
            SmartCardActions.repaymentDeepLink("com.cmbchina.ccd.pluto.cmbActivity")
        )
    }

    @Test
    fun repaymentDeepLink_unverifiedReturnsNull() {
        // 微信：没有信用卡还款页的深链；中信银行：只有裸 scheme、无还款页路径；
        // 其他银行：没有公开的还款页深链——一律退回打开应用，不硬凑。
        assertNull(SmartCardActions.repaymentDeepLink("com.tencent.mm"))
        assertNull(SmartCardActions.repaymentDeepLink("cn.citicbank.wap"))
        assertNull(SmartCardActions.repaymentDeepLink("com.china.citicbank"))
        assertNull(SmartCardActions.repaymentDeepLink("com.android.mms"))
    }
}
