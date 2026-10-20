package com.bobot.ailauncher.core.intent

import com.bobot.ailauncher.core.action.ActionRequest

/**
 * 意图入口 UI 状态（文档二 P0：恢复真实意图入口闭环）。
 *
 * 纯状态模型：只描述"界面现在该呈现什么"，不持有 Android 依赖。
 * 状态流转由 [IntentCoordinator] 驱动，UI（IntentEntry）只读渲染。
 */
sealed interface IntentUiState {
    /** 未输入、未提交 */
    data object Idle : IntentUiState

    /** 用户正在编辑输入框 */
    data class Editing(val text: String) : IntentUiState

    /** 已提交，正在等模型解析 */
    data class Planning(val text: String) : IntentUiState

    /** 中/高风险动作：等用户确认，request 是待执行的原始请求（未确认版） */
    data class NeedsConfirmation(val request: ActionRequest, val message: String) : IntentUiState

    /** 缺权限：message 说明用途，UI 负责引导授权 */
    data class NeedsPermission(val permission: String, val message: String) : IntentUiState

    /** 纯文本回复（含动作执行成功文案），走七仔气泡呈现 */
    data class Reply(val text: String) : IntentUiState

    /** 失败/不可用：message 如实说明原因，绝不声称已完成 */
    data class Error(val message: String) : IntentUiState
}
