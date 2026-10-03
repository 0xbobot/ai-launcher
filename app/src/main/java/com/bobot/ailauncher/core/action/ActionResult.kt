package com.bobot.ailauncher.core.action

/**
 * 动作执行结果（PRD §六十四：失败必须如实反馈，不许假装成功）。
 */
sealed interface ActionResult {
    /** 执行成功 */
    data class Done(val message: String) : ActionResult

    /** D/E 级：需要用户明确确认，UI 弹窗展示 confirmText */
    data class NeedsConfirmation(
        val request: ActionRequest,
        val confirmText: String
    ) : ActionResult

    /** 缺权限：UI 解释用途并引导授权（PRD §三十），不静默失败 */
    data class NeedsPermission(
        val permission: String,
        val rationale: String
    ) : ActionResult

    /** 失败：把真实原因告诉用户 */
    data class Failed(val reason: String) : ActionResult
}
