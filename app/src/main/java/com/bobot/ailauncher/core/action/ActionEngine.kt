package com.bobot.ailauncher.core.action

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.bobot.ailauncher.core.event.EventBus
import com.bobot.ailauncher.core.event.LauncherEvent
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.ExecutionMethod

/**
 * Action Engine（PRD §二十二/§二十三，技术方案 Phase 2）。
 *
 * 执行链：安全门（风险分级）→ 权限检查 → 工具选择 → 执行 → 结果回写。
 *
 * - LLM 永远不直接操作手机：只产生 [ActionRequest]，执行权在这里。
 * - D/E 级（中/高风险）未确认一律返回 [ActionResult.NeedsConfirmation]，绝不自动执行。
 * - 所有失败都以 [ActionResult.Failed] 如实返回（PRD §六十四）。
 * - 永不抛异常：调用方（UI）无需 try/catch。
 */
object ActionEngine {

    fun submit(context: Context, request: ActionRequest): ActionResult {
        // 1) 安全门：D/E 级必须用户明确确认（PRD §二十三）
        if (request.needsConfirmation) {
            return ActionResult.NeedsConfirmation(
                request = request,
                confirmText = "确定要执行吗？\n「${request.intent}」"
            )
        }

        // 2) 能力存在性
        val cap = CapabilityRegistry.find(request.capabilityId)
            ?: return fail(request, "找不到这个能力（${request.capabilityId}）")

        // 3) 权限检查（PRD §三十：缺权限要解释用途，不静默失败）
        cap.permission?.let { perm ->
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                return ActionResult.NeedsPermission(perm, "执行「${cap.label}」需要该权限")
            }
        }

        // 4) 工具选择 + 执行（v0.68.88：候选链——已装提供方深链优先序 → geo
        //    兜底 → 打开绑定的应用本体；不再「绑死一家、失败即死胡同」）
        val outcome = try {
            when (cap.executionMethod) {
                ExecutionMethod.APP_FUNCTION ->
                    return fail(request, "「${cap.label}」需要 App Functions，当前应用暂不支持")
                ExecutionMethod.ACCESSIBILITY ->
                    return fail(request, "「${cap.label}」需要无障碍操作，暂不提供")
                else ->
                    CapabilityRegistry.launchCapability(context, cap, request.params)
            }
        } catch (t: Throwable) {
            Log.w("ActionEngine", "execute failed: ${cap.id}", t)
            CapabilityRegistry.LaunchOutcome.Failed
        }

        // 5) 结果回写事件总线（成功/失败都要，PRD §六十四）+ 诚实文案分层：
        //    一步都没得试（没装任何能用的应用）≠ 试了但没打开，不许混报。
        return when (outcome) {
            is CapabilityRegistry.LaunchOutcome.Opened -> {
                EventBus.emit(LauncherEvent.TaskCompleted(request.id, true))
                ActionResult.Done("已为你打开${outcome.label}")
            }
            CapabilityRegistry.LaunchOutcome.NoProvider ->
                fail(request, "本机没有安装支持「${cap.label}」的应用")
            CapabilityRegistry.LaunchOutcome.Failed ->
                fail(request, "没能打开「${cap.label}」，换个说法试试")
        }
    }

    private fun fail(request: ActionRequest, reason: String): ActionResult.Failed {
        EventBus.emit(LauncherEvent.TaskCompleted(request.id, false))
        return ActionResult.Failed(reason)
    }
}
