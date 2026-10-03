package com.bobot.ailauncher.core.event

import com.bobot.ailauncher.data.SimpleNotification

/**
 * 全 App 统一事件（PRD §四十二）。
 *
 * 约定：
 * - 所有跨层通信（System/UI → AI Brain → 执行）只走 EventBus，不允许 UI 直调引擎。
 * - 事件是事实陈述（"发生了什么"），不携带"应该怎么做"——决策权在 AiBrain。
 * - 新增事件时同步更新 docs/TECHNICAL_DESIGN.md §四 的事件表。
 */
sealed interface LauncherEvent {

    // ---------- 系统 / 设备 ----------
    data object ScreenOn : LauncherEvent
    data object ScreenOff : LauncherEvent

    /** 前台 App 变化（Phase 3：UsageStats 轮询/事件源接入后启用） */
    data class AppOpened(val packageName: String) : LauncherEvent
    data class AppClosed(val packageName: String) : LauncherEvent

    // ---------- 通知 ----------
    data class NotificationReceived(val notification: SimpleNotification) : LauncherEvent
    data class NotificationRemoved(val key: String) : LauncherEvent

    // ---------- 用户交互 ----------
    data object UserTappedPet : LauncherEvent
    data object VoiceStarted : LauncherEvent
    data object VoiceEnded : LauncherEvent

    /** Action 执行结束：成功或失败都要回写（PRD §六十四：失败不许假装成功） */
    data class TaskCompleted(val taskId: String, val success: Boolean) : LauncherEvent

    /** Context 发生变化（时间/地点/前台 App/网络等），Phase 3 由 ContextEngine 发出 */
    data object ContextChanged : LauncherEvent

    // ---------- Launcher 自身 ----------
    data class DockStateChanged(val state: String) : LauncherEvent
}
