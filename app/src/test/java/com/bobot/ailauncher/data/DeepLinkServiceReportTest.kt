package com.bobot.ailauncher.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 反馈闭环·深链服务上报（Bob 2026-11-06：用户选了应用后，把映射同步上报
 * 给深链服务）单测：上报体字段、空地址短路。网络发送本身不单测（无服务器）。
 */
class DeepLinkServiceReportTest {

    @Test
    fun `buildReportBody carries the v1 required fields`() {
        val body = DeepLinkServiceReport.buildReportBody(
            requestId = "req-1",
            opened = true,
            packageName = "com.iqiyi",
            capabilityId = "shipin",
            query = "我想看《狂飙》"
        )
        assertTrue(body.contains("\"request_id\":\"req-1\""))
        assertTrue(body.contains("\"outcome\":\"opened\""))
        assertTrue(body.contains("\"provider\":\"com.iqiyi\""))
        assertTrue(body.contains("\"type\":\"user_mapping\""))
        assertTrue(body.contains("\"intent\":\"shipin\""))
        assertTrue(body.contains("\"query\":\"我想看《狂飙》\""))
        assertTrue(body.contains("\"source\":\"app-center-feedback\""))
    }

    @Test
    fun `buildReportBody uses failed outcome and unknown intent when the app did not open`() {
        val body = DeepLinkServiceReport.buildReportBody(
            requestId = "req-2",
            opened = false,
            packageName = "com.iqiyi",
            capabilityId = null,
            query = "我想看《狂飙》"
        )
        assertTrue(body.contains("\"outcome\":\"failed\""))
        assertTrue(body.contains("\"intent\":\"unknown\""))
    }

    @Test
    fun `buildReportBody escapes double quotes in the query`() {
        val body = DeepLinkServiceReport.buildReportBody(
            requestId = "req-3",
            opened = true,
            packageName = "com.iqiyi",
            capabilityId = "shipin",
            query = "看\"狂飙\""
        )
        assertTrue(body.contains("\\\"狂飙\\\""))
    }

    @Test
    fun `reportUserMapping is a no-op while the service is not deployed`() {
        // 服务地址未配（空串）：不发任何请求、不抛异常。
        // 若将来配了地址，本测试先失败（提醒改测试），而不是悄悄发真请求。
        assertTrue(DeepLinkServiceReport.SERVICE_BASE_URL.isBlank())
        DeepLinkServiceReport.reportUserMapping(
            query = "我想看《狂飙》",
            packageName = "com.iqiyi",
            capabilityId = "shipin",
            opened = true
        )
        // 能执行到这里即通过（无异常、无网络尝试）。
    }
}
