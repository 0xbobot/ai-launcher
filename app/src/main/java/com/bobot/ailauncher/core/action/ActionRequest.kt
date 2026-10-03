package com.bobot.ailauncher.core.action

import com.bobot.ailauncher.data.RiskLevel

/**
 * 动作请求（PRD §二十二）。
 *
 * 由 Intent 解析产生（关键词降级 / LLM 结构化输出），经 [ActionEngine.submit] 执行。
 * D/E 级（中/高风险）必须用户明确确认后才能执行第二次提交。
 */
data class ActionRequest(
    val id: String = "a${System.currentTimeMillis()}",
    /** 用户原话 */
    val intent: String,
    val capabilityId: String,
    val params: Map<String, String> = emptyMap(),
    val riskLevel: RiskLevel,
    /** 用户已在确认弹窗中点过"执行" */
    val confirmed: Boolean = false
) {
    /** D/E 级且尚未确认 → 必须先弹窗 */
    val needsConfirmation: Boolean
        get() = !confirmed &&
            (riskLevel == RiskLevel.MEDIUM_RISK || riskLevel == RiskLevel.HIGH_RISK)
}
