package com.bobot.ailauncher.core.brain

/**
 * AI Brain 的决策输出（PRD §四十三）。
 *
 * - [ActionRequest]：需要执行手机操作，走 ActionEngine（Phase 2），过安全门。
 * - [Nothing]：合法的一等结果——AI 判断现在不需要做任何事（PRD §四十三/四十四）。
 */
sealed interface Decision {
    /** 手机操作请求：Phase 2 由 ActionEngine 执行，本阶段仅占位 */
    data class ActionRequest(
        val intent: String,
        val capabilityId: String,
        val params: Map<String, String> = emptyMap()
    ) : Decision

    /** 什么都不做 */
    data object Nothing : Decision
}
