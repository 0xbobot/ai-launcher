package com.bobot.ailauncher.core.intent

import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.action.ActionResult
import com.bobot.ailauncher.data.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** 模型解析结果（与 LlmRouter.PlanResult 对齐的本地抽象，方便注入与单测） */
sealed interface PlanOutcome {
    data class Chat(val text: String) : PlanOutcome
    data class Action(
        val capabilityId: String,
        val params: Map<String, String>,
        val confirmText: String?
    ) : PlanOutcome

    data class Err(val message: String) : PlanOutcome
}

/** 大模型配置读取（生产实现 = LlmConfig） */
interface IntentConfig {
    fun hasKey(): Boolean
}

/** 意图解析（生产实现 = LlmRouter.plan 的适配器） */
interface IntentPlanner {
    suspend fun plan(input: String, validIds: Set<String>, hints: String): PlanOutcome
}

/** 能力注册表读取（生产实现 = CapabilityRegistry） */
interface IntentCapabilities {
    /** 调用 validIds/hints/find 前确保已加载（幂等） */
    fun ensureLoaded()
    fun validIds(): Set<String>

    /** "id: 名称" 多行文本，供模型选能力 */
    fun hints(): String

    /** 按 id 取注册表中的真实能力（含真实 riskLevel），不存在返回 null */
    fun find(id: String): Capability?
}

/** 动作执行（生产实现 = ActionEngine.submit） */
interface IntentActionExecutor {
    fun submit(request: ActionRequest): ActionResult
}

/** 七仔发声（生产实现 = PetRepository.say） */
interface IntentSpeaker {
    fun say(text: String)
}

/**
 * 意图入口协调器（文档二 P0 逻辑层）。
 *
 * 职责边界：
 * - 只做"一句话 → 状态"的编排：解析走 [IntentPlanner]，执行走 [IntentActionExecutor]，
 *   本类不碰网络、不碰 PackageManager、不启 App。
 * - 风险级别只信注册表：构造 [ActionRequest] 时 riskLevel 取 [IntentCapabilities.find]
 *   的真实值，模型返回值里没有也不信任何风险字段。
 * - 失败如实进 [IntentUiState.Error]，文案绝不声称已完成（PRD §六十四）。
 * - 15 秒超时（[timeoutMillis]）：模型层本身也有 15 秒 callTimeout，这里再兜一层，
 *   保证 UI 不会无限停在 Planning。
 */
class IntentCoordinator(
    private val config: IntentConfig,
    private val planner: IntentPlanner,
    private val capabilities: IntentCapabilities,
    private val executor: IntentActionExecutor,
    private val speaker: IntentSpeaker,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {
    private val _state = MutableStateFlow<IntentUiState>(IntentUiState.Idle)
    val state: StateFlow<IntentUiState> = _state.asStateFlow()

    /** 最近一次提交的原文，dismiss 后回到 Editing 时带回 */
    private var lastText: String = ""

    /**
     * 提交一句话。空输入直接返回（不发请求、不改状态）。
     * 无 Key 时进可恢复 Error 并让七仔说出原因，用户配好 Key 后可直接重提。
     */
    suspend fun submit(text: String) {
        val input = text.trim()
        if (input.isBlank()) return
        // 防重守卫（意图改进一批）：Planning 在途时直接忽略新提交——连点/双击提交
        // 不得重复规划、重复执行。宿主另有「先 cancel 旧 Job 再 launch」的顶替语义，
        // 被顶替的旧协程在下方 CancellationException 分支交还编辑态，不会卡死在 Planning。
        if (_state.value is IntentUiState.Planning) return
        lastText = input
        _state.value = IntentUiState.Planning(input)

        if (!config.hasKey()) {
            speaker.say(NO_KEY_MESSAGE)
            _state.value = IntentUiState.Error(NO_KEY_MESSAGE)
            return
        }

        capabilities.ensureLoaded()
        val outcome = try {
            withTimeoutOrNull(timeoutMillis) {
                planner.plan(input, capabilities.validIds(), capabilities.hints())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 宿主顶替取消（新提交 cancel 旧 Job）：若还停在 Planning 就交还编辑态
            // （带回原文可直接改/重提），避免状态永久卡在思考中；随后照常上抛。
            if (_state.value is IntentUiState.Planning) dismiss()
            throw e
        } catch (e: Exception) {
            PlanOutcome.Err("请求失败：${e.message.orEmpty().ifBlank { "未知错误" }}")
        }
        when (outcome) {
            null -> fail("模型响应超时，请重试")
            is PlanOutcome.Chat -> reply(outcome.text)
            is PlanOutcome.Action -> submitAction(input, outcome)
            is PlanOutcome.Err -> fail(outcome.message)
        }
    }

    /** 用户在确认态点了"执行"：以 confirmed=true 重新提交，只执行一次 */
    fun confirm(request: ActionRequest) {
        // 一次性守卫（意图改进一批）：只认当前正挂起的这一单——连点「执行」、
        // 或旧弹窗的迟到回调，看到的已不是确认态（或不是同一单），直接忽略。
        val pending = _state.value as? IntentUiState.NeedsConfirmation ?: return
        if (pending.request != request) return
        // 先离开确认态再执行：执行期间的第二次 confirm 必然落空，不会重复执行。
        _state.value = IntentUiState.Planning(request.intent)
        execute(request.copy(confirmed = true), isConfirm = true)
    }

    /** 取消/关闭当前反馈：回到最近输入的编辑态，没有输入则回 Idle */
    fun dismiss() {
        _state.value = if (lastText.isNotBlank()) {
            IntentUiState.Editing(lastText)
        } else {
            IntentUiState.Idle
        }
    }

    private fun submitAction(originalText: String, action: PlanOutcome.Action) {
        // 风险级别只信注册表：find 不到（非法 id / 注册表未含）直接失败，绝不执行
        val cap = capabilities.find(action.capabilityId)
        if (cap == null) {
            fail("找不到这个能力，无法执行")
            return
        }
        val request = ActionRequest(
            intent = originalText,
            capabilityId = cap.id,
            params = action.params,
            riskLevel = cap.riskLevel
        )
        execute(request, isConfirm = false)
    }

    private fun execute(request: ActionRequest, isConfirm: Boolean) {
        when (val result = executor.submit(request)) {
            is ActionResult.Done -> reply(result.message)
            is ActionResult.NeedsConfirmation -> {
                if (isConfirm) {
                    // 已确认过还要求确认：不再循环，明确失败（不声称已完成）
                    fail("无法确认这项操作")
                } else {
                    _state.value = IntentUiState.NeedsConfirmation(result.request, result.confirmText)
                }
            }
            is ActionResult.NeedsPermission -> {
                _state.value = IntentUiState.NeedsPermission(result.permission, result.rationale)
                speaker.say(result.rationale)
            }
            is ActionResult.Failed -> fail(result.reason)
        }
    }

    private fun reply(text: String) {
        _state.value = IntentUiState.Reply(text)
        speaker.say(text)
    }

    private fun fail(message: String) {
        _state.value = IntentUiState.Error(message)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        const val NO_KEY_MESSAGE = "先在设置里配置大模型 Key，才能理解这句话"
    }
}
