package com.bobot.ailauncher.core.brain

import android.content.Context
import com.bobot.ailauncher.core.event.LauncherEvent
import com.bobot.ailauncher.data.PetRepository

/**
 * AI Brain：Launcher 与 AI 的解耦点（PRD §三十九/§六十评审结论 1，§七十三）。
 *
 * 决策环（PRD §四十三）：
 * ```
 * Event → Context 更新 → 该想吗？ → 决策 → 执行 / 宠物行为 / Nothing
 * ```
 *
 * Phase 1 范围（行为与 v0.16.1 完全一致）：
 * - 只有 [LauncherEvent.NotificationReceived] 进入决策，走原有
 *   PetRepository.handleIncomingNotification 链路（分类/聚合/呈现逻辑不动）。
 * - 其余事件一律 [Decision.Nothing]——Nothing 是合法结果，不打扰用户。
 *
 * 后续 Phase 在此骨架上扩展：shouldThink 接入 Attention Budget（PRD §十四），
 * decide 接入 Intent/Context/Memory 引擎，ActionRequest 接入 ActionEngine（Phase 2）。
 */
object AiBrain {

    /** 事件总入口：由 MainActivity 的事件收集协程调用。永不抛异常。 */
    fun onEvent(event: LauncherEvent, context: Context) {
        try {
            // TODO(Phase 3): ContextEngine.update(event, context)
            if (!shouldThink(event)) return
            when (val decision = decide(event, context)) {
                is Decision.PetBehavior -> {
                    // 宠物行为走 PetRepository 的表达层（Phase 4 扩展）
                    android.util.Log.d("AiBrain", "PetBehavior: ${decision.description}")
                }
                is Decision.ActionRequest -> {
                    // TODO(Phase 2): ActionEngine.submit(decision)
                    android.util.Log.d("AiBrain", "ActionRequest queued: ${decision.intent}")
                }
                Decision.Nothing -> Unit
            }
        } catch (t: Throwable) {
            // Brain 永远不能拖垮 Launcher
            android.util.Log.w("AiBrain", "onEvent failed, ignored", t)
        }
    }

    /**
     * 该想吗？Phase 1 保守策略：只处理通知事件。
     * 后续接入：Attention Budget、免打扰时段、用户忽略后的降频（PRD §十四）。
     */
    private fun shouldThink(event: LauncherEvent): Boolean =
        event is LauncherEvent.NotificationReceived

    private fun decide(event: LauncherEvent, context: Context): Decision {
        return when (event) {
            is LauncherEvent.NotificationReceived -> {
                // 行为保持与 v0.16.1 一致：分类 → 聚合 → 呈现/归档
                PetRepository.handleIncomingNotification(event.notification)
                Decision.Nothing
            }
            else -> Decision.Nothing
        }
    }
}
