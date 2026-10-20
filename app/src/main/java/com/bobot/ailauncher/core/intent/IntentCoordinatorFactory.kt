package com.bobot.ailauncher.core.intent

import android.content.Context
import com.bobot.ailauncher.core.action.ActionEngine
import com.bobot.ailauncher.core.action.ActionRequest
import com.bobot.ailauncher.core.action.ActionResult
import com.bobot.ailauncher.data.Capability
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.LlmConfig
import com.bobot.ailauncher.data.LlmRouter
import com.bobot.ailauncher.data.PetRepository

/**
 * 生产装配：把 [IntentCoordinator] 的注入接口接到真实实现
 * （LlmConfig / LlmRouter.plan / CapabilityRegistry / ActionEngine / PetRepository）。
 * 单测不走这里，用假实现替换各接口。
 */
object IntentCoordinatorFactory {

    fun create(context: Context): IntentCoordinator {
        val appContext = context.applicationContext
        return IntentCoordinator(
            config = LlmConfigAdapter(appContext),
            planner = LlmPlannerAdapter(appContext),
            capabilities = RegistryAdapter(appContext),
            executor = EngineAdapter(appContext),
            speaker = PetSpeaker
        )
    }

    private class LlmConfigAdapter(private val context: Context) : IntentConfig {
        override fun hasKey(): Boolean = LlmConfig.hasKey(context)
    }

    private class LlmPlannerAdapter(private val context: Context) : IntentPlanner {
        override suspend fun plan(
            input: String,
            validIds: Set<String>,
            hints: String
        ): PlanOutcome =
            when (
                val r = LlmRouter.plan(
                    baseUrl = LlmConfig.getBaseUrl(context),
                    apiKey = LlmConfig.getApiKey(context),
                    model = LlmConfig.getModel(context),
                    input = input,
                    validIds = validIds,
                    hints = hints
                )
            ) {
                is LlmRouter.PlanResult.Chat -> PlanOutcome.Chat(r.text)
                is LlmRouter.PlanResult.Action ->
                    PlanOutcome.Action(r.capabilityId, r.params, r.confirmText)
                is LlmRouter.PlanResult.Err -> PlanOutcome.Err(r.message)
            }
    }

    private class RegistryAdapter(private val context: Context) : IntentCapabilities {
        override fun ensureLoaded() = CapabilityRegistry.load(context)

        override fun validIds(): Set<String> = CapabilityRegistry.validIds()

        override fun hints(): String =
            CapabilityRegistry.groups()
                .flatMap { it.capabilities }
                .joinToString("\n") { "${it.id}: ${it.label}" }

        override fun find(id: String): Capability? = CapabilityRegistry.find(id)
    }

    private class EngineAdapter(private val context: Context) : IntentActionExecutor {
        override fun submit(request: ActionRequest): ActionResult =
            ActionEngine.submit(context, request)
    }

    private object PetSpeaker : IntentSpeaker {
        override fun say(text: String) = PetRepository.say(text)
    }
}
